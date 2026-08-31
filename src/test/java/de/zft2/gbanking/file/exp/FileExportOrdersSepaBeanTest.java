package de.zft2.gbanking.file.exp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import de.zft2.gbanking.exception.ExportException;
import de.zft2.gbanking.testdata.TestDataFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileExportOrdersSepaBeanTest {

	private static final String ACCOUNT_IBAN = "DE30120300000018884074";
	private static final String RECIPIENT_IBAN = "DE73700202700005716977";

	private DBController dbController;
	private Path tempDir;

	@BeforeAll
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
	void exportFile_shouldWritePain00100109AndSkipUnsupportedOrderTypes() throws Exception {
		BankAccount account = insertAccount();
		insertTransfer(account, OrderType.TRANSFER, LocalDate.now(), "E2E-EXPORT", "GDDS", "Invoice 4711");
		insertTransfer(account, OrderType.STANDING_ORDER, LocalDate.now(), "E2E-STANDING", null, "Standing order");
		Path exportFile = tempDir.resolve("orders.xml");

		boolean exported = new FileExportOrdersSepaBean(null).exportFileFromDatatbase(List.of(account), exportFile.toString());

		Document document = readXml(exportFile);
		assertTrue(exported);
		assertEquals(FileExportOrdersSepaBean.NAMESPACE, document.getDocumentElement().getNamespaceURI());
		assertEquals("1", text(document, "NbOfTxs"));
		assertEquals(1, elements(document, "CdtTrfTxInf").getLength());
		assertEquals("E2E-EXPORT", text(document, "EndToEndId"));
		assertEquals("GDDS", text(document, "Purp"));
		assertEquals("Invoice 4711", text(document, "Ustrd"));
	}

	@Test
	void exportFile_shouldCreateSeparateInstantPaymentGroup() throws Exception {
		BankAccount account = insertAccount();
		LocalDate executionDate = LocalDate.now().plusDays(5);
		insertTransfer(account, OrderType.SCHEDULED_TRANSFER, executionDate, "E2E-SCHEDULED", null, "Scheduled");
		insertTransfer(account, OrderType.REALTIME_TRANSFER, executionDate, "E2E-INSTANT", null, "Instant");
		Path exportFile = tempDir.resolve("grouped-orders.xml");

		new FileExportOrdersSepaBean(null).exportFileFromDatatbase(List.of(account), exportFile.toString());

		Document document = readXml(exportFile);
		assertEquals(2, elements(document, "PmtInf").getLength());
		assertEquals(2, elements(document, "ReqdExctnDt").getLength());
		assertEquals(1, elements(document, "LclInstrm").getLength());
		assertEquals("INST", text(document, "LclInstrm"));
	}

	@Test
	void exportFile_shouldRejectSelectionWithoutSupportedOrders() {
		BankAccount account = insertAccount();
		insertTransfer(account, OrderType.FOREIGN_TRANSFER, LocalDate.now(), "E2E-FOREIGN", null, "Foreign");
		Path exportFile = tempDir.resolve("unsupported-orders.xml");

		assertThrows(ExportException.class,
				() -> new FileExportOrdersSepaBean(null).exportFileFromDatatbase(List.of(account), exportFile.toString()));
	}

	private BankAccount insertAccount() {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setIban(ACCOUNT_IBAN);
		account.setBic("BYLADEM1001");
		account.setOwnerName("Max Mustermann");
		return dbController.insertOrUpdate(account);
	}

	private void insertTransfer(BankAccount account, OrderType orderType, LocalDate executionDate, String endToEndId, String purposeCode,
			String purpose) {
		Recipient recipient = dbController.resolveRecipient(
				new Recipient("Test Recipient", RECIPIENT_IBAN, "HYVEDEMMXXX", null, null, null, Source.MONEYTRANSFER));
		MoneyTransfer transfer = new MoneyTransfer();
		transfer.setAccountId(account.getId());
		transfer.setOrderType(orderType);
		transfer.setRecipientId(recipient.getId());
		transfer.setRecipient(recipient);
		transfer.setPurpose(purpose);
		transfer.setPurposeCode(purposeCode);
		transfer.setEndToEndId(endToEndId);
		transfer.setAmount(new BigDecimal("12.34"));
		transfer.setExecutionDate(executionDate);
		if (orderType == OrderType.STANDING_ORDER) {
			transfer.setExecutionDay(executionDate.getDayOfMonth());
			transfer.setStandingorderMode(StandingorderMode.MONTHLY);
		}
		transfer.setMoneytransferStatus(MoneyTransferStatus.NEW);
		dbController.insertOrUpdate(transfer);
	}

	private Document readXml(Path path) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(path.toFile());
	}

	private NodeList elements(Document document, String localName) {
		return document.getElementsByTagNameNS(FileExportOrdersSepaBean.NAMESPACE, localName);
	}

	private String text(Document document, String localName) {
		return elements(document, localName).item(0).getTextContent().trim();
	}
}
