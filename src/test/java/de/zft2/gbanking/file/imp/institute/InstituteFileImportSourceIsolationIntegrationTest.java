package de.zft2.gbanking.file.imp.institute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Predicate;

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
class InstituteFileImportSourceIsolationIntegrationTest {

	private static final Path FIXTURE_DIRECTORY = Path.of("src", "test", "resources", "import");

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
	void clearDatabase() {
		DBControllerTestUtil.clearAllTables(DBController.getConnection());
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void updatesAllSourcesWithoutCrossSourceInterference() throws Exception {
		writeImportFiles(false);
		runAllImports();

		List<Institute> initial = dbController.getAll(Institute.class);
		assertEquals(5, initial.size());
		assertSourceDetails(1);
		assertAllActive(initial);

		Institute initialDk = find(initial, institute -> institute.getImportNumber() > 0);
		Institute initialDbb = find(initial, institute -> institute.getDatasetNumber() != null);
		Institute initialEpc = find(initial, institute -> institute.getCountry() != null);
		Institute initialReachable = find(initial, institute -> institute.getServiceSct() != null);
		Institute initialAdditional = find(initial, institute -> institute.getAdditionalBankNameShort() != null);

		writeImportFiles(true);
		runAllImports();

		List<Institute> updated = dbController.getAll(Institute.class);
		assertEquals(5, updated.size());
		assertSourceDetails(1);
		assertAllActive(updated);

		Institute updatedDk = find(updated, institute -> institute.getImportNumber() > 0);
		Institute updatedDbb = find(updated, institute -> institute.getDatasetNumber() != null);
		Institute updatedEpc = find(updated, institute -> institute.getCountry() != null);
		Institute updatedReachable = find(updated, institute -> institute.getServiceSct() != null);
		Institute updatedAdditional = find(updated, institute -> institute.getAdditionalBankNameShort() != null);

		assertEquals(initialDk.getId(), updatedDk.getId());
		assertEquals("neues Rechenzentrum", updatedDk.getDataCenter());
		assertEquals(initialDbb.getId(), updatedDbb.getId());
		assertEquals("Deutsche Bundesbank", updatedDbb.getBankNameShort());
		assertEquals(initialEpc.getId(), updatedEpc.getId());
		assertEquals("Updated address", updatedEpc.getAddress());
		assertEquals(initialReachable.getId(), updatedReachable.getId());
		assertEquals(1, updatedReachable.getServiceScc());
		assertEquals(initialAdditional.getId(), updatedAdditional.getId());
		assertEquals("902", updatedAdditional.getAdditionalIbanRule());
	}

	private void writeImportFiles(boolean updated) throws IOException {
		copyFixtureRow("institute_test.csv", InstituteFileImportDk.DEFAULT_FILENAME, StandardCharsets.ISO_8859_1,
				"eigenes Rechenzentrum", updated ? "neues Rechenzentrum" : "eigenes Rechenzentrum");
		copyFixtureRow("blz-aktuell_test-first-150.csv", InstituteFileImportDbb.DEFAULT_FILENAME, StandardCharsets.ISO_8859_1,
				"BBk Berlin", updated ? "Deutsche Bundesbank" : "BBk Berlin");
		copyFixtureRow("sct-first-150.csv", InstituteFileImportEpc.DEFAULT_FILENAME, StandardCharsets.UTF_8,
				"Rr. e Kavajes, Nd.27, H.1, Nj.B.10", updated ? "Updated address" : "Rr. e Kavajes, Nd.27, H.1, Nj.B.10");

		String reachableRow = "MARKDEF1100;Bundesbank;1;1;0;1;" + (updated ? "1" : "0");
		writeImportFile(InstituteFileImportDbbReachable.DEFAULT_FILENAME, StandardCharsets.UTF_8,
				List.of("Gueltig ab / valid from 17.08.2026;;;;;;",
						"BIC;Name;SERVICE SCT;SERVICE COR;SERVICE COR1;SERVICE B2B;SERVICE SCC", reachableRow));

		String additionalRow = "99999999;Zusatzbank München;München;Zusatzbank;09;ADDTDEMMXXX;80331;0;;"
				+ (updated ? "902" : "901") + ";1";
		writeImportFile(InstituteFileImportAdditional.DEFAULT_FILENAME, StandardCharsets.ISO_8859_1,
				List.of("BLZ;Institutsname;Ort;Kurzbezeichnung;Prüfziffermethode;BIC;PLZ;Löschmarker;Nachfolge-BLZ;IBAN-Regel;IBAN-Regel-Version",
						additionalRow));
	}

	private void copyFixtureRow(String fixtureName, String importFileName, Charset charset, String original, String replacement)
			throws IOException {
		List<String> fixtureLines = Files.readAllLines(FIXTURE_DIRECTORY.resolve(fixtureName), charset);
		writeImportFile(importFileName, charset, List.of(fixtureLines.get(0), fixtureLines.get(1).replace(original, replacement)));
	}

	private void writeImportFile(String fileName, Charset charset, List<String> lines) throws IOException {
		Files.writeString(basePath.resolve("import").resolve(fileName), String.join("\r\n", lines) + "\r\n", charset);
	}

	private void runAllImports() throws IOException {
		runImport(InstituteFileImportDk.class, InstituteFileImportDk.DEFAULT_FILENAME);
		runImport(InstituteFileImportDbb.class, InstituteFileImportDbb.DEFAULT_FILENAME);
		runImport(InstituteFileImportEpc.class, InstituteFileImportEpc.DEFAULT_FILENAME);
		runImport(InstituteFileImportDbbReachable.class, InstituteFileImportDbbReachable.DEFAULT_FILENAME);
		runImport(InstituteFileImportAdditional.class, InstituteFileImportAdditional.DEFAULT_FILENAME);
	}

	private void runImport(Class<? extends InstituteFileImport> type, String fileName) throws IOException {
		InstituteFileImport.getInstance(type, basePath.toString(), fileName).runImport();
	}

	private static Institute find(List<Institute> institutes, Predicate<Institute> predicate) {
		return institutes.stream().filter(predicate).findFirst().orElseThrow();
	}

	private static void assertAllActive(List<Institute> institutes) {
		assertTrue(institutes.stream().allMatch(institute -> institute.getStateType() == InstituteStatus.ACTIVE));
	}

	private static void assertSourceDetails(int expected) throws SQLException {
		try (var statement = DBController.getConnection().createStatement();
				var resultSet = statement.executeQuery("SELECT (SELECT COUNT(*) FROM institute_db.instituteDk), "
						+ "(SELECT COUNT(*) FROM institute_db.instituteDbb), (SELECT COUNT(*) FROM institute_db.instituteEpc), "
						+ "(SELECT COUNT(*) FROM institute_db.instituteDbbReachable), "
						+ "(SELECT COUNT(*) FROM institute_db.instituteAdditional)")) {
			assertTrue(resultSet.next());
			for (int column = 1; column <= 5; column++) {
				assertEquals(expected, resultSet.getInt(column));
			}
		}
	}
}
