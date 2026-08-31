package de.zft2.gbanking.file.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.testdata.TestDataFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoneyTransferSepaImportBeanTest {

	private static final String MONEY_PLEX_ACCOUNT_IBAN = "DE30120300000018884074";
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
	void importFile_shouldReadMoneyplexPain00100203ExampleIntoArchive() throws Exception {
		BankAccount account = insertAccount(MONEY_PLEX_ACCOUNT_IBAN);

		MoneyTransferImportBean.ImportResult result = new MoneyTransferSepaImportBean()
				.importFile(Path.of("src/test/resources/import/SEPA_MP_Example.xml"));

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, account.getId());
		assertEquals(1, result.importedCount());
		assertEquals(1, transfers.size());
		MoneyTransfer transfer = transfers.get(0);
		assertEquals(MoneyTransferStatus.IMPORTED, transfer.getMoneytransferStatus());
		assertEquals(OrderType.TRANSFER, transfer.getOrderType());
		assertEquals(0, new BigDecimal("0.01").compareTo(transfer.getAmount()));
		assertEquals("Bsp Vwz-4349", transfer.getPurpose());
		assertEquals("NOTPROVIDED", transfer.getEndToEndId());
		assertEquals("Telefonica Germany", transfer.getRecipient().getName());
		assertEquals(RECIPIENT_IBAN, transfer.getRecipient().getIban());
		assertEquals("HYVEDEMMXXX", transfer.getRecipient().getBic());
	}

	@Test
	void importFile_shouldReadPain00100109AsOpenScheduledTransfer() throws Exception {
		BankAccount account = insertAccount(MONEY_PLEX_ACCOUNT_IBAN);
		LocalDate executionDate = LocalDate.now().plusDays(10);
		Path importFile = writePain00100109(executionDate, null, "GDDS", "E2E-SCHEDULED");

		new MoneyTransferSepaImportBean(null, MoneyTransferStatus.NEW).importFile(importFile);

		MoneyTransfer transfer = dbController.getAllByParent(MoneyTransfer.class, account.getId()).get(0);
		assertEquals(MoneyTransferStatus.NEW, transfer.getMoneytransferStatus());
		assertEquals(OrderType.SCHEDULED_TRANSFER, transfer.getOrderType());
		assertEquals(executionDate, transfer.getExecutionDate());
		assertEquals("GDDS", transfer.getPurposeCode());
		assertEquals("E2E-SCHEDULED", transfer.getEndToEndId());
	}

	@Test
	void importFile_shouldRecognizeInstantPayment() throws Exception {
		BankAccount account = insertAccount(MONEY_PLEX_ACCOUNT_IBAN);
		Path importFile = writePain00100109(LocalDate.now(), "INST", null, "E2E-INSTANT");

		new MoneyTransferSepaImportBean(null, MoneyTransferStatus.NEW).importFile(importFile);

		MoneyTransfer transfer = dbController.getAllByParent(MoneyTransfer.class, account.getId()).get(0);
		assertEquals(OrderType.REALTIME_TRANSFER, transfer.getOrderType());
	}

	@Test
	void importFile_shouldSkipDuplicateEndToEndId() throws Exception {
		BankAccount account = insertAccount(MONEY_PLEX_ACCOUNT_IBAN);
		Path importFile = writePain00100109(LocalDate.now(), null, null, "E2E-DUPLICATE");

		new MoneyTransferSepaImportBean().importFile(importFile);
		MoneyTransferImportBean.ImportResult secondResult = new MoneyTransferSepaImportBean().importFile(importFile);

		assertEquals(0, secondResult.importedCount());
		assertEquals(1, secondResult.skippedDuplicateCount());
		assertEquals(1, dbController.getAllByParent(MoneyTransfer.class, account.getId()).size());
	}

	private BankAccount insertAccount(String iban) {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setIban(iban);
		return dbController.insertOrUpdate(account);
	}

	private Path writePain00100109(LocalDate executionDate, String localInstrument, String purposeCode, String endToEndId)
			throws Exception {
		String paymentType = localInstrument == null ? "" : "<PmtTpInf><LclInstrm><Cd>" + localInstrument + "</Cd></LclInstrm></PmtTpInf>";
		String purpose = purposeCode == null ? "" : "<Purp><Cd>" + purposeCode + "</Cd></Purp>";
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
				    <CstmrCdtTrfInitn>
				        <PmtInf>
				            <PmtMtd>TRF</PmtMtd>
				            %s
				            <ReqdExctnDt><Dt>%s</Dt></ReqdExctnDt>
				            <DbtrAcct><Id><IBAN>%s</IBAN></Id></DbtrAcct>
				            <CdtTrfTxInf>
				                <PmtId><EndToEndId>%s</EndToEndId></PmtId>
				                <Amt><InstdAmt Ccy="EUR">12.34</InstdAmt></Amt>
				                <Cdtr><Nm>Test Recipient</Nm></Cdtr>
				                <CdtrAcct><Id><IBAN>%s</IBAN></Id></CdtrAcct>
				                %s
				                <RmtInf><Ustrd>Test purpose</Ustrd></RmtInf>
				            </CdtTrfTxInf>
				        </PmtInf>
				    </CstmrCdtTrfInitn>
				</Document>
				""".formatted(paymentType, executionDate, MONEY_PLEX_ACCOUNT_IBAN, endToEndId, RECIPIENT_IBAN, purpose);
		Path importFile = Files.createTempFile(tempDir, "pain001_", ".xml");
		Files.writeString(importFile, xml, StandardCharsets.UTF_8);
		return importFile;
	}
}
