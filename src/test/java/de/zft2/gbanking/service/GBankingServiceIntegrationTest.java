package de.zft2.gbanking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.dto.MoneyTransferForm;
import de.zft2.gbanking.service.account.AccountTransactionService;
import de.zft2.gbanking.service.moneytransfer.MoneyTransferService;
import de.zft2.gbanking.testdata.TestDataFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GBankingServiceIntegrationTest {

	private DBController dbController;
	private Path tempDir;

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
	void saveMoneyTransferToDB_shouldCreateRecipientAndNewOpenTransfer() {
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		Recipient recipient = createRecipient("John Doe", "DE12345678901234567890");
		MoneyTransferForm form = createTransferForm(account, recipient, "100.00", "Invoice 2026");

		MoneyTransfer savedTransfer = moneyTransferService.saveMoneyTransferToDB(form);

		assertNotNull(savedTransfer);
		assertTrue(savedTransfer.getId() > 0);
		assertEquals(account.getId(), savedTransfer.getAccountId());
		assertEquals(OrderType.TRANSFER, savedTransfer.getOrderType());
		assertEquals(MoneyTransferStatus.NEW, savedTransfer.getMoneytransferStatus());
		assertAmountEquals("100.00", savedTransfer.getAmount());
		assertEquals("Invoice 2026", savedTransfer.getPurpose());
		assertEquals(Source.MONEYTRANSFER, savedTransfer.getRecipient().getSource());

		List<Recipient> recipients = dbController.getAll(Recipient.class);
		assertEquals(1, recipients.size());
		assertEquals("John Doe", recipients.get(0).getName());

		List<MoneyTransfer> openTransfers = moneyTransferService.retrieveOpenTransfers();
		assertEquals(1, openTransfers.size());
		assertEquals(savedTransfer.getId(), openTransfers.get(0).getId());
	}

	@Test
	void saveMoneyTransferToDB_shouldRollBackRecipientWhenTransferInsertFails() {
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		Recipient recipient = createRecipient("Rollback Recipient", "DE12345678901234567890");
		MoneyTransferForm invalidForm = new MoneyTransferForm(account, OrderType.TRANSFER, recipient, new BigDecimal("-1.00"), "Invalid transfer");

		assertThrows(GBankingException.class, () -> moneyTransferService.saveMoneyTransferToDB(invalidForm));

		assertTrue(dbController.getAll(Recipient.class).isEmpty());
		assertTrue(dbController.getAllByParent(MoneyTransfer.class, account.getId()).isEmpty());
	}

	@Test
	void saveMoneyTransferToDB_shouldMarkInventoryOrderAsChangedWhenEdited() {
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		Recipient recipient = createRecipient("Standing Order Recipient", "DE22345678901234567890");
		MoneyTransferForm form = createTransferForm(account, recipient, "25.00", "Initial purpose");
		MoneyTransfer inventoryTransfer = moneyTransferService.saveMoneyTransferToDB(form);
		inventoryTransfer.setOrderType(OrderType.SCHEDULED_TRANSFER);
		inventoryTransfer.setBankOrderId("scheduled-local-edit-1");
		inventoryTransfer.setMoneytransferStatus(MoneyTransferStatus.INVENTORY);
		inventoryTransfer = dbController.insertOrUpdate(inventoryTransfer);
		MoneyTransferForm changedForm = createTransferForm(account, recipient, "30.50", "Changed purpose");
		changedForm.setOrderType(OrderType.SCHEDULED_TRANSFER);

		MoneyTransfer changedTransfer = moneyTransferService.saveMoneyTransferToDB(changedForm, inventoryTransfer);

		assertNotEquals(inventoryTransfer.getId(), changedTransfer.getId());
		assertEquals(MoneyTransferStatus.CHANGED, changedTransfer.getMoneytransferStatus());
		assertAmountEquals("30.50", changedTransfer.getAmount());
		assertEquals("Changed purpose", changedTransfer.getPurpose());
		assertEquals(inventoryTransfer.getId(), changedTransfer.getHistoryorderId());
		assertEquals("scheduled-local-edit-1", changedTransfer.getBankOrderId());
		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, account.getId());
		assertEquals(2, transfers.size());
		MoneyTransfer confirmedBankTransfer = findTransferById(transfers, inventoryTransfer.getId());
		assertEquals(MoneyTransferStatus.INVENTORY, confirmedBankTransfer.getMoneytransferStatus());
		assertAmountEquals("25.00", confirmedBankTransfer.getAmount());
		assertEquals("Initial purpose", confirmedBankTransfer.getPurpose());
	}

	@Test
	void saveMoneyTransferToDB_shouldRollBackHistoryVersionWhenSuccessorInsertFails() {
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		Recipient recipient = dbController.insertOrUpdate(createRecipient("Rollback History Recipient", "DE27345678901234567890"));
		MoneyTransfer inventoryTransfer = insertTransfer(account, recipient, MoneyTransferStatus.INVENTORY);
		inventoryTransfer.setOrderType(OrderType.SCHEDULED_TRANSFER);
		inventoryTransfer.setBankOrderId("scheduled-rollback-1");
		inventoryTransfer = dbController.insertOrUpdate(inventoryTransfer);
		MoneyTransferForm invalidForm = createTransferForm(account, recipient, "-1.00", "Invalid successor");
		invalidForm.setOrderType(OrderType.SCHEDULED_TRANSFER);

		MoneyTransfer persistedInventoryTransfer = inventoryTransfer;
		assertThrows(GBankingException.class, () -> moneyTransferService.saveMoneyTransferToDB(invalidForm, persistedInventoryTransfer));

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, account.getId());
		assertEquals(1, transfers.size());
		assertEquals(inventoryTransfer.getId(), transfers.get(0).getId());
		assertEquals(MoneyTransferStatus.INVENTORY, transfers.get(0).getMoneytransferStatus());
		assertEquals("INVENTORY transfer", transfers.get(0).getPurpose());
	}

	@Test
	void retrieveOpenTransfers_shouldReturnOnlyExecutableStatuses() {
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		Recipient recipient = dbController.insertOrUpdate(createRecipient("Recipient", "DE32345678901234567890"));
		MoneyTransfer newTransfer = insertTransfer(account, recipient, MoneyTransferStatus.NEW);
		MoneyTransfer changedTransfer = insertTransfer(account, recipient, MoneyTransferStatus.CHANGED);
		MoneyTransfer errorTransfer = insertTransfer(account, recipient, MoneyTransferStatus.ERROR);
		MoneyTransfer sentTransfer = insertTransfer(account, recipient, MoneyTransferStatus.SENT);
		MoneyTransfer inventoryTransfer = insertTransfer(account, recipient, MoneyTransferStatus.INVENTORY);
		MoneyTransfer deletionPendingTransfer = insertTransfer(account, recipient, MoneyTransferStatus.DELETE_PENDING);

		List<MoneyTransfer> openTransfers = moneyTransferService.retrieveOpenTransfers();

		assertEquals(4, openTransfers.size());
		assertTrue(containsTransfer(openTransfers, newTransfer));
		assertTrue(containsTransfer(openTransfers, changedTransfer));
		assertTrue(containsTransfer(openTransfers, errorTransfer));
		assertTrue(containsTransfer(openTransfers, deletionPendingTransfer));
		assertFalse(containsTransfer(openTransfers, sentTransfer));
		assertFalse(containsTransfer(openTransfers, inventoryTransfer));
	}

	@Test
	void retrieveAccountTransactions_shouldReturnFalseAndClearPinWithoutBankAccess() {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setBankAccessId(0);
		char[] pin = "1234".toCharArray();

		AccountTransactionService accountTransactionService = ServiceRegistry.getService(AccountTransactionService.class);
		boolean result = accountTransactionService.retrieveAccountTransactions(account, pin);

		assertFalse(result);
		assertSecretCleared(pin);
	}

	@Test
	void executeTransfer_shouldPersistErrorAndClearPinWhenOrderTypeIsNotSupported() {
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		Recipient recipient = createRecipient("Unsupported Transfer Recipient", "DE42345678901234567890");
		MoneyTransferForm form = createTransferForm(account, recipient, "44.99", "Unsupported transfer");
		MoneyTransfer moneyTransfer = moneyTransferService.saveMoneyTransferToDB(form);
		char[] pin = "9876".toCharArray();

		boolean result = moneyTransferService.executeTransfer(moneyTransfer, account, pin);

		MoneyTransfer persistedTransfer = findTransferById(dbController.getAllByParent(MoneyTransfer.class, account.getId()), moneyTransfer.getId());
		assertFalse(result);
		assertNotNull(persistedTransfer);
		assertEquals(MoneyTransferStatus.ERROR, moneyTransfer.getMoneytransferStatus());
		assertEquals(MoneyTransferStatus.ERROR, persistedTransfer.getMoneytransferStatus());
		assertSecretCleared(pin);
		assertTrue(dbController.getAllByParent(MoneyTransferProtocol.class, moneyTransfer.getId()).isEmpty());
	}

	private static MoneyTransferForm createTransferForm(BankAccount account, Recipient recipient, String amount, String purpose) {
		return new MoneyTransferForm(account, OrderType.TRANSFER, recipient, new BigDecimal(amount), purpose,
				LocalDate.of(2026, Month.JULY, 9));
	}

	private static Recipient createRecipient(String name, String iban) {
		Recipient recipient = new Recipient();
		recipient.setName(name);
		recipient.setIban(iban);
		recipient.setBic("TESTDEFFXXX");
		recipient.setBank("Testbank");
		recipient.setSource(Source.MANUELL);
		return recipient;
	}

	private MoneyTransfer insertTransfer(BankAccount account, Recipient recipient, MoneyTransferStatus status) {
		MoneyTransfer moneyTransfer = new MoneyTransfer();
		moneyTransfer.setAccountId(account.getId());
		moneyTransfer.setOrderType(OrderType.TRANSFER);
		moneyTransfer.setRecipientId(recipient.getId());
		moneyTransfer.setRecipient(recipient);
		moneyTransfer.setPurpose(status.name() + " transfer");
		moneyTransfer.setAmount(new BigDecimal("12.34"));
		moneyTransfer.setCurrency("EUR");
		moneyTransfer.setExecutionDate(LocalDate.of(2026, Month.JULY, 9));
		moneyTransfer.setMoneytransferStatus(status);
		return dbController.insertOrUpdate(moneyTransfer);
	}

	private static boolean containsTransfer(List<MoneyTransfer> transfers, MoneyTransfer expectedTransfer) {
		for (MoneyTransfer transfer : transfers) {
			if (transfer.getId() == expectedTransfer.getId()) {
				return true;
			}
		}
		return false;
	}

	private static MoneyTransfer findTransferById(List<MoneyTransfer> transfers, int transferId) {
		for (MoneyTransfer transfer : transfers) {
			if (transfer.getId() == transferId) {
				return transfer;
			}
		}
		return null;
	}

	private static void assertAmountEquals(String expected, BigDecimal actual) {
		assertEquals(0, new BigDecimal(expected).compareTo(actual));
	}

	private static void assertSecretCleared(char[] secret) {
		for (char value : secret) {
			assertEquals('\0', value);
		}
	}
}
