package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		assertTrue(tableExists("institute_db", "instituteDbbReachable"));
		assertEquals(3, countInstituteStatusRows());

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

		DBController.getInstance(".");

		assertTrue(Files.isRegularFile(workDatabaseDirectory.resolve("gbanking.db")));
		assertTrue(Files.isRegularFile(dataRoot.resolve("institute.db")));
		assertFalse(Files.exists(tempDir.resolve("work").resolve("institute.db")));
		assertTrue(countInstituteRows() > 0);
		assertTrue(Files.size(dataRoot.resolve("institute.db"))
				>= Files.size(AppPaths.resolveInApplicationDirectory("data").resolve("institute.db")));
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
	}

	@Test
	void shouldNotCreateInstituteDatabaseWhenTemplateIsMissing() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		Path dataRoot = tempDir.resolve("shared-data");

		assertFalse(DbConnectionHandler.prepareInstituteDatabaseFile(dataRoot, tempDir.resolve("missing-institute.db")));
		assertFalse(Files.exists(dataRoot.resolve("institute.db")));
	}

	@Test
	void shouldUseInMemoryInstituteDatabaseAfterExplicitConfirmation() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		Path dataRoot = tempDir.resolve("shared-data");
		configureTenant(dataRoot);

		DBController.getInstance(".", null, true);

		assertFalse(Files.exists(dataRoot.resolve("institute.db")));
		assertTrue(tableExists("institute_db", "institute"));
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
		String sql = "SELECT 1 FROM " + schema + ".sqlite_master WHERE type = 'table' AND name = '" + tableName + "'";
		try (Statement statement = DBController.getConnection().createStatement();
				var rs = statement.executeQuery(sql)) {
			return rs.next();
		}
	}

	private static int countInstituteStatusRows() throws SQLException {
		try (Statement statement = DBController.getConnection().createStatement();
				var rs = statement.executeQuery("SELECT COUNT(*) AS count FROM institute_db.instituteStatus")) {
			assertTrue(rs.next());
			return rs.getInt("count");
		}
	}

	private static int countInstituteRows() throws SQLException {
		try (Statement statement = DBController.getConnection().createStatement();
				var rs = statement.executeQuery("SELECT COUNT(*) AS count FROM institute_db.institute")) {
			assertTrue(rs.next());
			return rs.getInt("count");
		}
	}
}
