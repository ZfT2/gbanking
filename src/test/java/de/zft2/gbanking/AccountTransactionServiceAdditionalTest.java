package de.zft2.gbanking;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
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
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Saldo;
import org.kapott.hbci.structures.Value;
import org.mockito.MockedConstruction;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.TestData;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.logging.GBankingLoggingHandler;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountTransactionServiceAdditionalTest {

	private Path tempDir;

	@BeforeAll
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		DBController.getInstance(tempDir.toString());
	}

	@BeforeEach
	void clearDatabase() {
		DBControllerTestUtil.clearAllTables(DBController.getConnection());
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void createUmsatzJob_shouldRequestAllBookingsWithoutStartDateWhenAccountHasNoBookings() throws Exception {
		GBankingBean hbciSupport = mock(GBankingBean.class);
		AccountTransactionService service = new AccountTransactionService(hbciSupport, mock(GBankingLoggingHandler.class));
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
	void createUmsatzJob_shouldIncludeStartDateWhenAccountAlreadyHasBookings() throws Exception {
		GBankingBean hbciSupport = mock(GBankingBean.class);
		AccountTransactionService service = new AccountTransactionService(hbciSupport, mock(GBankingLoggingHandler.class));
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRKUms> job = mock(HBCIJob.class);
		doReturn(job).when(hbciSupport).newHbciJob(handle, "KUmsAllCamt");
		Konto konto = new Konto();
		LocalDate lastBookingDate = LocalDate.of(2026, 5, 1);

		HBCIJob<?> createdJob = (HBCIJob<?>) invokePrivate(service, "createUmsatzJob",
				new Class<?>[] { HBCIHandler.class, Konto.class, LocalDate.class }, handle, konto, lastBookingDate);

		assertSame(job, createdJob);
		verify(job).setParam("my", konto);
		verify(job).setParam("startdate", toUtilDate(lastBookingDate));
		verify(job).addToQueue();
	}

	@Test
	void createAndAddHbciJob_shouldIgnoreNullAndUnsupportedParamsButStillQueueJob() throws Exception {
		GBankingBean hbciSupport = mock(GBankingBean.class);
		AccountTransactionService service = new AccountTransactionService(hbciSupport, mock(GBankingLoggingHandler.class));
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
		AccountTransactionService service = new AccountTransactionService(mock(GBankingBean.class), mock(GBankingLoggingHandler.class));
		HBCIPassport passport = mock(HBCIPassport.class);
		when(passport.getAccounts()).thenReturn(null);

		assertNull(invokePrivate(service, "getHbciAccountsFromPassport", new Class<?>[] { HBCIPassport.class }, passport));

		Konto[] emptyAccounts = new Konto[0];
		when(passport.getAccounts()).thenReturn(emptyAccounts);

		assertSame(emptyAccounts, invokePrivate(service, "getHbciAccountsFromPassport", new Class<?>[] { HBCIPassport.class }, passport));
	}

	@Test
	void hbciKontosMatches_shouldReturnFalseWhenAccountHasNoIdentifiers() throws Exception {
		AccountTransactionService service = new AccountTransactionService(mock(GBankingBean.class), mock(GBankingLoggingHandler.class));
		BankAccount bankAccount = new BankAccount();
		Konto konto = new Konto();
		konto.iban = "DE12345678901234567890";
		konto.number = "12345678";

		assertFalse((Boolean) invokePrivate(service, "hbciKontosMatches", new Class<?>[] { BankAccount.class, Konto.class }, bankAccount, konto));
	}

	@Test
	void clearSecret_shouldOverwriteSecretCharsAndAcceptNull() throws Exception {
		AccountTransactionService service = new AccountTransactionService(mock(GBankingBean.class), mock(GBankingLoggingHandler.class));
		char[] secret = "12345".toCharArray();

		invokePrivate(service, "clearSecret", new Class<?>[] { char[].class }, secret);
		invokePrivate(service, "clearSecret", new Class<?>[] { char[].class }, new Object[] { null });

		assertArrayEquals(new char[] { '\0', '\0', '\0', '\0', '\0' }, secret);
	}

	@Test
	void retrieveAccountTransactions_shouldReturnFalseAndClearPinWhenBankAccessIsMissing() {
		GBankingBean hbciSupport = mock(GBankingBean.class);
		AccountTransactionService service = new AccountTransactionService(hbciSupport, mock(GBankingLoggingHandler.class));
		BankAccount bankAccount = TestData.createSampleAccount(null);
		char[] pin = "1234".toCharArray();

		when(hbciSupport.initBankAccess(bankAccount, pin)).thenReturn(null);

		boolean result = service.retrieveAccountTransactions(bankAccount, pin);

		assertFalse(result);
		assertArrayEquals(new char[] { '\0', '\0', '\0', '\0' }, pin);
		verify(hbciSupport, never()).initBankConnection(any(BankAccess.class), any(GBankingHBCICallback.class));
	}

	@Test
	void retrieveAccountTransactions_shouldExecuteMatchingJobsPersistBookingsAndClearPin() {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount bankAccount = TestData.createSampleAccount(null);
		bankAccount.setIban("DE44500105175407324931");
		bankAccount.setNumber("987654321");
		bankAccount = dbController.insertOrUpdate(bankAccount);
		Booking previousNewBooking = TestData.createSampleBooking(bankAccount.getId());
		previousNewBooking.setSource(Source.ONLINE_NEW);
		previousNewBooking = dbController.insertOrUpdate(previousNewBooking);

		GBankingBean hbciSupport = mock(GBankingBean.class);
		GBankingLoggingHandler logHandler = mock(GBankingLoggingHandler.class);
		AccountTransactionService service = new AccountTransactionService(hbciSupport, logHandler);
		BankAccess bankAccess = TestData.createSampleBankAccess("10020030");
		char[] pin = "1234".toCharArray();
		HBCIPassport passport = mock(HBCIPassport.class);
		HBCIHandler handle = mock(HBCIHandler.class);
		Konto konto = createKonto("DE44500105175407324931", "987654321");
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRSaldoReq> saldoJob = mock(HBCIJob.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRKUms> umsatzJob = mock(HBCIJob.class);
		GVRSaldoReq saldoResult = createSaldoResult();
		GVRKUms umsatzResult = mock(GVRKUms.class);

		when(hbciSupport.initBankAccess(bankAccount, pin)).thenReturn(bankAccess);
		doReturn(passport).when(hbciSupport).initBankConnection(eq(bankAccess), any(GBankingHBCICallback.class));
		doReturn(handle).when(hbciSupport).createHBCIHandler(eq(GBankingBean.getVersion().getId()), same(passport));
		doReturn(saldoJob).when(hbciSupport).newHbciJob(handle, "SaldoReq");
		doReturn(umsatzJob).when(hbciSupport).newHbciJob(handle, "KUmsAllCamt");
		when(passport.getAccounts()).thenReturn(new Konto[] { konto });
		when(status.isOK()).thenReturn(true);
		when(handle.execute()).thenReturn(status);
		when(saldoJob.getJobResult()).thenReturn(saldoResult);
		when(umsatzJob.getJobResult()).thenReturn(umsatzResult);
		when(umsatzResult.isOK()).thenReturn(true);
		when(umsatzResult.getFlatData()).thenReturn(List.of(createUmsLine()));

		try (MockedConstruction<GBankingHBCICallback> callbacks = mockConstruction(GBankingHBCICallback.class)) {
			boolean result = service.retrieveAccountTransactions(bankAccount, pin);

			List<Booking> bookings = dbController.getAllByParentFull(Booking.class, bankAccount.getId());
			assertTrue(result);
			assertEquals(2, bookings.size());
			assertEquals(Source.ONLINE, dbController.getByIdFull(Booking.class, previousNewBooking.getId()).getSource());
			assertEquals(1, dbController.getAll(Recipient.class).size());
			assertArrayEquals(new char[] { '\0', '\0', '\0', '\0' }, pin);
			verify(saldoJob).setParam("my", konto);
			verify(umsatzJob).setParam("my", konto);
			verify(handle).execute();
			verify(callbacks.constructed().get(0)).startStatusDialog();
			verify(callbacks.constructed().get(0)).finishStatusDialog();
			verify(passport).close();
		}
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

	private static GVRSaldoReq createSaldoResult() {
		GVRSaldoReq saldoResult = mock(GVRSaldoReq.class);
		Info info = new Info();
		info.konto = new Konto();
		info.ready = new Saldo();
		info.ready.timestamp = new Date();
		info.ready.value = new Value();
		info.ready.value.setCurr("EUR");
		info.ready.value.setValue(BigDecimal.TEN);
		when(saldoResult.isOK()).thenReturn(true);
		when(saldoResult.getEntries()).thenReturn(new Info[] { info });
		return saldoResult;
	}

	private static UmsLine createUmsLine() {
		UmsLine umsLine = new UmsLine();
		umsLine.bdate = new Date();
		umsLine.valuta = new Date();
		umsLine.usage = List.of("Imported online booking");
		umsLine.value = new Value();
		umsLine.value.setCurr("EUR");
		umsLine.value.setValue(new BigDecimal("42.50"));
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

	private static Object invokePrivate(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
		Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
		method.setAccessible(true);
		return method.invoke(target, args);
	}
}
