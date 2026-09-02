package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteConfig;

class DbMigrationRunnerTest {

	@Test
	void shouldCompareVersionsNumerically() {
		assertTrue(DbMigrationRunner.compareVersions("0.1.10", "0.1.2") > 0);
		assertTrue(DbMigrationRunner.compareVersions("1.0.0", "0.9.9") > 0);
		assertEquals(0, DbMigrationRunner.compareVersions("0.1.0-SNAPSHOT", "0.1.0"));
		assertEquals(0, DbMigrationRunner.compareVersions("0.1", "0.1.0"));
		assertTrue(DbMigrationRunner.compareVersions(null, "0.0.1") < 0);
	}

	@Test
	void shouldTreatInvalidVersionPartsAsZero() {
		assertEquals(0, DbMigrationRunner.compareVersions("1.alpha.0", "1.0.0"));
		assertTrue(DbMigrationRunner.compareVersions("2.beta", "1.9.9") > 0);
	}

	@Test
	void shouldMigrateMoneyTransferStatusConstraints() throws SQLException {
		SQLiteConfig config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:", config.toProperties());
				Statement statement = connection.createStatement()) {
			createLegacyMoneyTransferSchema(statement);
			List<SqlTemplateRepository.VersionScript> migrationScripts = moneyTransferStatusMigrationScripts();

			assertTrue(DbMigrationRunner.hasPendingMigrations(connection, migrationScripts));

			DbMigrationRunner.migrate(connection, null, migrationScripts);

			assertFalse(DbMigrationRunner.hasPendingMigrations(connection, migrationScripts));

			statement.executeUpdate("""
					INSERT INTO moneytransfer (id, account_id, moneytransferType, recipient_id, purpose, amount, executionDate, executionDay,
					    moneytransferStatus, standingorderMode, historyorder_id, updatedAt)
					VALUES (2, 1, 'SCHEDULED_TRANSFER', 1, 'Inventory', 2.00, '01.01.26', NULL, 'INVENTORY', NULL, NULL, '01.01.26')
					""");
			statement.executeUpdate("""
					INSERT INTO moneytransferProtocol (id, moneytransfer_id, moneytransferStatus, timeStart, timeFinish, protocolText, updatedAt)
					VALUES (2, 2, 'CHANGED', '01.01.26', NULL, 'Changed', '01.01.26')
					""");
			statement.executeUpdate("""
					INSERT INTO moneytransfer (id, account_id, moneytransferType, recipient_id, purpose, amount, executionDate, executionDay,
					    moneytransferStatus, standingorderMode, historyorder_id, updatedAt)
					VALUES (3, 1, 'SCHEDULED_TRANSFER', 1, 'Deleted', 3.00, '01.01.26', NULL, 'DELETED', NULL, NULL, '01.01.26')
					""");
			statement.executeUpdate("""
					INSERT INTO moneytransferProtocol (id, moneytransfer_id, moneytransferStatus, timeStart, timeFinish, protocolText, updatedAt)
					VALUES (3, 3, 'DELETED', '01.01.26', NULL, 'Deleted', '01.01.26')
					""");

			try (var rs = statement.executeQuery("SELECT COUNT(*) AS count FROM moneytransfer WHERE moneytransferStatus IN ('SENT', 'INVENTORY', 'DELETED')")) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt("count"));
			}
		}
	}

	@Test
	void shouldNotifyMigrationProgress() throws SQLException {
		SQLiteConfig config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:", config.toProperties());
				Statement statement = connection.createStatement()) {
			createLegacyMoneyTransferSchema(statement);
			List<SqlTemplateRepository.VersionScript> migrationScripts = moneyTransferStatusMigrationScripts();

			List<String> progressEvents = new ArrayList<>();
			DbMigrationRunner.migrate(connection, (version, completed, completedSteps, totalSteps) ->
					progressEvents.add(version + ":" + completed + ":" + completedSteps + "/" + totalSteps), migrationScripts);

			assertFalse(progressEvents.isEmpty());
			assertTrue(progressEvents.stream().anyMatch(event -> event.contains(":false:")));
			assertTrue(progressEvents.stream().anyMatch(event -> event.contains(":true:")));
		}
	}

	@Test
	void migrationShouldRejectConnectionWithExistingTransaction() throws SQLException {
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			connection.setAutoCommit(false);

			SQLException exception = assertThrows(SQLException.class,
					() -> DbMigrationRunner.migrate(connection, null, List.of()));

			assertEquals("Database migration requires an auto-commit connection", exception.getMessage());
		}
	}

	@Test
	void freshSchemaShouldCreateCategoryRuleBankAccountRelation() throws SQLException {
		SQLiteConfig config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:", config.toProperties());
				Statement statement = connection.createStatement()) {
			createFreshCategoryRuleBankAccountSchema(statement);

			try (var rs = statement.executeQuery("PRAGMA table_info(categoryRule_bankAccount)")) {
				List<String> columns = new ArrayList<>();
				while (rs.next()) {
					columns.add(rs.getString("name"));
				}
				assertTrue(columns.contains("categoryRule_id"));
				assertFalse(columns.contains("category_id"));
			}
			try (var rs = statement.executeQuery(
					"SELECT COUNT(*) AS count FROM categoryRule_bankAccount WHERE categoryRule_id = 1 AND account_id = 1")) {
				assertTrue(rs.next());
				assertEquals(1, rs.getInt("count"));
			}

			statement.executeUpdate("INSERT INTO bankAccount (id) VALUES (2)");
			statement.executeUpdate(
					"INSERT INTO categoryRule_bankAccount (categoryRule_id, account_id, updatedAt) VALUES (1, 2, '01.01.26')");
			assertThrows(SQLException.class, () -> statement.executeUpdate(
					"INSERT INTO categoryRule_bankAccount (categoryRule_id, account_id, updatedAt) VALUES (99, 2, '01.01.26')"));
		}
	}

	@Test
	void baselineStatements_shouldContainUrgentTransferSchema() {
		List<String> baselineStatements = SqlTemplateRepository.getBaselineStatements();

		assertTrue(baselineStatements.stream().anyMatch(statement -> statement.contains("moneytransferType BETWEEN 1 AND 6")));
		assertTrue(baselineStatements.stream().anyMatch(statement -> statement.contains("CREATE TABLE moneytransferForeign")));
		assertTrue(baselineStatements.stream().anyMatch(statement -> statement.contains("moneytransfer_id INTEGER NOT NULL UNIQUE")));
		assertFalse(baselineStatements.stream().anyMatch(statement -> statement.contains("idx_moneytransferforeign_moneytransfer_id")));
		assertTrue(baselineStatements.stream().anyMatch(statement -> statement.contains("idx_moneytransfer_account_status")));
		assertTrue(baselineStatements.stream().anyMatch(statement -> statement.contains("name TEXT NOT NULL UNIQUE")));
		assertTrue(baselineStatements.stream().anyMatch(statement -> statement.contains("categoryRule_id INTEGER")));
		assertTrue(baselineStatements.stream().anyMatch(statement -> statement.contains("endToEndId TEXT")));
	}

	private static List<SqlTemplateRepository.VersionScript> moneyTransferStatusMigrationScripts() {
		return List.of(new SqlTemplateRepository.VersionScript("test-moneytransfer-status", List.of("""
				CREATE TABLE moneytransfer_new (
				  id INTEGER PRIMARY KEY,
				  account_id INTEGER NOT NULL,
				  moneytransferType TEXT NOT NULL,
				  recipient_id INTEGER,
				  purpose TEXT,
				  amount REAL,
				  executionDate TEXT,
				  executionDay INTEGER,
				  moneytransferStatus TEXT NOT NULL,
				  standingorderMode TEXT,
				  historyorder_id INTEGER,
				  updatedAt TEXT NOT NULL,
				  FOREIGN KEY(account_id) REFERENCES bankAccount(id) ON DELETE CASCADE,
				  FOREIGN KEY(recipient_id) REFERENCES recipient(id),
				  FOREIGN KEY(historyorder_id) REFERENCES moneytransfer_new(id),
				  CHECK (moneytransferType IN ('TRANSFER', 'REALTIME_TRANSFER', 'SCHEDULED_TRANSFER', 'STANDING_ORDER')),
				  CHECK (moneytransferStatus IN ('NEW', 'ERROR', 'SENT', 'INVENTORY', 'CHANGED', 'DELETED')),
				  CHECK (standingorderMode IN ('MONTHLY', 'BIMONTHLY', 'QUARTERLY', 'SEMI_ANNUALLY', 'ANNUALLY')))
				""", """
				INSERT INTO moneytransfer_new (id, account_id, moneytransferType, recipient_id, purpose, amount, executionDate, executionDay,
				    moneytransferStatus, standingorderMode, historyorder_id, updatedAt)
				SELECT id, account_id, moneytransferType, recipient_id, purpose, amount, executionDate, executionDay,
				    moneytransferStatus, standingorderMode, historyorder_id, updatedAt
				FROM moneytransfer
				""", "DROP TABLE moneytransfer", "ALTER TABLE moneytransfer_new RENAME TO moneytransfer", """
				CREATE TABLE moneytransferProtocol_new (
				  id INTEGER PRIMARY KEY,
				  moneytransfer_id INTEGER NOT NULL,
				  moneytransferStatus TEXT NOT NULL,
				  timeStart TEXT NOT NULL,
				  timeFinish TEXT,
				  protocolText TEXT,
				  updatedAt TEXT NOT NULL,
				  FOREIGN KEY(moneytransfer_id) REFERENCES moneytransfer(id) ON DELETE CASCADE,
				  CHECK (moneytransferStatus IN ('NEW', 'ERROR', 'SENT', 'INVENTORY', 'CHANGED', 'DELETED')))
				""", """
				INSERT INTO moneytransferProtocol_new (id, moneytransfer_id, moneytransferStatus, timeStart, timeFinish, protocolText, updatedAt)
				SELECT id, moneytransfer_id, moneytransferStatus, timeStart, timeFinish, protocolText, updatedAt
				FROM moneytransferProtocol
				""", "DROP TABLE moneytransferProtocol", "ALTER TABLE moneytransferProtocol_new RENAME TO moneytransferProtocol")));
	}

	private static void createLegacyMoneyTransferSchema(Statement statement) throws SQLException {
		statement.executeUpdate("CREATE TABLE bankAccess (id INTEGER PRIMARY KEY)");
		statement.executeUpdate("CREATE TABLE bankAccount (id INTEGER PRIMARY KEY)");
		statement.executeUpdate("CREATE TABLE recipient (id INTEGER PRIMARY KEY)");
		statement.executeUpdate("INSERT INTO bankAccount (id) VALUES (1)");
		statement.executeUpdate("INSERT INTO recipient (id) VALUES (1)");
		statement.executeUpdate("""
				CREATE TABLE moneytransfer (
				  id INTEGER PRIMARY KEY,
				  account_id INTEGER NOT NULL,
				  moneytransferType TEXT NOT NULL,
				  recipient_id INTEGER,
				  purpose TEXT,
				  amount REAL,
				  executionDate TEXT,
				  executionDay INTEGER,
				  moneytransferStatus TEXT NOT NULL,
				  standingorderMode TEXT,
				  historyorder_id INTEGER,
				  updatedAt TEXT NOT NULL,
				  CHECK (moneytransferType IN ('TRANSFER', 'REALTIME_TRANSFER', 'SCHEDULED_TRANSFER', 'STANDING_ORDER')),
				  CHECK (moneytransferStatus IN ('NEW', 'ERROR', 'SENT')),
				  CHECK (standingorderMode IN ('MONTHLY', 'BIMONTHLY', 'QUARTERLY', 'SEMI_ANNUALLY', 'ANNUALLY')))
				""");
		statement.executeUpdate("""
				CREATE TABLE moneytransferProtocol (
				  id INTEGER PRIMARY KEY,
				  moneytransfer_id INTEGER NOT NULL,
				  moneytransferStatus TEXT NOT NULL,
				  timeStart TEXT NOT NULL,
				  timeFinish TEXT,
				  protocolText TEXT,
				  updatedAt TEXT NOT NULL,
				  CHECK (moneytransferStatus IN ('NEW', 'ERROR', 'SENT')))
				""");
		statement.executeUpdate("""
				INSERT INTO moneytransfer (id, account_id, moneytransferType, recipient_id, purpose, amount, executionDate, executionDay,
				    moneytransferStatus, standingorderMode, historyorder_id, updatedAt)
				VALUES (1, 1, 'SCHEDULED_TRANSFER', 1, 'Existing', 1.00, '01.01.26', NULL, 'SENT', NULL, NULL, '01.01.26')
				""");
		statement.executeUpdate("""
				INSERT INTO moneytransferProtocol (id, moneytransfer_id, moneytransferStatus, timeStart, timeFinish, protocolText, updatedAt)
				VALUES (1, 1, 'SENT', '01.01.26', NULL, 'Sent', '01.01.26')
				""");
	}

	private static void createFreshCategoryRuleBankAccountSchema(Statement statement) throws SQLException {
		statement.executeUpdate("CREATE TABLE category (id INTEGER PRIMARY KEY)");
		statement.executeUpdate("CREATE TABLE bankAccount (id INTEGER PRIMARY KEY)");
		statement.executeUpdate(SqlTemplateRepository.getDdl("SQL_SETUP_CREATE_CATEGORYRULE"));
		statement.executeUpdate(SqlTemplateRepository.getDdl("SQL_SETUP_CREATE_CATEGORYRULE_BANKACCOUNT"));
		statement.executeUpdate("INSERT INTO category (id) VALUES (1)");
		statement.executeUpdate("INSERT INTO bankAccount (id) VALUES (1)");
		statement.executeUpdate("""
				INSERT INTO categoryRule (id, name, category_id, filterRecipientIsRegex, filterPurposeIsRegex, joinType, updatedAt)
				VALUES (1, 'Regel: Test', 1, 0, 0, 1, '01.01.26')
				""");
		statement.executeUpdate("INSERT INTO categoryRule_bankAccount (categoryRule_id, account_id, updatedAt) VALUES (1, 1, '01.01.26')");
	}

}
