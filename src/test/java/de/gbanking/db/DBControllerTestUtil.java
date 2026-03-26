package de.gbanking.db;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class DBControllerTestUtil {

	/**
	 * Clears all tables while respecting foreign key constraints. This method is
	 * intended for test setup and should not be used in production.
	 */
	public static void clearAllTables(Connection connection) {
		assertTestDatabase(connection);

		Map<String, String> tableViewMap = new HashMap<>();
		ResultSet rs;
		try {
			for (String tableObjectType : new String[] { "TABLE" /*, "VIEW"*/ }) {
				rs = connection.getMetaData().getTables(null, null, null, new String[] { tableObjectType });
				while (rs.next()) {
					tableViewMap.put(rs.getString("TABLE_NAME"), tableObjectType);
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to get all table names", e);
		}

		try (Statement stmt = connection.createStatement()) {
			// Temporarily disable foreign key check
			stmt.execute("PRAGMA foreign_keys = OFF");

			for (String tableVieName : tableViewMap.keySet()) {
				stmt.executeUpdate(String.format("DELETE FROM %s", tableVieName));
			}

			// Re-enable foreign key checks
			stmt.execute("PRAGMA foreign_keys = ON");

		} catch (SQLException e) {
			throw new RuntimeException("Failed to clear all tables", e);
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

	public static void closeAndNullifyConnection() throws Exception {
		Field connField = DbConnectionHandler.class.getDeclaredField("connection");
		connField.setAccessible(true);
		Object connObj = connField.get(null);
		if (connObj instanceof Connection) {
			Connection conn = (Connection) connObj;
			if (!conn.isClosed())
				conn.close();
		}
		connField.set(null, null);

		// messages (static) zurücksetzen, damit getInstance wieder
		// Messages.getInstance() aufruft
		try {
			Field messagesField = DbConnectionHandler.class.getDeclaredField("messages");
			messagesField.setAccessible(true);
			messagesField.set(null, null);
		} catch (NoSuchFieldException ignored) {
			// falls Feld nicht vorhanden - nicht kritisch
		}
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
