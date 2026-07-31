package de.zft2.gbanking.service.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Saldo;
import org.kapott.hbci.structures.Value;

import de.zft2.gbanking.cache.InstituteLookupCache;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountIdentifier;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.AccountIdentifierType;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.logging.GBankingLoggingHandler;
import de.zft2.gbanking.rebooking.DetectedRebookingPair;
import de.zft2.gbanking.rebooking.MissingRebookingCreationSummary;
import de.zft2.gbanking.rebooking.MissingRebookingRouteSummary;
import de.zft2.gbanking.rebooking.RebookingAssignmentSummary;
import de.zft2.gbanking.service.bankaccess.BankAccessService;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountTransactionServiceTest {

	private Path tempDir;

	@BeforeAll
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		DBController.getInstance(tempDir.toString());
	}

	@BeforeEach
	void clearDatabase() {
		DBControllerTestUtil.clearAllTables(DBController.getConnection());
		InstituteLookupCache.clear();
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		InstituteLookupCache.clear();
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void saveHbciBookingsForAccountShouldReuseExistingRecipientAndKeepNullRecipients() {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));

		BankAccount account = new BankAccount();
		account.setIban("DE12345678901234567890");
		account.setNumber("12345678");
		account.setAccountType(AccountType.CURRENT_ACCOUNT);
		account.setSource(Source.IMPORT_INITIAL);
		account.setAccountState(AccountState.ACTIVE);
		account.setCurrency("EUR");
		account = DBController.getInstance(tempDir.toString()).insertOrUpdate(account);

		Recipient existingRecipient = new Recipient();
		existingRecipient.setName("Max Mustermann");
		existingRecipient.setIban("DE99999999999999999999");
		existingRecipient.setBic("TESTDEFFXXX");
		existingRecipient.setBlz("50010517");
		existingRecipient.setAccountNumber("99887766");
		existingRecipient.setSource(Source.ONLINE);
		existingRecipient = DBController.getInstance(tempDir.toString()).insertOrUpdate(existingRecipient);

		UmsLine bookingWithRecipient = createUmsLine(createKonto("Max Mustermann", "DE99999999999999999999", "TESTDEFFXXX", "99887766", "50010517"),
				"Testbuchung 1", BigDecimal.valueOf(123.45));
		UmsLine bookingWithoutRecipient = createUmsLine(null, "Testbuchung 2", BigDecimal.valueOf(67.89));

		service.saveHbciBookingsForAccount(account, List.of(bookingWithRecipient, bookingWithoutRecipient));

		List<Recipient> recipients = DBController.getInstance(tempDir.toString()).getAll(Recipient.class);
		assertEquals(1, recipients.size());

		List<Booking> bookings = DBController.getInstance(tempDir.toString()).getAllByParentFull(Booking.class, account.getId());
		assertEquals(2, bookings.size());

		Booking booking01 = bookings.stream().filter(booking -> booking.getRecipientId() > 0).findFirst().orElseThrow();
		Booking booking02 = bookings.stream().filter(booking -> booking.getRecipientId() == 0).findFirst().orElseThrow();

		assertEquals(existingRecipient.getId(), booking01.getRecipientId());
		assertEquals(existingRecipient.getIban(), booking01.getRecipient().getIban());
		assertEquals(Source.ONLINE_NEW, booking01.getSource());
		assertEquals("EUR", booking01.getCurrency());
		assertEquals(BookingType.DEPOSIT, booking01.getBookingType());
		BookingAdditionalDetails details = booking01.getAdditionalDetails();
		assertNotNull(details);
		assertEquals("INST-REF-1", details.getInstref());
		assertEquals("166", details.getGvcode());
		assertEquals("SEPA-Gutschrift", details.getText());
		assertEquals("PN123", details.getPrimanota());
		assertEquals("ADDKEY", details.getKey());
		assertEquals(Boolean.TRUE, details.getStorno());
		assertEquals(new BigDecimal("123.45"), details.getOrigValue());
		assertEquals(new BigDecimal("1.23"), details.getChargeValue());
		assertEquals("raw bank data", details.getRawData());
		assertEquals(Boolean.TRUE, details.getSepa());
		assertEquals(Boolean.TRUE, details.getCamt());
		assertEquals(new BigDecimal("1000.00"), details.getBankSaldo());
		assertNull(booking02.getRecipient());
	}

	@Test
	void saveHbciBookingsForAccountShouldSkipImportedRebookingWhenOnlineRemovalMatchesRecipientAmountAndPurpose() {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount account = createAccount("DE12345678901234567890", "12345678");
		BankAccount crossAccount = createAccount("DE22222222222222222222", "22222222");
		Recipient recipient = dbController.insertOrUpdate(createRecipient("Max Mustermann", "DE99999999999999999999", "TESTDEFFXXX", "99887766",
				"50010517"));
		Booking importedRebooking = createBooking(account, "Identische Buchung", new BigDecimal("-42.00"), Source.IMPORT);
		importedRebooking.setBookingType(BookingType.REBOOKING_OUT);
		importedRebooking.setCrossAccountId(crossAccount.getId());
		importedRebooking.setRecipientId(recipient.getId());
		importedRebooking.setCurrency(null);
		dbController.insertOrUpdate(importedRebooking);

		UmsLine onlineRemoval = createUmsLine(createKonto("Max Mustermann", "DE99999999999999999999", "TESTDEFFXXX", "99887766", "50010517"),
				"Identische Buchung", new BigDecimal("-42.00"));
		onlineRemoval.customerref = "ONLINE-CUSTOMER-REF";

		service.saveHbciBookingsForAccount(account, List.of(onlineRemoval));

		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, account.getId());
		assertEquals(1, bookings.size());
		assertEquals(Source.IMPORT, bookings.get(0).getSource());
		assertEquals(BookingType.REBOOKING_OUT, bookings.get(0).getBookingType());
	}

	@Test
	void saveHbciBookingsForAccountShouldSetRecipientBankNameFromInstituteDatabase() {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount account = createAccount("DE12345678901234567890", "12345678");
		insertInstitute(dbController, "50010517", "TESTDEFFXXX", "Lookup Bank");

		UmsLine onlineBooking = createUmsLine(createKonto("Max Mustermann", "DE99999999999999999999", "TESTDEFFXXX", "99887766", "50010517"),
				"Onlinebuchung mit Bankname", new BigDecimal("42.00"));

		service.saveHbciBookingsForAccount(account, List.of(onlineBooking));

		List<Recipient> recipients = dbController.getAll(Recipient.class);
		assertEquals(1, recipients.size());
		assertEquals("Lookup Bank", recipients.get(0).getBank());

		Booking booking = dbController.getAllByParentFull(Booking.class, account.getId()).get(0);
		assertEquals("Lookup Bank", booking.getRecipient().getBank());
	}

	@Test
	void saveHbciBookingsForAccountShouldSkipImportedRebookingWhenOnlineDepositMatchesRecipientAmountAndPurpose() {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount account = createAccount("DE12345678901234567890", "12345678");
		BankAccount crossAccount = createAccount("DE22222222222222222222", "22222222");
		Recipient recipient = dbController.insertOrUpdate(createRecipient("Max Mustermann", "DE99999999999999999999", "TESTDEFFXXX", "99887766",
				"50010517"));
		Booking importedRebooking = createBooking(account, "Identische Gutschrift", new BigDecimal("42.00"), Source.IMPORT);
		importedRebooking.setBookingType(BookingType.REBOOKING_IN);
		importedRebooking.setCrossAccountId(crossAccount.getId());
		importedRebooking.setRecipientId(recipient.getId());
		importedRebooking.setCurrency(null);
		dbController.insertOrUpdate(importedRebooking);

		UmsLine onlineDeposit = createUmsLine(createKonto("Max Mustermann", "DE99999999999999999999", "TESTDEFFXXX", "99887766", "50010517"),
				"Identische Gutschrift", new BigDecimal("42.00"));
		onlineDeposit.customerref = "ONLINE-CUSTOMER-REF";

		service.saveHbciBookingsForAccount(account, List.of(onlineDeposit));

		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, account.getId());
		assertEquals(1, bookings.size());
		assertEquals(Source.IMPORT, bookings.get(0).getSource());
		assertEquals(BookingType.REBOOKING_IN, bookings.get(0).getBookingType());
	}

	@Test
	void saveHbciBookingsForAccountShouldSkipDuplicateOnlineBookingFromSameDay() {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount account = createAccount("DE12345678901234567890", "12345678");
		dbController.insertOrUpdate(createBooking(account, "Doppelte Onlinebuchung\n", new BigDecimal("42.00"), Source.ONLINE));

		service.saveHbciBookingsForAccount(account, List.of(createUmsLine(null, "Doppelte Onlinebuchung", new BigDecimal("42.00"))));

		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, account.getId());
		assertEquals(1, bookings.size());
		assertEquals(Source.ONLINE, bookings.get(0).getSource());
	}

	@Test
	void saveHbciBookingsForAccountShouldSkipImportedBookingWithMoneyplexPurposeVersionSuffix() {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount account = createAccount("DE12345678901234567890", "12345678");
		dbController.insertOrUpdate(createBooking(account, "Dauerauftrag  V00010", new BigDecimal("42.00"), Source.IMPORT));

		service.saveHbciBookingsForAccount(account, List.of(createUmsLine(null, "Dauerauftrag", new BigDecimal("42.00"))));

		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, account.getId());
		assertEquals(1, bookings.size());
		assertEquals(Source.IMPORT, bookings.get(0).getSource());
		assertEquals("Dauerauftrag  V00010", bookings.get(0).getPurpose());
	}

	@Test
	void saveHbciBookingsForAccountShouldStoreAdditionalIdenticalOnlineBookingFromSameDay() {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount account = createAccount("DE12345678901234567890", "12345678");
		dbController.insertOrUpdate(createBooking(account, "Identische Tagesbuchung", new BigDecimal("42.00"), Source.ONLINE));
		UmsLine firstFetchedBooking = createUmsLine(null, "Identische Tagesbuchung", new BigDecimal("42.00"));
		UmsLine secondFetchedBooking = createUmsLine(null, "Identische Tagesbuchung", new BigDecimal("42.00"));
		firstFetchedBooking.instref = null;
		secondFetchedBooking.instref = null;

		service.saveHbciBookingsForAccount(account, List.of(firstFetchedBooking, secondFetchedBooking));

		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, account.getId());
		assertEquals(2, bookings.size());
		assertEquals(1, bookings.stream().filter(booking -> booking.getSource() == Source.ONLINE_NEW).count());
	}

	@Test
	void saveHbciBookingsForAccountShouldLinkNewOnlineBookingWithExistingCounterBooking() {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount account = createAccount("DE12345678901234567890", "12345678");
		BankAccount crossAccount = createAccount("DE22222222222222222222", "22222222");
		Recipient crossRecipient = dbController.insertOrUpdate(createRecipient("Max Mustermann", "DE12345678901234567890", "TESTDEFFXXX",
				"12345678", "50010517"));
		Booking existingCounterBooking = createBooking(crossAccount, "Umbuchung Tagesgeld", new BigDecimal("42.00"), Source.ONLINE);
		existingCounterBooking.setRecipientId(crossRecipient.getId());
		existingCounterBooking = dbController.insertOrUpdate(existingCounterBooking);

		UmsLine onlineRemoval = createUmsLine(createKonto("Max Mustermann", "DE22222222222222222222", "TESTDEFFXXX", "22222222", "50010517"),
				"Umbuchung Tagesgeld", new BigDecimal("-42.00"));

		service.saveHbciBookingsForAccount(account, List.of(onlineRemoval));

		List<Booking> accountBookings = dbController.getAllByParentFull(Booking.class, account.getId());
		Booking onlineBooking = accountBookings.stream().filter(booking -> booking.getSource() == Source.ONLINE_NEW).findFirst().orElseThrow();
		Booking linkedCounterBooking = dbController.getByIdFull(Booking.class, existingCounterBooking.getId());

		assertEquals(BookingType.REBOOKING_OUT, onlineBooking.getBookingType());
		assertEquals(BookingType.REBOOKING_IN, linkedCounterBooking.getBookingType());
		assertEquals(crossAccount.getId(), onlineBooking.getCrossAccountId());
		assertEquals(account.getId(), linkedCounterBooking.getCrossAccountId());
		assertNotNull(onlineBooking.getCrossBookingId());
		assertEquals(linkedCounterBooking.getId(), onlineBooking.getCrossBookingId());
		assertEquals(onlineBooking.getId(), linkedCounterBooking.getCrossBookingId());
	}

	@Test
	void saveHbciBookingsForAccountShouldIgnoreCounterBookingsOutsideRebookingSearchWindow() {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
		DBController dbController = DBController.getInstance(tempDir.toString());
		LocalDate onlineDate = LocalDate.of(2026, Month.JUNE, 1);
		BankAccount account = createAccount("DE12345678901234567890", "12345678");
		BankAccount crossAccount = createAccount("DE22222222222222222222", "22222222");
		Recipient crossRecipient = dbController.insertOrUpdate(createRecipient("Max Mustermann", "DE12345678901234567890", "TESTDEFFXXX",
				"12345678", "50010517"));
		Booking existingCounterBooking = createBooking(crossAccount, "Umbuchung Tagesgeld", new BigDecimal("42.00"), Source.ONLINE);
		existingCounterBooking.setDateBooking(onlineDate.minusDays(7));
		existingCounterBooking.setDateValue(onlineDate.minusDays(7));
		existingCounterBooking.setRecipientId(crossRecipient.getId());
		existingCounterBooking = dbController.insertOrUpdate(existingCounterBooking);

		UmsLine onlineRemoval = createUmsLine(createKonto("Max Mustermann", "DE22222222222222222222", "TESTDEFFXXX", "22222222", "50010517"),
				"Umbuchung Tagesgeld", new BigDecimal("-42.00"));
		setUmsLineDate(onlineRemoval, onlineDate);

		service.saveHbciBookingsForAccount(account, List.of(onlineRemoval));

		List<Booking> accountBookings = dbController.getAllByParentFull(Booking.class, account.getId());
		Booking onlineBooking = accountBookings.stream().filter(booking -> booking.getSource() == Source.ONLINE_NEW).findFirst().orElseThrow();
		Booking unlinkedCounterBooking = dbController.getByIdFull(Booking.class, existingCounterBooking.getId());

		assertNull(onlineBooking.getCrossBookingId());
		assertNull(unlinkedCounterBooking.getCrossBookingId());
		assertEquals(BookingType.REMOVAL, onlineBooking.getBookingType());
		assertEquals(BookingType.DEPOSIT, unlinkedCounterBooking.getBookingType());
	}

	@Test
	void saveHbciBookingsForAccountShouldIgnoreStaleBookingCoreTransferState() throws Exception {
		Class<?> accountProcessorClass = Class.forName("de.zft2.core.process.AccountProcessor");
		Object originalPropsTransfer = getStaticField(accountProcessorClass, "propsTransfer");
		Object originalAccountNumbersMap = getStaticField(accountProcessorClass, "accountNumbersMap");
		try {
			Properties isolatedTransferProperties = new Properties();
			Map<String, Collection<String>> staleAccountNumbersMap = new HashMap<>();
			staleAccountNumbersMap.put("Veraltetes Tagesgeld", List.of("DE22222222222222222222", "22222222"));
			setStaticField(accountProcessorClass, "propsTransfer", isolatedTransferProperties);
			setStaticField(accountProcessorClass, "accountNumbersMap", staleAccountNumbersMap);

			AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
			DBController dbController = DBController.getInstance(tempDir.toString());
			BankAccount account = createAccount("DE12345678901234567890", "12345678");
			BankAccount crossAccount = createAccount("DE22222222222222222222", "22222222");
			Recipient crossRecipient = dbController.insertOrUpdate(createRecipient("Max Mustermann", "DE12345678901234567890", "TESTDEFFXXX",
					"12345678", "50010517"));
			Booking existingCounterBooking = createBooking(crossAccount, "Umbuchung Tagesgeld", new BigDecimal("42.00"), Source.ONLINE);
			existingCounterBooking.setRecipientId(crossRecipient.getId());
			existingCounterBooking = dbController.insertOrUpdate(existingCounterBooking);

			UmsLine onlineRemoval = createUmsLine(createKonto("Max Mustermann", "DE22222222222222222222", "TESTDEFFXXX", "22222222", "50010517"),
					"Umbuchung Tagesgeld", new BigDecimal("-42.00"));

			service.saveHbciBookingsForAccount(account, List.of(onlineRemoval));

			List<Booking> accountBookings = dbController.getAllByParentFull(Booking.class, account.getId());
			Booking onlineBooking = accountBookings.stream().filter(booking -> booking.getSource() == Source.ONLINE_NEW).findFirst().orElseThrow();
			Booking linkedCounterBooking = dbController.getByIdFull(Booking.class, existingCounterBooking.getId());

			assertEquals(BookingType.REBOOKING_OUT, onlineBooking.getBookingType());
			assertEquals(BookingType.REBOOKING_IN, linkedCounterBooking.getBookingType());
			assertEquals(linkedCounterBooking.getId(), onlineBooking.getCrossBookingId());
			assertEquals(onlineBooking.getId(), linkedCounterBooking.getCrossBookingId());
		} finally {
			setStaticField(accountProcessorClass, "propsTransfer", originalPropsTransfer);
			setStaticField(accountProcessorClass, "accountNumbersMap", originalAccountNumbersMap);
		}
	}

	@Test
	void saveHbciBookingsForAccountShouldUseConfiguredTransferPropertiesForChangedCounterpartyAccount() {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount account = createAccount("DE12345678901234567890", "12345678");
		BankAccount crossAccount = createAccount("DE22222222222222222222", "22222222");
		crossAccount.setAccountName("Tagesgeld fusioniert");
		crossAccount = dbController.insertOrUpdate(crossAccount);
		dbController.replaceBankAccountIdentifiers(crossAccount.getId(), List.of(
				new BankAccountIdentifier(0, crossAccount.getId(), AccountIdentifierType.ACCOUNT_TRANSFER, "DE33333333333333333333"),
				new BankAccountIdentifier(0, crossAccount.getId(), AccountIdentifierType.ACCOUNT_TRANSFER, "33333333"),
				new BankAccountIdentifier(0, crossAccount.getId(), AccountIdentifierType.ACCOUNT_TRANSFER, "DE22222222222222222222"),
				new BankAccountIdentifier(0, crossAccount.getId(), AccountIdentifierType.ACCOUNT_TRANSFER, "22222222")));
		Recipient crossRecipient = dbController.insertOrUpdate(createRecipient("Max Mustermann", "DE12345678901234567890", "TESTDEFFXXX",
				"12345678", "50010517"));
		Booking existingCounterBooking = createBooking(crossAccount, "Umbuchung Tagesgeld", new BigDecimal("42.00"), Source.ONLINE);
		existingCounterBooking.setRecipientId(crossRecipient.getId());
		existingCounterBooking = dbController.insertOrUpdate(existingCounterBooking);

		UmsLine onlineRemoval = createUmsLine(createKonto("Max Mustermann", "DE33333333333333333333", "TESTDEFFXXX", "33333333", "50010517"),
				"Umbuchung Tagesgeld", new BigDecimal("-42.00"));

		service.saveHbciBookingsForAccount(account, List.of(onlineRemoval));

		List<Booking> accountBookings = dbController.getAllByParentFull(Booking.class, account.getId());
		Booking onlineBooking = accountBookings.stream().filter(booking -> booking.getSource() == Source.ONLINE_NEW).findFirst().orElseThrow();
		Booking linkedCounterBooking = dbController.getByIdFull(Booking.class, existingCounterBooking.getId());

		assertEquals(BookingType.REBOOKING_OUT, onlineBooking.getBookingType());
		assertEquals(BookingType.REBOOKING_IN, linkedCounterBooking.getBookingType());
		assertEquals(crossAccount.getId(), onlineBooking.getCrossAccountId());
		assertEquals(linkedCounterBooking.getId(), onlineBooking.getCrossBookingId());
		assertEquals(onlineBooking.getId(), linkedCounterBooking.getCrossBookingId());
	}

	@Test
	void detectRebookingsShouldSummarizeSelectedAccountAndPersistOnlyAfterConfirmation() {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
		DBController dbController = DBController.getInstance(tempDir.toString());
		LocalDate rebookingDate = LocalDate.of(2026, Month.JUNE, 1);
		BankAccount account = createAccount("DE12345678901234567890", "12345678");
		BankAccount crossAccount = createAccount("DE22222222222222222222", "22222222");
		Recipient recipientToCrossAccount = dbController.insertOrUpdate(createRecipient("Tagesgeld", "DE22222222222222222222", "TESTDEFFXXX",
				"22222222", "50010517"));
		Recipient recipientToAccount = dbController.insertOrUpdate(createRecipient("Girokonto", "DE12345678901234567890", "TESTDEFFXXX",
				"12345678", "50010517"));

		Booking removal = createBooking(account, "Umbuchung Tagesgeld", new BigDecimal("-42.00"), Source.ONLINE);
		removal.setDateBooking(rebookingDate);
		removal.setDateValue(rebookingDate);
		removal.setRecipientId(recipientToCrossAccount.getId());
		removal = dbController.insertOrUpdate(removal);
		Booking deposit = createBooking(crossAccount, "Umbuchung Tagesgeld", new BigDecimal("42.00"), Source.ONLINE);
		deposit.setDateBooking(rebookingDate.plusDays(3));
		deposit.setDateValue(rebookingDate.plusDays(3));
		deposit.setRecipientId(recipientToAccount.getId());
		deposit = dbController.insertOrUpdate(deposit);

		RebookingAssignmentSummary summary = service.detectRebookings(rebookingDate, rebookingDate, List.of(account));

		assertEquals(1, summary.pairCount());
		assertEquals(2, summary.accountSummaries().size());
		assertEquals(1, summary.accountSummaries().stream()
				.filter(accountSummary -> accountSummary.accountId() == account.getId() && accountSummary.foundRebookings() == 1).count());
		assertEquals(1, summary.accountSummaries().stream()
				.filter(accountSummary -> accountSummary.accountId() == crossAccount.getId() && accountSummary.foundRebookings() == 1).count());
		assertNull(dbController.getByIdFull(Booking.class, removal.getId()).getCrossBookingId());
		assertNull(dbController.getByIdFull(Booking.class, deposit.getId()).getCrossBookingId());

		assertEquals(1, service.persistDetectedRebookingLinks(summary));

		Booking linkedRemoval = dbController.getByIdFull(Booking.class, removal.getId());
		Booking linkedDeposit = dbController.getByIdFull(Booking.class, deposit.getId());
		assertEquals(BookingType.REBOOKING_OUT, linkedRemoval.getBookingType());
		assertEquals(BookingType.REBOOKING_IN, linkedDeposit.getBookingType());
		assertEquals(crossAccount.getId(), linkedRemoval.getCrossAccountId());
		assertEquals(account.getId(), linkedDeposit.getCrossAccountId());
		assertEquals(linkedDeposit.getId(), linkedRemoval.getCrossBookingId());
		assertEquals(linkedRemoval.getId(), linkedDeposit.getCrossBookingId());
	}

	@Test
	void detectMissingRebookingsShouldCreateManualNewCounterBookingAfterConfirmation() {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
		DBController dbController = DBController.getInstance(tempDir.toString());
		LocalDate rebookingDate = LocalDate.of(2026, Month.JUNE, 1);
		BankAccount account = createAccount("DE12345678901234567890", "12345678");
		BankAccount targetAccount = createAccount("DE22222222222222222222", "22222222");
		Recipient recipientToTargetAccount = dbController.insertOrUpdate(createRecipient("Tagesgeld", "DE22222222222222222222", "TESTDEFFXXX",
				"22222222", "50010517"));

		Booking missingCounterSource = createBooking(account, "Umbuchung Tagesgeld", new BigDecimal("-42.00"), Source.ONLINE);
		missingCounterSource.setDateBooking(rebookingDate);
		missingCounterSource.setDateValue(rebookingDate);
		missingCounterSource.setRecipientId(recipientToTargetAccount.getId());
		missingCounterSource = dbController.insertOrUpdate(missingCounterSource);

		MissingRebookingCreationSummary summary = service.detectMissingRebookings(rebookingDate, rebookingDate, List.of(account));

		assertEquals(1, summary.candidateCount());
		assertEquals(1, summary.routeSummaries().size());
		MissingRebookingRouteSummary routeSummary = summary.routeSummaries().get(0);
		assertEquals(account.getId(), routeSummary.sourceAccountId());
		assertEquals(targetAccount.getId(), routeSummary.targetAccountId());
		assertEquals(1, routeSummary.missingRebookings());
		assertNull(dbController.getByIdFull(Booking.class, missingCounterSource.getId()).getCrossBookingId());
		assertEquals(0, dbController.getAllByParentFull(Booking.class, targetAccount.getId()).size());

		assertEquals(1, service.createMissingRebookings(summary));

		Booking linkedSource = dbController.getByIdFull(Booking.class, missingCounterSource.getId());
		List<Booking> targetBookings = dbController.getAllByParentFull(Booking.class, targetAccount.getId());
		assertEquals(1, targetBookings.size());
		Booking createdCounterBooking = targetBookings.get(0);
		assertEquals(Source.MANUELL_NEW, createdCounterBooking.getSource());
		assertEquals(new BigDecimal("42.00"), createdCounterBooking.getAmount());
		assertEquals(BookingType.REBOOKING_IN, createdCounterBooking.getBookingType());
		assertEquals(BookingType.REBOOKING_OUT, linkedSource.getBookingType());
		assertEquals(targetAccount.getId(), linkedSource.getCrossAccountId());
		assertEquals(account.getId(), createdCounterBooking.getCrossAccountId());
		assertEquals(createdCounterBooking.getId(), linkedSource.getCrossBookingId());
		assertEquals(linkedSource.getId(), createdCounterBooking.getCrossBookingId());
		assertEquals(account.getIban(), createdCounterBooking.getRecipient().getIban());
	}

	@Test
	void persistDetectedRebookingLinksShouldSkipSameAccountPairWithoutCancellation() {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount account = createAccount("DE12345678901234567890", "12345678");
		Booking removal = dbController.insertOrUpdate(createBooking(account, "Selbstreferenz", new BigDecimal("-42.00"), Source.ONLINE));
		Booking deposit = dbController.insertOrUpdate(createBooking(account, "Selbstreferenz", new BigDecimal("42.00"), Source.ONLINE));
		RebookingAssignmentSummary summary = new RebookingAssignmentSummary(List.of(new DetectedRebookingPair(removal, deposit)), List.of());

		assertEquals(0, service.persistDetectedRebookingLinks(summary));

		Booking reloadedRemoval = dbController.getByIdFull(Booking.class, removal.getId());
		Booking reloadedDeposit = dbController.getByIdFull(Booking.class, deposit.getId());
		assertNull(reloadedRemoval.getCrossBookingId());
		assertNull(reloadedDeposit.getCrossBookingId());
		assertEquals(BookingType.REMOVAL, reloadedRemoval.getBookingType());
		assertEquals(BookingType.DEPOSIT, reloadedDeposit.getBookingType());
	}

	@Test
	void persistDetectedRebookingLinksShouldAllowSameAccountCancellationPair() {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount account = createAccount("DE12345678901234567890", "12345678");
		Booking cancellation = createBooking(account, "Storno", new BigDecimal("-42.00"), Source.ONLINE);
		cancellation.setBookingType(BookingType.CANCEL);
		cancellation = dbController.insertOrUpdate(cancellation);
		Booking counterCancellation = createBooking(account, "Storno", new BigDecimal("42.00"), Source.ONLINE);
		counterCancellation.setBookingType(BookingType.CANCEL);
		counterCancellation = dbController.insertOrUpdate(counterCancellation);
		RebookingAssignmentSummary summary = new RebookingAssignmentSummary(List.of(new DetectedRebookingPair(cancellation, counterCancellation)),
				List.of());

		assertEquals(1, service.persistDetectedRebookingLinks(summary));

		Booking reloadedCancellation = dbController.getByIdFull(Booking.class, cancellation.getId());
		Booking reloadedCounterCancellation = dbController.getByIdFull(Booking.class, counterCancellation.getId());
		assertEquals(counterCancellation.getId(), reloadedCancellation.getCrossBookingId());
		assertEquals(cancellation.getId(), reloadedCounterCancellation.getCrossBookingId());
		assertEquals(account.getId(), reloadedCancellation.getCrossAccountId());
		assertEquals(account.getId(), reloadedCounterCancellation.getCrossAccountId());
		assertEquals(BookingType.CANCEL, reloadedCancellation.getBookingType());
		assertEquals(BookingType.CANCEL, reloadedCounterCancellation.getBookingType());
	}

	@Test
	void releaseRebookingLinksShouldClearOnlyCrossBookingIdsForSelectedBookingAndCounterBooking() {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount account = createAccount("DE12345678901234567890", "12345678");
		BankAccount crossAccount = createAccount("DE22222222222222222222", "22222222");
		Booking removal = dbController.insertOrUpdate(createBooking(account, "Umbuchung Tagesgeld", new BigDecimal("-42.00"), Source.ONLINE));
		Booking deposit = dbController.insertOrUpdate(createBooking(crossAccount, "Umbuchung Tagesgeld", new BigDecimal("42.00"), Source.ONLINE));
		removal.setBookingType(BookingType.REBOOKING_OUT);
		removal.setCrossAccountId(crossAccount.getId());
		removal.setCrossBookingId(deposit.getId());
		deposit.setBookingType(BookingType.REBOOKING_IN);
		deposit.setCrossAccountId(account.getId());
		deposit.setCrossBookingId(removal.getId());
		dbController.insertOrUpdate(removal);
		dbController.insertOrUpdate(deposit);

		assertEquals(2, service.releaseRebookingLinks(List.of(removal)));

		Booking releasedRemoval = dbController.getByIdFull(Booking.class, removal.getId());
		Booking releasedDeposit = dbController.getByIdFull(Booking.class, deposit.getId());
		assertNull(releasedRemoval.getCrossBookingId());
		assertNull(releasedDeposit.getCrossBookingId());
		assertEquals(crossAccount.getId(), releasedRemoval.getCrossAccountId());
		assertEquals(account.getId(), releasedDeposit.getCrossAccountId());
		assertEquals(BookingType.REBOOKING_OUT, releasedRemoval.getBookingType());
		assertEquals(BookingType.REBOOKING_IN, releasedDeposit.getBookingType());
		assertEquals(new BigDecimal("-42.00"), releasedRemoval.getAmount());
		assertEquals(new BigDecimal("42.00"), releasedDeposit.getAmount());
	}

	@Test
	void createAndAddHbciJobShouldApplySupportedParamTypesAndQueueJob() throws Exception {
		BankAccessService hbciSupport = mock(BankAccessService.class);
		GBankingLoggingHandler logHandler = mock(GBankingLoggingHandler.class);
		AccountTransactionService service = new AccountTransactionService(hbciSupport, logHandler);
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);

		when(hbciSupport.newHbciJob(handle, "TestJob")).thenReturn(job);

		Konto konto = createKonto("Name", "DE123", "BICCODE", "4711", "50010517");
		Date startDate = new Date();

		HBCIJob<?> createdJob = (HBCIJob<?>) invokePrivate(service, "createAndAddHbciJob",
				new Class<?>[] { HBCIHandler.class, String.class, Map.class },
				handle, "TestJob", Map.of("text", "value", "date", startDate, "count", Integer.valueOf(3), "konto", konto));

		assertEquals(job, createdJob);
		verify(job).setParam("text", "value");
		verify(job).setParam("date", startDate);
		verify(job).setParam("count", 3);
		verify(job).setParam("konto", konto);
		verify(job).addToQueue();
	}

	@Test
	void hbciKontosMatchesShouldMatchByIbanOrAccountNumber() throws Exception {
		AccountTransactionService service = new AccountTransactionService(mock(BankAccessService.class), mock(GBankingLoggingHandler.class));
		BankAccount account = new BankAccount();
		account.setIban("DE12345678901234567890");
		account.setNumber("12345678");

		Konto ibanMatch = createKonto("Name", "DE12345678901234567890", "BIC", "99999999", "50010517");
		Konto numberMatch = createKonto("Name", "DE00000000000000000000", "BIC", "12345678", "50010517");
		Konto noMatch = createKonto("Name", "DE00000000000000000000", "BIC", "87654321", "50010517");

		assertTrue((Boolean) invokePrivate(service, "hbciKontosMatches", new Class<?>[] { BankAccount.class, Konto.class }, account, ibanMatch));
		assertTrue((Boolean) invokePrivate(service, "hbciKontosMatches", new Class<?>[] { BankAccount.class, Konto.class }, account, numberMatch));
		assertEquals(Boolean.FALSE, invokePrivate(service, "hbciKontosMatches", new Class<?>[] { BankAccount.class, Konto.class }, account, noMatch));
	}

	private BankAccount createAccount(String iban, String number) {
		BankAccount account = new BankAccount();
		account.setIban(iban);
		account.setNumber(number);
		account.setAccountType(AccountType.CURRENT_ACCOUNT);
		account.setSource(Source.IMPORT_INITIAL);
		account.setAccountState(AccountState.ACTIVE);
		account.setCurrency("EUR");
		return DBController.getInstance(tempDir.toString()).insertOrUpdate(account);
	}

	private static Booking createBooking(BankAccount account, String purpose, BigDecimal amount, Source source) {
		Booking booking = new Booking();
		booking.setAccountId(account.getId());
		booking.setDateBooking(LocalDate.now(ZoneId.systemDefault()));
		booking.setDateValue(LocalDate.now(ZoneId.systemDefault()));
		booking.setPurpose(purpose);
		booking.setAmount(amount);
		booking.setCurrency("EUR");
		booking.setBookingType(amount.signum() < 0 ? BookingType.REMOVAL : BookingType.DEPOSIT);
		booking.setSource(source);
		booking.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return booking;
	}

	private static Recipient createRecipient(String name, String iban, String bic, String number, String blz) {
		Recipient recipient = new Recipient();
		recipient.setName(name);
		recipient.setIban(iban);
		recipient.setBic(bic);
		recipient.setAccountNumber(number);
		recipient.setBlz(blz);
		recipient.setSource(Source.IMPORT);
		return recipient;
	}

	private static void insertInstitute(DBController dbController, String blz, String bic, String bankName) {
		int importHistoryId = dbController.insertOrUpdate(new ImportHistory("online-recipient-bank-lookup.csv")).getId();
		Institute institute = new Institute();
		institute.setBlz(blz);
		institute.setBic(bic);
		institute.setBankName(bankName);
		institute.setImportNumber(1);
		institute.setLastChanged(LocalDate.of(2026, Month.APRIL, 10));
		institute.setImportFile(importHistoryId);
		institute.setStateType(InstituteStatus.ACTIVE);
		dbController.insertOrUpdate(institute);
		InstituteLookupCache.clear();
	}

	private static UmsLine createUmsLine(Konto other, String usageLine, BigDecimal amount) {
		UmsLine umsLine = new UmsLine();
		umsLine.bdate = new Date();
		umsLine.valuta = new Date();
		umsLine.usage = List.of(usageLine);
		Value value = new Value();
		value.setCurr("EUR");
		value.setValue(amount);
		umsLine.value = value;
		umsLine.other = other;
		umsLine.instref = "INST-REF-1";
		umsLine.gvcode = "166";
		umsLine.text = "SEPA-Gutschrift";
		umsLine.primanota = "PN123";
		umsLine.addkey = "ADDKEY";
		umsLine.isStorno = true;
		umsLine.orig_value = new Value(new BigDecimal("123.45"), "EUR");
		umsLine.charge_value = new Value(new BigDecimal("1.23"), "EUR");
		umsLine.additional = "raw bank data";
		umsLine.isSepa = true;
		umsLine.isCamt = true;
		umsLine.saldo = new Saldo();
		umsLine.saldo.value = new Value(new BigDecimal("1000.00"), "EUR");
		return umsLine;
	}

	private static void setUmsLineDate(UmsLine umsLine, LocalDate date) {
		Date utilDate = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
		umsLine.bdate = utilDate;
		umsLine.valuta = utilDate;
	}

	private static Konto createKonto(String name, String iban, String bic, String number, String blz) {
		Konto konto = new Konto();
		konto.name = name;
		konto.iban = iban;
		konto.bic = bic;
		konto.number = number;
		konto.blz = blz;
		return konto;
	}

	private static Object invokePrivate(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
		Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
		method.setAccessible(true);
		return method.invoke(target, args);
	}

	private static Object getStaticField(Class<?> type, String fieldName) throws Exception {
		Field field = type.getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.get(null);
	}

	private static void setStaticField(Class<?> type, String fieldName, Object value) throws Exception {
		Field field = type.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}

}
