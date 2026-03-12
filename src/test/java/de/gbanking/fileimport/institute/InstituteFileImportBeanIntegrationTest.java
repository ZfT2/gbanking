package de.gbanking.fileimport.institute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.gbanking.db.DBController;
import de.gbanking.db.DBControllerTestUtil;
import de.gbanking.db.dao.Institute;
import de.gbanking.db.dao.enu.InstituteStatus;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InstituteFileImportBeanIntegrationTest {

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

		InstituteFileImportBean importer = new InstituteFileImportBean(basePath.toString(), FILE_NAME, StandardCharsets.UTF_8);

		importer.runImport();

		List<Institute> all = dbController.getAll(Institute.class);

		assertFalse(all.isEmpty(), "No records imported");
		assertEquals(22, all.size(), "Not all CSV rows were imported");

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
		restoreFile(importDir, archiveDir);
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

		InstituteFileImportBean importer = new InstituteFileImportBean(basePath.toString(), FILE_NAME, StandardCharsets.UTF_8);

		// First import
		importer.runImport();
		restoreFile(importDir, archiveDir);

		int countAfterFirstImport = dbController.getAll(Institute.class).size();

		// Second import (identical file)
		importer.runImport();
		restoreFile(importDir, archiveDir);

		List<Institute> all = dbController.getAll(Institute.class);

		assertEquals(countAfterFirstImport, all.size(), "Re-import of identical file must not create new records");

		long archivedCount = all.stream().filter(i -> i.getStateType() == InstituteStatus.ARCHIVED).count();

		assertEquals(0, archivedCount, "No records should be archived when file is unchanged");
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

		InstituteFileImportBean importer = new InstituteFileImportBean(basePath.toString(), FILE_NAME, StandardCharsets.UTF_8);

		// Initial import using original file
		importer.runImport();
		restoreFile(importDir, archiveDir);
		
		List<Institute> all = dbController.getAll(Institute.class);
		int initialCount = all.size();

		assertFalse(all.isEmpty(), "No records imported");
		assertEquals(22, all.size(), "Not all CSV rows were imported");

		// Create modified copy of the CSV file
		String modifiedFileName = "institute_test_modified.csv";
		Path originalFile = importDir.resolve(FILE_NAME);
		Path modifiedFile = importDir.resolve(modifiedFileName);

		List<String> lines = Files.readAllLines(originalFile);

		// Modify one specific record (change BIC of first matching entry)
		for (int i = 1; i < lines.size(); i++) {
			if (lines.get(i).startsWith("2;10010010;")) {
				lines.set(i, lines.get(i).replace("PBNKDEFFXXX", "PBNKDEFFYYY"));
				break;
			}
		}

		Files.write(modifiedFile, lines);

		// Import modified file
		InstituteFileImportBean modifiedImporter = new InstituteFileImportBean(basePath.toString(), modifiedFileName, StandardCharsets.UTF_8);

		modifiedImporter.runImport();

		// Restore archived modified file so test remains repeatable
		restoreSpecificFile(importDir, archiveDir, modifiedFileName);

		// Delete temporary modified file from import directory
		Files.deleteIfExists(importDir.resolve(modifiedFileName));

		all = dbController.getAll(Institute.class);

		assertEquals(initialCount + 1, all.size(), "Modified record must create new version");

		long archivedCount = all.stream().filter(i -> i.getStateType() == InstituteStatus.ARCHIVED).count();

		assertEquals(1, archivedCount, "Exactly one record must be archived after modification");

		// Restore original file as well (if needed)
		restoreFile(importDir, archiveDir);
	}

	// ---------------------------------------------------------
	// Helper methods
	// ---------------------------------------------------------

	/**
	 * Moves the archived CSV file back to the import directory. This allows
	 * repeated execution of tests.
	 */
	private void restoreFile(Path importDir, Path archiveDir) throws Exception {

		Path archivedFile = archiveDir.resolve(FILE_NAME);
		Path importFile = importDir.resolve(FILE_NAME);

		if (Files.exists(archivedFile)) {
			Files.move(archivedFile, importFile, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Moves a specific archived CSV file back to the import directory. Used for
	 * temporary test files.
	 */
	private void restoreSpecificFile(Path importDir, Path archiveDir, String fileName) throws Exception {

		Path archivedFile = archiveDir.resolve(fileName);
		Path importFile = importDir.resolve(fileName);

		if (Files.exists(archivedFile)) {
			Files.move(archivedFile, importFile, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}