package de.zft2.gbanking.file.imp.institute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InstituteFileImportAdditionalIntegrationTest extends BaseInstituteFileImportTest {

	private static final String HEADER = "BLZ;Institutsname;Ort;Kurzbezeichnung;Prüfziffermethode;BIC;PLZ;Löschmarker;"
			+ "Nachfolge-BLZ;IBAN-Regel;IBAN-Regel-Version";

	private DBController dbController;
	private Path tempDir;
	private Path basePath;

	@BeforeAll
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		dbController = DBController.getInstance(tempDir.toString());
		basePath = tempDir.resolve("source");
		Files.createDirectories(basePath.resolve("import/archive"));
	}

	@BeforeEach
	void clearDatabaseAndImportFiles() throws Exception {
		DBControllerTestUtil.clearAllTables(DBController.getConnection());
		Files.deleteIfExists(basePath.resolve("import").resolve(InstituteFileImportAdditional.DEFAULT_FILENAME));
		Files.deleteIfExists(basePath.resolve("import/archive").resolve(InstituteFileImportAdditional.DEFAULT_FILENAME));
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void importsShortRowsAndUpdatesAndArchivesAdditionalInstitutes() throws Exception {
		writeImportFile("10000000;Müller Bank;Berlin;Müller;00;MULLDEBBXXX;10115;0;;901;1",
				"20000000;Historische Bank;Köln;Historisch;09");
		runImport();

		List<Institute> initial = dbController.getAll(Institute.class);
		assertEquals(2, initial.size());
		assertEquals(2, countAdditionalDetails());
		Institute initialMuller = findByBlz(initial, "10000000");
		Institute historic = findByBlz(initial, "20000000");
		assertEquals("Müller", initialMuller.getAdditionalBankNameShort());
		assertEquals("901", initialMuller.getAdditionalIbanRule());
		assertEquals("Köln", historic.getPlace());
		assertNull(historic.getBic());
		assertNull(historic.getAdditionalPostcode());

		writeImportFile("10000000;Müller Bank;Berlin;Müller aktuell;00;NEUEDEBBXXX;10115;0;;902;2");
		runImport();

		List<Institute> updated = dbController.getAll(Institute.class);
		assertEquals(2, updated.size());
		assertEquals(2, countAdditionalDetails());
		Institute updatedMuller = findByBlz(updated, "10000000");
		assertEquals(initialMuller.getId(), updatedMuller.getId());
		assertEquals("NEUEDEBBXXX", updatedMuller.getBic());
		assertEquals("Müller aktuell", updatedMuller.getAdditionalBankNameShort());
		assertEquals("902", updatedMuller.getAdditionalIbanRule());
		assertEquals("2", updatedMuller.getAdditionalIbanRuleVersion());
		assertEquals(InstituteStatus.ARCHIVED, findByBlz(updated, "20000000").getStateType());
		assertEquals(List.of(InstituteFileImportAdditional.DEFAULT_FILENAME, InstituteFileImportAdditional.DEFAULT_FILENAME),
				selectImportFileNames());
		assertTrue(Files.exists(basePath.resolve("import/archive").resolve(InstituteFileImportAdditional.DEFAULT_FILENAME)));
	}

	private void runImport() throws Exception {
		InstituteFileImport importer = InstituteFileImport.getInstance(InstituteFileImportAdditional.class, basePath.toString(),
				InstituteFileImportAdditional.DEFAULT_FILENAME);
		importer.runImport();
	}

	private void writeImportFile(String... rows) throws Exception {
		String content = HEADER + "\r\n" + String.join("\r\n", rows) + "\r\n";
		Files.writeString(basePath.resolve("import").resolve(InstituteFileImportAdditional.DEFAULT_FILENAME), content,
				StandardCharsets.ISO_8859_1);
	}

	private static Institute findByBlz(List<Institute> institutes, String blz) {
		return institutes.stream().filter(institute -> blz.equals(institute.getBlz())).findFirst().orElseThrow();
	}

	private static int countAdditionalDetails() throws Exception {
		try (var statement = DBController.getConnection().createStatement();
				var resultSet = statement.executeQuery("SELECT COUNT(*) FROM institute_db.instituteAdditional")) {
			assertTrue(resultSet.next());
			return resultSet.getInt(1);
		}
	}
}
