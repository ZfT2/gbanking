package de.zft2.gbanking.service.account;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.GV_Result.GVRSaldoReq;
import org.kapott.hbci.GV_Result.GVRSaldoReq.Info;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Saldo;
import org.kapott.hbci.structures.Value;
import org.mockito.MockedConstruction;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountRetrievalStatus;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingForeignCurrencyDetails;
import de.zft2.gbanking.db.dao.enu.AccountRetrievalStatus;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.messages.Messages;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.service.Service;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.ServiceStubbingUtil;
import de.zft2.gbanking.testdata.TestDataFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountTransactionServiceAdditionalTest {

	private Path tempDir;

	private static final List<Class<? extends Service>> SERVICES_TO_STUB = List.of(BankAccessService.class);

	@BeforeEach
	void setUp() throws Exception {
		clearDatabase();
		ServiceStubbingUtil.initStubbedServicesInContext(SERVICES_TO_STUB);
	}

	@AfterEach
	void tearDown() throws Exception {
		ServiceStubbingUtil.unloadStubbedServicesInContext(SERVICES_TO_STUB);
	}

	@BeforeAll
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		DBController.getInstance(tempDir.toString());
	}

	private void clearDatabase() {
		DBControllerTestUtil.clearAllTables(DBController.getConnection());
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void createUmsatzJob_shouldRequestAllBookingsWithoutStartDateWhenAccountHasNoBookings() throws Exception {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		AccountTransactionService service = new AccountTransactionService();
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRKUms> job = mock(HBCIJob.class);
		doReturn(job).when(hbciSupport).newHbciJob(handle, "KUmsAllCamt");
		Konto konto = new Konto();

		HBCIJob<?> createdJob = (HBCIJob<?>) invokePrivate(service, "createUmsatzJob",
				new Class<?>[] { HBCIHandler.class, Konto.class, LocalDate.class }, handle, konto, null);

		assertSame(job, createdJob);
		verify(job).setParam("my", konto);
		verify(job, never()).setParam(eq("startdate"), any(Date.class));
		verify(job).addToQueue();
	}

	@Test
	void createUmsatzJob_shouldIncludeProvidedRetrievalStartDate() throws Exception {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		AccountTransactionService service = new AccountTransactionService();
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRKUms> job = mock(HBCIJob.class);
		doReturn(job).when(hbciSupport).newHbciJob(handle, "KUmsAllCamt");
		Konto konto = new Konto();
		LocalDate retrievalStartDate = LocalDate.of(2026, Month.MAY, 1);

		HBCIJob<?> createdJob = (HBCIJob<?>) invokePrivate(service, "createUmsatzJob",
				new Class<?>[] { HBCIHandler.class, Konto.class, LocalDate.class }, handle, konto, retrievalStartDate);

		assertSame(job, createdJob);
		verify(job).setParam("my", konto);
		verify(job).setParam("startdate", toUtilDate(retrievalStartDate));
		verify(job).addToQueue();
	}

	@Test
	void resolveBookingRetrievalStartDate_shouldUseOneDayOverlapFromLastBookingDate() throws Exception {
		AccountTransactionService service = new AccountTransactionService();
		LocalDate lastBookingDate = LocalDate.now(ZoneId.systemDefault()).minusDays(10);

		LocalDate retrievalStartDate = (LocalDate) invokePrivate(service, "resolveBookingRetrievalStartDate",
				new Class<?>[] { LocalDate.class }, lastBookingDate);

		assertEquals(lastBookingDate.minusDays(1), retrievalStartDate);
	}

	@Test
	void createAndAddHbciJob_shouldIgnoreNullAndUnsupportedParamsButStillQueueJob() throws Exception {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		AccountTransactionService service = new AccountTransactionService();
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		when(hbciSupport.newHbciJob(handle, "TestJob")).thenReturn(job);

		Map<String, Object> params = new LinkedHashMap<>();
		params.put("nullValue", null);
		params.put("unsupportedValue", Double.valueOf(1.23));

		HBCIJob<?> createdJob = (HBCIJob<?>) invokePrivate(service, "createAndAddHbciJob",
				new Class<?>[] { HBCIHandler.class, String.class, Map.class }, handle, "TestJob", params);

		assertSame(job, createdJob);
		verify(job, never()).setParam(eq("nullValue"), any(String.class));
		verify(job, never()).setParam(eq("unsupportedValue"), any(String.class));
		verify(job).addToQueue();
	}

	@Test
	void getHbciAccountsFromPassport_shouldReturnNullAndEmptyAccountsAsProvided() throws Exception {
		AccountTransactionService service = new AccountTransactionService();
		HBCIPassport passport = mock(HBCIPassport.class);
		when(passport.getAccounts()).thenReturn(null);

		assertNull(invokePrivate(service, "getHbciAccountsFromPassport", new Class<?>[] { HBCIPassport.class }, passport));

		Konto[] emptyAccounts = new Konto[0];
		when(passport.getAccounts()).thenReturn(emptyAccounts);

		assertSame(emptyAccounts, invokePrivate(service, "getHbciAccountsFromPassport", new Class<?>[] { HBCIPassport.class }, passport));
	}

	@Test
	void hbciKontosMatches_shouldReturnFalseWhenAccountHasNoIdentifiers() throws Exception {
		AccountTransactionService service = new AccountTransactionService();
		BankAccount bankAccount = new BankAccount();
		Konto konto = new Konto();
		konto.iban = "DE12345678901234567890";
		konto.number = "12345678";

		assertFalse((Boolean) invokePrivate(service, "hbciKontosMatches", new Class<?>[] { BankAccount.class, Konto.class }, bankAccount, konto));
	}

	@Test
	void clearSecret_shouldOverwriteSecretCharsAndAcceptNull() throws Exception {
		AccountTransactionService service = new AccountTransactionService();
		char[] secret = "12345".toCharArray();

		invokePrivate(service, "clearSecret", new Class<?>[] { char[].class }, secret);
		invokePrivate(service, "clearSecret", new Class<?>[] { char[].class }, new Object[] { null });

		assertArrayEquals(new char[] { '\0', '\0', '\0', '\0', '\0' }, secret);
	}

	@Test
	void retrieveAccountTransactions_shouldReturnFalseAndClearPinWhenBankAccessIsMissing() {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		AccountTransactionService service = new AccountTransactionService();
		BankAccount bankAccount = TestDataFactory.createSampleAccount(null);
		char[] pin = "1234".toCharArray();

		when(hbciSupport.initBankAccess(bankAccount, pin)).thenReturn(null);

		boolean result = service.retrieveAccountTransactions(bankAccount, pin);

		assertFalse(result);
		assertArrayEquals(new char[] { '\0', '\0', '\0', '\0' }, pin);
		verify(hbciSupport, never()).initBankConnection(any(BankAccess.class), any(GBankingHBCICallback.class));
	}

	@Test
	void retrieveAccountTransactionsWithResult_shouldFlagWrongPinStatus() {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount bankAccount = TestDataFactory.createSampleAccount(null);
		bankAccount.setIban("DE44500105175407324931");
		bankAccount.setNumber("987654321");
		bankAccount = dbController.insertOrUpdate(bankAccount);
		Booking previousNewBooking = TestDataFactory.createSampleBooking(bankAccount.getId());
		previousNewBooking.setSource(Source.ONLINE_NEW);
		previousNewBooking = dbController.insertOrUpdate(previousNewBooking);

		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		AccountTransactionService service = new AccountTransactionService();
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("10020030");
		char[] pin = "1234".toCharArray();
		HBCIPassport passport = mock(HBCIPassport.class);
		HBCIHandler handle = mock(HBCIHandler.class);
		Konto konto = createKonto("DE44500105175407324931", "987654321");
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRSaldoReq> saldoJob = mock(HBCIJob.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRKUms> umsatzJob = mock(HBCIJob.class);

		when(hbciSupport.initBankAccess(bankAccount, pin)).thenReturn(bankAccess);
		doReturn(passport).when(hbciSupport).initBankConnection(eq(bankAccess), any(GBankingHBCICallback.class));
		doReturn(handle).when(hbciSupport).createHBCIHandler(eq(BaseMessagesDb.getVersion().getId()), same(passport));
		doReturn(saldoJob).when(hbciSupport).newHbciJob(handle, "SaldoReq");
		doReturn(umsatzJob).when(hbciSupport).newHbciJob(handle, "KUmsAllCamt");
		when(passport.getAccounts()).thenReturn(new Konto[] { konto });
		when(status.isOK()).thenReturn(false);
		when(status.getErrorString()).thenReturn("HIRMG:2:2+9942::*Anmeldedaten sind ung\u00fcltig.'");
		when(handle.execute()).thenReturn(status);

		try (MockedConstruction<GBankingHBCICallback> callbacks = mockConstruction(GBankingHBCICallback.class)) {
			AccountTransactionRetrievalResult result = service.retrieveAccountTransactionsWithResult(bankAccount, pin);

			assertFalse(result.successful());
			assertTrue(result.wrongPin());
			assertEquals(Source.ONLINE_NEW, dbController.getByIdFull(Booking.class, previousNewBooking.getId()).getSource());
			BankAccountRetrievalStatus retrievalStatus = dbController.getBankAccountRetrievalStatus(bankAccount.getId());
			assertEquals(AccountRetrievalStatus.WRONG_PIN, retrievalStatus.result());
			assertEquals(0, retrievalStatus.newBookingCount());
			assertEquals(0, retrievalStatus.pendingBookingCount());
			assertArrayEquals(new char[] { '\0', '\0', '\0', '\0' }, pin);
			verify(callbacks.constructed().get(0)).handleFailure("HIRMG:2:2+9942::*Anmeldedaten sind ung\u00fcltig.'");
		}
	}

	@Test
	void retrieveAccountTransactionsWithResult_shouldFlagWrongPinTransportFailure() {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		AccountTransactionService service = new AccountTransactionService();
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("10020030");
		char[] pin = "1234".toCharArray();
		HBCI_Exception exception = new HBCI_Exception("Fehler beim Empfangen der Daten vom HBCI-Server",
				new IOException("Server returned HTTP response code: 400 for URL: https://fints.dkb.de:443/fints"));

		when(hbciSupport.initBankAccess(bankAccount, pin)).thenReturn(bankAccess);
		doThrow(exception).when(hbciSupport).initBankConnection(eq(bankAccess), any(GBankingHBCICallback.class));

		try (MockedConstruction<GBankingHBCICallback> callbacks = mockConstruction(GBankingHBCICallback.class)) {
			AccountTransactionRetrievalResult result = service.retrieveAccountTransactionsWithResult(bankAccount, pin);

			assertFalse(result.successful());
			assertTrue(result.wrongPin());
			assertArrayEquals(new char[] { '\0', '\0', '\0', '\0' }, pin);
			verify(callbacks.constructed().get(0)).handleException(exception);
			verify(callbacks.constructed().get(0)).finishStatusDialog();
		}
	}

	@Test
	void retrieveAccountTransactions_shouldExecuteMatchingJobsPersistBookingsAndClearPin() {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount bankAccount = TestDataFactory.createSampleAccount(null);
		bankAccount.setIban("DE44500105175407324931");
		bankAccount.setNumber("987654321");
		bankAccount = dbController.insertOrUpdate(bankAccount);
		Booking previousNewBooking = TestDataFactory.createSampleBooking(bankAccount.getId());
		LocalDate existingBookingDate = LocalDate.now(ZoneId.systemDefault()).minusDays(10);
		previousNewBooking.setDateBooking(existingBookingDate);
		previousNewBooking.setDateValue(existingBookingDate);
		previousNewBooking.setSource(Source.ONLINE_NEW);
		previousNewBooking = dbController.insertOrUpdate(previousNewBooking);

		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		AccountTransactionService service = new AccountTransactionService();
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("10020030");
		char[] pin = "1234".toCharArray();
		HBCIPassport passport = mock(HBCIPassport.class);
		HBCIHandler handle = mock(HBCIHandler.class);
		Konto konto = createKonto("DE44500105175407324931", "987654321");
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRSaldoReq> saldoJob = mock(HBCIJob.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRKUms> umsatzJob = mock(HBCIJob.class);
		GVRSaldoReq saldoResult = createSaldoResult(new BigDecimal("1242.50"));
		GVRKUms umsatzResult = mock(GVRKUms.class);

		when(hbciSupport.initBankAccess(bankAccount, pin)).thenReturn(bankAccess);
		doReturn(passport).when(hbciSupport).initBankConnection(eq(bankAccess), any(GBankingHBCICallback.class));
		doReturn(handle).when(hbciSupport).createHBCIHandler(eq(BaseMessagesDb.getVersion().getId()), same(passport));
		doReturn(saldoJob).when(hbciSupport).newHbciJob(handle, "SaldoReq");
		doReturn(umsatzJob).when(hbciSupport).newHbciJob(handle, "KUmsAllCamt");
		when(passport.getAccounts()).thenReturn(new Konto[] { konto });
		when(status.isOK()).thenReturn(true);
		when(handle.execute()).thenReturn(status);
		when(saldoJob.getJobResult()).thenReturn(saldoResult);
		when(umsatzJob.getJobResult()).thenReturn(umsatzResult);
		when(umsatzResult.isOK()).thenReturn(true);
		when(umsatzResult.getFlatData()).thenReturn(List.of(createUmsLine()));
		when(umsatzResult.getFlatDataUnbooked()).thenReturn(List.of(createUmsLine("Pending online booking", new BigDecimal("13.37"))));

		try (MockedConstruction<GBankingHBCICallback> callbacks = mockConstruction(GBankingHBCICallback.class)) {
			AccountTransactionRetrievalResult result = service.retrieveAccountTransactionsWithResult(bankAccount, pin);

			List<Booking> bookings = dbController.getAllByParentFull(Booking.class, bankAccount.getId());
			assertTrue(result.successful());
			assertEquals(1, result.newBookingCount());
			assertEquals(1, result.pendingBookingCount());
			assertEquals(3, bookings.size());
			assertEquals(Source.ONLINE, dbController.getByIdFull(Booking.class, previousNewBooking.getId()).getSource());
			Booking pendingBooking = bookings.stream().filter(booking -> booking.getSource() == Source.ONLINE_PRENO_NEW).findFirst().orElseThrow();
			assertEquals(new BigDecimal("13.37"), pendingBooking.getAmount());
			assertEquals(Currency.EUR, bankAccount.getBaseCurrency());
			assertEquals(BookingType.DEPOSIT, pendingBooking.getBookingType());
			assertEquals(0, new BigDecimal("1242.50").compareTo(dbController.getById(BankAccount.class, bankAccount.getId()).getBalance()));
			assertEquals(1, dbController.getAll(Recipient.class).size());
			BankAccountRetrievalStatus retrievalStatus = dbController.getBankAccountRetrievalStatus(bankAccount.getId());
			assertEquals(AccountRetrievalStatus.SUCCESS, retrievalStatus.result());
			assertEquals(1, retrievalStatus.newBookingCount());
			assertEquals(1, retrievalStatus.pendingBookingCount());
			assertNull(retrievalStatus.lastError());
			assertArrayEquals(new char[] { '\0', '\0', '\0', '\0' }, pin);
			verify(saldoJob).setParam("my", konto);
			verify(umsatzJob).setParam("my", konto);
			verify(umsatzJob).setParam("startdate", toUtilDate(existingBookingDate.minusDays(1)));
			verify(handle).execute();
			verify(callbacks.constructed().get(0)).startStatusDialog();
			verify(callbacks.constructed().get(0)).finishStatusDialog();
			verify(passport).close();
		}
	}

	@Test
	void retrieveAccountTransactions_shouldPersistLowlevelPrenotificationsWhenSupported() {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount bankAccount = TestDataFactory.createSampleAccount(null);
		bankAccount.setIban("DE44500105175407324931");
		bankAccount.setNumber("987654321");
		bankAccount.setBlz("10020030");
		bankAccount = dbController.insertOrUpdate(bankAccount);

		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		AccountTransactionService service = new AccountTransactionService();
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("10020030");
		char[] pin = "1234".toCharArray();
		HBCIPassport passport = mock(HBCIPassport.class);
		HBCIHandler handle = mock(HBCIHandler.class);
		Konto konto = createKonto("DE44500105175407324931", "987654321");
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRSaldoReq> saldoJob = mock(HBCIJob.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRKUms> umsatzJob = mock(HBCIJob.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> vormerkpostenJob = mock(HBCIJob.class);
		GVRKUms umsatzResult = mock(GVRKUms.class);
		HBCIJobResult vormerkpostenResult = mock(HBCIJobResult.class);
		Properties vormerkpostenResultData = new Properties();
		vormerkpostenResultData.setProperty("content.mt942", ":20:dummy-mt942");
		UmsLine lowlevelUmsLine = createUmsLine("Pending lowlevel booking", new BigDecimal("23.45"));

		when(hbciSupport.initBankAccess(bankAccount, pin)).thenReturn(bankAccess);
		doReturn(passport).when(hbciSupport).initBankConnection(eq(bankAccess), any(GBankingHBCICallback.class));
		doReturn(handle).when(hbciSupport).createHBCIHandler(eq(BaseMessagesDb.getVersion().getId()), same(passport));
		doReturn(saldoJob).when(hbciSupport).newHbciJob(handle, "SaldoReq");
		doReturn(umsatzJob).when(hbciSupport).newHbciJob(handle, "KUmsAllCamt");
		doReturn(vormerkpostenJob).when(hbciSupport).newLowlevelHbciJob(handle, "Vormerkposten");
		when(passport.getAccounts()).thenReturn(new Konto[] { konto });
		when(handle.getSupportedLowlevelJobs()).thenReturn(createSupportedLowlevelJobs());
		when(status.isOK()).thenReturn(true);
		when(handle.execute()).thenReturn(status);
		when(umsatzJob.getJobResult()).thenReturn(umsatzResult);
		when(umsatzResult.isOK()).thenReturn(true);
		when(umsatzResult.getFlatData()).thenReturn(List.of());
		when(umsatzResult.getFlatDataUnbooked()).thenReturn(List.of());
		when(vormerkpostenJob.getJobResult()).thenReturn(vormerkpostenResult);
		when(vormerkpostenResult.isOK()).thenReturn(true);
		when(vormerkpostenResult.getResultData()).thenReturn(vormerkpostenResultData);

		try (MockedConstruction<GBankingHBCICallback> callbacks = mockConstruction(GBankingHBCICallback.class);
				MockedConstruction<GVRKUms> umsResults = mockConstruction(GVRKUms.class,
						(mock, context) -> when(mock.getFlatDataUnbooked()).thenReturn(List.of(lowlevelUmsLine)))) {
			boolean result = service.retrieveAccountTransactions(bankAccount, pin);

			List<Booking> bookings = dbController.getAllByParentFull(Booking.class, bankAccount.getId());
			assertTrue(result);
			assertEquals(1, bookings.size());
			Booking pendingBooking = bookings.get(0);
			assertEquals(Source.ONLINE_PRENO_NEW, pendingBooking.getSource());
			assertEquals(new BigDecimal("23.45"), pendingBooking.getAmount());
			assertEquals("Pending lowlevel booking\n", pendingBooking.getPurpose());
			verify(vormerkpostenJob).setParam("My.number", "987654321");
			verify(vormerkpostenJob).setParam("My.subnumber", "00");
			verify(vormerkpostenJob).setParam("My.KIK.country", "DE");
			verify(vormerkpostenJob).setParam("My.KIK.blz", "10020030");
			verify(vormerkpostenJob).setParam("allaccounts", "N");
			verify(vormerkpostenJob).addToQueue();
			verify(umsResults.constructed().get(0)).appendMT942Data(":20:dummy-mt942");
			verify(callbacks.constructed().get(0)).finishStatusDialog();
		}
	}

	@Test
	void retrieveAccountTransactions_shouldKeepExistingPrenotificationsWhenLowlevelResultFails() {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount bankAccount = TestDataFactory.createSampleAccount(null);
		bankAccount.setIban("DE44500105175407324931");
		bankAccount.setNumber("987654321");
		bankAccount.setBlz("10020030");
		bankAccount = dbController.insertOrUpdate(bankAccount);
		Booking existingPrenotification = insertBooking(dbController, bankAccount.getId(), Source.ONLINE_PRENO, new BigDecimal("99.99"));

		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		AccountTransactionService service = new AccountTransactionService();
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("10020030");
		char[] pin = "1234".toCharArray();
		HBCIPassport passport = mock(HBCIPassport.class);
		HBCIHandler handle = mock(HBCIHandler.class);
		Konto konto = createKonto("DE44500105175407324931", "987654321");
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRSaldoReq> saldoJob = mock(HBCIJob.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRKUms> umsatzJob = mock(HBCIJob.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> vormerkpostenJob = mock(HBCIJob.class);
		GVRKUms umsatzResult = mock(GVRKUms.class);
		HBCIJobResult vormerkpostenResult = mock(HBCIJobResult.class);

		when(hbciSupport.initBankAccess(bankAccount, pin)).thenReturn(bankAccess);
		doReturn(passport).when(hbciSupport).initBankConnection(eq(bankAccess), any(GBankingHBCICallback.class));
		doReturn(handle).when(hbciSupport).createHBCIHandler(eq(BaseMessagesDb.getVersion().getId()), same(passport));
		doReturn(saldoJob).when(hbciSupport).newHbciJob(handle, "SaldoReq");
		doReturn(umsatzJob).when(hbciSupport).newHbciJob(handle, "KUmsAllCamt");
		doReturn(vormerkpostenJob).when(hbciSupport).newLowlevelHbciJob(handle, "Vormerkposten");
		when(passport.getAccounts()).thenReturn(new Konto[] { konto });
		when(handle.getSupportedLowlevelJobs()).thenReturn(createSupportedLowlevelJobs());
		when(status.isOK()).thenReturn(true);
		when(handle.execute()).thenReturn(status);
		when(umsatzJob.getJobResult()).thenReturn(umsatzResult);
		when(umsatzResult.isOK()).thenReturn(true);
		when(umsatzResult.getFlatData()).thenReturn(List.of());
		when(umsatzResult.getFlatDataUnbooked()).thenReturn(List.of());
		when(vormerkpostenJob.getJobResult()).thenReturn(vormerkpostenResult);
		when(vormerkpostenResult.isOK()).thenReturn(false);

		try (MockedConstruction<GBankingHBCICallback> callbacks = mockConstruction(GBankingHBCICallback.class)) {
			boolean result = service.retrieveAccountTransactions(bankAccount, pin);

			List<Booking> bookings = dbController.getAllByParentFull(Booking.class, bankAccount.getId());
			assertTrue(result);
			assertEquals(1, bookings.size());
			assertEquals(existingPrenotification.getId(), bookings.get(0).getId());
			assertEquals(Source.ONLINE_PRENO, bookings.get(0).getSource());
			assertEquals(new BigDecimal("99.99"), bookings.get(0).getAmount());
			verify(callbacks.constructed().get(0)).finishStatusDialog();
		}
	}

	@Test
	void retrieveAccountTransactions_shouldNotPersistPartialDataWhenOverallStatusFails() {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount bankAccount = TestDataFactory.createSampleAccount(null);
		bankAccount.setIban("DE44500105175407324931");
		bankAccount.setNumber("987654321");
		bankAccount.setBlz("10020030");
		bankAccount = dbController.insertOrUpdate(bankAccount);
		insertBooking(dbController, bankAccount.getId(), Source.ONLINE_PRENO, new BigDecimal("99.99"));

		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		AccountTransactionService service = new AccountTransactionService();
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("10020030");
		char[] pin = "1234".toCharArray();
		HBCIPassport passport = mock(HBCIPassport.class);
		HBCIHandler handle = mock(HBCIHandler.class);
		Konto konto = createKonto("DE44500105175407324931", "987654321");
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRSaldoReq> saldoJob = mock(HBCIJob.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRKUms> umsatzJob = mock(HBCIJob.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> vormerkpostenJob = mock(HBCIJob.class);
		GVRKUms umsatzResult = mock(GVRKUms.class);
		HBCIJobResult vormerkpostenResult = mock(HBCIJobResult.class);
		UmsLine directUmsLine = createUmsLine("Pending direct booking", new BigDecimal("12.34"));

		when(hbciSupport.initBankAccess(bankAccount, pin)).thenReturn(bankAccess);
		doReturn(passport).when(hbciSupport).initBankConnection(eq(bankAccess), any(GBankingHBCICallback.class));
		doReturn(handle).when(hbciSupport).createHBCIHandler(eq(BaseMessagesDb.getVersion().getId()), same(passport));
		doReturn(saldoJob).when(hbciSupport).newHbciJob(handle, "SaldoReq");
		doReturn(umsatzJob).when(hbciSupport).newHbciJob(handle, "KUmsAllCamt");
		doReturn(vormerkpostenJob).when(hbciSupport).newLowlevelHbciJob(handle, "Vormerkposten");
		when(passport.getAccounts()).thenReturn(new Konto[] { konto });
		when(handle.getSupportedLowlevelJobs()).thenReturn(createSupportedLowlevelJobs());
		when(status.isOK()).thenReturn(false);
		when(status.getErrorString()).thenReturn("Vormerkposten failed");
		when(handle.execute()).thenReturn(status);
		when(umsatzJob.getJobResult()).thenReturn(umsatzResult);
		when(umsatzResult.isOK()).thenReturn(true);
		when(umsatzResult.getFlatData()).thenReturn(List.of());
		when(umsatzResult.getFlatDataUnbooked()).thenReturn(List.of(directUmsLine));
		when(vormerkpostenJob.getJobResult()).thenReturn(vormerkpostenResult);
		when(vormerkpostenResult.isOK()).thenReturn(false);

		try (MockedConstruction<GBankingHBCICallback> callbacks = mockConstruction(GBankingHBCICallback.class)) {
			AccountTransactionRetrievalResult result = service.retrieveAccountTransactionsWithResult(bankAccount, pin);

			List<Booking> bookings = dbController.getAllByParentFull(Booking.class, bankAccount.getId());
			assertFalse(result.successful());
			assertFalse(result.wrongPin());
			assertEquals(1, bookings.size());
			Booking pendingBooking = bookings.get(0);
			assertEquals(Source.ONLINE_PRENO, pendingBooking.getSource());
			assertEquals(new BigDecimal("99.99"), pendingBooking.getAmount());
			BankAccountRetrievalStatus retrievalStatus = dbController.getBankAccountRetrievalStatus(bankAccount.getId());
			assertEquals(AccountRetrievalStatus.FAILED, retrievalStatus.result());
			assertEquals(0, retrievalStatus.newBookingCount());
			assertEquals(0, retrievalStatus.pendingBookingCount());
			assertTrue(retrievalStatus.lastError() != null && !retrievalStatus.lastError().isBlank());
			verify(callbacks.constructed().get(0)).handleFailure("Vormerkposten failed");
			verify(callbacks.constructed().get(0)).finishStatusDialog();
		}
	}

	@Test
	void reconcileAccountBalance_shouldCreateAutomaticAdjustmentBookingWhenSaldoDiffersFromBookingBalance() {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		insertBooking(dbController, bankAccount.getId(), Source.ONLINE, new BigDecimal("90.00"));
		AccountTransactionService service = new AccountTransactionService();

		service.reconcileAccountBalance(bankAccount, new BigDecimal("100.00"));

		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, bankAccount.getId());
		Booking adjustment = bookings.stream().filter(booking -> booking.getSource() == Source.AUTO_ADJUSTING).findFirst().orElseThrow();
		assertEquals(2, bookings.size());
		assertEquals(new BigDecimal("10.00"), adjustment.getAmount());
		assertEquals(BookingType.DEPOSIT, adjustment.getBookingType());
		assertEquals(LocalDate.now(ZoneId.systemDefault()), adjustment.getDateBooking());
		assertEquals(LocalDate.now(ZoneId.systemDefault()), adjustment.getDateValue());
		assertEquals(Messages.getInstance().getMessage("BOOKING_PURPOSE_AUTO_ADJUSTING"), adjustment.getPurpose());
	}

	@Test
	void reconcileAccountBalance_shouldRemoveLastAutomaticAdjustmentWhenItMatchesNewDifference() {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		insertBooking(dbController, bankAccount.getId(), Source.ONLINE, new BigDecimal("100.00"));
		Booking adjustment = insertBooking(dbController, bankAccount.getId(), Source.AUTO_ADJUSTING, new BigDecimal("10.00"));
		AccountTransactionService service = new AccountTransactionService();

		service.reconcileAccountBalance(bankAccount, new BigDecimal("100.00"));

		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, bankAccount.getId());
		assertEquals(1, bookings.size());
		assertFalse(bookings.stream().anyMatch(booking -> booking.getId() == adjustment.getId()));
		assertFalse(bookings.stream().anyMatch(booking -> booking.getSource() == Source.AUTO_ADJUSTING));
	}

	@Test
	void reconcileAccountBalance_shouldIgnorePrenotifications() {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		insertBooking(dbController, bankAccount.getId(), Source.ONLINE, new BigDecimal("90.00"));
		insertBooking(dbController, bankAccount.getId(), Source.ONLINE_PRENO, new BigDecimal("500.00"));
		AccountTransactionService service = new AccountTransactionService();

		service.reconcileAccountBalance(bankAccount, new BigDecimal("100.00"));

		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, bankAccount.getId());
		Booking adjustment = bookings.stream().filter(booking -> booking.getSource() == Source.AUTO_ADJUSTING).findFirst().orElseThrow();
		assertEquals(new BigDecimal("10.00"), adjustment.getAmount());
	}

	@Test
	void reconcileAccountBalance_shouldUseBaseAmountOfForeignCurrencyBookings() {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		insertBooking(dbController, bankAccount.getId(), Source.ONLINE, new BigDecimal("90.00"));
		Booking foreignCurrencyBooking = TestDataFactory.createSampleBooking(bankAccount.getId());
		foreignCurrencyBooking.setAmount(new BigDecimal("5.00"));
		BookingForeignCurrencyDetails foreignDetails = new BookingForeignCurrencyDetails();
		foreignDetails.setForeignAmount(new BigDecimal("500.00"));
		foreignDetails.setForeignCurrency(Currency.USD);
		foreignDetails.setExchangeRateToBaseCurrency(new BigDecimal("0.01"));
		foreignCurrencyBooking.setForeignCurrencyDetails(foreignDetails);
		foreignCurrencyBooking.setSource(Source.ONLINE);
		dbController.insertOrUpdate(foreignCurrencyBooking);
		AccountTransactionService service = new AccountTransactionService();

		service.reconcileAccountBalance(bankAccount, new BigDecimal("100.00"));

		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, bankAccount.getId());
		Booking adjustment = bookings.stream().filter(booking -> booking.getSource() == Source.AUTO_ADJUSTING).findFirst().orElseThrow();
		assertEquals(new BigDecimal("5.00"), adjustment.getAmount());
	}

	private static Date toUtilDate(LocalDate date) {
		return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	private static Konto createKonto(String iban, String number) {
		Konto konto = new Konto();
		konto.iban = iban;
		konto.number = number;
		return konto;
	}

	private static GVRSaldoReq createSaldoResult(BigDecimal balance) {
		GVRSaldoReq saldoResult = mock(GVRSaldoReq.class);
		Info info = new Info();
		info.konto = new Konto();
		info.ready = new Saldo();
		info.ready.timestamp = new Date();
		info.ready.value = new Value();
		info.ready.value.setCurr("EUR");
		info.ready.value.setValue(balance);
		when(saldoResult.isOK()).thenReturn(true);
		when(saldoResult.getEntries()).thenReturn(new Info[] { info });
		return saldoResult;
	}

	private static Properties createSupportedLowlevelJobs() {
		Properties supportedLowlevelJobs = new Properties();
		supportedLowlevelJobs.setProperty("Vormerkposten", "1");
		return supportedLowlevelJobs;
	}

	private static UmsLine createUmsLine() {
		return createUmsLine("Imported online booking", new BigDecimal("42.50"));
	}

	private static UmsLine createUmsLine(String usage, BigDecimal amount) {
		UmsLine umsLine = new UmsLine();
		umsLine.bdate = new Date();
		umsLine.valuta = new Date();
		umsLine.usage = List.of(usage);
		umsLine.value = new Value();
		umsLine.value.setCurr("EUR");
		umsLine.value.setValue(amount);
		umsLine.other = createRecipientKonto();
		return umsLine;
	}

	private static Konto createRecipientKonto() {
		Konto konto = new Konto();
		konto.name = "Recipient";
		konto.iban = "DE99999999999999999999";
		konto.bic = "TESTDEFFXXX";
		konto.number = "11223344";
		konto.blz = "50010517";
		return konto;
	}

	private static Booking insertBooking(DBController dbController, int accountId, Source source, BigDecimal amount) {
		Booking booking = TestDataFactory.createSampleBooking(accountId);
		booking.setAmount(amount);
		booking.setBookingType(amount.signum() < 0 ? BookingType.REMOVAL : BookingType.DEPOSIT);
		booking.setSource(source);
		return dbController.insertOrUpdate(booking);
	}

	private static Object invokePrivate(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
		Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
		method.setAccessible(true);
		return method.invoke(target, args);
	}
}
