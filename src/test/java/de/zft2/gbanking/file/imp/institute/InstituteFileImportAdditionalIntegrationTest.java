package de.zft2.gbanking.file.imp.institute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;
import de.zft2.gbanking.db.dao.enu.InstituteValidityDateType;

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

	@Test
	void importsUnmodifiedHistoricalSnapshotsAndBuildsContinuousValidity() throws Exception {
		writeHistoricalFile("blz0007.txt", "10000000;Wiederkehrende Bank;Berlin;Wiederkehrend;09");
		writeHistoricalFile("blz0203.txt", "********;13;04.03.2002;14;03.06.2002",
				"20000000;Alte Bank;Köln;Alte Bank;10");
		writeHistoricalFile("blz0206.txt", "********;14;03.06.2002;15;02.09.2002",
				"10000000;Wiederkehrende Bank;Berlin;Wiederkehrend;09",
				"20000000;Neue Bank;Köln;Neue Bank;10");

		runImport();

		List<Institute> institutes = dbController.getAll(Institute.class);
		Institute reappearing = findByName(institutes, "Wiederkehrende Bank");
		Institute oldName = findByName(institutes, "Alte Bank");
		Institute newName = findByName(institutes, "Neue Bank");

		assertEquals(3, institutes.size());
		assertEquals(LocalDate.of(2000, 7, 1), reappearing.getValidFrom());
		assertEquals(InstituteValidityDateType.FILE_MONTH, reappearing.getValidFromType());
		assertNull(reappearing.getValidTo());
		assertEquals("blz0007.txt", reappearing.getFirstSeenFile());
		assertEquals("blz0206.txt", reappearing.getLastSeenFile());
		assertEquals(InstituteStatus.ACTIVE, reappearing.getStateType());

		assertEquals(LocalDate.of(2002, 3, 4), oldName.getValidFrom());
		assertEquals(LocalDate.of(2002, 6, 2), oldName.getValidTo());
		assertEquals(InstituteValidityDateType.SOURCE_DATE, oldName.getValidFromType());
		assertEquals(InstituteValidityDateType.FIRST_MISSING, oldName.getValidToType());
		assertEquals(InstituteStatus.ARCHIVED, oldName.getStateType());

		assertEquals(LocalDate.of(2002, 6, 3), newName.getValidFrom());
		assertNull(newName.getValidTo());
		assertEquals("blz0206.txt", newName.getFirstSeenFile());
		assertEquals("blz0206.txt", newName.getLastSeenFile());
		assertEquals(List.of("blz0007.txt", "blz0203.txt", "blz0206.txt"), selectImportFileNames());
	}

	@Test
	void importsHistoricalSnapshotsChronologicallyAcrossYearBoundary() throws Exception {
		String bank = "10000000;Chronologische Bank;Berlin;Chronologisch;09";
		writeHistoricalFile("blz2003.txt", "********;85;09.03.2020;86;08.06.2020", bank);
		writeHistoricalFile("blz1912.txt", "********;84;02.12.2019;85;09.03.2020", bank);
		writeHistoricalFile("blz1906.txt", "********;82;03.06.2019;83;09.09.2019", bank);
		writeHistoricalFile("blz1909.txt", "********;83;09.09.2019;84;02.12.2019", bank);

		runImport();

		Institute institute = findByName(dbController.getAll(Institute.class), "Chronologische Bank");
		assertEquals("blz1906.txt", institute.getFirstSeenFile());
		assertEquals("blz2003.txt", institute.getLastSeenFile());
		assertEquals(List.of("blz1906.txt", "blz1909.txt", "blz1912.txt", "blz2003.txt"), selectImportFileNames());
	}

	@Test
	void importsAllHistoricalColumnVariants() throws Exception {
		writeHistoricalFile("blz1109.txt", "********;51;05.09.2011;52;05.12.2011",
				"10000000;Formatbank;Berlin;Formatbank;09", "********;1198030069");
		writeHistoricalFile("blz1203.txt", "********;53;05.03.2012;54;04.06.2012",
				"10000000;Formatbank;Berlin;Formatbank;09;FORMATBBXXX;10115;0;", "********;1198030069");
		writeHistoricalFile("blz1306.txt", "********;58;03.06.2013;59;09.09.2013",
				"10000000;Formatbank;Berlin;Formatbank;09;FORMATBBXXX;10115;0;;42;1", "********;1198030069");

		runImport();

		Institute institute = findByName(dbController.getAll(Institute.class), "Formatbank");
		assertEquals("FORMATBBXXX", institute.getBic());
		assertEquals("10115", institute.getAdditionalPostcode());
		assertEquals("42", institute.getAdditionalIbanRule());
		assertEquals("1", institute.getAdditionalIbanRuleVersion());
		assertEquals("blz1109.txt", institute.getFirstSeenFile());
		assertEquals("blz1306.txt", institute.getLastSeenFile());
		assertEquals(1, dbController.getAll(Institute.class).size(), "Checksum footers must not create institutes");
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

	private void writeHistoricalFile(String fileName, String... rows) throws Exception {
		Files.writeString(basePath.resolve("import").resolve(fileName), String.join("\r\n", rows) + "\r\n",
				StandardCharsets.ISO_8859_1);
	}

	private static Institute findByBlz(List<Institute> institutes, String blz) {
		return institutes.stream().filter(institute -> blz.equals(institute.getBlz())).findFirst().orElseThrow();
	}

	private static Institute findByName(List<Institute> institutes, String bankName) {
		return institutes.stream().filter(institute -> bankName.equals(institute.getBankName())).findFirst().orElseThrow();
	}

	private static int countAdditionalDetails() throws Exception {
		try (var statement = DBController.getConnection().createStatement();
				var resultSet = statement.executeQuery("SELECT COUNT(*) FROM institute_db.instituteAdditional")) {
			assertTrue(resultSet.next());
			return resultSet.getInt(1);
		}
	}
}
