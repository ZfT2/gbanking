package de.zft2.gbanking.file.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.TestData;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.exp.FileExportBean.ExportConstants;
import de.zft2.gbanking.file.exp.FileExportOrdersCSVBean;
import de.zft2.gbanking.messages.Messages;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoneyTransferCsvImportBeanTest {

	private static final String HEADER = "AUFTRAGGEBER_IBAN;NAME;IBAN;BIC;BETRAG;ZWECK;PURPOSECODE";
	private static final String SENDER_IBAN = "DE44500105175407324931";

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
	void importFile_shouldPersistImportedTransferAndPurposeCodeForMatchingSenderIban() throws Exception {
		BankAccount account = insertAccount(SENDER_IBAN, "5407324931");
		Path csvFile = writeCsv(SENDER_IBAN + ";Max Empfänger;DE11100100101234567890;MARKDEF1100;123,45;Rechnung 4711;GDDS");

		MoneyTransferCsvImportBean.ImportResult result = new MoneyTransferCsvImportBean().importFile(csvFile);

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, account.getId());
		assertEquals(1, result.importedCount());
		assertEquals(0, result.skippedDuplicateCount());
		assertEquals(1, transfers.size());
		MoneyTransfer transfer = transfers.get(0);
		assertEquals(OrderType.TRANSFER, transfer.getOrderType());
		assertEquals(MoneyTransferStatus.IMPORTED, transfer.getMoneytransferStatus());
		assertEquals("Rechnung 4711", transfer.getPurpose());
		assertEquals("GDDS", transfer.getPurposeCode());
		assertEquals(0, new BigDecimal("123.45").compareTo(transfer.getAmount()));
		assertEquals("Max Empfänger", transfer.getRecipient().getName());
		assertEquals("DE11100100101234567890", transfer.getRecipient().getIban());
		assertEquals("MARKDEF1100", transfer.getRecipient().getBic());
	}

	@Test
	void importFile_shouldPersistOptionalProtocolData() throws Exception {
		BankAccount account = insertAccount(SENDER_IBAN, "5407324931");
		String header = HEADER + ";" + protocolHeader();
		Path csvFile = writeCsvWithHeader(header, SENDER_IBAN
				+ ";Max Empfaenger;DE11100100101234567890;MARKDEF1100;123,45;Rechnung 4711;GDDS;gesendet;2026-01-02T03:04:05;2026-01-02T03:05:06;OK");

		MoneyTransferCsvImportBean.ImportResult result = new MoneyTransferCsvImportBean().importFile(csvFile);

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, account.getId());
		assertEquals(1, result.importedCount());
		assertEquals(0, result.skippedDuplicateCount());
		assertEquals(1, transfers.size());
		List<MoneyTransferProtocol> protocols = dbController.getAllByParent(MoneyTransferProtocol.class, transfers.get(0).getId());
		assertEquals(1, protocols.size());
		MoneyTransferProtocol protocol = protocols.get(0);
		assertEquals(MoneyTransferStatus.SENT, protocol.getMoneytransferStatus());
		assertEquals(LocalDateTime.of(2026, Month.JANUARY, 2, 3, 4, 5), protocol.getTimeStart());
		assertEquals(LocalDateTime.of(2026, Month.JANUARY, 2, 3, 5, 6), protocol.getTimeFinish());
		assertEquals("OK", protocol.getProtocolText());
	}

	@Test
	void importFile_shouldReadGBankingExportFormatAndAppendMultipleProtocolsToOneTransfer() throws Exception {
		BankAccount account = insertAccount(SENDER_IBAN, "5407324931");
		String header = gbankingExportHeader();
		String baseRow = String.join(";", account.getAccountName(), account.getIban(), account.getNumber(), "", OrderType.TRANSFER.toString(),
				"Rechnung 4711", "GDDS", "E2E-4711", "123,45", "2026-02-03", MoneyTransferStatus.SENT.toString(), "Max Empfaenger",
				"DE11100100101234567890", "MARKDEF1100", "1234567890", "10010010", "Notiz", Source.MONEYTRANSFER.toString());
		Path csvFile = writeCsvWithHeader(header,
				baseRow + ";gesendet;2026-02-03T10:15:00;2026-02-03T10:16:00;Erstes Protokoll",
				baseRow + ";Fehler;2026-02-03T11:15:00;;Zweites Protokoll");

		MoneyTransferCsvImportBean.ImportResult result = new MoneyTransferCsvImportBean().importFile(csvFile);

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, account.getId());
		assertEquals(1, result.importedCount());
		assertEquals(0, result.skippedDuplicateCount());
		assertEquals(1, transfers.size());
		MoneyTransfer transfer = transfers.get(0);
		assertEquals(OrderType.TRANSFER, transfer.getOrderType());
		assertEquals(MoneyTransferStatus.IMPORTED, transfer.getMoneytransferStatus());
		assertEquals("GDDS", transfer.getPurposeCode());
		assertEquals("E2E-4711", transfer.getEndToEndId());
		List<MoneyTransferProtocol> protocols = dbController.getAllByParent(MoneyTransferProtocol.class, transfer.getId());
		assertEquals(2, protocols.size());
		assertTrue(protocols.stream().anyMatch(protocol -> "Erstes Protokoll".equals(protocol.getProtocolText())));
		assertTrue(protocols.stream().anyMatch(protocol -> "Zweites Protokoll".equals(protocol.getProtocolText())));
	}

	@Test
	void importFile_shouldUseSelectedOpenStatus() throws Exception {
		BankAccount account = insertAccount(SENDER_IBAN, "5407324931");
		Path csvFile = writeCsv(SENDER_IBAN + ";Max Empfaenger;DE11100100101234567890;MARKDEF1100;10,25;Rechnung;GDDS");

		new MoneyTransferCsvImportBean(null, MoneyTransferStatus.NEW).importFile(csvFile);

		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, account.getId());
		assertEquals(1, transfers.size());
		assertEquals(MoneyTransferStatus.NEW, transfers.get(0).getMoneytransferStatus());
	}

	@Test
	void exportFileFromDatatbase_shouldWriteOptionalProtocolData() throws Exception {
		BankAccount account = insertAccount(SENDER_IBAN, "5407324931");
		MoneyTransfer transfer = insertTransfer(account);
		insertProtocol(transfer, "Export-Protokoll");
		Path exportFile = tempDir.resolve("orders-with-protocol.csv");

		Locale previousLocale = Messages.getLocale();
		boolean result;
		try {
			Messages.setLocale(Locale.ENGLISH);
			result = new FileExportOrdersCSVBean(null).exportFileFromDatatbase(List.of(account), exportFile.toString());
		} finally {
			Messages.setLocale(previousLocale);
		}

		String csv = Files.readString(exportFile, StandardCharsets.UTF_8);
		assertTrue(result);
		assertTrue(csv.contains(ExportConstants.PROTOCOL_STATUS.toString()));
		assertTrue(csv.contains(ExportConstants.PROTOCOL_TIME_START.toString()));
		assertTrue(csv.contains("Export-Protokoll"));
		assertTrue(csv.contains("SEPA-Überweisung"));
		assertTrue(csv.contains("gesendet"));
	}

	@Test
	void importFile_shouldMatchAccountNumberAgainstIbanSuffixWhenAccountHasNoIban() throws Exception {
		BankAccount account = insertAccount(null, "7324931");
		Path csvFile = writeCsv(SENDER_IBAN + ";Max Empfänger;DE11100100101234567890;MARKDEF1100;10.25;Rechnung;GDDS");

		MoneyTransferCsvImportBean.ImportResult result = new MoneyTransferCsvImportBean().importFile(csvFile);

		assertEquals(1, result.importedCount());
		assertEquals(0, result.skippedDuplicateCount());
		assertEquals(1, dbController.getAllByParent(MoneyTransfer.class, account.getId()).size());
	}

	@Test
	void importFile_shouldSkipDuplicateTransfers() throws Exception {
		BankAccount account = insertAccount(SENDER_IBAN, "5407324931");
		Path csvFile = writeCsv(SENDER_IBAN + ";Max Empfänger;DE11100100101234567890;MARKDEF1100;123,45;Rechnung 4711;GDDS");

		new MoneyTransferCsvImportBean().importFile(csvFile);
		MoneyTransferCsvImportBean.ImportResult secondResult = new MoneyTransferCsvImportBean().importFile(csvFile);

		assertEquals(0, secondResult.importedCount());
		assertEquals(1, secondResult.skippedDuplicateCount());
		assertEquals(1, dbController.getAllByParent(MoneyTransfer.class, account.getId()).size());
	}

	@Test
	void importFileWithContextAccount_shouldAbortWhenSenderIbanDoesNotMatchSelectedAccount() throws Exception {
		BankAccount account = insertAccount("DE12500105170648489890", "0648489890");
		Path csvFile = writeCsv(SENDER_IBAN + ";Max Empfänger;DE11100100101234567890;MARKDEF1100;10,25;Rechnung;GDDS");

		MoneyTransferCsvImportBean moneyTransferCsvImportBean = new MoneyTransferCsvImportBean();
		assertThrows(GBankingException.class, () -> moneyTransferCsvImportBean.importFile(csvFile, account));
		assertTrue(dbController.getAllByParent(MoneyTransfer.class, account.getId()).isEmpty());
	}

	@Test
	void importFile_shouldAbortWithoutPartialImportWhenAnySenderAccountIsMissing() throws Exception {
		BankAccount account = insertAccount(SENDER_IBAN, "5407324931");
		Path csvFile = writeCsv(SENDER_IBAN + ";Max Empfänger;DE11100100101234567890;MARKDEF1100;10,25;Rechnung;GDDS",
				"DE12500105170648489890;Erika Empfänger;DE22100100101234567890;MARKDEF1100;20,50;Zweite Rechnung;");

		MoneyTransferCsvImportBean moneyTransferCsvImportBean = new MoneyTransferCsvImportBean();
		assertThrows(GBankingException.class, () -> moneyTransferCsvImportBean.importFile(csvFile));
		assertTrue(dbController.getAllByParent(MoneyTransfer.class, account.getId()).isEmpty());
	}

	@Test
	void importFile_shouldRollBackEarlierCandidatesWhenLaterTransferInsertFails() throws Exception {
		BankAccount account = insertAccount(SENDER_IBAN, "5407324931");
		Path csvFile = writeCsv(SENDER_IBAN + ";First Recipient;DE11100100101234567890;MARKDEF1100;10,25;First transfer;GDDS",
				SENDER_IBAN + ";Second Recipient;DE22100100101234567890;MARKDEF1100;20,50;Fail transfer;GDDS");
		executeSql("CREATE TRIGGER fail_second_transfer BEFORE INSERT ON moneytransfer "
				+ "WHEN NEW.purpose = 'Fail transfer' BEGIN SELECT RAISE(FAIL, 'forced transfer failure'); END");

		try {
			MoneyTransferCsvImportBean importBean = new MoneyTransferCsvImportBean();
			assertThrows(GBankingException.class, () -> importBean.importFile(csvFile));
		} finally {
			executeSql("DROP TRIGGER IF EXISTS fail_second_transfer");
		}

		assertTrue(dbController.getAllByParent(MoneyTransfer.class, account.getId()).isEmpty());
		assertTrue(dbController.getAll(Recipient.class).isEmpty());
	}

	@Test
	void importFile_shouldRollBackRecipientAndTransferWhenProtocolInsertFails() throws Exception {
		BankAccount account = insertAccount(SENDER_IBAN, "5407324931");
		Path csvFile = writeCsvWithHeader(HEADER + ";" + protocolHeader(), SENDER_IBAN
				+ ";Protocol Recipient;DE11100100101234567890;MARKDEF1100;10,25;Protocol transfer;GDDS;gesendet;"
				+ "2026-01-02T03:04:05;2026-01-02T03:05:06;Fail protocol");
		executeSql("CREATE TRIGGER fail_protocol BEFORE INSERT ON moneytransferProtocol "
				+ "WHEN NEW.protocolText = 'Fail protocol' BEGIN SELECT RAISE(FAIL, 'forced protocol failure'); END");

		try {
			MoneyTransferCsvImportBean importBean = new MoneyTransferCsvImportBean();
			assertThrows(GBankingException.class, () -> importBean.importFile(csvFile));
		} finally {
			executeSql("DROP TRIGGER IF EXISTS fail_protocol");
		}

		assertTrue(dbController.getAllByParent(MoneyTransfer.class, account.getId()).isEmpty());
		assertTrue(dbController.getAll(MoneyTransferProtocol.class).isEmpty());
		assertTrue(dbController.getAll(Recipient.class).isEmpty());
	}

	private BankAccount insertAccount(String iban, String accountNumber) {
		BankAccount account = TestData.createSampleAccount(null);
		account.setIban(iban);
		account.setNumber(accountNumber);
		return dbController.insertOrUpdate(account);
	}

	private MoneyTransfer insertTransfer(BankAccount account) {
		Recipient recipient = new Recipient("Export Recipient", "DE11100100101234567890", "MARKDEF1100", null, null, null, Source.MONEYTRANSFER);
		recipient = dbController.insertOrUpdate(recipient);

		MoneyTransfer transfer = new MoneyTransfer();
		transfer.setAccountId(account.getId());
		transfer.setOrderType(OrderType.TRANSFER);
		transfer.setRecipientId(recipient.getId());
		transfer.setRecipient(recipient);
		transfer.setPurpose("Export purpose");
		transfer.setPurposeCode("GDDS");
		transfer.setEndToEndId("E2E-EXPORT");
		transfer.setAmount(new BigDecimal("42.50"));
		transfer.setExecutionDate(LocalDateTime.of(2026, Month.JANUARY, 2, 0, 0).toLocalDate());
		transfer.setMoneytransferStatus(MoneyTransferStatus.SENT);
		return dbController.insertOrUpdate(transfer);
	}

	private void insertProtocol(MoneyTransfer transfer, String protocolText) {
		MoneyTransferProtocol protocol = new MoneyTransferProtocol();
		protocol.setMoneyTransferId(transfer.getId());
		protocol.setMoneytransferStatus(MoneyTransferStatus.SENT);
		protocol.setTimeStart(LocalDateTime.of(2026, Month.JANUARY, 2, 3, 4, 5));
		protocol.setTimeFinish(LocalDateTime.of(2026, Month.JANUARY, 2, 3, 5, 6));
		protocol.setProtocolText(protocolText);
		dbController.insertOrUpdate(protocol);
	}

	private Path writeCsv(String... rows) throws Exception {
		return writeCsvWithHeader(HEADER, rows);
	}

	private Path writeCsvWithHeader(String header, String... rows) throws Exception {
		Path csvFile = Files.createTempFile(tempDir, "moneytransfer_import_", ".csv");
		Files.writeString(csvFile, header + System.lineSeparator() + String.join(System.lineSeparator(), rows), StandardCharsets.UTF_8);
		return csvFile;
	}

	private void executeSql(String sql) throws Exception {
		try (Statement statement = DBController.getConnection().createStatement()) {
			statement.executeUpdate(sql);
		}
	}

	private String protocolHeader() {
		return String.join(";", ExportConstants.PROTOCOL_STATUS.toString(), ExportConstants.PROTOCOL_TIME_START.toString(),
				ExportConstants.PROTOCOL_TIME_FINISH.toString(), ExportConstants.PROTOCOL_TEXT.toString());
	}

	private String gbankingExportHeader() {
		return String.join(";", ExportConstants.ACCOUNT.toString(), ExportConstants.IBAN.toString(), ExportConstants.ACCOUNT_NUMBER.toString(),
				ExportConstants.SOURCE_MONEYTRANSFER.toString(), ExportConstants.TYP.toString(), ExportConstants.PURPOSE.toString(),
				ExportConstants.PURPOSE_CODE.toString(), ExportConstants.END_TO_END_ID.toString(), ExportConstants.AMOUNT.toString(),
				ExportConstants.EXECUTION_DATE.toString(),
				ExportConstants.STATE.toString(), ExportConstants.RECIPIENT_NAME.toString(), ExportConstants.RECIPIENT_IBAN.toString(),
				ExportConstants.RECIPIENT_BIC.toString(), ExportConstants.RECIPIENT_ACCOUNT_NUMBER.toString(), ExportConstants.BLZ.toString(),
				ExportConstants.NOTICE.toString(), ExportConstants.RECIPIENT_SOURCE.toString(), protocolHeader());
	}
}
