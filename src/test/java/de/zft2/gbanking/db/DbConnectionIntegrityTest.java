package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.util.AppPaths;

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

	@Test
	void shouldRejectMainDatabaseWithForeignKeyViolation() throws Exception {
		DBController.getInstance(tempDirectory.toString());
		Path databaseFile = tempDirectory.resolve("gbanking.db");
		DBController.resetConnection();
		try (var invalidConnection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
				Statement statement = invalidConnection.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO booking_category
						(booking_id, category_id, categoryRuleMode, updatedAt)
					VALUES (2147483647, 2147483647, 1, datetime())
					""");
		}

		assertThrows(DatabaseIntegrityException.class,
				() -> DBController.validateDatabaseIntegrity(databaseFile, false));
		assertThrows(DatabaseIntegrityException.class,
				() -> DBController.getInstance(tempDirectory.toString()));
		assertFalse(DBController.hasOpenConnection());
	}

	@Test
	void shouldNotReuseValidationAfterWalOnlyDatabaseChange() throws Exception {
		DBController.getInstance(tempDirectory.toString());
		Path databaseFile = tempDirectory.resolve("gbanking.db");
		DBController.resetConnection();
		DBController.validateDatabaseIntegrity(databaseFile, false);

		try (var invalidConnection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
				Statement statement = invalidConnection.createStatement()) {
			statement.execute("PRAGMA journal_mode=WAL");
			statement.executeUpdate("""
					INSERT INTO booking_category
						(booking_id, category_id, categoryRuleMode, updatedAt)
					VALUES (2147483647, 2147483647, 1, datetime())
					""");

			assertThrows(DatabaseIntegrityException.class,
					() -> DBController.getInstance(tempDirectory.toString()));
		}
		assertFalse(DBController.hasOpenConnection());
	}

	@Test
	void shouldNotReuseValidationAfterSameSizeDatabaseChangeWithRestoredTimestamp() throws Exception {
		DBController.getInstance(tempDirectory.toString());
		Path databaseFile = tempDirectory.resolve("gbanking.db");
		DBController.resetConnection();
		try (var journalModeConnection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
				Statement statement = journalModeConnection.createStatement()) {
			statement.execute("PRAGMA journal_mode=DELETE");
		}
		DBController.validateDatabaseIntegrity(databaseFile, false);
		long originalSize = Files.size(databaseFile);
		FileTime originalModifiedAt = Files.getLastModifiedTime(databaseFile);

		try (var invalidConnection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
				Statement statement = invalidConnection.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO booking_category
						(booking_id, category_id, categoryRuleMode, updatedAt)
					VALUES (2147483647, 2147483647, 1, datetime())
					""");
		}
		assertEquals(originalSize, Files.size(databaseFile));
		Files.setLastModifiedTime(databaseFile, originalModifiedAt);

		assertThrows(DatabaseIntegrityException.class,
				() -> DBController.getInstance(tempDirectory.toString()));
		assertFalse(DBController.hasOpenConnection());
	}

	@Test
	void shouldRejectAttachedInstituteDatabaseWithForeignKeyViolation() throws Exception {
		Path instituteDatabase = tempDirectory.resolve("institute.db");
		Files.copy(AppPaths.resolveInApplicationDirectory("data").resolve("institute.db"), instituteDatabase,
				StandardCopyOption.REPLACE_EXISTING);
		try (var invalidConnection = DriverManager.getConnection("jdbc:sqlite:" + instituteDatabase);
				Statement statement = invalidConnection.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO instituteDk (institute_id, importNumber, updatedAt)
					VALUES (2147483647, 1, datetime())
					""");
		}

		assertThrows(DatabaseIntegrityException.class,
				() -> DBController.getInstance(tempDirectory.toString()));
		assertFalse(DBController.hasOpenConnection());
	}
}
