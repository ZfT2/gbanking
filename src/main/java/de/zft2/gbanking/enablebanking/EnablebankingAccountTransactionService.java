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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccessEnablebanking;
import de.zft2.gbanking.db.dao.BankAccount;
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
	private static final Set<String> PENDING_STATUSES = Set.of("PDNG", "HOLD");
	private static final Set<String> BOOKED_STATUSES = Set.of("BOOK");

	private final AccountTransactionService accountTransactionService;
	private final EnablebankingAuthorizationService authorizationService;

	public EnablebankingAccountTransactionService() {
		this(ServiceRegistry.getService(AccountTransactionService.class), new EnablebankingAuthorizationService());
	}

	EnablebankingAccountTransactionService(AccountTransactionService accountTransactionService,
			EnablebankingAuthorizationService authorizationService) {
		this.accountTransactionService = accountTransactionService;
		this.authorizationService = authorizationService;
	}

	public AccountTransactionRetrievalResult retrieve(BankAccount bankAccount) {
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
			updateStatus(statusDialog, 0.1d, "UI_DIALOG_ENABLEBANKING_STATUS_SESSION");
			EnablebankingSession session = ensureAuthorizedSession(bankAccess, configuration, client, statusDialog);
			updateStatus(statusDialog, 0.3d, "UI_DIALOG_ENABLEBANKING_STATUS_ACCOUNT");
			EnablebankingRemoteAccount remoteAccount = findRemoteAccount(bankAccount, session.accounts());
			LocalDate from = resolveStart(bankAccount);
			List<Map<String, Object>> transactions = retrieveAllTransactions(client, remoteAccount.uid(), from,
					statusDialog);
			updateStatus(statusDialog, 0.7d, "UI_DIALOG_ENABLEBANKING_STATUS_PROCESSING");
			MappedTransactions mapped = mapTransactions(bankAccount, transactions, from);
			updateStatus(statusDialog, 0.8d, "UI_DIALOG_ENABLEBANKING_STATUS_BALANCE");
			Optional<BigDecimal> balance = resolveBookedBalance(client.getBalances(remoteAccount.uid()), bankAccount.getBaseCurrency());
			accessData.setRateLimitUntil(null);
			dbController.insertOrUpdate(bankAccess);
			updateStatus(statusDialog, 0.9d, "UI_DIALOG_ENABLEBANKING_STATUS_SAVING");
			AccountTransactionRetrievalResult result = accountTransactionService.persistExternalAccountData(bankAccount, balance, mapped.booked(),
					Optional.of(new PendingBookingSnapshot(mapped.pending(), from)), "Enablebanking");
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

	private BankAccess getAccess(BankAccount bankAccount) {
		if (bankAccount == null || bankAccount.getBankAccessId() == null) {
			return null;
		}
		BankAccess bankAccess = dbController.getBankAccessById(bankAccount.getBankAccessId());
		return bankAccess != null && bankAccess.isActive() && bankAccess.getAccessType() == BankAccessType.ENABLEBANKING
				? bankAccess : null;
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

	private List<Map<String, Object>> retrieveAllTransactions(EnablebankingApiClient client, String accountUid,
			LocalDate from, HbciCallbackMessageDialog statusDialog) {
		List<Map<String, Object>> transactions = new ArrayList<>();
		Set<String> seenContinuationKeys = new HashSet<>();
		String continuationKey = null;
		int pageNumber = 1;
		do {
			updateStatus(statusDialog, Math.min(0.65d, 0.35d + pageNumber * 0.1d),
					"UI_DIALOG_ENABLEBANKING_STATUS_TRANSACTIONS", pageNumber);
			EnablebankingTransactionPage page = client.getTransactions(accountUid, from,
					from == null ? "longest" : "default", continuationKey);
			transactions.addAll(page.transactions());
			continuationKey = page.continuationKey();
			if (continuationKey != null && !seenContinuationKeys.add(continuationKey)) {
				throw new EnablebankingException("Enablebanking hat einen ungültigen Fortsetzungsschlüssel geliefert.");
			}
			pageNumber++;
		} while (continuationKey != null && !continuationKey.isBlank());
		return transactions;
	}

	void updateStatus(HbciCallbackMessageDialog statusDialog, double progress, String messageKey,
			Object... parameters) {
		statusDialog.updateCurrentAction(getText(messageKey, parameters));
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
}
