package de.zft2.gbanking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.TestData;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.hbci.GBankingHBCICallback;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoneyTransferExecutionServiceAdditionalTest {

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
	void supportsTransferOrderType_shouldAllowAllTypesWhenNoBusinessCasesAreConfigured() {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService(mock(GBankingBean.class));

		BankAccount bankAccount = new BankAccount();

		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.TRANSFER));
		bankAccount.setAllowedBusinessCases(List.of());
		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.REALTIME_TRANSFER));
	}

	@Test
	void supportsTransferOrderType_shouldRejectNullAndBlankOnlyBusinessCases() {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService(mock(GBankingBean.class));
		BankAccount bankAccount = new BankAccount();
		bankAccount.setAllowedBusinessCases(List.of(createBusinessCase(null), createBusinessCase("   ")));

		assertFalse(service.supportsTransferOrderType(null, OrderType.TRANSFER));
		assertFalse(service.supportsTransferOrderType(bankAccount, null));
		assertFalse(service.supportsTransferOrderType(bankAccount, OrderType.TRANSFER));
	}

	@Test
	void supportsTransferOrderType_shouldAcceptAlternativeHbciBusinessCaseCodes() {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService(mock(GBankingBean.class));
		BankAccount bankAccount = new BankAccount();
		bankAccount.setAllowedBusinessCases(List.of(createBusinessCase("hkccs"), createBusinessCase("HKIPZ"), createBusinessCase(" hkcse "),
				createBusinessCase("HKDSE")));

		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.TRANSFER));
		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.REALTIME_TRANSFER));
		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.SCHEDULED_TRANSFER));
		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.STANDING_ORDER));
	}

	@Test
	void createTransferJob_shouldConfigureScheduledTransferJob() throws Exception {
		GBankingBean hbciSupport = mock(GBankingBean.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService(hbciSupport);
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		when(hbciSupport.newHbciJob(handle, "TermUebSEPA")).thenReturn(job);

		LocalDate executionDate = LocalDate.of(2026, 5, 10);
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.SCHEDULED_TRANSFER);
		moneyTransfer.setExecutionDate(executionDate);
		Konto senderAccount = new Konto();
		Konto recipientAccount = createRecipientAccount();

		HBCIJob<?> createdJob = (HBCIJob<?>) invokePrivate(service, "createTransferJob",
				new Class<?>[] { HBCIHandler.class, MoneyTransfer.class, Konto.class, Konto.class }, handle, moneyTransfer, senderAccount, recipientAccount);

		assertSame(job, createdJob);
		verify(job).setParam("src", senderAccount);
		verify(job).setParam("dst", recipientAccount);
		verify(job).setParam("btg.value", "12.34");
		verify(job).setParam("btg.curr", "EUR");
		verify(job).setParam("usage", "Test purpose");
		verify(job).setParam("date", toUtilDate(executionDate));
	}

	@Test
	void createTransferJob_shouldConfigureStandingOrderParams() throws Exception {
		GBankingBean hbciSupport = mock(GBankingBean.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService(hbciSupport);
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		when(hbciSupport.newHbciJob(handle, "DauerSEPANew")).thenReturn(job);

		LocalDate firstDate = LocalDate.of(2026, 6, 30);
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.STANDING_ORDER);
		moneyTransfer.setExecutionDate(firstDate);
		moneyTransfer.setExecutionDay(31);
		moneyTransfer.setStandingorderMode(StandingorderMode.ANNUALLY);

		invokePrivate(service, "createTransferJob", new Class<?>[] { HBCIHandler.class, MoneyTransfer.class, Konto.class, Konto.class }, handle,
				moneyTransfer, new Konto(), new Konto());

		verify(job).setParam("firstdate", toUtilDate(firstDate));
		verify(job).setParam("timeunit", "M");
		verify(job).setParam("turnus", "12");
		verify(job).setParam("execday", "31");
	}

	@Test
	void createTransferJob_shouldRejectIncompleteStandingOrder() {
		GBankingBean hbciSupport = mock(GBankingBean.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService(hbciSupport);
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		when(hbciSupport.newHbciJob(handle, "DauerSEPANew")).thenReturn(job);
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.STANDING_ORDER);
		moneyTransfer.setExecutionDate(LocalDate.of(2026, 6, 30));
		moneyTransfer.setExecutionDay(null);
		moneyTransfer.setStandingorderMode(StandingorderMode.MONTHLY);

		assertThrows(GBankingException.class, () -> invokePrivate(service, "createTransferJob",
				new Class<?>[] { HBCIHandler.class, MoneyTransfer.class, Konto.class, Konto.class }, handle, moneyTransfer, new Konto(), new Konto()));
	}

	@Test
	void createRecipientAccount_shouldMapRecipientFieldsToHbciKonto() throws Exception {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService(mock(GBankingBean.class));
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.TRANSFER);

		Konto recipientAccount = (Konto) invokePrivate(service, "createRecipientAccount", new Class<?>[] { MoneyTransfer.class }, moneyTransfer);

		assertEquals("Recipient Name", recipientAccount.name);
		assertEquals("DE12345678901234567890", recipientAccount.iban);
		assertEquals("TESTDEFFXXX", recipientAccount.bic);
	}

	@Test
	void standingOrderHelpers_shouldMapModesAndFormatExecutionDays() throws Exception {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService(mock(GBankingBean.class));

		assertEquals("1", invokePrivate(service, "determineStandingOrderTurnus", new Class<?>[] { StandingorderMode.class }, StandingorderMode.MONTHLY));
		assertEquals("2", invokePrivate(service, "determineStandingOrderTurnus", new Class<?>[] { StandingorderMode.class }, StandingorderMode.BIMONTHLY));
		assertEquals("3", invokePrivate(service, "determineStandingOrderTurnus", new Class<?>[] { StandingorderMode.class }, StandingorderMode.QUARTERLY));
		assertEquals("6",
				invokePrivate(service, "determineStandingOrderTurnus", new Class<?>[] { StandingorderMode.class }, StandingorderMode.SEMI_ANNUALLY));
		assertEquals("12", invokePrivate(service, "determineStandingOrderTurnus", new Class<?>[] { StandingorderMode.class }, StandingorderMode.ANNUALLY));

		assertEquals("01", invokePrivate(service, "formatStandingOrderExecutionDay", new Class<?>[] { Integer.class }, 1));
		assertEquals("09", invokePrivate(service, "formatStandingOrderExecutionDay", new Class<?>[] { Integer.class }, 9));
		assertEquals("30", invokePrivate(service, "formatStandingOrderExecutionDay", new Class<?>[] { Integer.class }, 30));
		assertEquals("31", invokePrivate(service, "formatStandingOrderExecutionDay", new Class<?>[] { Integer.class }, 31));
		assertEquals("31", invokePrivate(service, "formatStandingOrderExecutionDay", new Class<?>[] { Integer.class }, 99));
		assertNull(invokePrivate(service, "formatStandingOrderExecutionDay", new Class<?>[] { Integer.class }, new Object[] { null }));
	}

	@Test
	void updateMoneyTransferAfterExecution_shouldSetTransferSentAndExecutionDate() throws Exception {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService(mock(GBankingBean.class));
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.TRANSFER);
		LocalDate before = LocalDate.now();

		invokePrivate(service, "updateMoneyTransferAfterExecution",
				new Class<?>[] { MoneyTransfer.class, GBankingHBCICallback.class, HBCIExecStatus.class, HBCIJobResult.class, boolean.class }, moneyTransfer,
				mock(GBankingHBCICallback.class), mock(HBCIExecStatus.class), null, true);

		assertEquals(MoneyTransferStatus.SENT, moneyTransfer.getMoneytransferStatus());
		assertFalse(moneyTransfer.getExecutionDate().isBefore(before));
		assertFalse(moneyTransfer.getExecutionDate().isAfter(LocalDate.now()));
	}

	@Test
	void updateMoneyTransferAfterExecution_shouldNotOverwriteScheduledExecutionDate() throws Exception {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService(mock(GBankingBean.class));
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.SCHEDULED_TRANSFER);
		LocalDate executionDate = LocalDate.of(2026, 7, 15);
		moneyTransfer.setExecutionDate(executionDate);

		invokePrivate(service, "updateMoneyTransferAfterExecution",
				new Class<?>[] { MoneyTransfer.class, GBankingHBCICallback.class, HBCIExecStatus.class, HBCIJobResult.class, boolean.class }, moneyTransfer,
				mock(GBankingHBCICallback.class), mock(HBCIExecStatus.class), null, true);

		assertEquals(MoneyTransferStatus.SENT, moneyTransfer.getMoneytransferStatus());
		assertEquals(executionDate, moneyTransfer.getExecutionDate());
	}

	@Test
	void updateMoneyTransferAfterExecution_shouldSetErrorAndReportStatusFailure() throws Exception {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService(mock(GBankingBean.class));
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.TRANSFER);
		GBankingHBCICallback callback = mock(GBankingHBCICallback.class);
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		when(status.getErrorString()).thenReturn("bank rejected order");

		invokePrivate(service, "updateMoneyTransferAfterExecution",
				new Class<?>[] { MoneyTransfer.class, GBankingHBCICallback.class, HBCIExecStatus.class, HBCIJobResult.class, boolean.class }, moneyTransfer,
				callback, status, null, false);

		assertEquals(MoneyTransferStatus.ERROR, moneyTransfer.getMoneytransferStatus());
		verify(callback).handleFailure("bank rejected order");
	}

	@Test
	void executeTransfer_shouldRunHbciOrderAfterCallbackAndPersistSentStatus() {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount bankAccount = dbController.insertOrUpdate(TestData.createSampleAccount(null));
		Recipient recipient = dbController.insertOrUpdate(new Recipient("Recipient Name", "DE12345678901234567890", "TESTDEFFXXX", null, null,
				"Testbank", de.zft2.gbanking.db.dao.enu.Source.MANUELL));
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.TRANSFER);
		moneyTransfer.setAccountId(bankAccount.getId());
		moneyTransfer.setRecipientId(recipient.getId());
		moneyTransfer.setRecipient(recipient);

		GBankingBean hbciSupport = mock(GBankingBean.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService(hbciSupport);
		BankAccess bankAccess = TestData.createSampleBankAccess("10020030");
		char[] pin = "1234".toCharArray();
		HBCIPassport passport = mock(HBCIPassport.class);
		HBCIHandler handle = mock(HBCIHandler.class);
		Konto senderAccount = new Konto();
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		HBCIJobResult jobResult = mock(HBCIJobResult.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);

		doReturn(bankAccess).when(hbciSupport).initBankAccess(any(BankAccount.class), same(pin));
		doReturn(passport).when(hbciSupport).initBankConnection(eq(bankAccess), any(GBankingHBCICallback.class));
		doReturn(handle).when(hbciSupport).createHBCIHandler(eq(GBankingBean.getVersion().getId()), same(passport));
		doReturn(senderAccount).when(hbciSupport).getSenderAccount(eq(passport), any(BankAccount.class));
		doReturn(job).when(hbciSupport).newHbciJob(handle, "UebSEPA");
		when(status.isOK()).thenReturn(true);
		when(handle.execute()).thenReturn(status);
		when(job.getJobResult()).thenReturn(jobResult);
		when(jobResult.isOK()).thenReturn(true);

		try (MockedConstruction<GBankingHBCICallback> callbacks = mockConstruction(GBankingHBCICallback.class)) {
			boolean result = service.executeTransfer(moneyTransfer, bankAccount, pin);

			ArgumentCaptor<Konto> recipientCaptor = ArgumentCaptor.forClass(Konto.class);
			assertTrue(result);
			assertEquals(MoneyTransferStatus.SENT, moneyTransfer.getMoneytransferStatus());
			assertArrayCleared(pin);
			verify(job).setParam("src", senderAccount);
			verify(job).setParam(eq("dst"), recipientCaptor.capture());
			verify(job).setParam("btg.value", "12.34");
			verify(job).setParam("btg.curr", "EUR");
			verify(job).setParam("usage", "Test purpose");
			verify(job).addToQueue();
			verify(handle).execute();
			verify(callbacks.constructed().get(0)).startStatusDialog();
			verify(callbacks.constructed().get(0)).finishStatusDialog();
			verify(passport).close();
			assertEquals(recipient.getIban(), recipientCaptor.getValue().iban);
			assertEquals(recipient.getBic(), recipientCaptor.getValue().bic);
			assertEquals(recipient.getName(), recipientCaptor.getValue().name);
			assertEquals(MoneyTransferStatus.SENT, dbController.getAllByParent(MoneyTransfer.class, bankAccount.getId()).get(0).getMoneytransferStatus());
		}
	}

	private static MoneyTransfer createMoneyTransfer(OrderType orderType) {
		MoneyTransfer moneyTransfer = new MoneyTransfer();
		moneyTransfer.setOrderType(orderType);
		moneyTransfer.setAmount(new BigDecimal("12.34"));
		moneyTransfer.setPurpose("Test purpose");
		Recipient recipient = new Recipient();
		recipient.setName("Recipient Name");
		recipient.setIban("DE12345678901234567890");
		recipient.setBic("TESTDEFFXXX");
		moneyTransfer.setRecipient(recipient);
		return moneyTransfer;
	}

	private static Konto createRecipientAccount() {
		Konto recipientAccount = new Konto();
		recipientAccount.name = "Recipient Name";
		recipientAccount.iban = "DE12345678901234567890";
		recipientAccount.bic = "TESTDEFFXXX";
		return recipientAccount;
	}

	private static BusinessCase createBusinessCase(String caseValue) {
		BusinessCase businessCase = new BusinessCase();
		businessCase.setCaseValue(caseValue);
		return businessCase;
	}

	private static Date toUtilDate(LocalDate date) {
		return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	private static void assertArrayCleared(char[] secret) {
		for (char value : secret) {
			assertEquals('\0', value);
		}
	}

	private static Object invokePrivate(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
		Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
		method.setAccessible(true);
		try {
			return method.invoke(target, args);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (cause instanceof Exception exception) {
				throw exception;
			}
			throw e;
		}
	}
}
