package de.zft2.gbanking.file.imp.institute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
class InstituteFileImportDbbIntegrationTest extends BaseInstituteFileImportTest {

	private DBController dbController;
	private Path tempDir;
	private Path basePath;

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
	void testImportDbb01() throws Exception {

		final String FILE_NAME_01 = "blz-aktuell_test-first-150.csv";

		Path importDir = basePath.resolve("import");
		Path archiveDir = basePath.resolve("import/archive");

		Files.createDirectories(importDir);
		Files.createDirectories(archiveDir);

		InstituteFileImport importer = InstituteFileImport.getInstance(InstituteFileImportDbb.class, basePath.toString(), FILE_NAME_01, null, null);

		importer.runImport();

		List<Institute> all = dbController.getAll(Institute.class);

		assertFalse(all.isEmpty(), "No records imported");
		assertEquals(150, all.size(), "Not all CSV rows were imported");
		assertTrue(all.stream().allMatch(institute -> institute.getImportFile() != null), "Import file reference missing");
		assertEquals(List.of("blz-aktuell_test-first-150.csv"), selectImportFileNames());

		// Ensure exactly one ACTIVE per BLZ
		Map<String, List<Institute>> grouped = all.stream().collect(Collectors.groupingBy(Institute::getBlz));

		for (Map.Entry<String, List<Institute>> entry : grouped.entrySet()) {

			List<Institute> group = entry.getValue();

			long activeCount = group.stream().filter(i -> i.getStateType() == InstituteStatus.ACTIVE).count();

			assertEquals(1, activeCount, "Exactly one ACTIVE expected per BLZ: " + entry.getKey());
		}

		// Ensure duplicates exist
		long duplicateCount = all.stream().filter(i -> i.getStateType() == InstituteStatus.DUPLICATE).count();

		assertTrue(duplicateCount > 0, "No DUPLICATE records detected");

		// -----------------------------
		// check that file was moved to archive
		// -----------------------------
		assertFalse(Files.exists(importDir.resolve(FILE_NAME_01)), "Datei wurde nicht aus import entfernt");
		assertTrue(Files.exists(archiveDir.resolve(FILE_NAME_01)), "Datei wurde nicht ins archive verschoben");

		// Restore CSV file back to import directory for repeated test runs
		restoreFile(importDir, archiveDir, FILE_NAME_01);
	}

	@Test
	void testImportDbb02() throws Exception {

		final String FILE_NAME_02 = "blz-aktuell-csv-data-last-99.csv";

		Path importDir = basePath.resolve("import");
		Path archiveDir = basePath.resolve("import/archive");

		Files.createDirectories(importDir);
		Files.createDirectories(archiveDir);

		InstituteFileImport importer = InstituteFileImport.getInstance(InstituteFileImportDbb.class, basePath.toString(), FILE_NAME_02,
				null, null);

		importer.runImport();

		List<Institute> all = dbController.getAll(Institute.class);

		assertFalse(all.isEmpty(), "No records imported");
		assertEquals(99, all.size(), "Not all CSV rows were imported");
		assertTrue(all.stream().allMatch(institute -> institute.getImportFile() != null), "Import file reference missing");
		assertEquals(List.of(FILE_NAME_02), selectImportFileNames());

		// Ensure exactly one ACTIVE per BLZ
		Map<String, List<Institute>> grouped = all.stream().collect(Collectors.groupingBy(Institute::getBlz));

		for (Map.Entry<String, List<Institute>> entry : grouped.entrySet()) {

			List<Institute> group = entry.getValue();

			long activeCount = group.stream().filter(i -> i.getStateType() == InstituteStatus.ACTIVE).count();

			assertEquals(1, activeCount, "Exactly one ACTIVE expected per BLZ: " + entry.getKey());
		}

		// Ensure duplicates exist
		long duplicateCount = all.stream().filter(i -> i.getStateType() == InstituteStatus.DUPLICATE).count();

		assertTrue(duplicateCount > 0, "No DUPLICATE records detected");

		// -----------------------------
		// check that file was moved to archive
		// -----------------------------
		assertFalse(Files.exists(importDir.resolve(FILE_NAME_02)), "Datei wurde nicht aus import entfernt");
		assertTrue(Files.exists(archiveDir.resolve(FILE_NAME_02)), "Datei wurde nicht ins archive verschoben");

		// Restore CSV file back to import directory for repeated test runs
		restoreFile(importDir, archiveDir, FILE_NAME_02);
	}

}
