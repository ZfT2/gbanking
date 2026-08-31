package de.zft2.gbanking.service.moneytransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRDauerList;
import org.kapott.hbci.GV_Result.GVRTermUebList;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;
import org.mockito.MockedConstruction;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.service.BankingCapabilityService;
import de.zft2.gbanking.service.GBankingService;
import de.zft2.gbanking.service.Service;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.ServiceStubbingUtil;
import de.zft2.gbanking.testdata.TestDataFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoneyTransferInventoryServiceTest {

	private Path tempDir;
	private DBController dbController;

	private static final List<Class<? extends Service>> SERVICES_TO_STUB = List.of(GBankingService.class, BankAccessService.class,
			BankingCapabilityService.class, MoneyTransferService.class);

	@BeforeAll
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		dbController = DBController.getInstance(tempDir.toString());
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@BeforeEach
	void setUp() throws Exception {
		DBControllerTestUtil.clearAllTables(DBController.getConnection());

		ServiceStubbingUtil.initStubbedServicesInContext(SERVICES_TO_STUB);
	}

	@AfterEach
	void tearDown() throws Exception {
		ServiceStubbingUtil.unloadStubbedServicesInContext(SERVICES_TO_STUB);
	}

	@Test
	void retrieveInventory_shouldPersistStandingOrdersAndSkipDuplicates() {
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		MoneyTransferInventoryService service = new MoneyTransferInventoryService();
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("10020030");
		HBCIPassport passport = mock(HBCIPassport.class);
		HBCIHandler handle = mock(HBCIHandler.class);
		Konto senderAccount = new Konto();
		HBCIExecStatus status = okStatus();
		GVRDauerList result = mock(GVRDauerList.class);
		GVRDauerList.Dauer entry = standingOrderEntry();
		@SuppressWarnings("unchecked")
		HBCIJob<GVRDauerList> job = mock(HBCIJob.class);

		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		stubHbciSupport(hbciSupport, bankAccount, bankAccess, passport, handle, senderAccount);
		doReturn(job).when(hbciSupport).newHbciJob(handle, "DauerSEPAList");
		when(handle.execute()).thenReturn(status);
		when(job.getJobResult()).thenReturn(result);
		when(result.isOK()).thenReturn(true);
		when(result.getEntries()).thenReturn(new GVRDauerList.Dauer[] { entry });

		char[] firstPin = "1234".toCharArray();
		char[] secondPin = "5678".toCharArray();
		try (MockedConstruction<GBankingHBCICallback> callbacks = mockConstruction(GBankingHBCICallback.class)) {
			assertTrue(service.retrieveInventory(bankAccount, OrderType.STANDING_ORDER, firstPin));
			assertTrue(service.retrieveInventory(bankAccount, OrderType.STANDING_ORDER, secondPin));

			verify(job, times(2)).setParam("src", senderAccount);
			verify(job, times(2)).addToQueue();
			verify(handle, times(2)).execute();
			assertEquals(2, callbacks.constructed().size());
		}

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, bankAccount.getId());
		assertEquals(1, transfers.size());
		MoneyTransfer transfer = transfers.get(0);
		assertEquals(OrderType.STANDING_ORDER, transfer.getOrderType());
		assertEquals(MoneyTransferStatus.INVENTORY, transfer.getMoneytransferStatus());
		assertEquals(new BigDecimal("44.55"), transfer.getAmount());
		assertEquals("Standing purpose\nSecond line", transfer.getPurpose());
		assertEquals(LocalDate.of(2026, Month.JUNE, 1), transfer.getExecutionDate());
		assertEquals(31, transfer.getExecutionDay());
		assertEquals(StandingorderMode.MONTHLY, transfer.getStandingorderMode());
		assertEquals("standing-order-1", transfer.getBankOrderId());
		assertEquals("Standing Recipient", transfer.getRecipient().getName());
		assertArrayCleared(firstPin);
		assertArrayCleared(secondPin);
	}

	@Test
	void retrieveInventory_shouldPersistScheduledTransfers() {
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		MoneyTransferInventoryService service = new MoneyTransferInventoryService();
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("10020030");
		HBCIPassport passport = mock(HBCIPassport.class);
		HBCIHandler handle = mock(HBCIHandler.class);
		Konto senderAccount = new Konto();
		HBCIExecStatus status = okStatus();
		GVRTermUebList result = mock(GVRTermUebList.class);
		GVRTermUebList.Entry entry = scheduledTransferEntry();
		@SuppressWarnings("unchecked")
		HBCIJob<GVRTermUebList> job = mock(HBCIJob.class);

		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		stubHbciSupport(hbciSupport, bankAccount, bankAccess, passport, handle, senderAccount);
		doReturn(job).when(hbciSupport).newHbciJob(handle, "TermUebSEPAList");
		when(handle.execute()).thenReturn(status);
		when(job.getJobResult()).thenReturn(result);
		when(result.isOK()).thenReturn(true);
		when(result.getEntries()).thenReturn(new GVRTermUebList.Entry[] { entry });

		try (MockedConstruction<GBankingHBCICallback> ignored = mockConstruction(GBankingHBCICallback.class)) {
			assertTrue(service.retrieveInventory(bankAccount, OrderType.SCHEDULED_TRANSFER, "1234".toCharArray()));
		}

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, bankAccount.getId());
		assertEquals(1, transfers.size());
		MoneyTransfer transfer = transfers.get(0);
		assertEquals(OrderType.SCHEDULED_TRANSFER, transfer.getOrderType());
		assertEquals(MoneyTransferStatus.INVENTORY, transfer.getMoneytransferStatus());
		assertEquals(new BigDecimal("12.34"), transfer.getAmount());
		assertEquals("Scheduled purpose", transfer.getPurpose());
		assertEquals(LocalDate.of(2026, Month.JULY, 15), transfer.getExecutionDate());
		assertEquals("scheduled-transfer-1", transfer.getBankOrderId());
		assertEquals("Scheduled Recipient", transfer.getRecipient().getName());
		verify(job).setParam("src", senderAccount);
		verify(job).addToQueue();
	}

	@Test
	void retrieveInventory_shouldRejectUnsupportedOrderTypesAndClearPin() {
		BankAccessService hbciSupport = mock(BankAccessService.class);
		MoneyTransferInventoryService service = new MoneyTransferInventoryService();
		char[] pin = "1234".toCharArray();

		assertFalse(service.retrieveInventory(new BankAccount(), OrderType.TRANSFER, pin));

		verifyNoInteractions(hbciSupport);
		assertArrayCleared(pin);
	}

	@Test
	void saveRetrievedTransfers_shouldRollBackWholeBatchWhenLaterTransferFails() {
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		MoneyTransfer validTransfer = inventoryTransfer(bankAccount, "First recipient", "DE11111111111111111111", new BigDecimal("12.34"));
		MoneyTransfer invalidTransfer = inventoryTransfer(bankAccount, "Second recipient", "DE22222222222222222222", new BigDecimal("-1.00"));
		MoneyTransferInventoryService service = new MoneyTransferInventoryService();

		List<MoneyTransfer> retrievedTransfers = List.of(validTransfer, invalidTransfer);
		assertThrows(GBankingException.class, () -> service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, retrievedTransfers));

		assertTrue(dbController.getAllByParent(MoneyTransfer.class, bankAccount.getId()).isEmpty());
		assertTrue(dbController.getAll(Recipient.class).isEmpty());
		assertEquals(0, validTransfer.getId());
		assertEquals(0, validTransfer.getRecipient().getId());
	}

	@Test
	void saveRetrievedTransfers_shouldArchiveChangedBankOrderAndLinkNewVersion() {
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		MoneyTransferInventoryService service = new MoneyTransferInventoryService();
		MoneyTransfer initial = inventoryTransfer(bankAccount, "Initial recipient", "DE11111111111111111111", new BigDecimal("12.34"));
		initial.setBankOrderId("scheduled-4711");
		service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, List.of(initial));
		int initialId = initial.getId();

		MoneyTransfer changed = inventoryTransfer(bankAccount, "Changed recipient", "DE22222222222222222222", new BigDecimal("98.76"));
		changed.setBankOrderId("scheduled-4711");
		changed.setPurpose("Changed inventory data");
		service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, List.of(changed));

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, bankAccount.getId());
		assertEquals(2, transfers.size());
		MoneyTransfer archived = findByStatus(transfers, MoneyTransferStatus.SUPERSEDED);
		MoneyTransfer current = findByStatus(transfers, MoneyTransferStatus.INVENTORY);
		assertEquals(initialId, archived.getId());
		assertEquals("Inventory test", archived.getPurpose());
		assertEquals("Changed inventory data", current.getPurpose());
		assertEquals(archived.getId(), current.getHistoryorderId());
		assertEquals("scheduled-4711", current.getBankOrderId());
	}

	@Test
	void saveRetrievedTransfers_shouldMarkOrdersMissingFromCompleteInventory() {
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		MoneyTransferInventoryService service = new MoneyTransferInventoryService();
		MoneyTransfer transfer = inventoryTransfer(bankAccount, "Missing recipient", "DE33333333333333333333", new BigDecimal("12.34"));
		transfer.setBankOrderId("missing-1");
		service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, List.of(transfer));

		service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, List.of());

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, bankAccount.getId());
		assertEquals(1, transfers.size());
		assertEquals(MoneyTransferStatus.NOT_IN_BANK_INVENTORY, transfers.get(0).getMoneytransferStatus());
	}

	@Test
	void saveRetrievedTransfers_shouldLinkReappearingOrderToMissingVersion() {
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		MoneyTransferInventoryService service = new MoneyTransferInventoryService();
		MoneyTransfer initial = inventoryTransfer(bankAccount, "Recipient", "DE44444444444444444444", new BigDecimal("12.34"));
		initial.setBankOrderId("reappearing-1");
		service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, List.of(initial));
		service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, List.of());
		MoneyTransfer missing = dbController.getAllByParent(MoneyTransfer.class, bankAccount.getId()).get(0);

		MoneyTransfer reappearing = inventoryTransfer(bankAccount, "Recipient", "DE44444444444444444444", new BigDecimal("12.34"));
		reappearing.setBankOrderId("reappearing-1");
		service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, List.of(reappearing));

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, bankAccount.getId());
		assertEquals(2, transfers.size());
		MoneyTransfer current = findByStatus(transfers, MoneyTransferStatus.INVENTORY);
		assertEquals(missing.getId(), current.getHistoryorderId());
	}

	@Test
	void saveRetrievedTransfers_shouldReconcileOnlyRequestedOrderType() {
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		MoneyTransferInventoryService service = new MoneyTransferInventoryService();
		MoneyTransfer scheduled = inventoryTransfer(bankAccount, "Scheduled recipient", "DE55555555555555555555", new BigDecimal("12.34"));
		scheduled.setBankOrderId("scheduled-1");
		service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, List.of(scheduled));

		service.saveRetrievedTransfers(bankAccount, OrderType.STANDING_ORDER, List.of());

		MoneyTransfer persisted = dbController.getAllByParent(MoneyTransfer.class, bankAccount.getId()).get(0);
		assertEquals(MoneyTransferStatus.INVENTORY, persisted.getMoneytransferStatus());
	}

	@Test
	void saveRetrievedTransfers_shouldPreserveConfirmedVersionWhileEditIsPending() {
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		MoneyTransferInventoryService service = new MoneyTransferInventoryService();
		MoneyTransfer confirmed = inventoryTransfer(bankAccount, "Confirmed recipient", "DE56565656565656565656", new BigDecimal("12.34"));
		confirmed.setBankOrderId("pending-edit-1");
		service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, List.of(confirmed));
		MoneyTransfer pendingEdit = inventoryTransfer(bankAccount, "Changed recipient", "DE57575757575757575757", new BigDecimal("22.34"));
		Recipient recipient = dbController.resolveRecipient(pendingEdit.getRecipient());
		pendingEdit.setRecipient(recipient);
		pendingEdit.setRecipientId(recipient.getId());
		pendingEdit.setBankOrderId("pending-edit-1");
		pendingEdit.setHistoryorderId(confirmed.getId());
		pendingEdit.setMoneytransferStatus(MoneyTransferStatus.CHANGED);
		dbController.insertOrUpdate(pendingEdit);
		MoneyTransfer bankResponse = inventoryTransfer(bankAccount, "Confirmed recipient", "DE56565656565656565656", new BigDecimal("12.34"));
		bankResponse.setBankOrderId("pending-edit-1");

		service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, List.of(bankResponse));

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, bankAccount.getId());
		assertEquals(2, transfers.size());
		assertEquals(MoneyTransferStatus.INVENTORY,
				transfers.stream().filter(transfer -> transfer.getId() == confirmed.getId()).findFirst().orElseThrow().getMoneytransferStatus());
		assertEquals(MoneyTransferStatus.CHANGED,
				transfers.stream().filter(transfer -> transfer.getId() == pendingEdit.getId()).findFirst().orElseThrow().getMoneytransferStatus());
	}

	@Test
	void saveRetrievedTransfers_shouldNotDuplicateOrderWhileDeletionIsPending() {
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		MoneyTransferInventoryService service = new MoneyTransferInventoryService();
		MoneyTransfer pendingDeletion = inventoryTransfer(bankAccount, "Deletion recipient", "DE58585858585858585858", new BigDecimal("12.34"));
		pendingDeletion.setBankOrderId("pending-delete-1");
		service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, List.of(pendingDeletion));
		pendingDeletion.setMoneytransferStatus(MoneyTransferStatus.DELETE_PENDING);
		dbController.insertOrUpdate(pendingDeletion);
		MoneyTransfer bankResponse = inventoryTransfer(bankAccount, "Deletion recipient", "DE58585858585858585858", new BigDecimal("12.34"));
		bankResponse.setBankOrderId("pending-delete-1");

		service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, List.of(bankResponse));

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, bankAccount.getId());
		assertEquals(1, transfers.size());
		assertEquals(MoneyTransferStatus.DELETE_PENDING, transfers.get(0).getMoneytransferStatus());
	}

	@Test
	void saveRetrievedTransfers_shouldRollBackArchiveAndSuccessorWhenLaterInsertFails() {
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		MoneyTransferInventoryService service = new MoneyTransferInventoryService();
		MoneyTransfer initial = inventoryTransfer(bankAccount, "Initial recipient", "DE66666666666666666666", new BigDecimal("12.34"));
		initial.setBankOrderId("existing-1");
		service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, List.of(initial));

		MoneyTransfer changed = inventoryTransfer(bankAccount, "Changed recipient", "DE77777777777777777777", new BigDecimal("22.22"));
		changed.setBankOrderId("existing-1");
		MoneyTransfer invalid = inventoryTransfer(bankAccount, "Invalid recipient", "DE88888888888888888888", new BigDecimal("-1.00"));
		invalid.setBankOrderId("invalid-1");

		List<MoneyTransfer> retrievedTransfers = List.of(changed, invalid);
		assertThrows(GBankingException.class, () -> service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, retrievedTransfers));

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, bankAccount.getId());
		assertEquals(1, transfers.size());
		assertEquals(MoneyTransferStatus.INVENTORY, transfers.get(0).getMoneytransferStatus());
		assertEquals(new BigDecimal("12.34"), transfers.get(0).getAmount());
	}

	@Test
	void retrieveInventory_shouldNotMarkExistingOrdersMissingWhenResponseEntryIsIncomplete() {
		BankAccount bankAccount = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		BankAccessService hbciSupport = mock(BankAccessService.class);

		MoneyTransferInventoryService service = new MoneyTransferInventoryService();
		MoneyTransfer existing = inventoryTransfer(bankAccount, "Existing recipient", "DE99999999999999999999", new BigDecimal("12.34"));
		existing.setBankOrderId("existing-1");
		service.saveRetrievedTransfers(bankAccount, OrderType.SCHEDULED_TRANSFER, List.of(existing));
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("10020030");
		HBCIPassport passport = mock(HBCIPassport.class);
		HBCIHandler handle = mock(HBCIHandler.class);
		GVRTermUebList result = mock(GVRTermUebList.class);
		GVRTermUebList.Entry incompleteEntry = scheduledTransferEntry();
		incompleteEntry.value = null;
		@SuppressWarnings("unchecked")
		HBCIJob<GVRTermUebList> job = mock(HBCIJob.class);

		stubHbciSupport(hbciSupport, bankAccount, bankAccess, passport, handle, new Konto());
		doReturn(job).when(hbciSupport).newHbciJob(handle, "TermUebSEPAList");
		HBCIExecStatus status = okStatus();
		when(handle.execute()).thenReturn(status);
		when(job.getJobResult()).thenReturn(result);
		when(result.isOK()).thenReturn(true);
		when(result.getEntries()).thenReturn(new GVRTermUebList.Entry[] { incompleteEntry });

		try (MockedConstruction<GBankingHBCICallback> ignored = mockConstruction(GBankingHBCICallback.class)) {
			assertFalse(service.retrieveInventory(bankAccount, OrderType.SCHEDULED_TRANSFER, "1234".toCharArray()));
		}

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, bankAccount.getId());
		assertEquals(1, transfers.size());
		assertEquals(MoneyTransferStatus.INVENTORY, transfers.get(0).getMoneytransferStatus());
	}

	private static void stubHbciSupport(BankAccessService bankAccessService, BankAccount bankAccount, BankAccess bankAccess,
			HBCIPassport passport, HBCIHandler handle, Konto senderAccount) {

		BankingCapabilityService bankingCapabilityService = ServiceRegistry.getService(BankingCapabilityService.class);
		MoneyTransferService moneyTransferService = ServiceRegistry.getService(MoneyTransferService.class);

		doReturn(bankAccess).when(bankAccessService).initBankAccess(eq(bankAccount), any(char[].class));
		doReturn(true).when(bankingCapabilityService).supportsOrderInventory(bankAccount, OrderType.STANDING_ORDER);
		doReturn(true).when(bankingCapabilityService).supportsOrderInventory(bankAccount, OrderType.SCHEDULED_TRANSFER);
		doReturn(passport).when(bankAccessService).initBankConnection(eq(bankAccess), any(GBankingHBCICallback.class));
		doReturn(handle).when(bankAccessService).createHBCIHandler(BaseMessagesDb.getVersion().getId(), passport);
		doReturn(senderAccount).when(moneyTransferService).getSenderAccount(passport, bankAccount);
	}

	private static HBCIExecStatus okStatus() {
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		when(status.isOK()).thenReturn(true);
		return status;
	}

	private static GVRDauerList.Dauer standingOrderEntry() {
		GVRDauerList.Dauer entry = new GVRDauerList.Dauer();
		entry.orderid = "standing-order-1";
		entry.other = recipient("Standing Recipient", "DE11111111111111111111");
		entry.value = new Value(new BigDecimal("44.55"), "EUR");
		entry.usage = new String[] { "Standing purpose", "Second line" };
		entry.firstdate = toUtilDate(LocalDate.of(2026, Month.JUNE, 1));
		entry.timeunit = "M";
		entry.turnus = 1;
		entry.execday = 31;
		return entry;
	}

	private static GVRTermUebList.Entry scheduledTransferEntry() {
		GVRTermUebList.Entry entry = new GVRTermUebList.Entry();
		entry.orderid = "scheduled-transfer-1";
		entry.other = recipient("Scheduled Recipient", "DE22222222222222222222");
		entry.value = new Value(new BigDecimal("12.34"), "EUR");
		entry.usage = new String[] { "Scheduled purpose" };
		entry.date = toUtilDate(LocalDate.of(2026, Month.JULY, 15));
		return entry;
	}

	private static Konto recipient(String name, String iban) {
		Konto recipient = new Konto();
		recipient.name = name;
		recipient.iban = iban;
		recipient.bic = "TESTDEFFXXX";
		return recipient;
	}

	private static MoneyTransfer inventoryTransfer(BankAccount bankAccount, String recipientName, String recipientIban, BigDecimal amount) {
		Recipient recipient = new Recipient(recipientName, recipientIban, "TESTDEFFXXX", null, null, null, Source.MONEYTRANSFER);
		MoneyTransfer transfer = new MoneyTransfer();
		transfer.setAccountId(bankAccount.getId());
		transfer.setOrderType(OrderType.SCHEDULED_TRANSFER);
		transfer.setRecipient(recipient);
		transfer.setPurpose("Inventory test");
		transfer.setAmount(amount);
		transfer.setExecutionDate(LocalDate.of(2026, Month.JULY, 15));
		transfer.setMoneytransferStatus(MoneyTransferStatus.INVENTORY);
		return transfer;
	}

	private static MoneyTransfer findByStatus(List<MoneyTransfer> transfers, MoneyTransferStatus status) {
		return transfers.stream().filter(transfer -> transfer.getMoneytransferStatus() == status).findFirst().orElseThrow();
	}

	private static Date toUtilDate(LocalDate date) {
		return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	private static void assertArrayCleared(char[] secret) {
		for (char value : secret) {
			assertEquals('\0', value);
		}
	}
}
