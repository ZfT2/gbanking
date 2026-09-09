package de.zft2.gbanking.file.imp.institute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.gbanking.cache.InstituteLookupCache;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InstituteFileImportSourceIsolationIntegrationTest {

	private static final Path FIXTURE_DIRECTORY = Path.of("src", "test", "resources", "import");
	private static final String SHARED_BIC = "MARKDEF1100";

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
		InstituteLookupCache.clear();
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		InstituteLookupCache.clear();
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

	@Test
	void calculatesStatusPerSourceForOverlappingBankIdentifiers() throws Exception {
		writeOverlappingSources();
		runAllImports();
		assertIndependentSourceStates();
		List<String> dkBefore = sourceSnapshot(institute -> institute.getImportNumber() > 0);
		List<String> dbbBefore = sourceSnapshot(institute -> institute.getDatasetNumber() != null);

		writeImportFile(InstituteFileImportDbbReachable.DEFAULT_FILENAME, StandardCharsets.UTF_8,
				List.of("Gueltig ab / valid from 20.08.2026;;;;;;",
						"BIC;Name;SERVICE SCT;SERVICE COR;SERVICE COR1;SERVICE B2B;SERVICE SCC",
						"MARKDEF1200;Andere Bank;1;0;0;0;0"));
		runImport(InstituteFileImportDbbReachable.class, InstituteFileImportDbbReachable.DEFAULT_FILENAME);
		assertEquals(dkBefore, sourceSnapshot(institute -> institute.getImportNumber() > 0));
		assertEquals(dbbBefore, sourceSnapshot(institute -> institute.getDatasetNumber() != null));
		Institute archivedReachable = find(dbController.getAll(Institute.class),
				institute -> SHARED_BIC.equals(institute.getBic()) && institute.getServiceSct() != null);
		assertEquals(InstituteStatus.ARCHIVED, archivedReachable.getStateType());

		// Re-import in reverse source order, including promotion of an existing DK duplicate.
		writeOverlappingSources();
		Path dkFile = basePath.resolve("import").resolve(InstituteFileImportDk.DEFAULT_FILENAME);
		List<String> dkLines = Files.readAllLines(dkFile, StandardCharsets.ISO_8859_1);
		writeImportFile(InstituteFileImportDk.DEFAULT_FILENAME, StandardCharsets.ISO_8859_1, List.of(dkLines.get(0), dkLines.get(2)));
		runImport(InstituteFileImportAdditional.class, InstituteFileImportAdditional.DEFAULT_FILENAME);
		runImport(InstituteFileImportDbbReachable.class, InstituteFileImportDbbReachable.DEFAULT_FILENAME);
		runImport(InstituteFileImportEpc.class, InstituteFileImportEpc.DEFAULT_FILENAME);
		runImport(InstituteFileImportDbb.class, InstituteFileImportDbb.DEFAULT_FILENAME);
		List<String> dbbBeforeDk = sourceSnapshot(institute -> institute.getDatasetNumber() != null);
		runImport(InstituteFileImportDk.class, InstituteFileImportDk.DEFAULT_FILENAME);
		assertIndependentSourceStates();
		Institute activeDk = find(dbController.getAll(Institute.class),
				institute -> institute.getImportNumber() > 0 && institute.getStateType() == InstituteStatus.ACTIVE);
		assertEquals("Potsdam", activeDk.getPlace());
		assertEquals(dbbBeforeDk, sourceSnapshot(institute -> institute.getDatasetNumber() != null));
	}

	@Test
	void committedImportRefreshesBothBankLookupIndexes() throws Exception {
		assertTrue(InstituteLookupCache.getEntriesForBlz("10000000").isEmpty());
		writeOverlappingSources();
		runAllImports();

		assertEquals("Bundesbank", InstituteLookupCache.getEntriesForBlz("10000000").get(0).bankName());
		assertEquals("Bundesbank", InstituteLookupCache.getEntriesForBic(SHARED_BIC).get(0).bankName());
	}

	private void writeOverlappingSources() throws IOException {
		List<String> dk = Files.readAllLines(FIXTURE_DIRECTORY.resolve("institute_test.csv"), StandardCharsets.ISO_8859_1);
		String dkRow = dk.get(1).replace("10010010", "10000000").replace("PBNKDEFFXXX", SHARED_BIC).replace("Postbank", "Bundesbank");
		writeImportFile(InstituteFileImportDk.DEFAULT_FILENAME, StandardCharsets.ISO_8859_1,
				List.of(dk.get(0), dkRow, dkRow.replaceFirst("^2;", "3;").replace(";Berlin;", ";Potsdam;")));
		List<String> dbb = Files.readAllLines(FIXTURE_DIRECTORY.resolve("blz-aktuell_test-first-150.csv"), StandardCharsets.ISO_8859_1);
		writeImportFile(InstituteFileImportDbb.DEFAULT_FILENAME, StandardCharsets.ISO_8859_1,
				List.of(dbb.get(0), dbb.get(1), dbb.get(1).replace("011380", "011381").replace(";Berlin;", ";Potsdam;")));
		writeImportFile(InstituteFileImportEpc.DEFAULT_FILENAME, StandardCharsets.UTF_8,
				List.of("Country,ParticipantName,Address,City,BIC,Readiness Date,Scheme Leaving Date,Scheme Options",
						"Germany,Bundesbank,Strasse 1,Berlin," + SHARED_BIC + ",2026-01-01,,",
						"Germany,Andere Bank,Strasse 2,Potsdam,MARKDEF1200,2026-01-01,,"));
		writeImportFile(InstituteFileImportDbbReachable.DEFAULT_FILENAME, StandardCharsets.UTF_8,
				List.of("Gueltig ab / valid from 17.08.2026;;;;;;",
						"BIC;Name;SERVICE SCT;SERVICE COR;SERVICE COR1;SERVICE B2B;SERVICE SCC",
						SHARED_BIC + ";Bundesbank;1;1;0;1;0", "MARKDEF1200;Andere Bank;1;0;0;0;0"));
		writeImportFile(InstituteFileImportAdditional.DEFAULT_FILENAME, StandardCharsets.ISO_8859_1,
				List.of("BLZ;Institutsname;Ort;Kurzbezeichnung;Prüfziffermethode;BIC;PLZ;Löschmarker;Nachfolge-BLZ;IBAN-Regel;IBAN-Regel-Version",
						"10000000;Bundesbank;Berlin;Bundesbank;09;" + SHARED_BIC + ";10591;0;;901;1",
						"10000000;Bundesbank;Potsdam;Bundesbank;09;" + SHARED_BIC + ";14467;0;;901;1"));
	}

	private void assertIndependentSourceStates() {
		assertSourceStates(institute -> institute.getImportNumber() > 0, institute -> institute.getBlz());
		assertSourceStates(institute -> institute.getDatasetNumber() != null, institute -> institute.getBlz());
		assertSourceStates(institute -> institute.getCountry() != null, institute -> institute.getBic());
		assertSourceStates(institute -> institute.getServiceSct() != null, institute -> institute.getBic());
		assertSourceStates(institute -> institute.getAdditionalBankNameShort() != null, institute -> institute.getBlz());
	}

	private void assertSourceStates(Predicate<Institute> source, Function<Institute, String> groupKey) {
		Map<String, List<Institute>> groups = dbController.getAll(Institute.class).stream().filter(source)
				.filter(institute -> institute.getStateType() != InstituteStatus.ARCHIVED).collect(Collectors.groupingBy(groupKey));
		assertFalse(groups.isEmpty());
		for (List<Institute> group : groups.values()) {
			assertEquals(1, group.stream().filter(institute -> institute.getStateType() == InstituteStatus.ACTIVE).count());
			assertEquals(group.size() - 1, group.stream().filter(institute -> institute.getStateType() == InstituteStatus.DUPLICATE).count());
		}
	}

	private List<String> sourceSnapshot(Predicate<Institute> source) {
		return dbController.getAll(Institute.class).stream().filter(source)
				.sorted(Comparator.comparingInt(institute -> institute.getId()))
				.map(institute -> institute.toString() + ":" + institute.getImportFile()).toList();
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
