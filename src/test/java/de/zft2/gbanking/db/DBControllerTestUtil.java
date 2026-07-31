package de.zft2.gbanking.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DBControllerTestUtil {

	/**
	 * Clears all tables while respecting foreign key constraints. This method is
	 * intended for test setup and should not be used in production.
	 */
	public static void clearAllTables(Connection connection) {
		assertTestDatabase(connection);

		List<String> tablesToClear = new ArrayList<>();
		try {
			try (Statement stmt = connection.createStatement(); ResultSet databases = stmt.executeQuery("PRAGMA database_list")) {
				while (databases.next()) {
					String schema = databases.getString("name");
					try (Statement tableStatement = connection.createStatement();
							ResultSet tables = tableStatement.executeQuery(
									"SELECT name FROM " + quoteIdentifier(schema) + ".sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")) {
						while (tables.next()) {
							String tableName = tables.getString("name");
							if (!isStaticLookupTable(schema, tableName)) {
								tablesToClear.add(quoteIdentifier(schema) + "." + quoteIdentifier(tableName));
							}
						}
					}
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to get all table names", e);
		}

		try (Statement stmt = connection.createStatement()) {
			// Temporarily disable foreign key check
			stmt.execute("PRAGMA foreign_keys = OFF");
			dropIntegrityTriggers(stmt);

			for (String tableName : tablesToClear) {
				stmt.executeUpdate("DELETE FROM " + tableName);
			}

			restorePatternSettings(stmt);
			restoreIntegrityTriggers(stmt);
			// Re-enable foreign key check
			stmt.execute("PRAGMA foreign_keys = ON");

		} catch (SQLException e) {
			throw new RuntimeException("Failed to clear all tables", e);
		}
	}

	private static void restorePatternSettings(Statement stmt) throws SQLException {
		for (String sql : SqlTemplateRepository.getBaselineStatements()) {
			if (sql.startsWith("INSERT OR IGNORE INTO setting") && sql.contains("'pattern.")) {
				stmt.executeUpdate(sql);
			}
		}
	}

	private static String quoteIdentifier(String identifier) {
		return "\"" + identifier.replace("\"", "\"\"") + "\"";
	}

	private static boolean isStaticLookupTable(String schema, String tableName) {
		return "institute_db".equals(schema) && "instituteStatus".equalsIgnoreCase(tableName);
	}

	private static void dropIntegrityTriggers(Statement stmt) throws SQLException {
		String[] triggers = {
				"block_update_moneytransfer",
				"block_delete_moneytransfer",
				"validate_moneytransfer_insert",
				"validate_moneytransfer_update",
				"validate_moneytransferforeign_insert",
				"validate_moneytransferforeign_update",
				"validate_moneytransfer_foreign_parent_update",
				"block_system_booking_core_update",
				"block_protected_booking_recipient_update",
				"validate_booking_relations_insert",
				"validate_booking_relations_update",
				"set_null_booking_cross_reference_delete",
				"prevent_category_cycle_insert",
				"prevent_category_cycle_update",
				"validate_bankaccess_insert",
				"validate_bankaccess_update",
				"delete_unused_parameterdata_after_bankaccess_parameterdata_delete",
				"validate_bankaccount_insert",
				"validate_bankaccount_update",
				"block_referenced_recipient_identity_update",
				"institute_db.validate_institute_insert",
				"institute_db.validate_institute_update"
		};
		for (String trigger : triggers) {
			stmt.executeUpdate("DROP TRIGGER IF EXISTS " + trigger);
		}
	}

	private static void restoreIntegrityTriggers(Statement stmt) throws SQLException {
		String[] triggerKeys = {
				"SQL_SETUP_CREATE_TRIGGER_MONEYTRANSFER_BLOCK_UPDATE",
				"SQL_SETUP_CREATE_TRIGGER_MONEYTRANSFER_BLOCK_DELETE",
				"SQL_SETUP_CREATE_TRIGGER_MONEYTRANSFER_VALIDATE_INSERT",
				"SQL_SETUP_CREATE_TRIGGER_MONEYTRANSFER_VALIDATE_UPDATE",
				"SQL_SETUP_CREATE_TRIGGER_MONEYTRANSFER_FOREIGN_INSERT",
				"SQL_SETUP_CREATE_TRIGGER_MONEYTRANSFER_FOREIGN_UPDATE",
				"SQL_SETUP_CREATE_TRIGGER_MONEYTRANSFER_FOREIGN_PARENT_UPDATE",
				"SQL_SETUP_CREATE_TRIGGER_BOOKING_BLOCK_SYSTEM_CORE_UPDATE",
				"SQL_SETUP_CREATE_TRIGGER_BOOKING_BLOCK_PROTECTED_RECIPIENT_UPDATE",
				"SQL_SETUP_CREATE_TRIGGER_BOOKING_VALIDATE_RELATIONS_INSERT",
				"SQL_SETUP_CREATE_TRIGGER_BOOKING_VALIDATE_RELATIONS_UPDATE",
				"SQL_SETUP_CREATE_TRIGGER_BOOKING_CROSS_REFERENCE_DELETE",
				"SQL_SETUP_CREATE_TRIGGER_CATEGORY_PREVENT_CYCLE_INSERT",
				"SQL_SETUP_CREATE_TRIGGER_CATEGORY_PREVENT_CYCLE_UPDATE",
				"SQL_SETUP_CREATE_TRIGGER_BANKACCESS_VALIDATE_INSERT",
				"SQL_SETUP_CREATE_TRIGGER_BANKACCESS_VALIDATE_UPDATE",
				"SQL_SETUP_CREATE_TRIGGER_BANKACCESS_PARAMETERDATA_DELETE_UNUSED",
				"SQL_SETUP_CREATE_TRIGGER_BANKACCOUNT_VALIDATE_INSERT",
				"SQL_SETUP_CREATE_TRIGGER_BANKACCOUNT_VALIDATE_UPDATE",
				"SQL_SETUP_CREATE_TRIGGER_RECIPIENT_BLOCK_REFERENCED_IDENTITY_UPDATE",
				"SQL_SETUP_CREATE_TRIGGER_INSTITUTEDK_VALIDATE_UNIQUE_BLZ_IMPORTNUMBER_INSERT",
				"SQL_SETUP_CREATE_TRIGGER_INSTITUTEDK_VALIDATE_UNIQUE_BLZ_IMPORTNUMBER_UPDATE",
				"SQL_SETUP_CREATE_TRIGGER_INSTITUTEDBB_VALIDATE_UNIQUE_BLZ_DATASETNUMBER_INSERT",
				"SQL_SETUP_CREATE_TRIGGER_INSTITUTEDBB_VALIDATE_UNIQUE_BLZ_DATASETNUMBER_UPDATE"
		};
		for (String triggerKey : triggerKeys) {
			stmt.executeUpdate(SqlTemplateRepository.getDdl(triggerKey));
		}
	}

	private static void assertTestDatabase(Connection connection) {
		if (connection == null) {
			throw new IllegalStateException("No test database connection available");
		}

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("PRAGMA database_list")) {
			while (rs.next()) {
				String file = rs.getString("file");
				if (file != null && !file.isBlank() && !file.contains("gb_test_")) {
					throw new IllegalStateException("Refusing to clear non-test database: " + file);
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to verify test database", e);
		}
	}

	public static void closeAndNullifyConnection() {
		DbConnectionHandler.resetConnection();
		DbRuntimeContext.setCurrentDbDirectory(".");
	}

	public static void deleteTemporaryDir(Path tempDir) throws IOException {
		if (tempDir != null && Files.exists(tempDir)) {
			Files.walk(tempDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(f -> {
				if (!f.delete()) {
					f.deleteOnExit();
				}
			});
		}
	}
}
