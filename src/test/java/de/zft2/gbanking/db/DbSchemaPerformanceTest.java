package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteConfig;

class DbSchemaPerformanceTest {

	private static final String EXPLAIN_QUERY_PLAN = "EXPLAIN QUERY PLAN ";
	private static final String LEGACY_INDEXES_RESOURCE = "sql/test/legacy-performance-indexes.sql";
	private static final String SELECT_EXPLICIT_INDEXES_RESOURCE = "sql/test/select-explicit-indexes.sql";
	private static final String PERFORMANCE_MIGRATION_VERSION = "0.4.1";

	@Test
	void performanceMigration_shouldConvergeWithFreshSchema() throws Exception {
		try (Connection freshConnection = openConnection();
				Connection migratedConnection = openConnection()) {
			createBaseline(freshConnection);
			createBaseline(migratedConnection);
			executeStatements(migratedConnection, LEGACY_INDEXES_RESOURCE);

			Map<String, String> expectedIndexes = readExplicitIndexes(freshConnection);
			assertTrue(expectedIndexes.get("idx_booking_account_root_amount_date")
					.contains("(account_id, amount, dateBooking)"));
			assertTrue(expectedIndexes.get("idx_booking_account")
					.contains("(account_id, parentBooking_id, id DESC)"));
			assertTrue(expectedIndexes.get("idx_booking_cross_booking")
					.contains("WHERE crossBooking_id IS NOT NULL"));
			assertFalse(expectedIndexes.containsKey("idx_booking_account_root_effective_date"));
			assertFalse(expectedIndexes.containsKey("idx_booking_parent"));
			assertFalse(expectedIndexes.containsKey("idx_bankmessage_bankaccess"));
			assertFalse(expectedIndexes.containsKey("uk_booking_category"));
			assertTrue(readExplicitIndexes(migratedConnection).containsKey("uk_booking_category"));

			DbMigrationRunner.migrate(migratedConnection, null, List.of(findPerformanceMigration()));

			Map<String, String> migratedIndexes = readExplicitIndexes(migratedConnection);
			assertEquals(expectedIndexes, migratedIndexes);
			assertFalse(migratedIndexes.containsKey("uk_booking_category"));
			assertFalse(migratedIndexes.containsKey("idx_moneytransferforeign_moneytransfer_id"));
		}
	}

	@Test
	void hotQueries_shouldUsePurposeBuiltIndexes() throws Exception {
		try (Connection connection = openConnection()) {
			createBaseline(connection);

			assertPlanUses(connection, "SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT", "idx_booking_account", 1);
			assertPlanUses(connection, "SQL_DELETE_BANKACCOUNT_BY_PRIMARY_ID", "idx_booking_account", 1);
			assertPlanUses(connection, "SQL_DELETE_BOOKING", "idx_booking_cross_booking", 1);
			assertPlanUses(connection, "SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT_AND_STATE",
					"idx_moneytransfer_account_status", 1, 0);
			assertPlanUses(connection, "SQL_SELECT_ALL_MONEYTRANSFER_PROTOCOLS_BY_MONEYTRANSFER",
					"idx_moneytransferprotocol_transfer", 1);
			assertPlanUses(connection, "SQL_SELECT_PREFERRED_RECIPIENT_BY_IBAN", "idx_recipient_iban_lookup",
					"DE00123456780000000000", "DE00123456780000000000");
			assertPlanUses(connection, "SQL_SELECT_PREFERRED_RECIPIENT_BY_IBAN", "idx_booking_recipient_usage",
					"DE00123456780000000000", "DE00123456780000000000");
			assertPlanUses(connection, "SQL_SELECT_PREFERRED_RECIPIENT_BY_IBAN", "idx_moneytransfer_recipient_usage",
					"DE00123456780000000000", "DE00123456780000000000");
		}
	}

	private static Connection openConnection() throws SQLException {
		SQLiteConfig config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		return DriverManager.getConnection("jdbc:sqlite::memory:", config.toProperties());
	}

	private static void createBaseline(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			for (String sql : SqlTemplateRepository.getMainBaselineStatements()) {
				statement.addBatch(sql);
			}
			statement.executeBatch();
		}
	}

	private static SqlTemplateRepository.VersionScript findPerformanceMigration() {
		return SqlTemplateRepository.getVersionScripts().stream()
				.filter(script -> PERFORMANCE_MIGRATION_VERSION.equals(script.getVersion()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Missing performance migration " + PERFORMANCE_MIGRATION_VERSION));
	}

	private static void executeStatements(Connection connection, String resource) throws IOException, SQLException {
		try (BufferedReader reader = openResource(resource);
				Statement statement = connection.createStatement()) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.isBlank() && !line.stripLeading().startsWith("--")) {
					statement.addBatch(line);
				}
			}
			statement.executeBatch();
		}
	}

	private static Map<String, String> readExplicitIndexes(Connection connection) throws IOException, SQLException {
		Map<String, String> indexes = new LinkedHashMap<>();
		try (Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(readResource(SELECT_EXPLICIT_INDEXES_RESOURCE))) {
			while (resultSet.next()) {
				indexes.put(resultSet.getString("name"), normalizeSql(resultSet.getString("sql")));
			}
		}
		return indexes;
	}

	private static void assertPlanUses(Connection connection, String sqlKey, String indexName, Object... parameters) throws SQLException {
		String sql = EXPLAIN_QUERY_PLAN + SqlTemplateRepository.getDml(sqlKey);
		StringBuilder plan = new StringBuilder();
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < parameters.length; i++) {
				statement.setObject(i + 1, parameters[i]);
			}
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					plan.append(resultSet.getString("detail")).append(System.lineSeparator());
				}
			}
		}
		assertTrue(plan.toString().contains(indexName), () -> sqlKey + " did not use " + indexName + ":\n" + plan);
	}

	private static String readResource(String resource) throws IOException {
		try (BufferedReader reader = openResource(resource)) {
			StringBuilder result = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				result.append(line).append(System.lineSeparator());
			}
			return result.toString();
		}
	}

	private static BufferedReader openResource(String resource) {
		InputStream input = DbSchemaPerformanceTest.class.getClassLoader().getResourceAsStream(resource);
		if (input == null) {
			throw new IllegalStateException("Missing test SQL resource: " + resource);
		}
		return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
	}

	private static String normalizeSql(String sql) {
		return sql.replaceAll("\\s+", " ").trim();
	}
}
