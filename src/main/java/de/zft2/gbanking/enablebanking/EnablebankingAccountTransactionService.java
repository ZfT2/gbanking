package de.zft2.gbanking.enablebanking;

import static de.zft2.gbanking.enablebanking.EnablebankingJson.decimal;
import static de.zft2.gbanking.enablebanking.EnablebankingJson.firstText;
import static de.zft2.gbanking.enablebanking.EnablebankingJson.object;
import static de.zft2.gbanking.enablebanking.EnablebankingJson.string;
import static de.zft2.gbanking.enablebanking.EnablebankingJson.upper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccessEnablebanking;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountRetrievalStatus;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.Psd2ClientConfiguration;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog;
import de.zft2.gbanking.mapper.BookingCurrencyMapper;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.account.AccountTransactionRetrievalResult;
import de.zft2.gbanking.service.account.AccountTransactionService;
import de.zft2.gbanking.service.account.PendingBookingSnapshot;

public class EnablebankingAccountTransactionService extends AbstractDbService {

	private static final int RETRIEVAL_OVERLAP_DAYS = 7;
	private static final int RATE_LIMIT_COOLDOWN_HOURS = 6;
	private static final int REAUTHORIZATION_OFFER_DAYS = 90;
	private static final List<Integer> INITIAL_LOOKBACK_DAYS = List.of(1440, 1080, 720, 360, 180, 90);
	private static final Set<String> PENDING_STATUSES = Set.of("PDNG", "HOLD");
	private static final Set<String> BOOKED_STATUSES = Set.of("BOOK");
	private static final Logger log = LogManager.getLogger(EnablebankingAccountTransactionService.class);

	private final AccountTransactionService accountTransactionService;
	private final EnablebankingAuthorizationService authorizationService;
	private final ThreadLocal<Map<Integer, AccountTransactionRetrievalResult>> completedBatchResults =
			ThreadLocal.withInitial(HashMap::new);

	public EnablebankingAccountTransactionService() {
		this(ServiceRegistry.getService(AccountTransactionService.class), new EnablebankingAuthorizationService());
	}

	EnablebankingAccountTransactionService(AccountTransactionService accountTransactionService,
			EnablebankingAuthorizationService authorizationService) {
		this.accountTransactionService = accountTransactionService;
		this.authorizationService = authorizationService;
	}

	public AccountTransactionRetrievalResult retrieve(BankAccount bankAccount) {
		AccountTransactionRetrievalResult completedResult = takeCompletedBatchResult(bankAccount);
		if (completedResult != null) {
			return completedResult;
		}
		BankAccess bankAccess = getAccess(bankAccount);
		if (bankAccess == null) {
			return persist(bankAccount, AccountTransactionRetrievalResult.failure(
					getText("ERROR_ACCOUNT_TRANSACTION_RETRIEVAL_NO_BANK_ACCESS")));
		}
		BankAccessEnablebanking accessData = bankAccess.getEnablebanking();
		if (isRateLimited(accessData)) {
			return persist(bankAccount, AccountTransactionRetrievalResult.failure(
					getText("ERROR_ENABLEBANKING_RATE_LIMIT", accessData.getRateLimitUntil())));
		}
		HbciCallbackMessageDialog statusDialog = new HbciCallbackMessageDialog(
				DialogWindowSupport.findBestOwnerWindow().orElse(null));
		boolean successful = false;
		statusDialog.showDialog();
		updateStatus(statusDialog, 0d, "UI_DIALOG_HBCI_STATUS_CONNECTING");
		try {
			Psd2ClientConfiguration configuration = getConfiguration(accessData);
			EnablebankingApiClient client = new EnablebankingApiClient(configuration);
			if (shouldOfferReauthorization(bankAccount) && requestReauthorization(bankAccess, statusDialog)) {
				BatchRetrieval batch = reauthorizeAndRetrieve(bankAccess, configuration, client, statusDialog);
				completedBatchResults.get().putAll(batch.results());
				AccountTransactionRetrievalResult result = takeCompletedBatchResult(bankAccount);
				if (result == null) {
					result = persist(bankAccount, AccountTransactionRetrievalResult.failure(
							getText("ERROR_ENABLEBANKING_REAUTHORIZATION")));
				}
				successful = batch.successful() && result.successful();
				return result;
			}
			updateStatus(statusDialog, 0.1d, "UI_DIALOG_ENABLEBANKING_STATUS_SESSION");
			String previousSessionId = accessData.getSessionId();
			EnablebankingSession session = ensureAuthorizedSession(bankAccess, configuration, client, statusDialog);
			LocalDate from = previousSessionId != null && previousSessionId.equals(session.sessionId())
					? resolveStart(bankAccount) : null;
			AccountTransactionRetrievalResult result = retrieve(bankAccount, bankAccess, client, session,
					from, statusDialog);
			successful = result.successful();
			return result;
		} catch (EnablebankingException exception) {
			if (exception.isRateLimited()) {
				accessData.setRateLimitUntil(OffsetDateTime.now(ZoneOffset.UTC).plusHours(RATE_LIMIT_COOLDOWN_HOURS));
				dbController.insertOrUpdate(bankAccess);
			}
			statusDialog.appendMessages(exception.getMessage());
			statusDialog.updateCurrentAction(exception.getMessage());
			return persist(bankAccount, AccountTransactionRetrievalResult.failure(exception.getMessage()));
		} finally {
			statusDialog.markFinished(successful);
		}
	}

	public boolean reauthorizeAndRetrieve(BankAccess selectedAccess) {
		BankAccess bankAccess = selectedAccess != null ? dbController.getBankAccessById(selectedAccess.getId()) : null;
		if (bankAccess == null || bankAccess.getAccessType() != BankAccessType.ENABLEBANKING
				|| bankAccess.getEnablebanking() == null) {
			throw new EnablebankingException(getText("ERROR_ENABLEBANKING_REAUTHORIZATION_ACCESS"));
		}
		HbciCallbackMessageDialog statusDialog = new HbciCallbackMessageDialog(
				DialogWindowSupport.findBestOwnerWindow().orElse(null));
		boolean successful = false;
		statusDialog.showDialog();
		try {
			updateStatus(statusDialog, 0d, "UI_DIALOG_HBCI_STATUS_CONNECTING");
			BankAccessEnablebanking accessData = bankAccess.getEnablebanking();
			Psd2ClientConfiguration configuration = getConfiguration(accessData);
			EnablebankingApiClient client = new EnablebankingApiClient(configuration);
			successful = reauthorizeAndRetrieve(bankAccess, configuration, client, statusDialog).successful();
			return successful;
		} catch (EnablebankingException exception) {
			statusDialog.appendMessages(exception.getMessage());
			statusDialog.updateCurrentAction(exception.getMessage());
			return false;
		} finally {
			statusDialog.markFinished(successful);
		}
	}

	private BatchRetrieval reauthorizeAndRetrieve(BankAccess bankAccess, Psd2ClientConfiguration configuration,
			EnablebankingApiClient client, HbciCallbackMessageDialog statusDialog) {
		BankAccessEnablebanking accessData = bankAccess.getEnablebanking();
		EnablebankingSession session = authorizeSession(bankAccess, configuration, client, statusDialog);
		accessData.setRateLimitUntil(null);
		dbController.insertOrUpdate(bankAccess);

		List<BankAccount> accounts = dbController.getAllByParent(BankAccount.class, bankAccess.getId());
		if (accounts.isEmpty()) {
			throw new EnablebankingException(getText("ERROR_ENABLEBANKING_REAUTHORIZATION_NO_ACCOUNTS"));
		}
		Map<Integer, AccountTransactionRetrievalResult> results = new HashMap<>();
		boolean successful = true;
		for (int index = 0; index < accounts.size(); index++) {
			BankAccount account = accounts.get(index);
			updateStatus(statusDialog, 0.25d, "UI_DIALOG_ENABLEBANKING_STATUS_ACCOUNT_RETRIEVAL",
					index + 1, accounts.size(), account.getAccountName());
			try {
				AccountTransactionRetrievalResult result = retrieve(account, bankAccess, client, session, null, statusDialog);
				results.put(account.getId(), result);
				successful &= result.successful();
			} catch (EnablebankingException exception) {
				successful = false;
				statusDialog.appendMessages(exception.getMessage());
				results.put(account.getId(), persist(account, AccountTransactionRetrievalResult.failure(exception.getMessage())));
				if (exception.isRateLimited()) {
					accessData.setRateLimitUntil(OffsetDateTime.now(ZoneOffset.UTC).plusHours(RATE_LIMIT_COOLDOWN_HOURS));
					dbController.insertOrUpdate(bankAccess);
					for (BankAccount remainingAccount : accounts.subList(index + 1, accounts.size())) {
						results.put(remainingAccount.getId(), persist(remainingAccount,
								AccountTransactionRetrievalResult.failure(exception.getMessage())));
					}
					break;
				}
			}
		}
		updateStatus(statusDialog, 0.95d, "UI_DIALOG_ENABLEBANKING_STATUS_REAUTHORIZATION_COMPLETE");
		return new BatchRetrieval(Map.copyOf(results), successful);
	}

	private AccountTransactionRetrievalResult retrieve(BankAccount bankAccount, BankAccess bankAccess,
			EnablebankingApiClient client, EnablebankingSession session, LocalDate from,
			HbciCallbackMessageDialog statusDialog) {
		BankAccessEnablebanking accessData = bankAccess.getEnablebanking();
		updateStatus(statusDialog, 0.3d, "UI_DIALOG_ENABLEBANKING_STATUS_ACCOUNT");
		EnablebankingRemoteAccount remoteAccount = findRemoteAccount(bankAccount, session.accounts());
		String accountUid = remoteAccount.uid();
		remoteAccount = client.getAccountDetails(accountUid);
		EnablebankingSetupService.mapAccount(accessData.getAspspName(), accessData.getAspspCountry(),
				remoteAccount, bankAccount);
		dbController.insertOrUpdate(bankAccount);
		TransactionRetrieval retrieval = retrieveTransactions(client, accountUid, from, statusDialog);
		updateStatus(statusDialog, 0.7d, "UI_DIALOG_ENABLEBANKING_STATUS_PROCESSING");
		MappedTransactions mapped = mapTransactions(bankAccount, retrieval.transactions(), retrieval.from());
		updateStatus(statusDialog, 0.8d, "UI_DIALOG_ENABLEBANKING_STATUS_BALANCE");
		Optional<BigDecimal> balance = resolveBookedBalance(client.getBalances(accountUid), bankAccount.getBaseCurrency());
		accessData.setRateLimitUntil(null);
		dbController.insertOrUpdate(bankAccess);
		updateStatus(statusDialog, 0.9d, "UI_DIALOG_ENABLEBANKING_STATUS_SAVING");
		AccountTransactionRetrievalResult result = accountTransactionService.persistExternalAccountData(bankAccount, balance, mapped.booked(),
				Optional.of(new PendingBookingSnapshot(mapped.pending(), retrieval.from())), "Enablebanking");
		statusDialog.appendMessages(getText("UI_DIALOG_ENABLEBANKING_STATUS_ACCOUNT_RESULT", bankAccount.getAccountName(),
				result.newBookingCount(), result.pendingBookingCount()));
		return result;
	}

	private BankAccess getAccess(BankAccount bankAccount) {
		if (bankAccount == null || bankAccount.getBankAccessId() == null) {
			return null;
		}
		BankAccess bankAccess = dbController.getBankAccessById(bankAccount.getBankAccessId());
		return bankAccess != null && bankAccess.isActive() && bankAccess.getAccessType() == BankAccessType.ENABLEBANKING
				? bankAccess : null;
	}

	private boolean shouldOfferReauthorization(BankAccount bankAccount) {
		if (bankAccount == null || bankAccount.getId() <= 0) {
			return false;
		}
		return isReauthorizationDue(dbController.getBankAccountRetrievalStatus(bankAccount.getId()),
				LocalDateTime.now(ZoneId.systemDefault()));
	}

	static boolean isReauthorizationDue(BankAccountRetrievalStatus retrievalStatus, LocalDateTime now) {
		return retrievalStatus != null
				&& !retrievalStatus.retrievedAt().isAfter(now.minusDays(REAUTHORIZATION_OFFER_DAYS));
	}

	private boolean requestReauthorization(BankAccess bankAccess, HbciCallbackMessageDialog statusDialog) {
		boolean confirmed = statusDialog.requestConfirmation(
				getText("UI_ENABLEBANKING_REAUTHORIZE_PAUSE_PROMPT", bankAccess.getBankName(),
						REAUTHORIZATION_OFFER_DAYS),
				getText("UI_ENABLEBANKING_REAUTHORIZE_PAUSE_DETAILS"),
				getText("UI_ENABLEBANKING_REAUTHORIZE_CONFIRM"), getText("UI_BUTTON_CANCEL"));
		if (!confirmed) {
			updateStatus(statusDialog, 0.05d, "UI_DIALOG_ENABLEBANKING_STATUS_REAUTHORIZATION_SKIPPED");
		}
		return confirmed;
	}

	private AccountTransactionRetrievalResult takeCompletedBatchResult(BankAccount bankAccount) {
		if (bankAccount == null) {
			return null;
		}
		Map<Integer, AccountTransactionRetrievalResult> results = completedBatchResults.get();
		AccountTransactionRetrievalResult result = results.remove(bankAccount.getId());
		if (results.isEmpty()) {
			completedBatchResults.remove();
		}
		return result;
	}

	private Psd2ClientConfiguration getConfiguration(BankAccessEnablebanking accessData) {
		Psd2ClientConfiguration configuration = dbController.getById(Psd2ClientConfiguration.class,
				accessData.getPsd2ClientConfigurationId());
		if (configuration == null) {
			throw new EnablebankingException("Die Enablebanking-Clientkonfiguration fehlt.");
		}
		return configuration;
	}

	private EnablebankingSession ensureAuthorizedSession(BankAccess bankAccess, Psd2ClientConfiguration configuration,
			EnablebankingApiClient client, HbciCallbackMessageDialog statusDialog) {
		BankAccessEnablebanking accessData = bankAccess.getEnablebanking();
		try {
			EnablebankingSession session = client.getSession(accessData.getSessionId());
			if (session.isAuthorized() && !isExpired(session.validUntil())) {
				return session;
			}
		} catch (EnablebankingException exception) {
			if (!exception.isUnauthorized()) {
				throw exception;
			}
		}

		return authorizeSession(bankAccess, configuration, client, statusDialog);
	}

	private EnablebankingSession authorizeSession(BankAccess bankAccess, Psd2ClientConfiguration configuration,
			EnablebankingApiClient client, HbciCallbackMessageDialog statusDialog) {
		BankAccessEnablebanking accessData = bankAccess.getEnablebanking();
		updateStatus(statusDialog, 0.2d, "UI_DIALOG_ENABLEBANKING_STATUS_AUTHORIZATION");
		EnablebankingAspsp aspsp = client.getAspsps().stream()
				.filter(candidate -> candidate.name().equals(accessData.getAspspName())
						&& candidate.country().equalsIgnoreCase(accessData.getAspspCountry()))
				.findFirst()
				.orElseThrow(() -> new EnablebankingException("Das konfigurierte Kreditinstitut wird von Enablebanking nicht mehr angeboten."));
		EnablebankingSession session = authorizationService.authorize(configuration, aspsp,
				accessData.getPsuType(), accessData.getAuthMethod());
		if (!session.isAuthorized()) {
			throw new EnablebankingException("Die Enablebanking-Sitzung wurde nicht autorisiert.");
		}
		accessData.setSessionId(session.sessionId());
		accessData.setValidUntil(session.validUntil());
		bankAccess.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		dbController.insertOrUpdate(configuration);
		dbController.insertOrUpdate(bankAccess);
		return session;
	}

	private boolean isExpired(OffsetDateTime validUntil) {
		return validUntil != null && !validUntil.isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1));
	}

	private boolean isRateLimited(BankAccessEnablebanking accessData) {
		return accessData.getRateLimitUntil() != null
				&& accessData.getRateLimitUntil().isAfter(OffsetDateTime.now(ZoneOffset.UTC));
	}

	private EnablebankingRemoteAccount findRemoteAccount(BankAccount bankAccount,
			List<EnablebankingRemoteAccount> remoteAccounts) {
		return remoteAccounts.stream()
				.filter(account -> bankAccount.getProviderAccountId() != null
						&& bankAccount.getProviderAccountId().equals(account.identificationHash()))
				.findFirst()
				.orElseThrow(() -> new EnablebankingException(
						"Das Konto konnte in der aktuellen Enablebanking-Sitzung nicht wiedererkannt werden."));
	}

	private LocalDate resolveStart(BankAccount bankAccount) {
		LocalDate lastBookingDate = accountTransactionService.getLastOnlineBookingDate(bankAccount);
		return lastBookingDate != null ? lastBookingDate.minusDays(RETRIEVAL_OVERLAP_DAYS) : null;
	}

	TransactionRetrieval retrieveTransactions(EnablebankingApiClient client, String accountUid,
			LocalDate from, HbciCallbackMessageDialog statusDialog) {
		if (from != null) {
			updateStatus(statusDialog, 0.35d, "UI_DIALOG_ENABLEBANKING_STATUS_TRANSACTIONS_SINCE", from);
			return new TransactionRetrieval(retrieveAllTransactions(client, accountUid, from, null, statusDialog), from);
		}
		updateStatus(statusDialog, 0.35d, "UI_DIALOG_ENABLEBANKING_STATUS_TRANSACTIONS_LONGEST");
		List<Map<String, Object>> transactions = retrieveAllTransactions(client, accountUid, null, "longest", statusDialog);
		if (!transactions.isEmpty()) {
			return new TransactionRetrieval(transactions, null);
		}
		for (int lookbackDays : INITIAL_LOOKBACK_DAYS) {
			LocalDate fallbackFrom = LocalDate.now(ZoneOffset.UTC).minusDays(lookbackDays - 1L);
			try {
				updateStatus(statusDialog, 0.35d, "UI_DIALOG_ENABLEBANKING_STATUS_TRANSACTIONS_PERIOD", lookbackDays);
				transactions = retrieveAllTransactions(client, accountUid, fallbackFrom, null, statusDialog);
				if (!transactions.isEmpty()
						|| lookbackDays == INITIAL_LOOKBACK_DAYS.get(INITIAL_LOOKBACK_DAYS.size() - 1)) {
					return new TransactionRetrieval(transactions, fallbackFrom);
				}
				log.info("Enablebanking returned no transactions for the initial period of {} days; trying a shorter period.",
						lookbackDays);
			} catch (EnablebankingException exception) {
				if (!exception.isWrongTransactionsPeriod()) {
					throw exception;
				}
				log.info("Enablebanking does not provide the requested initial transaction period of {} days.", lookbackDays);
			}
		}
		throw new EnablebankingException("Enablebanking stellt auch für die letzten "
				+ INITIAL_LOOKBACK_DAYS.get(INITIAL_LOOKBACK_DAYS.size() - 1) + " Tage keine Umsätze bereit.");
	}

	private List<Map<String, Object>> retrieveAllTransactions(EnablebankingApiClient client, String accountUid,
			LocalDate from, String strategy, HbciCallbackMessageDialog statusDialog) {
		List<Map<String, Object>> transactions = new ArrayList<>();
		Set<String> seenContinuationKeys = new HashSet<>();
		String continuationKey = null;
		int pageNumber = 1;
		do {
			updateStatus(statusDialog, Math.min(0.65d, 0.35d + pageNumber * 0.1d),
					"UI_DIALOG_ENABLEBANKING_STATUS_TRANSACTIONS", pageNumber);
			EnablebankingTransactionPage page = client.getTransactions(accountUid, from, strategy, continuationKey);
			transactions.addAll(page.transactions());
			continuationKey = page.continuationKey();
			if (continuationKey != null && !seenContinuationKeys.add(continuationKey)) {
				throw new EnablebankingException("Enablebanking hat einen ungültigen Fortsetzungsschlüssel geliefert.");
			}
			pageNumber++;
		} while (continuationKey != null && !continuationKey.isBlank());
		statusDialog.appendMessages(getText("UI_DIALOG_ENABLEBANKING_STATUS_TRANSACTIONS_RESULT", transactions.size()));
		log.info("Retrieved {} Enablebanking transactions in {} page(s), strategy={}, lookbackDays={}",
				transactions.size(), pageNumber - 1, strategy != null ? strategy : "default",
				from != null ? java.time.temporal.ChronoUnit.DAYS.between(from, LocalDate.now(ZoneOffset.UTC)) + 1 : null);
		return transactions;
	}

	void updateStatus(HbciCallbackMessageDialog statusDialog, double progress, String messageKey,
			Object... parameters) {
		String message = getText(messageKey, parameters);
		statusDialog.updateCurrentAction(message);
		statusDialog.appendMessages(message);
		statusDialog.updateProgress(progress);
	}

	MappedTransactions mapTransactions(BankAccount account, List<Map<String, Object>> transactions,
			LocalDate from) {
		Map<String, Booking> booked = new LinkedHashMap<>();
		Map<String, Booking> pending = new LinkedHashMap<>();
		for (Map<String, Object> transaction : transactions) {
			String status = upper(string(transaction.get("status")));
			if (!BOOKED_STATUSES.contains(status) && !PENDING_STATUSES.contains(status)) {
				continue;
			}
			Booking booking = mapBooking(account, transaction, status);
			if (from != null && booking.getDateBooking() != null && booking.getDateBooking().isBefore(from)) {
				continue;
			}
			String fingerprint = stableReference(transaction, booking);
			booking.setAdditionalDetails(additionalDetails(transaction, status, fingerprint));
			(BOOKED_STATUSES.contains(status) ? booked : pending).putIfAbsent(fingerprint, booking);
		}
		return new MappedTransactions(List.copyOf(booked.values()), List.copyOf(pending.values()));
	}

	private Booking mapBooking(BankAccount account, Map<String, Object> transaction, String status) {
		Map<String, Object> transactionAmount = object(transaction.get("transaction_amount"));
		BigDecimal amount = decimal(transactionAmount.get("amount"));
		if (amount == null) {
			throw new EnablebankingException("Ein Enablebanking-Umsatz enthält keinen Betrag.");
		}
		String indicator = upper(string(transaction.get("credit_debit_indicator")));
		amount = "DBIT".equals(indicator) ? amount.abs().negate() : amount.abs();
		LocalDate bookingDate = firstDate(transaction, "booking_date", "transaction_date", "value_date");
		if (bookingDate == null) {
			bookingDate = LocalDate.now(ZoneId.systemDefault());
		}
		Booking booking = new Booking();
		booking.setAccountId(account.getId());
		booking.setDateBooking(bookingDate);
		booking.setDateValue(Optional.ofNullable(firstDate(transaction, "value_date")).orElse(bookingDate));
		booking.setPurpose(purpose(transaction));
		Map<String, Object> exchangeRate = object(transaction.get("exchange_rate"));
		Map<String, Object> instructedAmount = object(exchangeRate.get("instructed_amount"));
		BigDecimal foreignAmount = decimal(instructedAmount.get("amount"));
		BookingCurrencyMapper.mapAmounts(booking, amount, firstText(transactionAmount, "currency", "currency_code"),
				account.getBaseCurrency(), foreignAmount, firstText(instructedAmount, "currency", "currency_code"),
				decimal(exchangeRate.get("exchange_rate")));
		booking.setBookingType(amount.signum() < 0 ? BookingType.REMOVAL : BookingType.DEPOSIT);
		booking.setSource(PENDING_STATUSES.contains(status) ? Source.ONLINE_PRENO_NEW : Source.ONLINE_NEW);
		booking.setRecipient(recipient(transaction, amount.signum() < 0));
		booking.setBalance(nestedAmount(transaction.get("balance_after_transaction")));
		return booking;
	}

	private BookingAdditionalDetails additionalDetails(Map<String, Object> transaction, String status,
			String stableReference) {
		BookingAdditionalDetails details = new BookingAdditionalDetails();
		details.setInstref("ENABLEBANKING:" + stableReference);
		Map<String, Object> transactionCode = object(transaction.get("bank_transaction_code"));
		details.setGvcode(firstText(transactionCode, "code", "description"));
		details.setText(status);
		details.setBankSaldo(nestedAmount(transaction.get("balance_after_transaction")));
		return details;
	}

	private Recipient recipient(Map<String, Object> transaction, boolean debit) {
		String prefix = debit ? "creditor" : "debtor";
		Map<String, Object> party = object(transaction.get(prefix));
		Map<String, Object> account = object(transaction.get(prefix + "_account"));
		Map<String, Object> agent = object(transaction.get(prefix + "_agent"));
		String name = firstText(party, "name", "display_name");
		String iban = firstText(account, "iban");
		String number = firstText(account, "bban", "identification");
		String bic = firstText(agent, "bic_fi", "bic");
		if (name == null && iban == null && number == null) {
			return null;
		}
		Recipient recipient = new Recipient(name, iban, bic);
		recipient.setAccountNumber(number);
		recipient.setSource(Source.ONLINE);
		return recipient;
	}

	private String purpose(Map<String, Object> transaction) {
		Object remittance = transaction.get("remittance_information");
		if (remittance instanceof List<?> values) {
			String joined = values.stream().map(value -> string(value))
					.filter(value -> value != null && !value.isBlank()).reduce((left, right) -> left + "\n" + right).orElse(null);
			if (joined != null) {
				return joined;
			}
		}
		return firstText(transaction, "remittance_information_unstructured", "additional_information", "entry_reference");
	}

	private Optional<BigDecimal> resolveBookedBalance(List<Map<String, Object>> balances, Currency baseCurrency) {
		Map<String, Integer> priorities = Map.of("CLBD", 1, "CLOSINGBOOKED", 1,
				"ITBD", 2, "INTERIMBOOKED", 2, "PRCD", 3, "PREVIOUSLYCLOSEDBOOKED", 3,
				"XPCD", 4, "EXPECTED", 4);
		Map<String, Object> amount = balances.stream()
				.filter(balance -> priorities.containsKey(upper(string(balance.get("balance_type")))))
				.sorted(Comparator.comparingInt(balance -> priorities.get(upper(string(balance.get("balance_type"))))))
				.map(balance -> object(balance.get("balance_amount")))
				.filter(balanceAmount -> nestedAmount(balanceAmount) != null)
				.findFirst().orElse(null);
		if (amount == null) {
			return Optional.empty();
		}
		Currency balanceCurrency = Currency.forCodeOrDefault(firstText(amount, "currency", "currency_code"), baseCurrency);
		if (balanceCurrency != baseCurrency) {
			throw new EnablebankingException("Der Kontosaldo wurde nicht in der Kontowährung " + baseCurrency + " geliefert.");
		}
		return Optional.ofNullable(nestedAmount(amount));
	}

	private String stableReference(Map<String, Object> transaction, Booking booking) {
		String source = String.join("|", Optional.ofNullable(string(transaction.get("entry_reference"))).orElse(""),
				Optional.ofNullable(string(transaction.get("transaction_id"))).orElse(""),
				Optional.ofNullable(booking.getDateBooking()).map(LocalDate::toString).orElse(""),
				Optional.ofNullable(booking.getDateValue()).map(LocalDate::toString).orElse(""),
				booking.getAmount().toPlainString(),
				Optional.ofNullable(firstText(object(transaction.get("transaction_amount")), "currency", "currency_code")).orElse(""),
				Optional.ofNullable(booking.getPurpose()).orElse(""));
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	private AccountTransactionRetrievalResult persist(BankAccount account, AccountTransactionRetrievalResult result) {
		accountTransactionService.persistExternalRetrievalStatus(account, result);
		return result;
	}

	private static LocalDate firstDate(Map<String, Object> values, String... keys) {
		for (String key : keys) {
			String value = string(values.get(key));
			if (value == null || value.length() < 10) {
				continue;
			}
			try {
				return LocalDate.parse(value.substring(0, 10));
			} catch (java.time.DateTimeException exception) {
				// Try the next available date field.
			}
		}
		return null;
	}

	private static BigDecimal nestedAmount(Object value) {
		Map<String, Object> amount = object(value);
		if (amount.containsKey("amount")) {
			return decimal(amount.get("amount"));
		}
		Map<String, Object> balanceAmount = object(amount.get("balance_amount"));
		return decimal(balanceAmount.get("amount"));
	}

	record MappedTransactions(List<Booking> booked, List<Booking> pending) {
	}

	record TransactionRetrieval(List<Map<String, Object>> transactions, LocalDate from) {
	}

	record BatchRetrieval(Map<Integer, AccountTransactionRetrievalResult> results, boolean successful) {
	}
}
