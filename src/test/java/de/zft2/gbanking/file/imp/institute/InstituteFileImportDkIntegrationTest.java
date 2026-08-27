package de.zft2.gbanking.file.imp.institute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
class InstituteFileImportDkIntegrationTest extends BaseInstituteFileImportTest {

	private DBController dbController;
	private Path tempDir;
	private Path basePath;

	private static final String FILE_NAME = "institute_test.csv";

	@BeforeAll
	void setupDatabase() throws Exception {

		// Create fresh SQLite database inside a temporary directory
		tempDir = Files.createTempDirectory("gb_test_");
		dbController = DBController.getInstance(tempDir.toString());

		// Use src/test/resources as base path for file import
		basePath = Paths.get("src", "test", "resources").toAbsolutePath();
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

	// ---------------------------------------------------------
	// 1) Initial import + duplicate handling
	// ---------------------------------------------------------

	@Test
	void testImportWithDuplicateHandling() throws Exception {

		Path importDir = basePath.resolve("import");
		Path archiveDir = basePath.resolve("import/archive");

		Files.createDirectories(importDir);
		Files.createDirectories(archiveDir);

		InstituteFileImport importer = InstituteFileImport.getInstance(InstituteFileImportDk.class, basePath.toString(), FILE_NAME);
		importer.runImport();

		List<Institute> all = dbController.getAll(Institute.class);

		assertFalse(all.isEmpty(), "No records imported");
		assertEquals(22, all.size(), "Not all CSV rows were imported");
		assertTrue(all.stream().allMatch(institute -> institute.getImportFile() != null), "Import file reference missing");
		assertEquals(List.of(FILE_NAME), selectImportFileNames());

		// Ensure exactly one ACTIVE per BLZ
		Map<String, List<Institute>> grouped = all.stream().collect(Collectors.groupingBy(Institute::getBlz));

		for (Map.Entry<String, List<Institute>> entry : grouped.entrySet()) {

			List<Institute> group = entry.getValue();

			long activeCount = group.stream().filter(i -> i.getStateType() == InstituteStatus.ACTIVE).count();

			assertEquals(1, activeCount, "Exactly one ACTIVE expected per BLZ: " + entry.getKey());

			// The record with the lowest import number must be ACTIVE
			Institute lowestNr = group.stream().min(Comparator.comparing(Institute::getImportNumber)).orElseThrow();

			assertEquals(InstituteStatus.ACTIVE, lowestNr.getStateType());
		}

		// Ensure duplicates exist
		long duplicateCount = all.stream().filter(i -> i.getStateType() == InstituteStatus.DUPLICATE).count();

		assertTrue(duplicateCount > 0, "No DUPLICATE records detected");

		// -----------------------------
		// check that file was moved to archive
		// -----------------------------
		assertFalse(Files.exists(importDir.resolve(FILE_NAME)), "Datei wurde nicht aus import entfernt");
		assertTrue(Files.exists(archiveDir.resolve(FILE_NAME)), "Datei wurde nicht ins archive verschoben");

		// Restore CSV file back to import directory for repeated test runs
		restoreFile(importDir, archiveDir, FILE_NAME);
	}

	// ---------------------------------------------------------
	// 2) Re-import of identical file
	// ---------------------------------------------------------

	@Test
	void testReImportWithoutChanges() throws Exception {

		Path importDir = basePath.resolve("import");
		Path archiveDir = basePath.resolve("import/archive");

		Files.createDirectories(importDir);
		Files.createDirectories(archiveDir);

		InstituteFileImport importer = InstituteFileImport.getInstance(InstituteFileImportDk.class, basePath.toString(), FILE_NAME,
				StandardCharsets.ISO_8859_1, null);

		// First import
		importer.runImport();
		restoreFile(importDir, archiveDir, FILE_NAME);

		int countAfterFirstImport = dbController.getAll(Institute.class).size();
		dropInstituteLookupIndex();
		DBController.resetConnection();
		dbController = DBController.getInstance(tempDir.toString());
		assertFalse(instituteLookupIndexExists(), "Opening an existing institute database must not modify it");

		// Second import (identical file)
		importer.runImport();
		restoreFile(importDir, archiveDir, FILE_NAME);

		List<Institute> all = dbController.getAll(Institute.class);

		assertEquals(countAfterFirstImport, all.size(), "Re-import of identical file must not create new records");

		long archivedCount = all.stream().filter(i -> i.getStateType() == InstituteStatus.ARCHIVED).count();

		assertEquals(0, archivedCount, "No records should be archived when file is unchanged");
		assertTrue(instituteLookupIndexExists(), "An unchanged import must restore the lookup index");
	}

	// ---------------------------------------------------------
	// 3) Change detection test
	// ---------------------------------------------------------

	@Test
	void testImportWithModifiedRecord() throws Exception {

		Path importDir = basePath.resolve("import");
		Path archiveDir = basePath.resolve("import/archive");

		Files.createDirectories(importDir);
		Files.createDirectories(archiveDir);

		InstituteFileImport importer = InstituteFileImport.getInstance(InstituteFileImportDk.class, basePath.toString(), FILE_NAME,
				StandardCharsets.ISO_8859_1, null);

		// Initial import using original file
		importer.runImport();
		restoreFile(importDir, archiveDir, FILE_NAME);
		
		List<Institute> all = dbController.getAll(Institute.class);
		int initialCount = all.size();

		assertFalse(all.isEmpty(), "No records imported");
		assertEquals(22, all.size(), "Not all CSV rows were imported");

		// Create modified copy of the CSV file
		String modifiedFileName = "institute_test_modified.csv";
		Path originalFile = importDir.resolve(FILE_NAME);
		Path modifiedFile = importDir.resolve(modifiedFileName);

		List<String> lines = Files.readAllLines(originalFile, StandardCharsets.ISO_8859_1);

		// Modify one specific record (change BIC of first matching entry)
		for (int i = 1; i < lines.size(); i++) {
			if (lines.get(i).startsWith("2;10010010;")) {
				lines.set(i, lines.get(i).replace("PBNKDEFFXXX", "PBNKDEFFYYY"));
				break;
			}
		}

		Files.write(modifiedFile, lines, StandardCharsets.ISO_8859_1);

		// Import modified file
		InstituteFileImport modifiedImporter = InstituteFileImport.getInstance(InstituteFileImportDk.class, basePath.toString(), modifiedFileName,
				StandardCharsets.ISO_8859_1, null);

		modifiedImporter.runImport();

		// Restore archived modified file so test remains repeatable
		restoreFile(importDir, archiveDir, modifiedFileName);

		// Delete temporary modified file from import directory
		Files.deleteIfExists(importDir.resolve(modifiedFileName));

		all = dbController.getAll(Institute.class);

		assertEquals(initialCount + 1, all.size(), "Modified record must create new version");

		long archivedCount = all.stream().filter(i -> i.getStateType() == InstituteStatus.ARCHIVED).count();

		assertEquals(1, archivedCount, "Exactly one record must be archived after modification");

		// Restore original file as well (if needed)
		restoreFile(importDir, archiveDir, FILE_NAME);
	}

	@Test
	void testDatedImportsAreProcessedOldestFirstAndIgnoreArchivedVersionsForUpdates() throws Exception {
		Path datedBasePath = tempDir.resolve("dated-institute-import");
		Path importDir = datedBasePath.resolve("import");
		Files.createDirectories(importDir);

		String baseFileName = "fints_institute NEU mit BIC Master";
		Files.writeString(importDir.resolve(baseFileName + "_20260103.csv"), singleInstituteCsv("NEWBICXX"), StandardCharsets.UTF_8);
		Files.writeString(importDir.resolve(baseFileName + "_20260101.csv"), singleInstituteCsv("OLDBICXX"), StandardCharsets.UTF_8);
		Files.writeString(importDir.resolve(baseFileName + "_20260102.csv"), singleInstituteCsv("MIDBICXX"), StandardCharsets.UTF_8);

		InstituteFileImport importer = InstituteFileImport.getInstance(InstituteFileImportDk.class, datedBasePath.toString(), baseFileName,
				StandardCharsets.UTF_8, null);

		importer.runImport();

		List<Institute> all = dbController.getAll(Institute.class);
		List<Institute> currentInstitutes = all.stream()
				.filter(institute -> institute.getStateType() == InstituteStatus.ACTIVE || institute.getStateType() == InstituteStatus.DUPLICATE)
				.toList();

		assertEquals(3, all.size(), "Each changed dated import must create a new version");
		assertEquals(2, all.stream().filter(institute -> institute.getStateType() == InstituteStatus.ARCHIVED).count());
		assertEquals(1, currentInstitutes.size());
		assertEquals("NEWBICXX", currentInstitutes.get(0).getBic(), "The newest dated import must remain current");
		assertEquals(List.of(baseFileName + "_20260101.csv", baseFileName + "_20260102.csv", baseFileName + "_20260103.csv"), selectImportFileNames());
		assertTrue(Files.exists(datedBasePath.resolve("import/archive").resolve(baseFileName + "_20260101.csv")));
		assertTrue(Files.exists(datedBasePath.resolve("import/archive").resolve(baseFileName + "_20260102.csv")));
		assertTrue(Files.exists(datedBasePath.resolve("import/archive").resolve(baseFileName + "_20260103.csv")));
	}

	@Test
	void testRowsWithOnlyImportNumberAreIgnored() throws Exception {
		Path importBasePath = tempDir.resolve("empty-institute-row-import");
		Path importDir = importBasePath.resolve("import");
		Files.createDirectories(importDir);

		String fileName = "fints_institute NEU mit BIC Master.csv";
		Files.writeString(importDir.resolve(fileName), instituteCsv(List.of(
				instituteCsvRow(1, "10010010", "PBNKDEFFXXX", "Postbank", "Berlin"),
				"2;;;;;;;;;;;;;;;;;;;;;;;;;;")), StandardCharsets.UTF_8);

		InstituteFileImport importer = InstituteFileImport.getInstance(InstituteFileImportDk.class, importBasePath.toString(), fileName,
				StandardCharsets.UTF_8, null);

		importer.runImport();

		List<Institute> all = dbController.getAll(Institute.class);
		assertEquals(1, all.size(), "Rows with only an import number must be ignored");
		assertEquals("10010010", all.get(0).getBlz());
	}

	@Test
	void testShiftedImportNumbersDoNotCreateFalseVersionsAndDeletedRowsAreArchived() throws Exception {
		Path importBasePath = tempDir.resolve("shifted-institute-number-import");
		Path importDir = importBasePath.resolve("import");
		Files.createDirectories(importDir);

		String baseFileName = "fints_institute NEU mit BIC Master";
		Files.writeString(importDir.resolve(baseFileName + "_20260520.csv"), instituteCsv(List.of(
				instituteCsvRow(1, "10020890", "HYVEDEMM488", "UniCredit Bank", "Berlin"),
				instituteCsvRow(2, "10020890", "HYVEDEMM488", "UniCredit Bank", "Kleinmachnow"))), StandardCharsets.UTF_8);
		Files.writeString(importDir.resolve(baseFileName + "_20260521.csv"), instituteCsv(List.of(
				instituteCsvRow(1, "10020890", "HYVEDEMM488", "UniCredit Bank", "Kleinmachnow"))), StandardCharsets.UTF_8);

		InstituteFileImport importer = InstituteFileImport.getInstance(InstituteFileImportDk.class, importBasePath.toString(), baseFileName,
				StandardCharsets.UTF_8, null);

		importer.runImport();

		List<Institute> all = dbController.getAll(Institute.class);
		List<Institute> currentInstitutes = all.stream()
				.filter(institute -> institute.getStateType() == InstituteStatus.ACTIVE || institute.getStateType() == InstituteStatus.DUPLICATE)
				.toList();

		assertEquals(2, all.size(), "A shifted import number must update the current row instead of creating a false new version");
		assertEquals(1, all.stream().filter(institute -> institute.getStateType() == InstituteStatus.ARCHIVED).count());
		assertEquals(1, currentInstitutes.size());
		assertEquals(InstituteStatus.ACTIVE, currentInstitutes.get(0).getStateType());
		assertEquals(1, currentInstitutes.get(0).getImportNumber());
		assertEquals("Kleinmachnow", currentInstitutes.get(0).getPlace());
		assertEquals(1, currentInstitutes.get(0).getImportFile(), "A pure import-number shift must keep the original content import file");
	}

	@Test
	void testTechnicalDataChangesUpdateCurrentRowWithoutCreatingFalseVersion() throws Exception {
		Path importBasePath = tempDir.resolve("technical-institute-change-import");
		Path importDir = importBasePath.resolve("import");
		Files.createDirectories(importDir);

		String baseFileName = "fints_institute NEU mit BIC Master";
		Files.writeString(importDir.resolve(baseFileName + "_20260520.csv"), instituteCsv(List.of(
				instituteCsvRow(1, "10020890", "HYVEDEMM488", "UniCredit Bank", "Berlin", "https://old.example.test/fints"))), StandardCharsets.UTF_8);
		Files.writeString(importDir.resolve(baseFileName + "_20260521.csv"), instituteCsv(List.of(
				instituteCsvRow(1, "10020890", "HYVEDEMM488", "UniCredit Bank", "Berlin", "https://new.example.test/fints"))), StandardCharsets.UTF_8);

		InstituteFileImport importer = InstituteFileImport.getInstance(InstituteFileImportDk.class, importBasePath.toString(), baseFileName,
				StandardCharsets.UTF_8, null);

		importer.runImport();

		List<Institute> all = dbController.getAll(Institute.class);

		assertEquals(1, all.size(), "Technical data changes on the same institute identity must update the current row");
		assertEquals("https://new.example.test/fints", all.get(0).getPinUrl());
		assertEquals(2, all.get(0).getImportFile(), "Content changes must reference the import file that supplied the new content");
	}

	@Test
	void testShiftedImportNumbersButSameContentDoesNotUpdate() throws Exception {
		Path importDir = basePath.resolve("import");
		Path archiveDir = basePath.resolve("import/archive");

		Files.createDirectories(importDir);
		Files.createDirectories(archiveDir);

		final String FILE_1 = "institute_shifted_same_content-1.csv";

		InstituteFileImport importer = InstituteFileImport.getInstance(InstituteFileImportDk.class, basePath.toString(), FILE_1);
		importer.runImport();

		List<Institute> all = dbController.getAll(Institute.class);

		assertFalse(all.isEmpty(), "No records imported");
		assertEquals(4, all.size(), "Not all CSV rows were imported");

		final String FILE_2 = "institute_shifted_same_content-2.csv";

		importer = InstituteFileImport.getInstance(InstituteFileImportDk.class, basePath.toString(), FILE_2);
		importer.runImport();

		all = dbController.getAll(Institute.class);

		assertFalse(all.isEmpty(), "No records imported");
		assertEquals(4, all.size(), "Not all CSV rows were imported");

		// -----------------------------
		// check that file was moved to archive
		// -----------------------------
		assertFalse(Files.exists(importDir.resolve(FILE_1)), "Datei wurde nicht aus import entfernt");
		assertTrue(Files.exists(archiveDir.resolve(FILE_1)), "Datei wurde nicht ins archive verschoben");
		assertFalse(Files.exists(importDir.resolve(FILE_2)), "Datei wurde nicht aus import entfernt");
		assertTrue(Files.exists(archiveDir.resolve(FILE_2)), "Datei wurde nicht ins archive verschoben");

		// Restore CSV file back to import directory for repeated test runs
		restoreFile(importDir, archiveDir, FILE_1);
		restoreFile(importDir, archiveDir, FILE_2);
	}

	@Test
	void testShiftedImportNumbersButSameContentDoesNotUpdateSimple() throws Exception {

		for (int i = 0; i < 20; i++) {

			Path importBasePath = tempDir.resolve("shifted-institute-number-import");
			Path importDir = importBasePath.resolve("import");
			Files.createDirectories(importDir);

			String baseFileName = "fints_institute NEU mit BIC Master";
			Files.writeString(importDir.resolve(baseFileName + "_20260520.csv"),
					instituteCsv(List.of(instituteCsvRow(1145, "40164352", "GENODEM1CNO", "Volksbank Nottuln eG", "Nottuln"),
							instituteCsvRow(1146, "40164352", "GENODEM1CNO", "Volksbank Nottuln eG", "Nottuln"))),
					StandardCharsets.UTF_8);
			Files.writeString(importDir.resolve(baseFileName + "_20260521.csv"),
					instituteCsv(List.of(instituteCsvRow(1146, "40164352", "GENODEM1CNO", "Volksbank Nottuln eG", "Nottuln"),
							instituteCsvRow(1145, "40164352", "GENODEM1CNO", "Volksbank Nottuln eG", "Nottuln"))),
					StandardCharsets.UTF_8);

			InstituteFileImport importer = InstituteFileImport.getInstance(InstituteFileImportDk.class, importBasePath.toString(), baseFileName,
					StandardCharsets.UTF_8, null);
			importer.runImport();

			List<Institute> all = dbController.getAll(Institute.class);

			assertFalse(all.isEmpty(), "No records imported");
			assertEquals(2, all.size(), "Not all CSV rows were imported");
			assertTrue(all.stream().allMatch(institute -> institute.getImportFile() != null), "Import file reference missing");
		}
	}

	private String singleInstituteCsv(String bic) {
		return instituteCsv(List.of(instituteCsvRow(1, "10010010", bic, "Test Bank", "Berlin")));
	}

	private String instituteCsv(List<String> rows) {
		return String.join(";", List.of("Nr.", "BLZ", "BIC", "Institut", "Ort", "RZ", "Organisation", "HBCI-Zugang DNS",
				"HBCI- Zugang     IP-Adresse", "HBCI-Version", "DDV", "RDH-1", "RDH-2", "RDH-3", "RDH-4", "RDH-5", "RDH-6", "RDH-7", "RDH-8",
				"RDH-9", "RDH-10", "RAH-7", "RAH-9", "RAH-10", "PIN/TAN-Zugang URL", "Version", "Datum letzte \u00c4nderung"))
				+ System.lineSeparator() + String.join(System.lineSeparator(), rows) + System.lineSeparator();
	}

	private String instituteCsvRow(int importNumber, String blz, String bic, String bankName, String place) {
		return instituteCsvRow(importNumber, blz, bic, bankName, place, "https://example.test/fints");
	}

	private String instituteCsvRow(int importNumber, String blz, String bic, String bankName, String place, String pinUrl) {
		List<String> values = new ArrayList<>();
		values.add(Integer.toString(importNumber));
		values.add(blz);
		values.add(bic);
		values.add(bankName);
		values.add(place);
		values.add("RZ");
		values.add("Org");
		values.add("dns");
		values.add("ip");
		values.add("3.0");
		for (int i = 0; i < 14; i++) {
			values.add("");
		}
		values.add(pinUrl);
		values.add("FinTS V3.0");
		values.add("01.01.2026");
		return String.join(";", values);
	}
}
