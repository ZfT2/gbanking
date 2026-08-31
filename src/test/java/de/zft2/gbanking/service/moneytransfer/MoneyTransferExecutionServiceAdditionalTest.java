package de.zft2.gbanking.service.moneytransfer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRDauerEdit;
import org.kapott.hbci.GV_Result.GVRInstUebSEPA;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIDialogStatus;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.status.HBCIRetVal;
import org.kapott.hbci.status.HBCIStatus;
import org.kapott.hbci.structures.Konto;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.ForeignChargeBearer;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.SepaCancellationCode;
import de.zft2.gbanking.db.dao.enu.SepaOrderStatus;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.messages.Messages;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.service.BankingCapabilityService;
import de.zft2.gbanking.service.GBankingService;
import de.zft2.gbanking.service.Service;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.ServiceStubbingUtil;
import de.zft2.gbanking.testdata.TestDataFactory;
import de.zft2.gbanking.testdata.HbciParameterTestDataFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoneyTransferExecutionServiceAdditionalTest {

	private Path tempDir;

	private static final List<Class<? extends Service>> SERVICES_TO_STUB = List.of(GBankingService.class, BankAccessService.class,
			MoneyTransferService.class);

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
	void supportsTransferOrderType_shouldRejectWhenNoBusinessCasesAreConfigured() {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();

		BankAccess bankAccess = insertBankAccessWithBpd("HKCCS", "HKIPZ");
		BankAccount bankAccount = TestDataFactory.createForBankAccess(bankAccess.getId());

		assertFalse(service.supportsTransferOrderType(bankAccount, OrderType.TRANSFER));
		bankAccount.setAllowedBusinessCases(List.of());
		assertFalse(service.supportsTransferOrderType(bankAccount, OrderType.REALTIME_TRANSFER));
	}

	@Test
	void supportsTransferOrderType_shouldRejectNullAndBlankOnlyBusinessCases() {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		BankAccess bankAccess = insertBankAccessWithBpd("HKCCS");
		BankAccount bankAccount = TestDataFactory.createForBankAccess(bankAccess.getId());
		bankAccount.setAllowedBusinessCases(List.of(TestDataFactory.createBusinessCase(null), TestDataFactory.createBusinessCase("   ")));

		assertFalse(service.supportsTransferOrderType(null, OrderType.TRANSFER));
		assertFalse(service.supportsTransferOrderType(bankAccount, null));
		assertFalse(service.supportsTransferOrderType(bankAccount, OrderType.TRANSFER));
	}

	@Test
	void supportsTransferOrderType_shouldAcceptAlternativeHbciBusinessCaseCodes() {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		BankAccess bankAccess = insertBankAccessWithBpd("HKCCS", "HKIPZ", "HKEIL", "HKCSE", "HKCDE", "HKAUB");
		BankAccount bankAccount = TestDataFactory.createForBankAccess(bankAccess.getId());
		bankAccount.setAllowedBusinessCases(List.of(TestDataFactory.createBusinessCase("hkccs"), TestDataFactory.createBusinessCase("HKIPZ"), TestDataFactory.createBusinessCase(" hkcse "),
				TestDataFactory.createBusinessCase("HKCDE"), TestDataFactory.createBusinessCase("HKAUB"), TestDataFactory.createBusinessCase("HKEIL")));

		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.TRANSFER));
		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.REALTIME_TRANSFER));
		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.URGENT_TRANSFER));
		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.SCHEDULED_TRANSFER));
		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.STANDING_ORDER));
		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.FOREIGN_TRANSFER));
	}

	@Test
	void createTransferJob_shouldConfigureScheduledTransferJob() throws Exception {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		when(hbciSupport.newHbciJob(handle, "TermUebSEPA")).thenReturn(job);

		LocalDate executionDate = LocalDate.of(2026, Month.MAY, 10);
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
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		when(hbciSupport.newHbciJob(handle, "DauerSEPANew")).thenReturn(job);

		LocalDate firstDate = LocalDate.of(2026, Month.JUNE, 30);
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
	void createTransferJob_shouldConfigureScheduledTransferEditJob() throws Exception {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		when(hbciSupport.newHbciJob(handle, "TermUebSEPAEdit")).thenReturn(job);
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.SCHEDULED_TRANSFER);
		moneyTransfer.setExecutionDate(LocalDate.of(2026, Month.JUNE, 30));
		moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.CHANGED);
		moneyTransfer.setBankOrderId("scheduled-edit-1");

		invokePrivate(service, "createTransferJob", new Class<?>[] { HBCIHandler.class, MoneyTransfer.class, Konto.class, Konto.class }, handle,
				moneyTransfer, new Konto(), new Konto());

		verify(job).setParam("orderid", "scheduled-edit-1");
	}

	@Test
	void createTransferJob_shouldConfigureStandingOrderDeleteJob() throws Exception {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		when(hbciSupport.newHbciJob(handle, "DauerSEPADel")).thenReturn(job);
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.STANDING_ORDER);
		moneyTransfer.setExecutionDate(LocalDate.of(2026, Month.JUNE, 30));
		moneyTransfer.setExecutionDay(15);
		moneyTransfer.setStandingorderMode(StandingorderMode.MONTHLY);
		moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.DELETE_PENDING);
		moneyTransfer.setBankOrderId("standing-delete-1");

		invokePrivate(service, "createTransferJob", new Class<?>[] { HBCIHandler.class, MoneyTransfer.class, Konto.class, Konto.class }, handle,
				moneyTransfer, new Konto(), new Konto());

		verify(job).setParam("orderid", "standing-delete-1");
	}

	@Test
	void resolveJobName_shouldMapAllExistingBankOrderOperations() throws Exception {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();

		assertEquals("TermUebSEPAEdit", invokePrivate(service, "resolveJobName",
				new Class<?>[] { OrderType.class, BankOrderOperation.class }, OrderType.SCHEDULED_TRANSFER, BankOrderOperation.EDIT));
		assertEquals("TermUebSEPADel", invokePrivate(service, "resolveJobName",
				new Class<?>[] { OrderType.class, BankOrderOperation.class }, OrderType.SCHEDULED_TRANSFER, BankOrderOperation.DELETE));
		assertEquals("DauerSEPAEdit", invokePrivate(service, "resolveJobName",
				new Class<?>[] { OrderType.class, BankOrderOperation.class }, OrderType.STANDING_ORDER, BankOrderOperation.EDIT));
		assertEquals("DauerSEPADel", invokePrivate(service, "resolveJobName",
				new Class<?>[] { OrderType.class, BankOrderOperation.class }, OrderType.STANDING_ORDER, BankOrderOperation.DELETE));
	}

	@Test
	void createTransferJob_shouldConfigureForeignTransferJob() throws Exception {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		when(hbciSupport.newHbciJob(handle, "UebForeign")).thenReturn(job);

		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.FOREIGN_TRANSFER);
		moneyTransfer.setCurrency("USD");
		moneyTransfer.getRecipient().setBank("Foreign Recipient Bank");
		MoneyTransferForeign foreignTransfer = new MoneyTransferForeign();
		foreignTransfer.setRecipientCountry("US");
		foreignTransfer.setRecipientAccountNumber("123456789");
		foreignTransfer.setRecipientBankCode("021000021");
		foreignTransfer.setRecipientSubAccount("01");
		foreignTransfer.setChargeBearer(ForeignChargeBearer.SENDER);
		moneyTransfer.setForeignTransfer(foreignTransfer);

		HBCIJob<?> createdJob = (HBCIJob<?>) invokePrivate(service, "createTransferJob",
				new Class<?>[] { HBCIHandler.class, MoneyTransfer.class, Konto.class, Konto.class }, handle, moneyTransfer, new Konto(), new Konto());

		assertSame(job, createdJob);
		verify(job).setParam("btg.curr", "USD");
		verify(job).setParam("dst.kiname", "Foreign Recipient Bank");
		verify(job).setParam("kostentraeger", "2");
	}

	@Test
	void createTransferJob_shouldConfigureUrgentTransferJob() throws Exception {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		when(hbciSupport.newHbciJob(handle, "UebEil")).thenReturn(job);

		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.URGENT_TRANSFER);
		invokePrivate(service, "createTransferJob", new Class<?>[] { HBCIHandler.class, MoneyTransfer.class, Konto.class, Konto.class }, handle,
				moneyTransfer, new Konto(), new Konto());

		verify(job).setParam("name", "Recipient Name");
		verify(job).setParam("usage0", "Test purpose");
		verify(job, never()).setParam("usage", "Test purpose");
	}

	@Test
	void createTransferJob_shouldRejectIncompleteStandingOrder() {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		when(hbciSupport.newHbciJob(handle, "DauerSEPANew")).thenReturn(job);
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.STANDING_ORDER);
		moneyTransfer.setExecutionDate(LocalDate.of(2026, Month.JUNE, 30));
		moneyTransfer.setExecutionDay(null);
		moneyTransfer.setStandingorderMode(StandingorderMode.MONTHLY);

		Konto sender = new Konto();
		Konto recipient = new Konto();

		GBankingException exception = assertThrows(GBankingException.class, () -> {
			invokePrivate(service, "createTransferJob",
					new Class<?>[] { HBCIHandler.class, MoneyTransfer.class, Konto.class, Konto.class }, handle, moneyTransfer, sender, recipient);
		});
		assertTrue(exception.getMessage().contains(fieldLabel("UI_LABEL_DAY")));
	}

	@Test
	void createTransferJob_shouldRejectIncompleteScheduledTransferWithFieldName() {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		when(hbciSupport.newHbciJob(handle, "TermUebSEPA")).thenReturn(job);
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.SCHEDULED_TRANSFER);
		moneyTransfer.setExecutionDate(null);

		Konto sender = new Konto();
		Konto recipient = new Konto();

		GBankingException exception = assertThrows(GBankingException.class, () -> invokePrivate(service, "createTransferJob",
				new Class<?>[] { HBCIHandler.class, MoneyTransfer.class, Konto.class, Konto.class }, handle, moneyTransfer, sender, recipient));
		assertTrue(exception.getMessage().contains(fieldLabel("UI_LABEL_EXECUTION_DATE")));
	}

	@Test
	void createTransferJob_shouldRejectIncompleteForeignTransferWithFieldName() {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		when(hbciSupport.newHbciJob(handle, "UebForeign")).thenReturn(job);
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.FOREIGN_TRANSFER);
		moneyTransfer.getRecipient().setBank(null);

		Konto sender = new Konto();
		Konto recipient = new Konto();

		GBankingException exception = assertThrows(GBankingException.class, () -> invokePrivate(service, "createTransferJob",
				new Class<?>[] { HBCIHandler.class, MoneyTransfer.class, Konto.class, Konto.class }, handle, moneyTransfer, sender, recipient));
		assertTrue(exception.getMessage().contains(fieldLabel("UI_LABEL_BANK")));
	}

	@Test
	void createTransferJob_shouldRejectIncompleteUrgentTransferWithFieldName() {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		when(hbciSupport.newHbciJob(handle, "UebEil")).thenReturn(job);
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.URGENT_TRANSFER);
		moneyTransfer.getRecipient().setName(null);

		Konto sender = new Konto();
		Konto recipient = new Konto();

		GBankingException exception = assertThrows(GBankingException.class, () -> invokePrivate(service, "createTransferJob",
				new Class<?>[] { HBCIHandler.class, MoneyTransfer.class, Konto.class, Konto.class }, handle, moneyTransfer, sender, recipient));
		assertTrue(exception.getMessage().contains(fieldLabel("UI_LABEL_TRANSFER_RECIPIENT")));
	}

	@Test
	void createRecipientAccount_shouldMapRecipientFieldsToHbciKonto() throws Exception {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.TRANSFER);

		Konto recipientAccount = (Konto) invokePrivate(service, "createRecipientAccount", new Class<?>[] { MoneyTransfer.class }, moneyTransfer);

		assertEquals("Recipient Name", recipientAccount.name);
		assertEquals("DE12345678901234567890", recipientAccount.iban);
		assertEquals("TESTDEFFXXX", recipientAccount.bic);
		assertEquals("DE", recipientAccount.country);
	}

	@Test
	void createRecipientAccount_shouldMapGermanIbanToNationalFieldsForUrgentTransfer() throws Exception {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.URGENT_TRANSFER);
		moneyTransfer.getRecipient().setIban("DE89370400440532013000");
		moneyTransfer.getRecipient().setBic(null);

		Konto recipientAccount = (Konto) invokePrivate(service, "createRecipientAccount", new Class<?>[] { MoneyTransfer.class }, moneyTransfer);

		assertEquals("DE89370400440532013000", recipientAccount.iban);
		assertEquals("37040044", recipientAccount.blz);
		assertEquals("0532013000", recipientAccount.number);
		assertEquals("DE", recipientAccount.country);
	}

	@Test
	void createRecipientAccount_shouldMapForeignNationalAccountFields() throws Exception {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.FOREIGN_TRANSFER);
		moneyTransfer.getRecipient().setIban(null);
		MoneyTransferForeign foreignTransfer = new MoneyTransferForeign();
		foreignTransfer.setRecipientCountry("US");
		foreignTransfer.setRecipientAccountNumber("123456789");
		foreignTransfer.setRecipientBankCode("021000021");
		foreignTransfer.setRecipientSubAccount("01");
		moneyTransfer.setForeignTransfer(foreignTransfer);

		Konto recipientAccount = (Konto) invokePrivate(service, "createRecipientAccount", new Class<?>[] { MoneyTransfer.class }, moneyTransfer);

		assertEquals("US", recipientAccount.country);
		assertEquals("123456789", recipientAccount.number);
		assertEquals("021000021", recipientAccount.blz);
		assertEquals("01", recipientAccount.subnumber);
	}

	@Test
	void standingOrderHelpers_shouldMapModesAndFormatExecutionDays() throws Exception {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();

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
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.TRANSFER);
		LocalDate before = LocalDate.now(ZoneId.systemDefault());

		invokePrivate(service, "updateMoneyTransferAfterExecution",
				new Class<?>[] { MoneyTransfer.class, BankOrderOperation.class, GBankingHBCICallback.class, HBCIExecStatus.class, HBCIJobResult.class,
						boolean.class },
				moneyTransfer, BankOrderOperation.CREATE, mock(GBankingHBCICallback.class), mock(HBCIExecStatus.class), null, true);

		assertEquals(MoneyTransferStatus.SENT, moneyTransfer.getMoneytransferStatus());
		assertFalse(moneyTransfer.getExecutionDate().isBefore(before));
		assertFalse(moneyTransfer.getExecutionDate().isAfter(LocalDate.now(ZoneId.systemDefault())));
	}

	@Test
	void updateMoneyTransferAfterExecution_shouldExtractInstantPaymentResponse() throws Exception {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.REALTIME_TRANSFER);
		GVRInstUebSEPA result = new GVRInstUebSEPA();
		result.setOrderId("instant-4711");
		result.setOrderStatus("3");
		result.setCancellationCode("4");

		Object response = invokePrivate(service, "updateMoneyTransferAfterExecution",
				new Class<?>[] { MoneyTransfer.class, BankOrderOperation.class, GBankingHBCICallback.class, HBCIExecStatus.class, HBCIJobResult.class,
						boolean.class },
				moneyTransfer, BankOrderOperation.CREATE, mock(GBankingHBCICallback.class), mock(HBCIExecStatus.class), result, true);

		assertEquals("instant-4711", moneyTransfer.getBankOrderId());
		assertEquals(SepaOrderStatus.PROCESSING, invokePrivate(response, "sepaOrderStatus", new Class<?>[0]));
		assertEquals(SepaCancellationCode.RECALL, invokePrivate(response, "sepaCancellationCode", new Class<?>[0]));
	}

	@Test
	void instantPaymentStatusService_shouldRetrieveAndPersistFinalStatus() {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		Recipient recipient = dbController.insertOrUpdate(new Recipient("Recipient Name", "DE12345678901234567890", "TESTDEFFXXX", null, null,
				"Testbank", de.zft2.gbanking.db.dao.enu.Source.MONEYTRANSFER));
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.REALTIME_TRANSFER);
		moneyTransfer.setAccountId(account.getId());
		moneyTransfer.setRecipientId(recipient.getId());
		moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.SENT);
		moneyTransfer = dbController.insertOrUpdate(moneyTransfer);
		moneyTransfer.setBankOrderId("instant-4711");

		HBCIHandler handler = mock(HBCIHandler.class);
		Properties supportedJobs = new Properties();
		supportedJobs.setProperty(InstantPaymentStatusService.JOB_NAME, "1");
		when(handler.getSupportedLowlevelJobs()).thenReturn(supportedJobs);
		Properties restrictions = new Properties();
		restrictions.setProperty("suppformats", "urn:iso:std:iso:20022:tech:xsd:pain.001.001.03");
		when(handler.getLowlevelJobRestrictions(InstantPaymentStatusService.JOB_NAME)).thenReturn(restrictions);
		HBCIExecStatus executionStatus = mock(HBCIExecStatus.class);
		when(executionStatus.isOK()).thenReturn(true);
		when(handler.execute()).thenReturn(executionStatus);

		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		HBCIJobResult result = mock(HBCIJobResult.class);
		Properties resultData = new Properties();
		resultData.setProperty("content.orderid", "instant-4711");
		resultData.setProperty("content.orderstatus", "7");
		resultData.setProperty("content.ccode", "4");
		when(result.getResultData()).thenReturn(resultData);
		when(result.isOK()).thenReturn(true);
		when(job.getJobResult()).thenReturn(result);
		BankAccessService bankAccessService = ServiceRegistry.getService(BankAccessService.class);
		when(bankAccessService.newLowlevelHbciJob(handler, InstantPaymentStatusService.JOB_NAME)).thenReturn(job);
		Konto senderAccount = new Konto();
		senderAccount.iban = "DE44500105175407324931";
		senderAccount.bic = "INGDDEFFXXX";
		GBankingHBCICallback callback = mock(GBankingHBCICallback.class);

		InstantPaymentStatusService statusService = new InstantPaymentStatusService();
		statusService.retrieveStatusIfNecessary(handler, callback, moneyTransfer, senderAccount,
				SepaOrderStatus.PROCESSING, null);
		assertTrue(statusService.retrieveStatus(handler, callback, moneyTransfer, senderAccount));

		verify(job, times(2)).setParam("My.iban", senderAccount.iban);
		verify(job, times(2)).setParam("formats.format", "urn:iso:std:iso:20022:tech:xsd:pain.001.001.03");
		verify(job, times(2)).setParam("orderid", "instant-4711");
		verify(job, times(2)).addToQueue();
		verify(callback, times(2)).registerJobDescription(same(job), anyString());
		MoneyTransferProtocol protocol = dbController.getAllByParent(MoneyTransferProtocol.class, moneyTransfer.getId()).get(0);
		assertEquals("instant-4711", protocol.getBankOrderId());
		assertEquals(SepaOrderStatus.COMPLETED, protocol.getSepaOrderStatus());
		assertEquals(SepaCancellationCode.RECALL, protocol.getSepaCancellationCode());
		assertEquals(2, dbController.getAllByParent(MoneyTransferProtocol.class, moneyTransfer.getId()).size());
	}

	@Test
	void instantPaymentStatusService_shouldRefreshLegacyBpdWhenBankRequestsStatusQuery() throws Exception {
		InstantPaymentStatusService service = new InstantPaymentStatusService();
		HBCIHandler handler = mock(HBCIHandler.class);
		Properties supportedJobs = new Properties();
		supportedJobs.setProperty(InstantPaymentStatusService.JOB_NAME, "1");
		when(handler.getSupportedLowlevelJobs()).thenReturn(new Properties(), supportedJobs);

		HBCIPassport passport = mock(HBCIPassport.class);
		when(passport.getBPD()).thenReturn(new Properties());
		when(handler.getPassport()).thenReturn(passport);
		HBCIDialogStatus refreshStatus = mock(HBCIDialogStatus.class);
		when(refreshStatus.isOK()).thenReturn(true);
		when(handler.refreshXPD(HBCIHandler.REFRESH_BPD)).thenReturn(refreshStatus);

		HBCIStatus jobStatus = new HBCIStatus();
		jobStatus.addRetVal(new HBCIRetVal(null, null, null, "3045", "Status query required", null));
		HBCIJobResult initialResult = mock(HBCIJobResult.class);
		when(initialResult.getJobStatus()).thenReturn(jobStatus);

		boolean supported = (boolean) invokePrivate(service, "ensureStatusRequestSupported",
				new Class<?>[] { HBCIHandler.class, HBCIJobResult.class }, handler, initialResult);

		assertTrue(supported);
		verify(handler).refreshXPD(HBCIHandler.REFRESH_BPD);
	}

	@Test
	void retrieveInstantPaymentStatuses_shouldRejectRegularTransfersAndClearPin() {
		char[] pin = "secret".toCharArray();

		int successfulRequests = new MoneyTransferExecutionService().retrieveInstantPaymentStatuses(
				List.of(createMoneyTransfer(OrderType.TRANSFER)), new BankAccount(), pin);

		assertEquals(0, successfulRequests);
		assertArrayEquals(new char[pin.length], pin);
	}

	@Test
	void updateMoneyTransferAfterExecution_shouldNotOverwriteScheduledExecutionDate() throws Exception {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.SCHEDULED_TRANSFER);
		LocalDate executionDate = LocalDate.of(2026, Month.JULY, 15);
		moneyTransfer.setExecutionDate(executionDate);

		invokePrivate(service, "updateMoneyTransferAfterExecution",
				new Class<?>[] { MoneyTransfer.class, BankOrderOperation.class, GBankingHBCICallback.class, HBCIExecStatus.class, HBCIJobResult.class,
						boolean.class },
				moneyTransfer, BankOrderOperation.CREATE, mock(GBankingHBCICallback.class), mock(HBCIExecStatus.class), null, true);

		assertEquals(MoneyTransferStatus.SENT, moneyTransfer.getMoneytransferStatus());
		assertEquals(executionDate, moneyTransfer.getExecutionDate());
	}

	@Test
	void updateMoneyTransferAfterExecution_shouldSetErrorAndReportStatusFailure() throws Exception {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.TRANSFER);
		GBankingHBCICallback callback = mock(GBankingHBCICallback.class);
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		when(status.getErrorString()).thenReturn("bank rejected order");

		invokePrivate(service, "updateMoneyTransferAfterExecution",
				new Class<?>[] { MoneyTransfer.class, BankOrderOperation.class, GBankingHBCICallback.class, HBCIExecStatus.class, HBCIJobResult.class,
						boolean.class },
				moneyTransfer, BankOrderOperation.CREATE, callback, status, null, false);

		assertEquals(MoneyTransferStatus.ERROR, moneyTransfer.getMoneytransferStatus());
		verify(callback).handleFailure("bank rejected order");
	}

	@Test
	void persistExecutionResult_shouldFinalizeSuccessfulStandingOrderEdit() throws Exception {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		Recipient recipient = dbController.insertOrUpdate(new Recipient("Recipient Name", "DE12345678901234567890", "TESTDEFFXXX", null, null,
				"Testbank", de.zft2.gbanking.db.dao.enu.Source.MONEYTRANSFER));
		MoneyTransfer predecessor = createPersistableStandingOrder(account, recipient, MoneyTransferStatus.INVENTORY);
		predecessor.setBankOrderId("standing-old-1");
		predecessor = dbController.insertOrUpdate(predecessor);
		int predecessorId = predecessor.getId();
		MoneyTransfer changedTransfer = createPersistableStandingOrder(account, recipient, MoneyTransferStatus.CHANGED);
		changedTransfer.setBankOrderId("standing-old-1");
		changedTransfer.setHistoryorderId(predecessorId);
		changedTransfer = dbController.insertOrUpdate(changedTransfer);
		int changedTransferId = changedTransfer.getId();
		GVRDauerEdit result = new GVRDauerEdit();
		result.setOrderId("standing-new-1");
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();

		Object bankResponse = invokePrivate(service, "updateMoneyTransferAfterExecution",
				new Class<?>[] { MoneyTransfer.class, BankOrderOperation.class, GBankingHBCICallback.class, HBCIExecStatus.class, HBCIJobResult.class,
						boolean.class },
				changedTransfer, BankOrderOperation.EDIT, mock(GBankingHBCICallback.class), mock(HBCIExecStatus.class), result, true);
		LocalDateTime start = LocalDateTime.now();
		invokePrivate(service, "persistExecutionResult",
				new Class<?>[] { MoneyTransfer.class, BankOrderOperation.class, boolean.class, LocalDateTime.class, LocalDateTime.class,
						MoneyTransferStatus.class, String.class, bankResponse.getClass() },
				changedTransfer, BankOrderOperation.EDIT, true, start, start.plusSeconds(1), MoneyTransferStatus.INVENTORY, "accepted", bankResponse);

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, account.getId());
		assertEquals(MoneyTransferStatus.SUPERSEDED,
				transfers.stream().filter(transfer -> transfer.getId() == predecessorId).findFirst().orElseThrow().getMoneytransferStatus());
		MoneyTransfer current = transfers.stream().filter(transfer -> transfer.getId() == changedTransferId).findFirst().orElseThrow();
		assertEquals(MoneyTransferStatus.INVENTORY, current.getMoneytransferStatus());
		assertEquals("standing-new-1", current.getBankOrderId());
		assertEquals(MoneyTransferStatus.INVENTORY,
				dbController.getAllByParent(MoneyTransferProtocol.class, current.getId()).get(0).getMoneytransferStatus());
	}

	@Test
	void updateMoneyTransferAfterExecution_shouldKeepFailedDeletePendingAndCompleteSuccessfulDelete() throws Exception {
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.SCHEDULED_TRANSFER);
		moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.DELETE_PENDING);
		GBankingHBCICallback callback = mock(GBankingHBCICallback.class);
		HBCIExecStatus failedStatus = mock(HBCIExecStatus.class);
		when(failedStatus.getErrorString()).thenReturn("bank rejected deletion");

		invokePrivate(service, "updateMoneyTransferAfterExecution",
				new Class<?>[] { MoneyTransfer.class, BankOrderOperation.class, GBankingHBCICallback.class, HBCIExecStatus.class, HBCIJobResult.class,
						boolean.class },
				moneyTransfer, BankOrderOperation.DELETE, callback, failedStatus, null, false);
		assertEquals(MoneyTransferStatus.DELETE_PENDING, moneyTransfer.getMoneytransferStatus());

		invokePrivate(service, "updateMoneyTransferAfterExecution",
				new Class<?>[] { MoneyTransfer.class, BankOrderOperation.class, GBankingHBCICallback.class, HBCIExecStatus.class, HBCIJobResult.class,
						boolean.class },
				moneyTransfer, BankOrderOperation.DELETE, callback, mock(HBCIExecStatus.class), null, true);
		assertEquals(MoneyTransferStatus.DELETED, moneyTransfer.getMoneytransferStatus());
	}

	@Test
	void applyRecipientNameFromVoP_shouldPersistCorrectedRecipientAndAssignItToOrder() throws Exception {
		DBController dbController = DBController.getInstance(tempDir.toString());
		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		Recipient originalRecipient = dbController.insertOrUpdate(new Recipient("Wrong Recipient", "DE12345678901234567890", "TESTDEFFXXX", null, null,
				"Testbank", de.zft2.gbanking.db.dao.enu.Source.MANUELL));
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.TRANSFER);
		moneyTransfer.setRecipient(originalRecipient);
		moneyTransfer.setRecipientId(originalRecipient.getId());
		GBankingHBCICallback callback = mock(GBankingHBCICallback.class);
		when(callback.getConfirmedRecipientName()).thenReturn("Correct Recipient");

		invokePrivate(service, "applyRecipientNameFromVoP", new Class<?>[] { MoneyTransfer.class, GBankingHBCICallback.class }, moneyTransfer, callback);

		assertEquals("Correct Recipient", moneyTransfer.getRecipient().getName());
		assertEquals(moneyTransfer.getRecipient().getId(), moneyTransfer.getRecipientId());
		assertEquals(1, dbController.getAll(Recipient.class).size());
	}

	@Test
	void executeTransfer_shouldRunHbciOrderAfterCallbackAndPersistSentStatus() {

		BankingCapabilityService bankingCapabilityService = mock(BankingCapabilityService.class);
		ServiceRegistry.setService(BankingCapabilityService.class, bankingCapabilityService);

		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		Recipient recipient = dbController.insertOrUpdate(new Recipient("Recipient Name", "DE12345678901234567890", "TESTDEFFXXX", null, null,
				"Testbank", de.zft2.gbanking.db.dao.enu.Source.MANUELL));
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.TRANSFER);
		moneyTransfer.setAccountId(bankAccount.getId());
		moneyTransfer.setRecipientId(recipient.getId());
		moneyTransfer.setRecipient(recipient);

		MoneyTransferService moneyTransferService = ServiceRegistry.getService(MoneyTransferService.class);
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);

		MoneyTransferExecutionService service = new MoneyTransferExecutionService();

		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("10020030");
		char[] pin = "1234".toCharArray();
		HBCIPassport passport = mock(HBCIPassport.class);
		HBCIHandler handle = mock(HBCIHandler.class);
		Konto senderAccount = new Konto();
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		HBCIJobResult jobResult = mock(HBCIJobResult.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);

		doReturn(bankAccess).when(hbciSupport).initBankAccess(any(BankAccount.class), same(pin));
		doReturn(true).when(bankingCapabilityService).supportsTransferOrderType(any(BankAccount.class), eq(OrderType.TRANSFER));
		doReturn(passport).when(hbciSupport).initBankConnection(eq(bankAccess), any(GBankingHBCICallback.class));
		doReturn(handle).when(hbciSupport).createHBCIHandler(eq(BaseMessagesDb.getVersion().getId()), same(passport));
		doReturn(senderAccount).when(moneyTransferService).getSenderAccount(eq(passport), any(BankAccount.class));
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
			List<MoneyTransferProtocol> protocols = dbController.getAllByParent(MoneyTransferProtocol.class, moneyTransfer.getId());
			assertEquals(1, protocols.size());
			assertEquals(MoneyTransferStatus.SENT, protocols.get(0).getMoneytransferStatus());
			assertNotNull(protocols.get(0).getTimeStart());
			assertNotNull(protocols.get(0).getTimeFinish());
			assertTrue(protocols.get(0).getProtocolText().contains("HBCI execution status"));
		}
		ServiceRegistry.removeService(BankingCapabilityService.class);
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

	private static MoneyTransfer createPersistableStandingOrder(BankAccount account, Recipient recipient, MoneyTransferStatus status) {
		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.STANDING_ORDER);
		moneyTransfer.setAccountId(account.getId());
		moneyTransfer.setRecipient(recipient);
		moneyTransfer.setRecipientId(recipient.getId());
		moneyTransfer.setExecutionDate(LocalDate.of(2026, Month.AUGUST, 1));
		moneyTransfer.setExecutionDay(15);
		moneyTransfer.setStandingorderMode(StandingorderMode.MONTHLY);
		moneyTransfer.setMoneytransferStatus(status);
		return moneyTransfer;
	}

	private static Konto createRecipientAccount() {
		Konto recipientAccount = new Konto();
		recipientAccount.name = "Recipient Name";
		recipientAccount.iban = "DE12345678901234567890";
		recipientAccount.bic = "TESTDEFFXXX";
		return recipientAccount;
	}

	private BankAccess insertBankAccessWithBpd(String... businessCases) {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccess bankAccess = dbController.insertOrUpdate(TestDataFactory.createSampleBankAccess("10020030"));
		bankAccess.getFints().setBpd(HbciParameterTestDataFactory.buildCapabilityBpd(businessCases));
		dbController.insertOrUpdatePD(bankAccess);
		return bankAccess;
	}

	private static Date toUtilDate(LocalDate date) {
		return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	private static void assertArrayCleared(char[] secret) {
		for (char value : secret) {
			assertEquals('\0', value);
		}
	}

	private static String fieldLabel(String key) {
		return Messages.getInstance().getMessage(key);
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
