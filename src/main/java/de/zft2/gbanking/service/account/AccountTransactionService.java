package de.zft2.gbanking.service.account;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.GV_Result.GVRSaldoReq;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;

import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountRetrievalStatus;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.enablebanking.EnablebankingAccountTransactionService;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.hbci.HbciStatusMessageExtractor;
import de.zft2.gbanking.logging.GBankingLoggingHandler;
import de.zft2.gbanking.logging.SensitiveDataMasker;
import de.zft2.gbanking.mapper.HbciMapper;
import de.zft2.gbanking.mapper.BookingCurrencyMapper;
import de.zft2.gbanking.paypal.PaypalAccountTransactionService;
import de.zft2.gbanking.rebooking.MissingRebookingCreationSummary;
import de.zft2.gbanking.rebooking.RebookingAssignmentSummary;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.HbciSessionRunner;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.bankaccess.BankAccessService;

public class AccountTransactionService extends AbstractDbService {

	private static final Logger log = LogManager.getLogger(AccountTransactionService.class);

	private static final String UMS_JOB_CAMT = "KUmsAllCamt";
	private static final String VORMERKPOSTEN_JOB = "Vormerkposten";
	private static final String LOWLEVEL_RESULT_MT942 = "mt942";

	private static final String ERROR_RETRIEVAL_FAILED = "ERROR_ACCOUNT_TRANSACTION_RETRIEVAL_FAILED";
	private static final int BOOKING_RETRIEVAL_OVERLAP_DAYS = 1;
	private static final int MAX_RETRIEVAL_ERROR_LENGTH = 1000;

	private final GBankingLoggingHandler logHandler;
	private final HbciSessionRunner hbciSessionRunner;
	private final BookingDuplicateChecker bookingDuplicateChecker;
	private final RebookingService rebookingService;

	public AccountTransactionService() {
		this.logHandler = GBankingLoggingHandler.getInstance();
		this.hbciSessionRunner = new HbciSessionRunner();
		this.bookingDuplicateChecker = new BookingDuplicateChecker();
		this.rebookingService = new RebookingService();
	}

	public boolean retrieveAccountTransactions(BankAccount bankAccount, char[] pin) {
		return retrieveAccountTransactionsWithResult(bankAccount, pin).successful();
	}

	public AccountTransactionRetrievalResult retrieveAccountTransactionsWithResult(BankAccount bankAccount, char[] pin) {
		BankAccess configuredAccess = getConfiguredAccess(bankAccount);
		if (configuredAccess != null && configuredAccess.getAccessType() == BankAccessType.PAYPAL) {
			return ServiceRegistry.getService(PaypalAccountTransactionService.class).retrieve(bankAccount, pin);
		}
		if (configuredAccess != null && configuredAccess.getAccessType() == BankAccessType.ENABLEBANKING) {
			clearSecret(pin);
			return ServiceRegistry.getService(EnablebankingAccountTransactionService.class).retrieve(bankAccount);
		}
		return retrieveFintsAccountTransactions(bankAccount, pin);
	}

	private BankAccess getConfiguredAccess(BankAccount bankAccount) {
		if (bankAccount == null || bankAccount.getBankAccessId() == null || bankAccount.getBankAccessId() <= 0) {
			return null;
		}
		return dbController.getBankAccessById(bankAccount.getBankAccessId());
	}

	private AccountTransactionRetrievalResult retrieveFintsAccountTransactions(BankAccount bankAccount, char[] pin) {
		log.info("Starting HBCI account transaction retrieval for account id {}", bankAccount != null ? bankAccount.getId() : null);
		String bankAccountId = getNullableBankAccountId(bankAccount);

		try {
			BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
			BankAccess bankAccess = hbciSupport.initBankAccess(bankAccount, pin);
			if (bankAccess == null) {
				log.info("HBCI account transaction retrieval skipped, no bank access available.");
				AccountTransactionRetrievalResult result = AccountTransactionRetrievalResult.failure(
						getText("ERROR_ACCOUNT_TRANSACTION_RETRIEVAL_NO_BANK_ACCESS"));
				persistRetrievalStatus(bankAccount, result);
				return result;
			}

			LocalDate lastBookingDate = getAccountLastBookingDate(bankAccount);
			LocalDate bookingRetrievalStartDate = resolveBookingRetrievalStartDate(lastBookingDate);
			log.debug("Using booking retrieval start date {} derived from last booking date {} for HBCI retrieval on account id {}",
					bookingRetrievalStartDate, lastBookingDate, bankAccountId);

			AccountTransactionRetrievalResult result = hbciSessionRunner.run(bankAccess, pin,
					session -> retrieveAccountTransactions(bankAccount, bookingRetrievalStartDate, session));
			if (!result.successful()) {
				persistRetrievalStatus(bankAccount, result);
			}
			log.info("Finished HBCI account transaction retrieval for account id {}, success={}", bankAccountId, result.successful());
			return result;
		} catch (InterruptedException e) {
			log.error("Error handling HBCI account transaction retrieval for account id {}", bankAccountId, e);
			Thread.currentThread().interrupt();
			AccountTransactionRetrievalResult result = AccountTransactionRetrievalResult.cancelled(
					getText("ERROR_ACCOUNT_TRANSACTION_RETRIEVAL_CANCELLED"));
			persistRetrievalStatus(bankAccount, result);
			return result;
		} catch (HBCI_Exception e) {
			if (HbciStatusMessageExtractor.containsWrongPinFeedback(e)) {
				log.warn("Stopping HBCI account transaction retrieval for account id {} because the bank reported invalid PIN credentials.", bankAccountId);
				AccountTransactionRetrievalResult result = AccountTransactionRetrievalResult.wrongPinFailure(
						toStoredError(e.getMessage(), "ERROR_ACCOUNT_TRANSACTION_RETRIEVAL_WRONG_PIN"));
				persistRetrievalStatus(bankAccount, result);
				return result;
			}
			persistRetrievalStatusSafely(bankAccount,
					AccountTransactionRetrievalResult.failure(toStoredError(e.getMessage(), ERROR_RETRIEVAL_FAILED)), e);
			throw e;
		} catch (RuntimeException e) {
			persistRetrievalStatusSafely(bankAccount,
					AccountTransactionRetrievalResult.failure(getText("ERROR_ACCOUNT_TRANSACTION_RETRIEVAL_STORAGE_FAILED")), e);
			throw e;
		} finally {
			clearSecret(pin);
		}
	}

	private AccountTransactionRetrievalResult retrieveAccountTransactions(BankAccount bankAccount, LocalDate bookingRetrievalStartDate,
			HbciSessionRunner.HbciSession session) {
		logHandler.logRetrivedBankAccessInfo(session.passport(), false);

		Konto kontoMatched = null;
		for (Konto konto : getHbciAccountsFromPassport(session.passport())) {
			if (hbciKontosMatches(bankAccount, konto)) {
				/** some banks, e.g. DKB, seem to need the BIC here. **/
				konto.bic = bankAccount.getBic() != null ? bankAccount.getBic() : konto.bic;
				kontoMatched = konto;
				break;
			}
		}

		logHandler.logRetrievedAccountInfo(kontoMatched);

		HBCIJob<GVRSaldoReq> saldoJob = createAndAddHbciJob(session.handler(), "SaldoReq", Map.of("my", kontoMatched));
		HBCIJob<GVRKUms> umsatzJob = createUmsatzJob(session.handler(), kontoMatched, bookingRetrievalStartDate);
		Optional<HBCIJob<HBCIJobResult>> vormerkpostenJob = createVormerkpostenJob(session.handler(), kontoMatched, bankAccount);
		String accountName = session.callback().getAccountDescription(bankAccount);
		session.callback().registerJobDescription(saldoJob, getText("UI_DIALOG_HBCI_JOB_BALANCE", accountName));
		session.callback().registerJobDescription(umsatzJob, getText("UI_DIALOG_HBCI_JOB_TRANSACTIONS", accountName));
		vormerkpostenJob.ifPresent(job -> session.callback().registerJobDescription(job,
				getText("UI_DIALOG_HBCI_JOB_PENDING_TRANSACTIONS", accountName)));

		HBCIExecStatus status = session.handler().execute();
		boolean result = status.isOK();

		if (!result) {
			log.error("HBCI Error, Status: {}", status);
			session.callback().handleFailure(status.getErrorString());
			return toRetrievalResult(status);
		}

		if (!hasSuccessfulUmsatzResult(umsatzJob)) {
			String errorMessage = getText(ERROR_RETRIEVAL_FAILED);
			session.callback().handleFailure(errorMessage);
			return AccountTransactionRetrievalResult.failure(errorMessage);
		}

		Optional<BigDecimal> accountBalance = readSaldo(saldoJob, bankAccount.getBaseCurrency());
		List<UmsLine> bookedUms = readBookedUms(umsatzJob);
		Optional<List<UmsLine>> prenotificationResult = readPrenotifications(umsatzJob, vormerkpostenJob);
		return persistRetrievedAccountData(bankAccount, accountBalance, bookedUms, prenotificationResult);
	}

	private AccountTransactionRetrievalResult persistRetrievedAccountData(BankAccount bankAccount, Optional<BigDecimal> accountBalance,
			List<UmsLine> bookedUms, Optional<List<UmsLine>> prenotificationResult) {
		return dbController.executeInTransaction(() -> {
			refreshBankAccount(bankAccount);
			updatePreviousNewBookings(bankAccount);
			accountBalance.ifPresent(saldo -> updateAccountBalance(bankAccount, saldo));

			int newBookingCount = saveHbciBookingsForAccount(bankAccount, bookedUms);
			int pendingBookingCount = updatePrenotifications(bankAccount, prenotificationResult);
			accountBalance.ifPresent(saldo -> reconcileAccountBalance(bankAccount, saldo));

			AccountTransactionRetrievalResult result = AccountTransactionRetrievalResult.success(newBookingCount, pendingBookingCount);
			persistRetrievalStatus(bankAccount, result);
			log.info("Processed HBCI account transaction result for account id {}, new={}, prenotified={}, success=true", bankAccount.getId(),
					newBookingCount, pendingBookingCount);
			return result;
		});
	}

	public AccountTransactionRetrievalResult persistExternalAccountData(BankAccount bankAccount, BigDecimal accountBalance,
			List<Booking> bookings) {
		return persistExternalAccountData(bankAccount, Optional.ofNullable(accountBalance), bookings, Optional.empty(), "online");
	}

	public AccountTransactionRetrievalResult persistExternalAccountData(BankAccount bankAccount, Optional<BigDecimal> accountBalance,
			List<Booking> bookings, Optional<PendingBookingSnapshot> pendingBookings, String providerName) {
		return dbController.executeInTransaction(() -> {
			refreshBankAccount(bankAccount);
			updatePreviousNewBookings(bankAccount);
			accountBalance.ifPresent(balance -> updateAccountBalance(bankAccount, balance));
			int newBookingCount = saveOnlineBookingsForAccount(bankAccount, bookings, Source.ONLINE_NEW, providerName);
			int pendingBookingCount = updatePendingBookings(bankAccount, pendingBookings, providerName);
			accountBalance.ifPresent(balance -> reconcileAccountBalance(bankAccount, balance));

			AccountTransactionRetrievalResult result = AccountTransactionRetrievalResult.success(newBookingCount, pendingBookingCount);
			persistRetrievalStatus(bankAccount, result);
			return result;
		});
	}

	private int updatePrenotifications(BankAccount bankAccount, Optional<List<UmsLine>> prenotificationResult) {
		if (prenotificationResult.isEmpty()) {
			log.warn("Keeping existing prenotifications for account id {} because the HBCI prenotification refresh was incomplete.", bankAccount.getId());
			return (int) getAccountBookings(bankAccount.getId()).stream().filter(this::isPrenotification).count();
		}

		List<Booking> bookings = mapHbciBookings(bankAccount, prenotificationResult.get(), Source.ONLINE_PRENO_NEW);
		return updatePendingBookings(bankAccount,
				Optional.of(new PendingBookingSnapshot(bookings, null)), "HBCI");
	}

	private int updatePendingBookings(BankAccount bankAccount, Optional<PendingBookingSnapshot> snapshot, String providerName) {
		if (snapshot.isEmpty()) {
			return (int) getAccountBookings(bankAccount.getId()).stream().filter(this::isPrenotification).count();
		}
		PendingBookingSnapshot pending = snapshot.get();
		deletePreviousPrenotifications(bankAccount, pending.fromInclusive());
		saveOnlineBookingsForAccount(bankAccount, pending.bookings(), Source.ONLINE_PRENO_NEW, providerName);
		return pending.bookings().size();
	}

	private AccountTransactionRetrievalResult toRetrievalResult(HBCIExecStatus status) {
		String errorMessage = toStoredError(status != null ? status.getErrorString() : null, ERROR_RETRIEVAL_FAILED);
		return HbciStatusMessageExtractor.containsWrongPinFeedback(status) ? AccountTransactionRetrievalResult.wrongPinFailure(errorMessage)
				: AccountTransactionRetrievalResult.failure(errorMessage);
	}

	public int saveHbciBookingsForAccount(BankAccount bankAccount, List<UmsLine> buchungen) {
		return saveHbciBookingsForAccount(bankAccount, buchungen, Source.ONLINE_NEW);
	}

	int saveHbciBookingsForAccount(BankAccount bankAccount, List<UmsLine> buchungen, Source source) {
		return saveOnlineBookingsForAccount(bankAccount, mapHbciBookings(bankAccount, buchungen, source), source, "HBCI");
	}

	private List<Booking> mapHbciBookings(BankAccount bankAccount, List<UmsLine> buchungen, Source source) {
		List<Booking> mappedBookings = new ArrayList<>();
		if (buchungen == null) {
			return mappedBookings;
		}
		for (UmsLine buchung : buchungen) {
			logHandler.logRetrivedBookingInfo(buchung);
			Booking newBooking = HbciMapper.mapUmsLineToBooking(bankAccount.getId(), buchung, bankAccount.getBaseCurrency(), source);
			log.debug("Booking counterparty data present: {}", () -> SensitiveDataMasker.describePresence(buchung.other));
			newBooking.setRecipient(HbciMapper.mapUmsLineKontoToRecipient(buchung.other));
			mappedBookings.add(newBooking);
		}
		return mappedBookings;
	}

	private int saveOnlineBookingsForAccount(BankAccount bankAccount, List<Booking> bookings, Source source, String providerName) {
		if (bookings == null || bookings.isEmpty()) {
			return 0;
		}

		List<Booking> duplicateCheckBookings = getAccountBookingsForDuplicateCheck(bankAccount.getId());
		List<Booking> processedIncomingBookings = new ArrayList<>();
		List<Booking> newBookingsList = new ArrayList<>();
		Map<Recipient, Set<Integer>> recipientBookingMap = new HashMap<>();
		int skippedDuplicates = 0;

		for (Booking newBooking : bookings) {
			newBooking.setAccountId(bankAccount.getId());
			newBooking.setSource(source);
			BookingCurrencyMapper.validate(newBooking, bankAccount.getBaseCurrency());

			if (bookingDuplicateChecker.isDuplicate(newBooking, duplicateCheckBookings, processedIncomingBookings)) {
				skippedDuplicates++;
				logDebug(bankAccount, newBooking);
				processedIncomingBookings.add(newBooking);
				continue;
			}

			if (newBooking.getRecipient() != null) {
				newBooking.setRecipient(dbController.resolveRecipient(newBooking.getRecipient()));
			}

			newBookingsList.add(newBooking);
			processedIncomingBookings.add(newBooking);
		}

		if (skippedDuplicates > 0) {
			log.info("Skipped {} duplicate {} bookings for account id {}", skippedDuplicates, providerName, bankAccount.getId());
		}
		if (newBookingsList.isEmpty()) {
			return 0;
		}

		if (!dbController.insertAccountBookings(newBookingsList)) {
			throw new GBankingException(providerName + " bookings were not saved");
		}
		log.info("Saved {} {} bookings for account id {}, source={}", newBookingsList.size(), providerName, bankAccount.getId(), source);

		for (Booking booking : newBookingsList) {
			Recipient recipient = booking.getRecipient();
			if (recipient == null) {
				continue;
			}

			recipientBookingMap.computeIfAbsent(recipient, ignored -> new HashSet<>()).add(booking.getId());
		}

		dbController.updateBookingsWithRecipients(recipientBookingMap);
		linkNewOnlineCounterBookings(newBookingsList, source);
		return newBookingsList.size();
	}

	private void logDebug(BankAccount bankAccount, Booking newBooking) {
		if (log.isDebugEnabled()) {
			log.debug("Skipping duplicate booking for account id {}, booking date {}, amount {}, purpose {}", bankAccount::getId,
					newBooking::getDateBooking, () -> SensitiveDataMasker.describeAmount(newBooking.getAmount()),
					() -> SensitiveDataMasker.describeText(newBooking.getPurpose()));
		}
	}

	public int adjustRebookings(BankAccount checkedAccount) {
		return rebookingService.adjustRebookings(checkedAccount);
	}

	private int linkNewOnlineCounterBookings(List<Booking> newBookings, Source source) {
		if (source != Source.ONLINE_NEW || newBookings == null || newBookings.isEmpty()) {
			return 0;
		}

		return linkNewOnlineCounterBookings(newBookings);
	}

	private int linkNewOnlineCounterBookings(List<Booking> newBookings) {
		return rebookingService.linkNewOnlineCounterBookings(newBookings);
	}

	public RebookingAssignmentSummary detectRebookings(LocalDate dateFrom, LocalDate dateTo, Collection<BankAccount> anchorAccounts) {
		return rebookingService.detectRebookings(dateFrom, dateTo, anchorAccounts);
	}

	public MissingRebookingCreationSummary detectMissingRebookings(LocalDate dateFrom, LocalDate dateTo, Collection<BankAccount> anchorAccounts) {
		return rebookingService.detectMissingRebookings(dateFrom, dateTo, anchorAccounts);
	}

	public int createMissingRebookings(MissingRebookingCreationSummary summary) {
		return rebookingService.createMissingRebookings(summary);
	}

	public int releaseRebookingLinks(Collection<Booking> bookings) {
		return rebookingService.releaseRebookingLinks(bookings);
	}

	public int persistDetectedRebookingLinks(RebookingAssignmentSummary summary) {
		return rebookingService.persistDetectedRebookingLinks(summary);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private List<Booking> getAccountBookingsForDuplicateCheck(int accountId) {
		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, accountId);
		return bookings != null ? new ArrayList<>(bookings) : new ArrayList<>();
	}

	private LocalDate resolveBookingRetrievalStartDate(LocalDate lastBookingDate) {
		if (lastBookingDate == null) {
			return null;
		}
		LocalDate today = LocalDate.now(ZoneId.systemDefault());
		LocalDate effectiveLastBookingDate = lastBookingDate.isAfter(today) ? today : lastBookingDate;
		return effectiveLastBookingDate.minusDays(BOOKING_RETRIEVAL_OVERLAP_DAYS);
	}

	private HBCIJob<GVRKUms> createUmsatzJob(HBCIHandler handle, Konto konto, LocalDate bookingRetrievalStartDate) {
		if (bookingRetrievalStartDate == null) {
			return createAndAddHbciJob(handle, UMS_JOB_CAMT, Map.of("my", konto));
		}
		return createAndAddHbciJob(handle, UMS_JOB_CAMT,
				Map.of("my", konto, "startdate", Date.from(bookingRetrievalStartDate.atStartOfDay(ZoneId.systemDefault()).toInstant())));
	}

	private Optional<HBCIJob<HBCIJobResult>> createVormerkpostenJob(HBCIHandler handle, Konto konto, BankAccount bankAccount) {
		if (!isVormerkpostenJobSupported(handle)) {
			log.debug("Lowlevel HBCI job {} is not supported for this bank access.", VORMERKPOSTEN_JOB);
			return Optional.empty();
		}

		String number = firstText(konto != null ? konto.number : null, bankAccount != null ? bankAccount.getNumber() : null);
		String subnumber = firstText(konto != null ? konto.subnumber : null, bankAccount != null ? bankAccount.getSubnumber() : null);
		String country = firstText(konto != null ? konto.country : null, bankAccount != null ? bankAccount.getCountry() : null, "DE");
		String blz = firstText(konto != null ? konto.blz : null, bankAccount != null ? bankAccount.getBlz() : null);
		if (!hasText(number) || !hasText(country) || !hasText(blz)) {
			log.warn("Skipping lowlevel HBCI job {} because national account data is incomplete for account id {}.", VORMERKPOSTEN_JOB,
					bankAccount != null ? bankAccount.getId() : null);
			return Optional.empty();
		}

		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		HBCIJob<HBCIJobResult> job = hbciSupport.newLowlevelHbciJob(handle, VORMERKPOSTEN_JOB);
		job.setParam("My.number", number);
		setParamIfPresent(job, "My.subnumber", subnumber);
		job.setParam("My.KIK.country", country);
		job.setParam("My.KIK.blz", blz);
		job.setParam("allaccounts", "N");
		job.addToQueue();
		log.debug("Queued lowlevel HBCI job {} for account id {}.", VORMERKPOSTEN_JOB, bankAccount != null ? bankAccount.getId() : null);
		return Optional.of(job);
	}

	private boolean isVormerkpostenJobSupported(HBCIHandler handle) {
		Properties supportedLowlevelJobs = handle.getSupportedLowlevelJobs();
		return supportedLowlevelJobs != null && supportedLowlevelJobs.containsKey(VORMERKPOSTEN_JOB);
	}

	private String firstText(String... values) {
		for (String value : values) {
			if (hasText(value)) {
				return value;
			}
		}
		return null;
	}

	private void setParamIfPresent(HBCIJob<?> job, String parameterName, String value) {
		if (hasText(value)) {
			job.setParam(parameterName, value);
		}
	}

	private void refreshBankAccount(BankAccount bankAccount) {
		bankAccount.setBookings(dbController.getAllByParent(Booking.class, bankAccount.getId()));
	}

	private void updatePreviousNewBookings(BankAccount bankAccount) {
		for (Booking booking : bankAccount.getBookings()) {
			if (booking.getSource() == null || !booking.getSource().isNew()) {
				continue;
			}
			booking.setSource(booking.getSource().getCorresponding());
			dbController.executeSimpleUpdate(List.of(booking), StatementsConfig.StatementType.UPDATE_BOOKING_SOURCE, Booking.class);
		}
	}

	private void deletePreviousPrenotifications(BankAccount bankAccount, LocalDate fromInclusive) {
		getAccountBookings(bankAccount.getId()).stream()
				.filter(this::isPrenotification)
				.filter(booking -> fromInclusive == null || booking.getDateBooking() == null
						|| !booking.getDateBooking().isBefore(fromInclusive))
				.forEach(booking -> dbController.delete(booking, null));
	}

	private Optional<BigDecimal> readSaldo(HBCIJob<GVRSaldoReq> saldoJob, Currency baseCurrency) {
		GVRSaldoReq saldoResult = saldoJob.getJobResult();
		if (saldoResult == null) {
			log.debug("No Saldo result returned.");
			return Optional.empty();
		}
		if (!saldoResult.isOK()) {
			log.error("Error in retrieving Saldo: {}", saldoResult);
			return Optional.empty();
		}

		Value saldo = readReadySaldoValue(saldoResult);
		if (saldo == null) {
			log.debug("No booked Saldo value returned.");
			return Optional.empty();
		}
		Currency saldoCurrency = Currency.forCodeOrDefault(saldo.getCurr(), baseCurrency);
		if (saldoCurrency != baseCurrency) {
			throw new GBankingException("Der Kontosaldo wurde nicht in der Kontowährung " + baseCurrency + " geliefert.");
		}

		log.info("Received bank balance from HBCI.");
		return Optional.ofNullable(saldo.getBigDecimalValue());
	}

	private Value readReadySaldoValue(GVRSaldoReq saldoResult) {
		GVRSaldoReq.Info[] entries = saldoResult.getEntries();
		if (entries == null || entries.length == 0) {
			return null;
		}
		for (GVRSaldoReq.Info entry : entries) {
			if (entry != null && entry.ready != null && entry.ready.value != null) {
				return entry.ready.value;
			}
		}
		return null;
	}

	private void updateAccountBalance(BankAccount bankAccount, BigDecimal saldo) {
		bankAccount.setBalance(saldo);
		dbController.insertOrUpdate(bankAccount);
		log.info("Updated balance for bank account id {}", bankAccount.getId());
	}

	void reconcileAccountBalance(BankAccount bankAccount, BigDecimal accountBalance) {
		List<Booking> bookings = getAccountBookings(bankAccount.getId());
		BigDecimal bookingBalance = calculateBookingBalance(bookings);
		BigDecimal difference = normalizeAmount(accountBalance).subtract(bookingBalance);
		if (isZero(difference)) {
			return;
		}

		Optional<Booking> lastAdjustment = findLastAutomaticAdjustment(bookings);
		if (lastAdjustment.isPresent() && sameAmount(difference.negate(), lastAdjustment.get().getAmount())) {
			dbController.delete(lastAdjustment.get(), null);
			log.info("Removed automatic adjustment booking {} for bank account id {}", lastAdjustment.get().getId(), bankAccount.getId());
			return;
		}

		Booking adjustment = createAutomaticAdjustmentBooking(bankAccount, difference);
		dbController.insertOrUpdate(adjustment);

		log.info("Created automatic adjustment booking {} for bank account id {}, amount={}", adjustment::getId, bankAccount::getId,
				() -> SensitiveDataMasker.describeAmount(difference));
	}

	private List<Booking> getAccountBookings(int accountId) {
		List<Booking> bookings = dbController.getAllByParent(Booking.class, accountId);
		return bookings != null ? bookings : List.of();
	}

	private BigDecimal calculateBookingBalance(List<Booking> bookings) {
		return bookings.stream()
				.filter(booking -> !isPrenotification(booking))
				.map(Booking::getAmount)
				.filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);
	}

	private Optional<Booking> findLastAutomaticAdjustment(List<Booking> bookings) {
		return bookings.stream()
				.filter(booking -> booking.getSource() == Source.AUTO_ADJUSTING || booking.getSource() == Source.AUTO_ADJUSTING_NEW)
				.max(Comparator.comparingInt(Booking::getId));
	}

	private Booking createAutomaticAdjustmentBooking(BankAccount bankAccount, BigDecimal amount) {
		Booking booking = new Booking();
		booking.setAccountId(bankAccount.getId());
		booking.setDateBooking(LocalDate.now(ZoneId.systemDefault()));
		booking.setDateValue(LocalDate.now(ZoneId.systemDefault()));
		booking.setPurpose(getText("BOOKING_PURPOSE_AUTO_ADJUSTING"));
		booking.setAmount(amount);
		booking.setBookingType(amount.signum() < 0 ? BookingType.REMOVAL : BookingType.DEPOSIT);
		booking.setSource(Source.AUTO_ADJUSTING);
		booking.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return booking;
	}

	private BigDecimal normalizeAmount(BigDecimal amount) {
		return amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
	}

	private boolean sameAmount(BigDecimal left, BigDecimal right) {
		return normalizeAmount(left).compareTo(normalizeAmount(right)) == 0;
	}

	private boolean isZero(BigDecimal amount) {
		return sameAmount(amount, BigDecimal.ZERO);
	}

	private boolean isPrenotification(Booking booking) {
		return booking != null && booking.getSource() != null && booking.getSource().isPrenotification();
	}

	private List<UmsLine> readBookedUms(HBCIJob<GVRKUms> umsatzJob) {
		return readUms(umsatzJob, false);
	}

	private List<UmsLine> readUnbookedUms(HBCIJob<GVRKUms> umsatzJob) {
		return readUms(umsatzJob, true);
	}

	private boolean hasSuccessfulUmsatzResult(HBCIJob<GVRKUms> umsatzJob) {
		GVRKUms umsResult = umsatzJob.getJobResult();
		return umsResult != null && umsResult.isOK();
	}

	private Optional<List<UmsLine>> readPrenotifications(HBCIJob<GVRKUms> umsatzJob, Optional<HBCIJob<HBCIJobResult>> vormerkpostenJob) {
		List<UmsLine> umsLines = new ArrayList<>(readUnbookedUms(umsatzJob));
		if (vormerkpostenJob.isEmpty()) {
			return Optional.of(deduplicateUmsLines(umsLines));
		}

		HBCIJobResult lowlevelResult = vormerkpostenJob.get().getJobResult();
		if (!isSuccessfulVormerkpostenResult(lowlevelResult)) {
			return useDirectPrenotificationsIfPresent(umsLines, "did not return a successful result");
		}

		Optional<List<UmsLine>> lowlevelUmsLines = readVormerkpostenUms(lowlevelResult);
		if (lowlevelUmsLines.isEmpty()) {
			return useDirectPrenotificationsIfPresent(umsLines, "could not be parsed");
		}
		umsLines.addAll(lowlevelUmsLines.get());
		return Optional.of(deduplicateUmsLines(umsLines));
	}

	private Optional<List<UmsLine>> useDirectPrenotificationsIfPresent(List<UmsLine> umsLines, String reason) {
		if (umsLines.isEmpty()) {
			return Optional.empty();
		}
		log.warn("Using {} prenotifications from the Umsatz job because the lowlevel HBCI job {} {}.", umsLines.size(), VORMERKPOSTEN_JOB, reason);
		return Optional.of(deduplicateUmsLines(umsLines));
	}

	private boolean isSuccessfulVormerkpostenResult(HBCIJobResult lowlevelResult) {
		if (lowlevelResult == null) {
			log.debug("No lowlevel HBCI {} result returned.", VORMERKPOSTEN_JOB);
			return false;
		}
		if (!lowlevelResult.isOK()) {
			log.error("Error in retrieving {}: {}", VORMERKPOSTEN_JOB, lowlevelResult);
			return false;
		}
		return true;
	}

	private Optional<List<UmsLine>> readVormerkpostenUms(HBCIJobResult lowlevelResult) {
		List<String> mt942Blocks = extractMt942ResultData(lowlevelResult);
		if (mt942Blocks.isEmpty()) {
			log.debug("No MT942 data returned by lowlevel HBCI job {}.", VORMERKPOSTEN_JOB);
			return Optional.of(List.of());
		}

		try {
			GVRKUms umsResult = new GVRKUms();
			mt942Blocks.forEach(umsResult::appendMT942Data);
			List<UmsLine> umsLines = umsResult.getFlatDataUnbooked();
			log.debug("Read {} prenotified Umsatz entries from lowlevel HBCI job {}.", umsLines != null ? umsLines.size() : 0, VORMERKPOSTEN_JOB);
			return Optional.of(umsLines != null ? umsLines : List.of());
		} catch (HBCI_Exception e) {
			log.error("Could not parse MT942 data returned by lowlevel HBCI job {}.", VORMERKPOSTEN_JOB, e);
			return Optional.empty();
		}
	}

	private List<String> extractMt942ResultData(HBCIJobResult lowlevelResult) {
		Properties resultData = lowlevelResult.getResultData();
		if (resultData == null || resultData.isEmpty()) {
			return List.of();
		}
		return resultData.stringPropertyNames().stream()
				.filter(this::isMt942ResultKey)
				.sorted()
				.map(resultData::getProperty)
				.filter(this::hasText)
				.toList();
	}

	private boolean isMt942ResultKey(String key) {
		return LOWLEVEL_RESULT_MT942.equals(key) || key.endsWith("." + LOWLEVEL_RESULT_MT942);
	}

	private List<UmsLine> deduplicateUmsLines(List<UmsLine> umsLines) {
		if (umsLines.isEmpty()) {
			return List.of();
		}
		List<UmsLine> deduplicatedUmsLines = new ArrayList<>();
		Set<String> fingerprints = new HashSet<>();
		for (UmsLine umsLine : umsLines) {
			if (fingerprints.add(createUmsLineFingerprint(umsLine))) {
				deduplicatedUmsLines.add(umsLine);
			}
		}
		return deduplicatedUmsLines;
	}

	private String createUmsLineFingerprint(UmsLine umsLine) {
		if (umsLine == null) {
			return "";
		}
		return String.join("|", formatDate(umsLine.bdate), formatDate(umsLine.valuta), formatValue(umsLine.value),
				Objects.toString(BookingDuplicateChecker.normalizePurposeForFingerprint(resolvePurpose(umsLine)), ""),
				Objects.toString(BookingDuplicateChecker.normalizeReference(umsLine.instref), ""),
				Objects.toString(BookingDuplicateChecker.normalizeReference(umsLine.customerref), ""));
	}

	private String resolvePurpose(UmsLine umsLine) {
		if (umsLine.usage == null || umsLine.usage.isEmpty()) {
			return umsLine.text;
		}
		return String.join("\n", umsLine.usage);
	}

	private String formatDate(Date date) {
		if (date == null) {
			return "";
		}
		if (date instanceof java.sql.Date sqlDate) {
			return sqlDate.toLocalDate().toString();
		}
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString();
	}

	private String formatValue(Value value) {
		if (value == null) {
			return "";
		}
		BigDecimal amount = value.getBigDecimalValue();
		String formattedAmount = amount != null ? normalizeAmount(amount).toPlainString() : "";
		return formattedAmount + " " + Objects.toString(BookingDuplicateChecker.normalizeText(value.getCurr()), "");
	}

	private List<UmsLine> readUms(HBCIJob<GVRKUms> umsatzJob, boolean unbooked) {
		GVRKUms umsResult = umsatzJob.getJobResult();
		if (umsResult == null) {
			log.debug("No Umsatz result returned.");
			return List.of();
		}

		if (!umsResult.isOK()) {
			log.error("Error in retrieving Umsatz: {}", umsResult);
		}

		List<UmsLine> umsLines = unbooked ? umsResult.getFlatDataUnbooked() : umsResult.getFlatData();
		log.debug("Read {} {} Umsatz entries from HBCI result.", umsLines != null ? umsLines.size() : 0, unbooked ? "unbooked" : "booked");
		return umsLines != null ? umsLines : List.of();
	}

	private boolean hbciKontosMatches(BankAccount bankAccount, Konto konto) {
		return bankAccount.getIban() != null && bankAccount.getIban().equalsIgnoreCase(konto.iban)
				|| bankAccount.getNumber() != null && bankAccount.getNumber().equalsIgnoreCase(konto.number);
	}

	private <T extends HBCIJobResult> HBCIJob<T> createAndAddHbciJob(HBCIHandler handle, String jobDescription, Map<String, Object> params) {
		HBCIJob<T> job = null;
		try {
			BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
			job = hbciSupport.newHbciJob(handle, jobDescription);
		} catch (HBCI_Exception hbcie) {
			job = tryCorrespondingMTJobInstead(handle, jobDescription, hbcie);
		}

		for (Entry<String, Object> param : params.entrySet()) {
			Object value = param.getValue();

			if (value instanceof String s) {
				job.setParam(param.getKey(), s);
			} else if (value instanceof Date d) {
				job.setParam(param.getKey(), d);
			} else if (value instanceof Integer i) {
				job.setParam(param.getKey(), i);
			} else if (value instanceof Konto k) {
				job.setParam(param.getKey(), k);
			} else if (value == null) {
				log.log(Level.ERROR, () -> getText("HBCI_PARAM_NULL", param.getKey()));
			} else {
				log.log(Level.ERROR, () -> getText("HBCI_PARAM_UNKNOWN_TYPE", param.getKey(), value.getClass().getName()));
			}
		}
		job.addToQueue();

		return job;
	}

	private <T extends HBCIJobResult> HBCIJob<T> tryCorrespondingMTJobInstead(HBCIHandler handle, String jobDescription, HBCI_Exception hbcie) {
		HBCIJob<T> job;
		String correspondingMtJob;
		switch (jobDescription) {
		case UMS_JOB_CAMT:
			correspondingMtJob = "KUmsAll";
			break;
		case "KUmsZeitCamt":
			correspondingMtJob = "KUmsZeit";
			break;
		default:
			throw hbcie;
		}
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		job = hbciSupport.newHbciJob(handle, correspondingMtJob);
		return job;
	}

	private Konto[] getHbciAccountsFromPassport(HBCIPassport passport) {
		Konto[] konten = passport.getAccounts();
		if (konten == null || konten.length == 0) {
			log.error("No Accounts were found on bank site");
		} else {
			log.info("Number of accounts found: {}", konten.length);
		}
		return konten;
	}

	private LocalDate getAccountLastBookingDate(BankAccount bankAccount) {
		return dbController.getSingleResultField(bankAccount, StatementsConfig.StatementType.SELECT_ACCOUNT_LAST_BOOKING_DATE, LocalDate.class);
	}

	public LocalDate getLastOnlineBookingDate(BankAccount bankAccount) {
		return getAccountLastBookingDate(bankAccount);
	}

	private void persistRetrievalStatus(BankAccount bankAccount, AccountTransactionRetrievalResult result) {
		if (bankAccount == null || bankAccount.getId() <= 0) {
			return;
		}
		dbController.upsertBankAccountRetrievalStatus(new BankAccountRetrievalStatus(bankAccount.getId(),
				LocalDateTime.now(ZoneId.systemDefault()), result.status(), result.newBookingCount(), result.pendingBookingCount(), result.errorMessage()));
	}

	public void persistExternalRetrievalStatus(BankAccount bankAccount, AccountTransactionRetrievalResult result) {
		persistRetrievalStatus(bankAccount, result);
	}

	private void persistRetrievalStatusSafely(BankAccount bankAccount, AccountTransactionRetrievalResult result, Throwable originalFailure) {
		try {
			persistRetrievalStatus(bankAccount, result);
		} catch (RuntimeException statusFailure) {
			originalFailure.addSuppressed(statusFailure);
			log.error("Could not persist failed account transaction retrieval status for account id {}", bankAccount != null ? bankAccount.getId() : null,
					statusFailure);
		}
	}

	private String toStoredError(String rawMessage, String fallbackMessageKey) {
		String extractedMessage = HbciStatusMessageExtractor.extractMessages(rawMessage);
		String message = extractedMessage.isBlank() ? getText(fallbackMessageKey) : extractedMessage;
		String normalizedMessage = message.replaceAll("\\s+", " ").trim();
		return normalizedMessage.length() <= MAX_RETRIEVAL_ERROR_LENGTH ? normalizedMessage
				: normalizedMessage.substring(0, MAX_RETRIEVAL_ERROR_LENGTH);
	}

	private void clearSecret(char[] secret) {
		HbciSessionRunner.clearSecret(secret);
	}
}
