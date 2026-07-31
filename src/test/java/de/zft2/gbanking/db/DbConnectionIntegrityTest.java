package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.zft2.gbanking.exception.GBankingException;

class DbConnectionIntegrityTest {

	private static final int EXPECTED_BUSY_TIMEOUT_MILLIS = 5_000;

	@TempDir
	private Path tempDirectory;

	@AfterEach
	void resetDatabase() {
		DbConnectionHandler.resetConnection();
		DbRuntimeContext.setCurrentDbDirectory(".");
	}

	@Test
	void shouldConfigureBusyTimeoutForEveryConnection() throws Exception {
		DBController.getInstance(tempDirectory.toString());

		try (Statement statement = DBController.getConnection().createStatement();
				ResultSet resultSet = statement.executeQuery("PRAGMA busy_timeout")) {
			assertTrue(resultSet.next());
			assertEquals(EXPECTED_BUSY_TIMEOUT_MILLIS, resultSet.getInt(1));
		}
	}

	@Test
	void shouldAcceptQuickAndFullIntegrityChecksForValidDatabase() {
		DBController.getInstance(tempDirectory.toString());
		Path databaseFile = tempDirectory.resolve("gbanking.db");
		DBController.resetConnection();

		assertDoesNotThrow(() -> DBController.validateDatabaseIntegrity(databaseFile, false));
		assertDoesNotThrow(() -> DBController.validateDatabaseIntegrity(databaseFile, true));
	}

	@Test
	void shouldRejectCorruptDatabaseBeforeInitialization() throws Exception {
		Path databaseFile = tempDirectory.resolve("gbanking.db");
		Files.writeString(databaseFile, "not a SQLite database");

		assertThrows(DatabaseIntegrityException.class, () -> DBController.validateDatabaseIntegrity(databaseFile, false));
		String databaseDirectory = tempDirectory.toString();
		assertThrows(GBankingException.class, () -> DBController.getInstance(databaseDirectory));

		assertFalse(DBController.hasOpenConnection());
	}
}
