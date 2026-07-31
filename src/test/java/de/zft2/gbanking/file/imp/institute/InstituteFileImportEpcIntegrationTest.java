package de.zft2.gbanking.file.imp.institute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
class InstituteFileImportEpcIntegrationTest extends BaseInstituteFileImportTest {

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
	void testImportEpc01() throws Exception {

		final String FILE_NAME_01 = "sct-first-150.csv";

		Path importDir = basePath.resolve("import");
		Path archiveDir = basePath.resolve("import/archive");

		Files.createDirectories(importDir);
		Files.createDirectories(archiveDir);

		InstituteFileImport importer = InstituteFileImport.getInstance(InstituteFileImportEpc.class, basePath.toString(), FILE_NAME_01, null, null);

		importer.runImport();

		List<Institute> all = dbController.getAll(Institute.class);

		assertFalse(all.isEmpty(), "No records imported");
		assertEquals(150, all.size(), "Not all CSV rows were imported");
		assertTrue(all.stream().allMatch(institute -> institute.getImportFile() != null), "Import file reference missing");
		assertEquals(List.of("sct-first-150.csv"), selectImportFileNames());

		// Ensure exactly one ACTIVE per BIC
		Map<String, List<Institute>> grouped = all.stream().collect(Collectors.groupingBy(Institute::getBic));

		for (Map.Entry<String, List<Institute>> entry : grouped.entrySet()) {

			List<Institute> group = entry.getValue();

			long activeCount = group.stream().filter(i -> i.getStateType() == InstituteStatus.ACTIVE).count();

			assertEquals(1, activeCount, "Exactly one ACTIVE expected per BIC: " + entry.getKey());

			// The record with the lowest import number must be ACTIVE
			Institute lowestBic = group.stream().min(Comparator.comparing(Institute::getBic)).orElseThrow();

			assertEquals(InstituteStatus.ACTIVE, lowestBic.getStateType());
		}

		// Ensure duplicates
		long duplicateCount = all.stream().filter(i -> i.getStateType() == InstituteStatus.DUPLICATE).count();

		assertEquals(0, duplicateCount, "DUPLICATE records detected");

		// -----------------------------
		// check that file was moved to archive
		// -----------------------------
		assertFalse(Files.exists(importDir.resolve(FILE_NAME_01)), "Datei wurde nicht aus import entfernt");
		assertTrue(Files.exists(archiveDir.resolve(FILE_NAME_01)), "Datei wurde nicht ins archive verschoben");

		// Restore CSV file back to import directory for repeated test runs
		restoreFile(importDir, archiveDir, FILE_NAME_01);
	}


}
