package de.gbanking.fileimport.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.w3c.dom.Document;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import de.gbanking.GBankingBean;
import de.gbanking.db.DBController;
import de.gbanking.db.DBControllerTestUtil;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.enu.AccountType;
import de.gbanking.exception.GBankingException;
import de.gbanking.fileexport.fx.FileExportBean;
import de.gbanking.fileexport.fx.FileExportXMLBean;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileImportIntegrationTest {

	private FileImportBean fileImportBean;
	private FileExportBean fileExportBean;
	private GBankingBean gBankingBean;
	private File importFile;
	private File exportFile;

	private DBController dbController;
	private Path tempDir;

	@BeforeAll
	void setupDatabase() throws Exception {

		// Create fresh DBControllerForTest instance
		tempDir = Files.createTempDirectory("gb_test_");
		dbController = DBController.getInstance(tempDir.toString());

		gBankingBean = new GBankingBean();
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

	@BeforeEach
	void setUp() throws Exception {
		gBankingBean = new GBankingBean();
		fileImportBean = new FileImportBean(null);
		fileExportBean = new FileExportXMLBean(null);

		// Load test XML from resources
		URL resource = getClass().getClassLoader().getResource("dummy_import.xml");
		assertNotNull(resource, "Test XML file dummy_import.xml must be in src/test/resources");
		importFile = new File(resource.toURI());

		// Create temporary export file
		exportFile = File.createTempFile("export_test_", ".xml");
		exportFile.deleteOnExit();
	}

	// ------------------------------------------------------------------------
	// TEST 1: Roundtrip Import → Export → Validate XML
	// ------------------------------------------------------------------------
	@Test
	void testImportExport_Roundtrip_ContentValidation() throws Exception {
		boolean importResult = fileImportBean.importFile(importFile.getAbsolutePath());
		assertTrue(importResult, "Import should succeed for valid XML");

		// Create a dummy account list for export
		BankAccount dummyAccount = new BankAccount();
		dummyAccount.setId(1);
		dummyAccount.setAccountName("Testkonto");
		dummyAccount.setAccountType(AccountType.CURRENT_ACCOUNT);
		dummyAccount.setIban("DE56600160020008290050");
		dummyAccount.setBic("JTBPDEFFXXX");
		dummyAccount.setNumber("8290050");
		dummyAccount.setBlz("50015001");
		dummyAccount.setCurrency("EUR");
		dummyAccount.setBalance(BigDecimal.valueOf(0.46));

		boolean exportResult = fileExportBean.exportFileFromDatatbase(List.of(dummyAccount), exportFile.getAbsolutePath()); // .exportFile(List.of(dummyAccount),
																															// exportFile.getAbsolutePath(),
																															// FileType.XML);
		assertTrue(exportResult, "Export should succeed");

		assertTrue(exportFile.exists() && exportFile.length() > 0, "Exported file should exist and not be empty");

		// Read both files
		String originalXml = Files.readString(importFile.toPath());
		String exportedXml = Files.readString(exportFile.toPath());

		// === Structural comparison ===
		Diff diff = DiffBuilder.compare(originalXml).withTest(exportedXml).ignoreComments().ignoreWhitespace().checkForSimilar().build();

		assertFalse(diff.hasDifferences(), () -> "Exported XML should be structurally similar to imported XML, differences: " + diff.toString());

		// === Content validation ===
		assertTrue(exportedXml.contains("Max Mustermann"), "Recipient name should appear in export");
		assertTrue(exportedXml.contains("DE12345678901234567890"), "IBAN should appear in export");
		assertTrue(exportedXml.contains(">1234567890"), "Account number should appear in export");
		assertTrue(exportedXml.contains("Testbuchung"), "Purpose should appear in export");
		assertTrue(exportedXml.contains("123,45"), "Amount should appear in export");
		assertTrue(exportedXml.contains("EUR"), "Currency should appear in export");
		assertTrue(exportedXml.contains("Sonstiges"), "Category should appear in export");
	}

	// ------------------------------------------------------------------------
	// TEST 2: Missing file should fail gracefully
	// ------------------------------------------------------------------------
	@Test
	void testImportFile_FileNotFound() {
		GBankingException exception = assertThrows(GBankingException.class, () -> fileImportBean.importFile("nonexistent.xml"));

		assertEquals("File not found: nonexistent.xml: ", exception.getMessage());
	}

	// ------------------------------------------------------------------------
	// TEST 3: Export invalid path should fail gracefully
	// ------------------------------------------------------------------------
	@Test
	void testExportFile_InvalidPath() {
		boolean result = fileExportBean.exportFileFromDatatbase(List.of(), "/invalid_path/export.xml"); // .exportFile(List.of(), "/invalid_path/export.xml",
																										// FileType.XML);
		assertFalse(result, "Export should fail for invalid output path");
	}

	// ------------------------------------------------------------------------
	// TEST 4: Parse XML structure manually (optional sanity check)
	// ------------------------------------------------------------------------
	@Test
	void testImportFile_ParsesXMLCorrectly() throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.parse(importFile);

		String accountName = doc.getElementsByTagName("BEZEICHNUNG").item(0).getTextContent();
		assertEquals("Testkonto", accountName);
	}
}
