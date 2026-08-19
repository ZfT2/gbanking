package de.zft2.gbanking.tenant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TenantDatabaseLifecycleManagerTest {

	@TempDir
	private Path tempDirectory;

	@Test
	void shouldEncryptVerifyDeleteAndOpenDatabaseAgain() throws Exception {
		try (TenantSession session = createSession("secret")) {
			byte[] originalContent = "SQLite database content".getBytes();
			Files.write(session.paths().databaseFile(), originalContent);
			List<String> closeProgress = new ArrayList<>();
			TenantDatabaseLifecycleManager service = new TenantDatabaseLifecycleManager();

			service.closeAndEncryptDatabase(session, progressCollector(closeProgress));

			assertFalse(Files.exists(session.paths().databaseFile()));
			assertFalse(Files.exists(session.paths().databaseEncryptionTempFile()));
			assertTrue(Files.isRegularFile(session.paths().encryptedDatabaseFile()));
			assertTrue(closeProgress.contains("0/5:UI_TENANT_DB_CLOSE_PROGRESS_CLOSE"));
			assertTrue(closeProgress.contains("1/5:UI_TENANT_DB_CLOSE_PROGRESS_SIDECARS"));
			assertTrue(closeProgress.contains("2/5:UI_TENANT_DB_CLOSE_PROGRESS_ENCRYPT"));
			assertTrue(closeProgress.contains("3/5:UI_TENANT_DB_CLOSE_PROGRESS_VERIFY"));
			assertTrue(closeProgress.contains("4/5:UI_TENANT_DB_CLOSE_PROGRESS_DELETE"));
			assertTrue(closeProgress.contains("5/5:UI_TENANT_DB_CLOSE_PROGRESS_FINISHED"));

			List<String> openProgress = new ArrayList<>();
			TenantDatabaseLifecycleManager.OpenResult result = service.prepareDatabaseForOpen(session, progressCollector(openProgress));

			assertFalse(result.recoveredPlaintext());
			assertFalse(result.sqliteRecoveryRequired());
			assertArrayEquals(originalContent, Files.readAllBytes(session.paths().databaseFile()));
			assertFalse(Files.exists(session.paths().databaseDecryptionTempFile()));
			assertTrue(openProgress.contains("0/4:UI_TENANT_DB_OPEN_PROGRESS_DECRYPT"));
			assertTrue(openProgress.contains("1/4:UI_TENANT_DB_OPEN_PROGRESS_AUTHENTICATED"));
			assertTrue(openProgress.contains("2/4:UI_TENANT_DB_OPEN_PROGRESS_MOVE"));
			assertTrue(openProgress.contains("3/4:UI_TENANT_DB_OPEN_PROGRESS_MOVED"));
		}
	}

	@Test
	void shouldUseLocalPlaintextAndSharedEncryptedDatabaseForExternalDataDirectory() throws Exception {
		Path sharedDataDirectory = tempDirectory.resolve("shared-data");
		Path localWorkDirectory = tempDirectory.resolve("application-work");
		TenantStore tenantStore = new TenantStore(sharedDataDirectory, localWorkDirectory);
		TenantProfile profile = tenantStore.createTenant("external-tenant", "secret".toCharArray());
		try (TenantSession session = tenantStore.authenticateSession(profile.id(), "secret".toCharArray()).orElseThrow()) {
			byte[] originalContent = "local SQLite content".getBytes();
			Files.write(session.paths().databaseFile(), originalContent);
			TenantDatabaseLifecycleManager service = new TenantDatabaseLifecycleManager();

			service.closeAndEncryptDatabase(session, null);

			assertTrue(session.paths().databaseFile().startsWith(localWorkDirectory));
			assertFalse(Files.exists(session.paths().databaseFile()));
			assertTrue(session.paths().encryptedDatabaseFile().startsWith(sharedDataDirectory));
			assertTrue(Files.isRegularFile(session.paths().encryptedDatabaseFile()));

			service.prepareDatabaseForOpen(session, null);

			assertArrayEquals(originalContent, Files.readAllBytes(session.paths().databaseFile()));
		}
	}

	@Test
	void shouldRejectTamperedEncryptedDatabaseWithoutLeavingPlaintext() throws Exception {
		try (TenantSession session = createSession("secret")) {
			Files.writeString(session.paths().databaseFile(), "database-content");
			TenantDatabaseLifecycleManager service = new TenantDatabaseLifecycleManager();
			service.closeAndEncryptDatabase(session, null);
			Files.write(session.paths().encryptedDatabaseFile(), new byte[] { 1 }, StandardOpenOption.APPEND);

			assertThrows(IOException.class, () -> service.prepareDatabaseForOpen(session, null));

			assertFalse(Files.exists(session.paths().databaseFile()));
			assertFalse(Files.exists(session.paths().databaseDecryptionTempFile()));
		}
	}

	@Test
	void shouldPreferPlaintextFromInterruptedSessionOverOlderEncryptedState() throws Exception {
		try (TenantSession session = createSession("secret")) {
			Files.writeString(session.paths().databaseFile(), "older-state");
			TenantDatabaseLifecycleManager service = new TenantDatabaseLifecycleManager();
			service.closeAndEncryptDatabase(session, null);
			Files.writeString(session.paths().databaseFile(), "newer-crash-state");
			Path databaseFile = session.paths().databaseFile();
			Files.writeString(databaseFile.resolveSibling(databaseFile.getFileName() + "-wal"), "pending-wal");

			TenantDatabaseLifecycleManager.OpenResult result = service.prepareDatabaseForOpen(session, null);

			assertTrue(result.recoveredPlaintext());
			assertTrue(result.sqliteRecoveryRequired());
			assertTrue(Files.readString(session.paths().databaseFile()).contains("newer-crash-state"));
		}
	}

	@Test
	void shouldRemoveSqliteSidecarsOnlyAfterConnectionWasClosed() throws Exception {
		try (TenantSession session = createSession("secret")) {
			Path databaseFile = session.paths().databaseFile();
			Files.writeString(databaseFile, "database-content");
			Files.writeString(databaseFile.resolveSibling(databaseFile.getFileName() + "-journal"), "journal");
			Files.writeString(databaseFile.resolveSibling(databaseFile.getFileName() + "-wal"), "wal");
			Files.writeString(databaseFile.resolveSibling(databaseFile.getFileName() + "-shm"), "shm");

			new TenantDatabaseLifecycleManager().closeAndEncryptDatabase(session, null);

			assertFalse(Files.exists(databaseFile.resolveSibling(databaseFile.getFileName() + "-journal")));
			assertFalse(Files.exists(databaseFile.resolveSibling(databaseFile.getFileName() + "-wal")));
			assertFalse(Files.exists(databaseFile.resolveSibling(databaseFile.getFileName() + "-shm")));
		}
	}

	private TenantSession createSession(String password) {
		TenantStore tenantStore = new TenantStore(tempDirectory.resolve("data"));
		TenantProfile profile = tenantStore.createTenant("tenant", password.toCharArray());
		return tenantStore.authenticateSession(profile.id(), password.toCharArray()).orElseThrow();
	}

	private TenantDatabaseLifecycleManager.ProgressReporter progressCollector(List<String> progressEntries) {
		return (completedSteps, totalSteps, messageKey) -> progressEntries.add(completedSteps + "/" + totalSteps + ":" + messageKey);
	}
}
