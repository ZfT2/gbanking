package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;
import de.zft2.gbanking.db.repository.SqlTemplateRepository;
import de.zft2.gbanking.tenant.TenantPaths;
import de.zft2.gbanking.util.AppPaths;

class DbConnectionHandlerInstituteDatabaseTest {

	private Path tempDir;

	@AfterEach
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void shouldStoreInstituteTableInSharedDatabaseNextToTenantDirectories() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		Path dataRoot = tempDir.resolve("data");
		Files.createDirectories(dataRoot);
		Files.writeString(dataRoot.resolve("tenants.properties"), "tenant.ids=" + System.lineSeparator());

		Path tenantDatabaseDirectory = dataRoot.resolve("tenant").resolve(UUID.randomUUID().toString()).resolve("db");
		DBController dbController = DBController.getInstance(tenantDatabaseDirectory.toString());

		assertTrue(Files.exists(tenantDatabaseDirectory.resolve("gbanking.db")));
		assertTrue(Files.exists(dataRoot.resolve("institute.db")));
		assertFalse(tableExists("main", "institute"));
		assertTrue(tableExists("institute_db", "institute"));
		assertTrue(tableExists("institute_db", "instituteStatus"));
		assertTrue(tableExists("institute_db", "importHistory"));
		assertTrue(tableExists("institute_db", "instituteValidity"));
		assertTrue(validityImportHistoryForeignKeyExists("firstSeenFile"));
		assertTrue(validityImportHistoryForeignKeyExists("lastSeenFile"));
		assertTrue(tableExists("institute_db", "instituteDbbReachable"));
		assertTrue(tableExists("institute_db", "instituteAdditional"));
		assertTrue(sqliteObjectExists("institute_db", "view", "bankNameLookup"));
		assertTrue(indexExists("institute_db", "idx_institute_blz_state"));
		assertInstituteSchemaComplete();
		assertEquals(3, countInstituteStatusRows());
		assertEquals("INIT", selectFirstImportFileName());

		ImportHistory importHistory = dbController.insertOrUpdate(new ImportHistory("shared-test.csv"));
		Institute institute = new Institute();
		institute.setImportNumber(1);
		institute.setBlz("10010010");
		institute.setBankName("Shared Bank");
		institute.setStateType(InstituteStatus.ACTIVE);
		institute.setImportFile(importHistory.getId());
		institute.setUpdatedAt(LocalDate.of(2026, Month.MAY, 29));

		dbController.insertOrUpdate(institute);

		List<Institute> institutes = dbController.getAll(Institute.class);
		assertEquals(1, institutes.size());
		assertEquals("Shared Bank", institutes.get(0).getBankName());
		assertEquals(importHistory.getId(), institutes.get(0).getImportFile());
	}

	@Test
	void shouldKeepInstituteDatabaseInSharedDataDirectoryForLocalWorkingDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		Path dataRoot = tempDir.resolve("shared-data");
		Path tenantDirectory = dataRoot.resolve("tenant").resolve(UUID.randomUUID().toString());
		Path workDatabaseDirectory = tempDir.resolve("work").resolve("tenant").resolve(tenantDirectory.getFileName()).resolve("db");
		TenantPaths tenantPaths = new TenantPaths(tenantDirectory, workDatabaseDirectory);
		tenantPaths.createDirectories();
		DbRuntimeContext.setCurrentTenantPaths(tenantPaths);

		DBController dbController = DBController.getInstance(".");

		assertTrue(Files.isRegularFile(workDatabaseDirectory.resolve("gbanking.db")));
		assertTrue(Files.isRegularFile(dataRoot.resolve("institute.db")));
		assertFalse(Files.exists(tempDir.resolve("work").resolve("institute.db")));
		assertTrue(countInstituteRows() > 0);
		assertTrue(Files.size(dataRoot.resolve("institute.db"))
				>= Files.size(AppPaths.resolveInApplicationDirectory("data").resolve("institute.db")));
		assertInstituteSchemaComplete();
		Institute institute = dbController.getAll(Institute.class).get(0);
		assertNotNull(institute.getValidFrom());
		assertNotNull(institute.getFirstSeenFile());
		assertNotNull(institute.getLastSeenFile());
		assertNotNull(institute.getValidityUpdatedAt());
	}

	@Test
	void shouldReplaceOlderInstituteDatabaseInSharedDataDirectory() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		Path dataRoot = createInstituteDatabaseCopy("2000-01-01");
		configureTenant(dataRoot);

		DBController.getInstance(".");

		assertFalse(tableExists("institute_db", "bootstrap_marker"));
		assertTrue(countInstituteRows() > 0);
	}

	@Test
	void shouldKeepNewerInstituteDatabaseInSharedDataDirectory() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		Path dataRoot = createInstituteDatabaseCopy("9999-12-31");
		configureTenant(dataRoot);

		DBController.getInstance(".");

		assertTrue(tableExists("institute_db", "bootstrap_marker"));
		assertInstituteSchemaComplete();
	}

	@Test
	void shouldNotModifyCurrentInstituteDatabaseDuringRepeatedConnectionSetup() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		Path dataRoot = tempDir.resolve("shared-data");
		Files.createDirectories(dataRoot);
		Path instituteDatabase = dataRoot.resolve("institute.db");
		Files.copy(AppPaths.resolveInApplicationDirectory("data").resolve("institute.db"), instituteDatabase);
		configureTenant(dataRoot);

		DBController.getInstance(".");
		DBControllerTestUtil.closeAndNullifyConnection();
		assertTrue(tableExistsInFile(instituteDatabase, "instituteAdditional"));
		assertTrue(tableExistsInFile(instituteDatabase, "instituteValidity"));

		Path referenceDatabase = tempDir.resolve("institute-reference.db");
		Files.copy(instituteDatabase, referenceDatabase);
		configureTenant(dataRoot);
		DBController.getInstance(".");
		DBControllerTestUtil.closeAndNullifyConnection();

		assertEquals(-1L, Files.mismatch(referenceDatabase, instituteDatabase));
	}

	@Test
	void shouldNotCreateInstituteDatabaseWhenTemplateIsMissing() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		Path dataRoot = tempDir.resolve("shared-data");

		assertFalse(DbConnectionHandler.prepareInstituteDatabaseFile(dataRoot, tempDir.resolve("missing-institute.db")));
		assertFalse(Files.exists(dataRoot.resolve("institute.db")));
	}

	@Test
	void shouldNotRecreateLookupViewInExistingDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		DBController dbController = DBController.getInstance(tempDir.toString());
		int importFile = dbController.insertOrUpdate(new ImportHistory("existing-institute.csv")).getId();
		Institute institute = new Institute();
		institute.setBlz("10010010");
		institute.setBankName("Existing Bank");
		institute.setImportNumber(1);
		institute.setStateType(InstituteStatus.ACTIVE);
		institute.setImportFile(importFile);
		dbController.insertOrUpdate(institute);
		try (Statement statement = DBController.getConnection().createStatement()) {
			statement.executeUpdate("DROP VIEW institute_db.bankNameLookup");
		}
		DBControllerTestUtil.closeAndNullifyConnection();

		dbController = DBController.getInstance(tempDir.toString());

		assertInstituteSchemaComplete();
		assertFalse(sqliteObjectExists("institute_db", "view", "bankNameLookup"));
		assertEquals(1, countInstituteRows());
		Institute storedInstitute = dbController.getAll(Institute.class).get(0);
		assertEquals("Existing Bank", storedInstitute.getBankName());
		assertEquals(institute.getId(), storedInstitute.getId());
		assertEquals(importFile, storedInstitute.getImportFile());
	}

	@Test
	void shouldUseInMemoryInstituteDatabaseAfterExplicitConfirmation() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		Path dataRoot = tempDir.resolve("shared-data");
		configureTenant(dataRoot);

		DBController.getInstance(".", null, true);

		assertFalse(Files.exists(dataRoot.resolve("institute.db")));
		assertTrue(tableExists("institute_db", "institute"));
		assertTrue(indexExists("institute_db", "idx_institute_blz_state"));
		assertEquals(0, countInstituteRows());
	}

	private Path createInstituteDatabaseCopy(String updatedAt) throws Exception {
		Path dataRoot = tempDir.resolve("shared-data");
		Files.createDirectories(dataRoot);
		Path instituteDatabase = dataRoot.resolve("institute.db");
		Files.copy(AppPaths.resolveInApplicationDirectory("data").resolve("institute.db"), instituteDatabase,
				StandardCopyOption.REPLACE_EXISTING);
		try (var existingConnection = DriverManager.getConnection("jdbc:sqlite:" + instituteDatabase);
				var versionUpdate = existingConnection.prepareStatement("UPDATE importHistory SET updatedAt = ?");
				Statement statement = existingConnection.createStatement()) {
			versionUpdate.setString(1, updatedAt);
			versionUpdate.executeUpdate();
			statement.executeUpdate("CREATE TABLE bootstrap_marker (id INTEGER PRIMARY KEY)");
		}
		return dataRoot;
	}

	private void configureTenant(Path dataRoot) throws Exception {
		Path tenantDirectory = dataRoot.resolve("tenant").resolve(UUID.randomUUID().toString());
		TenantPaths tenantPaths = new TenantPaths(tenantDirectory);
		tenantPaths.createDirectories();
		DbRuntimeContext.setCurrentTenantPaths(tenantPaths);
	}

	private static boolean tableExists(String schema, String tableName) throws SQLException {
		return sqliteObjectExists(schema, "table", tableName);
	}

	private static boolean indexExists(String schema, String indexName) throws SQLException {
		return sqliteObjectExists(schema, "index", indexName);
	}

	private static boolean sqliteObjectExists(String schema, String type, String name) throws SQLException {
		String sql = "SELECT 1 FROM " + schema + ".sqlite_master WHERE type = '" + type + "' AND name = '" + name + "'";
		try (Statement statement = DBController.getConnection().createStatement();
				var rs = statement.executeQuery(sql)) {
			return rs.next();
		}
	}

	private static boolean tableExistsInFile(Path database, String tableName) throws SQLException {
		try (var fileConnection = DriverManager.getConnection("jdbc:sqlite:" + database);
				var statement = fileConnection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
			statement.setString(1, tableName);
			try (var resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private static int countInstituteStatusRows() throws SQLException {
		try (Statement statement = DBController.getConnection().createStatement();
				var rs = statement.executeQuery("SELECT COUNT(*) AS count FROM institute_db.instituteStatus")) {
			assertTrue(rs.next());
			return rs.getInt("count");
		}
	}

	private static String selectFirstImportFileName() throws SQLException {
		try (Statement statement = DBController.getConnection().createStatement();
				var resultSet = statement.executeQuery("SELECT importFileName FROM institute_db.importHistory ORDER BY id LIMIT 1")) {
			assertTrue(resultSet.next());
			return resultSet.getString(1);
		}
	}

	private static int countInstituteRows() throws SQLException {
		try (Statement statement = DBController.getConnection().createStatement();
				var rs = statement.executeQuery("SELECT COUNT(*) AS count FROM institute_db.institute")) {
			assertTrue(rs.next());
			return rs.getInt("count");
		}
	}

	private static boolean validityImportHistoryForeignKeyExists(String column) throws SQLException {
		try (Statement statement = DBController.getConnection().createStatement();
				var resultSet = statement.executeQuery("PRAGMA institute_db.foreign_key_list('instituteValidity')")) {
			while (resultSet.next()) {
				if ("importHistory".equals(resultSet.getString("table")) && column.equals(resultSet.getString("from"))
						&& "id".equals(resultSet.getString("to"))) {
					return true;
				}
			}
			return false;
		}
	}

	private static void assertInstituteSchemaComplete() throws SQLException {
		try (Statement statement = DBController.getConnection().createStatement();
				var resultSet = statement.executeQuery(
						SqlTemplateRepository.getConfig("SQL_IS_INSTITUTE_SCHEMA_COMPLETE"))) {
			assertTrue(resultSet.next());
			assertTrue(resultSet.getBoolean(1));
		}
	}
}
