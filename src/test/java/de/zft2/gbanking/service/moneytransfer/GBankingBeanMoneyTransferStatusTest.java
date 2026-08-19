package de.zft2.gbanking.service.moneytransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.TestData;
import de.zft2.gbanking.gui.dto.MoneyTransferForm;
import de.zft2.gbanking.service.ServiceRegistry;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GBankingBeanMoneyTransferStatusTest {

	private Path tempDir;
	private DBController dbController;
	private MoneyTransferService moneyTransferService;

	@BeforeAll
	void setup() throws Exception {
		setupDatabase();
		moneyTransferService = ServiceRegistry.getService(MoneyTransferService.class);
	}

	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		dbController = DBController.getInstance(tempDir.toString());
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
	void saveMoneyTransferToDB_shouldMarkEditedInventoryTransferAsChanged() {
		BankAccount account = dbController.insertOrUpdate(TestData.createSampleAccount(null));
		Recipient recipient = dbController
				.insertOrUpdate(new Recipient("Inventory Recipient", "DE11111111111111111111", "TESTDEFFXXX", null, null, "Testbank", Source.MONEYTRANSFER));
		MoneyTransfer inventoryTransfer = createInventoryTransfer(account.getId(), recipient.getId());
		inventoryTransfer = dbController.insertOrUpdate(inventoryTransfer);
		int inventoryTransferId = inventoryTransfer.getId();

		Recipient changedRecipient = new Recipient("Inventory Recipient", "DE11111111111111111111", "TESTDEFFXXX", null, null, "TestBank", Source.ONLINE);
		MoneyTransferForm changedForm = new MoneyTransferForm(account, OrderType.SCHEDULED_TRANSFER, changedRecipient, new BigDecimal("19.99"),
				"Changed purpose", LocalDate.of(2026, Month.AUGUST, 15));

		MoneyTransfer savedTransfer = moneyTransferService.saveMoneyTransferToDB(changedForm, inventoryTransfer);

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, account.getId());
		assertEquals(2, transfers.size());
		assertEquals(inventoryTransferId, savedTransfer.getHistoryorderId());
		assertEquals(MoneyTransferStatus.CHANGED, savedTransfer.getMoneytransferStatus());
		assertEquals("Changed purpose",
				transfers.stream().filter(transfer -> transfer.getId() == savedTransfer.getId()).findFirst().orElseThrow().getPurpose());
		assertEquals(MoneyTransferStatus.INVENTORY,
				transfers.stream().filter(transfer -> transfer.getId() == inventoryTransferId).findFirst().orElseThrow().getMoneytransferStatus());
	}

	@Test
	void saveMoneyTransferToDB_shouldKeepNewStatusForNewTransfer() {
		BankAccount account = dbController.insertOrUpdate(TestData.createSampleAccount(null));
		Recipient recipient = new Recipient("New Recipient", "DE22222222222222222222", "TESTDEFFXXX", null, null, "Testbank", Source.ONLINE);
		MoneyTransferForm form = new MoneyTransferForm(account, OrderType.TRANSFER, recipient, new BigDecimal("9.99"), "New purpose",
				LocalDate.of(2026, Month.AUGUST, 16));

		MoneyTransfer savedTransfer = moneyTransferService.saveMoneyTransferToDB(form);

		assertEquals(MoneyTransferStatus.NEW, savedTransfer.getMoneytransferStatus());
	}

	@Test
	void retrieveOpenTransfers_shouldReturnExecutableTransfers() {
		BankAccount account = dbController.insertOrUpdate(TestData.createSampleAccount(null));
		Recipient recipient = dbController.insertOrUpdate(
				new Recipient("Open Transfer Recipient", "DE33333333333333333333", "TESTDEFFXXX", null, null, "Testbank", Source.MONEYTRANSFER));
		dbController.insertOrUpdate(new MoneyTransfer(account.getId(), OrderType.TRANSFER, recipient.getId(), "Open transfer", new BigDecimal("29.99"),
				LocalDate.of(2026, Month.AUGUST, 17), MoneyTransferStatus.NEW));
		dbController.insertOrUpdate(new MoneyTransfer(account.getId(), OrderType.TRANSFER, recipient.getId(), "Changed transfer", new BigDecimal("34.99"),
				LocalDate.of(2026, Month.AUGUST, 18), MoneyTransferStatus.CHANGED));
		dbController.insertOrUpdate(new MoneyTransfer(account.getId(), OrderType.TRANSFER, recipient.getId(), "Retry transfer", new BigDecimal("36.99"),
				LocalDate.of(2026, Month.AUGUST, 19), MoneyTransferStatus.ERROR));
		dbController.insertOrUpdate(new MoneyTransfer(account.getId(), OrderType.SCHEDULED_TRANSFER, recipient.getId(), "Delete transfer",
				new BigDecimal("37.99"), LocalDate.of(2026, Month.AUGUST, 20), MoneyTransferStatus.DELETE_PENDING));
		dbController.insertOrUpdate(new MoneyTransfer(account.getId(), OrderType.TRANSFER, recipient.getId(), "Sent transfer", new BigDecimal("39.99"),
				LocalDate.of(2026, Month.AUGUST, 18), MoneyTransferStatus.SENT));

		List<MoneyTransfer> transfers = moneyTransferService.retrieveOpenTransfers();

		assertEquals(4, transfers.size());
		assertEquals("Open transfer", transfers.get(0).getPurpose());
		assertEquals(MoneyTransferStatus.NEW, transfers.get(0).getMoneytransferStatus());
		assertEquals("Changed transfer", transfers.get(1).getPurpose());
		assertEquals(MoneyTransferStatus.CHANGED, transfers.get(1).getMoneytransferStatus());
		assertEquals("Retry transfer", transfers.get(2).getPurpose());
		assertEquals(MoneyTransferStatus.ERROR, transfers.get(2).getMoneytransferStatus());
		assertEquals("Delete transfer", transfers.get(3).getPurpose());
		assertEquals(MoneyTransferStatus.DELETE_PENDING, transfers.get(3).getMoneytransferStatus());
	}

	@Test
	void getAllByParentWithFilter_shouldApplyParentAndEnumFilter() {
		BankAccount account = dbController.insertOrUpdate(TestData.createSampleAccount(null));
		BankAccount otherAccount = dbController.insertOrUpdate(TestData.createSampleAccount(null));
		Recipient recipient = dbController.insertOrUpdate(
				new Recipient("Parent Filter Recipient", "DE44444444444444444444", "TESTDEFFXXX", null, null, "Testbank", Source.MONEYTRANSFER));
		dbController.insertOrUpdate(new MoneyTransfer(account.getId(), OrderType.TRANSFER, recipient.getId(), "Matching transfer", new BigDecimal("49.99"),
				LocalDate.of(2026, Month.AUGUST, 19), MoneyTransferStatus.NEW));
		dbController.insertOrUpdate(new MoneyTransfer(account.getId(), OrderType.TRANSFER, recipient.getId(), "Wrong status", new BigDecimal("59.99"),
				LocalDate.of(2026, Month.AUGUST, 20), MoneyTransferStatus.SENT));
		dbController.insertOrUpdate(new MoneyTransfer(otherAccount.getId(), OrderType.TRANSFER, recipient.getId(), "Wrong account", new BigDecimal("69.99"),
				LocalDate.of(2026, Month.AUGUST, 21), MoneyTransferStatus.NEW));

		List<MoneyTransfer> transfers = dbController.getAllByParentWithFilter(MoneyTransfer.class, account.getId(), MoneyTransferStatus.NEW);

		assertEquals(1, transfers.size());
		assertEquals("Matching transfer", transfers.get(0).getPurpose());
		assertEquals(account.getId(), transfers.get(0).getAccountId());
	}

	@Test
	void requestAndCancelBankOrderDeletion_shouldKeepOrderRetryable() {
		BankAccount account = dbController.insertOrUpdate(TestData.createSampleAccount(null));
		Recipient recipient = dbController
				.insertOrUpdate(new Recipient("Deletion Recipient", "DE55555555555555555555", "TESTDEFFXXX", null, null, "Testbank", Source.MONEYTRANSFER));
		MoneyTransfer inventoryTransfer = createInventoryTransfer(account.getId(), recipient.getId());
		inventoryTransfer.setBankOrderId("scheduled-delete-1");
		inventoryTransfer = dbController.insertOrUpdate(inventoryTransfer);

		MoneyTransfer deletionPending = moneyTransferService.requestBankOrderDeletion(inventoryTransfer);

		assertEquals(MoneyTransferStatus.DELETE_PENDING, deletionPending.getMoneytransferStatus());
		assertEquals(deletionPending.getId(), moneyTransferService.retrieveOpenTransfers().get(0).getId());

		MoneyTransfer restored = moneyTransferService.cancelBankOrderDeletion(deletionPending);

		assertEquals(MoneyTransferStatus.INVENTORY, restored.getMoneytransferStatus());
		assertEquals(0, moneyTransferService.retrieveOpenTransfers().size());
	}

	@Test
	void requestBankOrderDeletion_shouldDiscardPendingEditAndDeleteConfirmedVersion() {
		BankAccount account = dbController.insertOrUpdate(TestData.createSampleAccount(null));
		Recipient recipient = dbController
				.insertOrUpdate(new Recipient("Changed Recipient", "DE66666666666666666666", "TESTDEFFXXX", null, null, "Testbank", Source.MONEYTRANSFER));
		MoneyTransfer inventoryTransfer = createInventoryTransfer(account.getId(), recipient.getId());
		inventoryTransfer.setBankOrderId("scheduled-delete-2");
		inventoryTransfer = dbController.insertOrUpdate(inventoryTransfer);
		MoneyTransferForm changedForm = new MoneyTransferForm(account, OrderType.SCHEDULED_TRANSFER, recipient, new BigDecimal("21.00"),
				"Changed before deletion", LocalDate.of(2026, Month.AUGUST, 15));
		MoneyTransfer changedTransfer = moneyTransferService.saveMoneyTransferToDB(changedForm, inventoryTransfer);

		MoneyTransfer deletionPending = moneyTransferService.requestBankOrderDeletion(changedTransfer);

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, account.getId());
		assertEquals(2, transfers.size());
		assertEquals(inventoryTransfer.getId(), deletionPending.getId());
		assertEquals(MoneyTransferStatus.DELETE_PENDING, deletionPending.getMoneytransferStatus());
		assertEquals("Original purpose", deletionPending.getPurpose());
		assertEquals(MoneyTransferStatus.SUPERSEDED,
				transfers.stream().filter(transfer -> transfer.getId() == changedTransfer.getId()).findFirst().orElseThrow().getMoneytransferStatus());
	}

	private static MoneyTransfer createInventoryTransfer(int accountId, int recipientId) {
		MoneyTransfer moneyTransfer = new MoneyTransfer();
		moneyTransfer.setAccountId(accountId);
		moneyTransfer.setOrderType(OrderType.SCHEDULED_TRANSFER);
		moneyTransfer.setRecipientId(recipientId);
		moneyTransfer.setPurpose("Original purpose");
		moneyTransfer.setAmount(new BigDecimal("10.00"));
		moneyTransfer.setExecutionDate(LocalDate.of(2026, Month.AUGUST, 10));
		moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.INVENTORY);
		return moneyTransfer;
	}
}
