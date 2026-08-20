package de.zft2.gbanking.file.imp.institute;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InstituteFileImportDbbReachableIntegrationTest extends BaseInstituteFileImportTest {

	private static final String DBB_BIC = "AACSDE33";
	private static final String SECOND_BIC = "AAAARSBG";

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
		Files.deleteIfExists(basePath.resolve("import").resolve(InstituteFileImportDbbReachable.DEFAULT_FILENAME));
		Files.deleteIfExists(basePath.resolve("import/archive").resolve(InstituteFileImportDbbReachable.DEFAULT_FILENAME));
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void importsAndUpdatesReachabilityWithoutChangingOtherSources() throws Exception {
		Institute dbbInstitute = insertDbbInstitute();
		writeImportFile(row(DBB_BIC, "SPARKASSE AACHEN", 1, 1, 0, 1, 1),
				row(SECOND_BIC, "Yettel Bank Beograd", 1, 0, 0, 0, 0));

		runImport();

		List<Institute> afterInitialImport = dbController.getAll(Institute.class);
		assertEquals(3, afterInitialImport.size());
		assertEquals(2, countReachableDetails());
		assertEquals(InstituteStatus.ACTIVE, findById(afterInitialImport, dbbInstitute.getId()).getStateType());
		Institute initialReachable = findReachableByBic(afterInitialImport, DBB_BIC);
		assertEquals(1, initialReachable.getServiceSct());
		assertEquals(1, initialReachable.getServiceScc());

		writeImportFile(row(DBB_BIC, "SPARKASSE AACHEN", 1, 1, 0, 1, 0));
		runImport();

		List<Institute> afterUpdate = dbController.getAll(Institute.class);
		assertEquals(3, afterUpdate.size());
		assertEquals(InstituteStatus.ACTIVE, findById(afterUpdate, dbbInstitute.getId()).getStateType());
		Institute updatedReachable = findReachableByBic(afterUpdate, DBB_BIC);
		assertEquals(0, updatedReachable.getServiceScc());
		assertEquals(InstituteStatus.ACTIVE, updatedReachable.getStateType());
		assertEquals(InstituteStatus.ARCHIVED, findReachableByBic(afterUpdate, SECOND_BIC).getStateType());
		assertEquals(List.of("dbb-test.csv", InstituteFileImportDbbReachable.DEFAULT_FILENAME,
				InstituteFileImportDbbReachable.DEFAULT_FILENAME), selectImportFileNames());
		assertTrue(Files.exists(basePath.resolve("import/archive").resolve(InstituteFileImportDbbReachable.DEFAULT_FILENAME)));
	}

	private Institute insertDbbInstitute() {
		ImportHistory history = dbController.insertOrUpdate(new ImportHistory("dbb-test.csv"));
		Institute institute = new Institute();
		institute.setBlz("39050000");
		institute.setBic(DBB_BIC);
		institute.setBankName("Sparkasse Aachen");
		institute.setDatasetNumber("000001");
		institute.setFeatureChange('U');
		institute.setStateType(InstituteStatus.ACTIVE);
		institute.setImportFile(history.getId());
		institute.setUpdatedAt(LocalDate.now());
		return dbController.insertOrUpdate(institute);
	}

	private void runImport() throws Exception {
		InstituteFileImport importer = InstituteFileImport.getInstance(InstituteFileImportDbbReachable.class, basePath.toString(),
				InstituteFileImportDbbReachable.DEFAULT_FILENAME);
		importer.runImport();
	}

	private void writeImportFile(String... rows) throws Exception {
		String content = "Gueltig ab / valid from 17.08.2026;;;;;;\r\n"
				+ "BIC;Name;SERVICE SCT;SERVICE COR;SERVICE COR1;SERVICE B2B;SERVICE SCC\r\n"
				+ String.join("\r\n", rows) + "\r\n";
		Files.writeString(basePath.resolve("import").resolve(InstituteFileImportDbbReachable.DEFAULT_FILENAME), content,
				StandardCharsets.UTF_8);
	}

	private static String row(String bic, String name, int sct, int cor, int cor1, int b2b, int scc) {
		return String.join(";", bic, name, Integer.toString(sct), Integer.toString(cor), Integer.toString(cor1), Integer.toString(b2b),
				Integer.toString(scc));
	}

	private static Institute findById(List<Institute> institutes, int id) {
		return institutes.stream().filter(institute -> institute.getId() == id).findFirst().orElseThrow();
	}

	private static Institute findReachableByBic(List<Institute> institutes, String bic) {
		return institutes.stream().filter(institute -> bic.equals(institute.getBic()) && institute.getServiceSct() != null)
				.findFirst().orElseThrow();
	}

	private static int countReachableDetails() throws Exception {
		try (var statement = DBController.getConnection().createStatement();
				var resultSet = statement.executeQuery("SELECT COUNT(*) FROM institute_db.instituteDbbReachable")) {
			assertTrue(resultSet.next());
			return resultSet.getInt(1);
		}
	}
}
