package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.InstituteBankLookup;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;
import de.zft2.gbanking.db.repository.SqlTemplateRepository;
import de.zft2.gbanking.util.AppPaths;

class InstituteBankLookupTest extends DBControllerIntegrationBaseTest {

	private int importFile;
	private int sequence;

	@BeforeEach
	void initializeImportHistory() {
		importFile = db.insertOrUpdate(new ImportHistory("lookup-test.csv")).getId();
		sequence = 0;
	}

	@Test
	void selectsActiveBanksFromAllSourcesInPriorityOrder() {
		insertBank(5, "10000000", "TESTDEFFXXX", InstituteStatus.ACTIVE);
		insertBank(2, "10000000", "TESTDEFFXXX", InstituteStatus.ACTIVE);
		Institute dk = insertBank(1, "10000000", "TESTDEFFXXX", InstituteStatus.ACTIVE);
		insertBank(1, "10000000", "TESTDEFFXXX", InstituteStatus.ARCHIVED);
		insertBank(1, "10000000", "TESTDEFFXXX", InstituteStatus.DUPLICATE);
		Institute dbb = insertBank(2, "20000000", null, InstituteStatus.ACTIVE);
		Institute epc = insertBank(3, null, "EPCTDEFFXXX", InstituteStatus.ACTIVE);
		Institute reachable = insertBank(4, null, "REACDEFF", InstituteStatus.ACTIVE);
		Institute additional = insertBank(5, "50000000", null, InstituteStatus.ACTIVE);

		List<InstituteBankLookup> banks = db.getInstituteBankLookup();

		assertEquals(List.of(dk.getBankName(), dbb.getBankName(), epc.getBankName(), reachable.getBankName(),
				additional.getBankName()), banks.stream().map(InstituteBankLookup::bankName).toList());
		assertEquals(9, db.getAll(Institute.class).size(), "The directory must still expose the complete history");
	}

	@Test
	void preservesDifferentBlzAndSuppressesEquivalentBicOnlyEntries() {
		insertBank(1, "10000000", "TESTDEFFXXX", InstituteStatus.ACTIVE);
		insertBank(2, "20000000", "TESTDEFFXXX", InstituteStatus.ACTIVE);
		insertBank(3, null, " test deff ", InstituteStatus.ACTIVE);
		insertBank(4, null, "TESTDEFFXXX", InstituteStatus.ACTIVE);
		insertBank(3, null, "TESTDEFF123", InstituteStatus.ACTIVE);

		List<InstituteBankLookup> banks = db.getInstituteBankLookup();

		assertEquals(3, banks.size());
		assertEquals(List.of("10000000", "20000000"), banks.stream().map(bank -> bank.blz()).filter(blz -> blz != null).toList());
		assertEquals("TESTDEFF123", banks.get(2).bic());
	}

	@Test
	void discardedBlzRowsDoNotSuppressOtherBics() {
		Institute dk = insertBank(1, "10000000", "TESTDEFFXXX", InstituteStatus.ACTIVE);
		insertBank(2, "10000000", "OTHRDEFFXXX", InstituteStatus.ACTIVE);
		Institute epc = insertBank(3, null, "OTHRDEFFXXX", InstituteStatus.ACTIVE);

		assertEquals(List.of(dk.getBankName(), epc.getBankName()), db.getInstituteBankLookup().stream()
				.map(InstituteBankLookup::bankName).toList());
	}

	@Test
	void lowerPriorityBlzDoesNotSuppressHigherPriorityBicOnlyBank() {
		Institute epc = insertBank(3, null, "TESTDEFFXXX", InstituteStatus.ACTIVE);
		insertBank(4, " ", "TESTDEFF", InstituteStatus.ACTIVE);
		Institute additional = insertBank(5, "10000000", "TESTDEFFXXX", InstituteStatus.ACTIVE);

		assertEquals(List.of(epc.getBankName(), additional.getBankName()), db.getInstituteBankLookup().stream()
				.map(InstituteBankLookup::bankName).toList());
	}

	@Test
	void ignoresInactiveSourcesAndChoosesStableWinnerForEquivalentKeys() {
		insertBank(1, "10000000", "TESTDEFFXXX", InstituteStatus.ARCHIVED);
		insertBank(2, "10000000", "TESTDEFFXXX", InstituteStatus.DUPLICATE);
		Institute epc = insertBank(3, null, "testdeff", InstituteStatus.ACTIVE);
		insertBank(3, null, "TESTDEFFXXX", InstituteStatus.ACTIVE);
		insertBank(4, null, "", InstituteStatus.ACTIVE);

		assertEquals(1, db.getInstituteBankLookup().size());
		assertEquals(epc.getBankName(), db.getInstituteBankLookup().get(0).bankName());
	}

	@Test
	void exposesSeparateBlzAndBicWithoutInternalKeys() throws Exception {
		List<String> columns = new ArrayList<>();
		try (var statement = DBController.getConnection().createStatement();
				var result = statement.executeQuery("PRAGMA institute_db.table_info(bankNameLookup)")) {
			while (result.next()) {
				columns.add(result.getString("name"));
			}
		}

		assertEquals(List.of("id", "blz", "bic", "bankName", "place", "stateType", "source",
				"importFile", "importFileName", "updatedAt"), columns);
	}

	@Test
	void bundledDatabaseViewMatchesApplicationDefinitionWithoutCommonTableExpressions() throws SQLException {
		String bundledView = readBundledViewDefinition();
		String application = SqlTemplateRepository.getDdl("SQL_SETUP_CREATE_VIEW_BANK_NAME_LOOKUP");

		assertFalse(application.matches("(?is).*\\bWITH\\b.*"));
		assertEquals(normalizeSql(bundledView),
				normalizeSql(application.replace("IF NOT EXISTS institute_db.", "")));
		assertTrue(db.getInstituteBankLookup().isEmpty());
	}

	@Test
	void bundledDatabaseContainsOneValidityRowPerInstitute() throws SQLException {
		Path database = AppPaths.resolveInApplicationDirectory("data", "institute.db");
		String url = "jdbc:sqlite:" + database.toUri() + "?mode=ro";
		try (var connection = DriverManager.getConnection(url);
				var statement = connection.createStatement();
				var result = statement.executeQuery("SELECT "
						+ "(SELECT COUNT(*) FROM institute) AS instituteCount, "
						+ "(SELECT COUNT(*) FROM instituteValidity) AS validityCount, "
						+ "(SELECT COUNT(DISTINCT institute_id) FROM instituteValidity) AS distinctInstituteCount, "
						+ "(SELECT COUNT(*) FROM instituteAdditional a JOIN institute i ON i.id = a.institute_id "
						+ " WHERE i.validTo IS NULL) AS currentAdditionalCount")) {
			assertTrue(result.next());
			assertEquals(result.getInt("instituteCount"), result.getInt("validityCount"));
			assertEquals(result.getInt("validityCount"), result.getInt("distinctInstituteCount"));
			assertTrue(result.getInt("currentAdditionalCount") > 0);
		}
	}

	private static String readBundledViewDefinition() throws SQLException {
		Path database = AppPaths.resolveInApplicationDirectory("data", "institute.db");
		String url = "jdbc:sqlite:" + database.toUri() + "?mode=ro";
		try (var connection = DriverManager.getConnection(url);
				var statement = connection.prepareStatement(
						"SELECT sql FROM sqlite_master WHERE type = 'view' AND name = ?")) {
			statement.setString(1, "bankNameLookup");
			try (var result = statement.executeQuery()) {
				assertTrue(result.next(), "The bundled institute database must contain bankNameLookup");
				return result.getString("sql");
			}
		}
	}

	private static String normalizeSql(String sql) {
		return sql.replaceFirst(";\\s*$", "").replaceAll("\\s+", " ").trim();
	}

	private Institute insertBank(int sourcePriority, String blz, String bic, InstituteStatus status) {
		Institute institute = new Institute();
		institute.setBankName("Bank " + ++sequence);
		institute.setBlz(blz);
		institute.setBic(bic);
		institute.setStateType(status);
		institute.setImportFile(importFile);
		switch (sourcePriority) {
		case 1 -> institute.setImportNumber(sequence);
		case 2 -> institute.setDatasetNumber(Integer.toString(sequence));
		case 3 -> institute.setCountry("Germany");
		case 4 -> {
			institute.setServiceSct(1);
			institute.setServiceCor(0);
			institute.setServiceCor1(0);
			institute.setServiceB2b(0);
			institute.setServiceScc(0);
		}
		case 5 -> institute.setAdditionalBankNameShort(institute.getBankName());
		default -> throw new IllegalArgumentException("Unknown source priority: " + sourcePriority);
		}
		return db.insertOrUpdate(institute);
	}
}
