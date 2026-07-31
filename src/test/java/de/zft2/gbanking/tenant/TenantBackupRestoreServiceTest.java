package de.zft2.gbanking.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.zft2.gbanking.db.DatabaseIntegrityException;

class TenantBackupRestoreServiceTest {

	private static final Clock TEST_CLOCK = Clock.fixed(Instant.parse("2026-07-19T10:15:30Z"), ZoneId.of("Europe/Berlin"));

	@TempDir
	private Path tempDirectory;

	@Test
	void shouldCreateManualBackupAfterClosingAndCheckingDatabase() throws Exception {
		try (TenantSession session = createSession()) {
			Files.writeString(session.paths().databaseFile(), "current-database");
			Files.writeString(session.paths().accountStatementsDirectory().resolve("current.pdf"), "current-statement");
			RecordingDatabaseOperations databaseOperations = new RecordingDatabaseOperations();
			TenantBackupRestoreService service = createService(databaseOperations);

			Path backupFile = service.createManualBackup(session, null);

			assertEquals("gbanking.db.manual.20260719_121530.gbbackup", backupFile.getFileName().toString());
			assertEquals("current-database", readBackupEntry(session, backupFile, "db/gbanking.db"));
			assertEquals("current-statement", readBackupEntry(session, backupFile, "accountStatements/current.pdf"));
			assertEquals(1, databaseOperations.closeCount);
			assertEquals(1, databaseOperations.openCount);
			assertEquals(List.of(false), databaseOperations.integrityChecks);
		}
	}

	@Test
	void shouldRestoreDatabaseAndStatementsAndKeepSafetyBackup() throws Exception {
		try (TenantSession session = createSession()) {
			Path selectedBackup = createSelectedBackup(session);
			writeCurrentState(session);
			Files.writeString(session.paths().encryptedDatabaseFile(), "obsolete-container");
			RecordingDatabaseOperations databaseOperations = new RecordingDatabaseOperations();
			TenantBackupRestoreService service = createService(databaseOperations);

			TenantBackupRestoreService.RestoreResult result = service.restoreBackup(session, selectedBackup, null);

			assertEquals("restored-database", Files.readString(session.paths().databaseFile()));
			assertEquals("restored-statement", Files.readString(session.paths().accountStatementsDirectory().resolve("restored.pdf")));
			assertFalse(Files.exists(session.paths().accountStatementsDirectory().resolve("current.pdf")));
			assertFalse(Files.exists(session.paths().encryptedDatabaseFile()));
			assertTrue(result.cleanupComplete());
			assertTrue(result.safetyBackupFile().getFileName().toString().startsWith("gbanking.db.before_restore.20260719_121530"));
			assertEquals("current-database", readBackupEntry(session, result.safetyBackupFile(), "db/gbanking.db"));
			assertEquals("current-statement", readBackupEntry(session, result.safetyBackupFile(), "accountStatements/current.pdf"));
			assertEquals(1, databaseOperations.closeCount);
			assertEquals(1, databaseOperations.openCount);
			assertEquals(List.of(true), databaseOperations.integrityChecks);
		}
	}

	@Test
	void shouldRejectUnsafeArchiveBeforeClosingCurrentDatabase() throws Exception {
		try (TenantSession session = createSession()) {
			writeCurrentState(session);
			Path maliciousBackup = session.paths().backupDirectory().resolve("malicious.gbbackup");
			writeEncryptedArchive(session, maliciousBackup, zipOutput -> {
				addZipFile(zipOutput, "db/gbanking.db", "restored-database");
				addZipFile(zipOutput, "accountStatements/../../escaped.txt", "unsafe");
			});
			RecordingDatabaseOperations databaseOperations = new RecordingDatabaseOperations();

			assertThrows(IOException.class, () -> createService(databaseOperations).restoreBackup(session, maliciousBackup, null));

			assertEquals("current-database", Files.readString(session.paths().databaseFile()));
			assertFalse(Files.exists(session.paths().tenantDirectory().resolve("escaped.txt")));
			assertEquals(0, databaseOperations.closeCount);
			assertEquals(0, databaseOperations.openCount);
		}
	}

	@Test
	void shouldRejectBackupFromAnotherTenantBeforeClosingCurrentDatabase() throws Exception {
		try (TenantSession sourceSession = createSession(); TenantSession targetSession = createSession()) {
			Path foreignBackup = createSelectedBackup(sourceSession);
			writeCurrentState(targetSession);
			RecordingDatabaseOperations databaseOperations = new RecordingDatabaseOperations();

			assertThrows(IOException.class, () -> createService(databaseOperations).restoreBackup(targetSession, foreignBackup, null));

			assertEquals("current-database", Files.readString(targetSession.paths().databaseFile()));
			assertEquals(0, databaseOperations.closeCount);
			assertEquals(0, databaseOperations.openCount);
		}
	}

	@Test
	void shouldKeepCurrentStateWhenRestoredDatabaseFailsIntegrityCheck() throws Exception {
		try (TenantSession session = createSession()) {
			Path selectedBackup = createSelectedBackup(session);
			writeCurrentState(session);
			RecordingDatabaseOperations databaseOperations = new RecordingDatabaseOperations();
			databaseOperations.rejectFullIntegrityCheck = true;
			TenantBackupRestoreService service = createService(databaseOperations);

			assertThrows(DatabaseIntegrityException.class,
					() -> service.restoreBackup(session, selectedBackup, null));

			assertEquals("current-database", Files.readString(session.paths().databaseFile()));
			assertEquals("current-statement", Files.readString(session.paths().accountStatementsDirectory().resolve("current.pdf")));
			assertEquals(0, databaseOperations.closeCount);
			assertEquals(0, databaseOperations.openCount);
		}
	}

	@Test
	void shouldRollBackFilesWhenOpeningRestoredDatabaseFails() throws Exception {
		try (TenantSession session = createSession()) {
			Path selectedBackup = createSelectedBackup(session);
			writeCurrentState(session);
			RecordingDatabaseOperations databaseOperations = new RecordingDatabaseOperations();
			databaseOperations.failNextOpen = true;
			TenantBackupRestoreService service = createService(databaseOperations);

			assertThrows(IllegalStateException.class,
					() -> service.restoreBackup(session, selectedBackup, null));

			assertEquals("current-database", Files.readString(session.paths().databaseFile()));
			assertEquals("current-statement", Files.readString(session.paths().accountStatementsDirectory().resolve("current.pdf")));
			assertFalse(Files.exists(session.paths().accountStatementsDirectory().resolve("restored.pdf")));
			assertEquals(2, databaseOperations.closeCount);
			assertEquals(2, databaseOperations.openCount);
		}
	}

	private TenantBackupRestoreService createService(RecordingDatabaseOperations databaseOperations) {
		return new TenantBackupRestoreService(new TenantBackupService(TEST_CLOCK), new TenantEncryptionService(), databaseOperations);
	}

	private TenantSession createSession() {
		TenantStore tenantStore = new TenantStore(tempDirectory.resolve("data-" + UUID.randomUUID()));
		TenantProfile profile = tenantStore.createTenant("tenant", "secret".toCharArray());
		return tenantStore.authenticateSession(profile.id(), "secret".toCharArray()).orElseThrow();
	}

	private Path createSelectedBackup(TenantSession session) throws Exception {
		Files.writeString(session.paths().databaseFile(), "restored-database");
		Files.writeString(session.paths().accountStatementsDirectory().resolve("restored.pdf"), "restored-statement");
		return new TenantBackupService(TEST_CLOCK).createManualBackup(session);
	}

	private void writeCurrentState(TenantSession session) throws Exception {
		Files.writeString(session.paths().databaseFile(), "current-database");
		deleteDirectoryContents(session.paths().accountStatementsDirectory());
		Files.writeString(session.paths().accountStatementsDirectory().resolve("current.pdf"), "current-statement");
	}

	private void deleteDirectoryContents(Path directory) throws Exception {
		try (var paths = Files.list(directory)) {
			for (Path path : paths.toList()) {
				Files.delete(path);
			}
		}
	}

	private String readBackupEntry(TenantSession session, Path backupFile, String entryName) throws Exception {
		Path zipFile = tempDirectory.resolve(UUID.randomUUID() + ".zip");
		try {
			new TenantEncryptionService().decryptFile(backupFile, zipFile, session.dataKey());
			try (ZipFile archive = new ZipFile(zipFile.toFile(), StandardCharsets.UTF_8)) {
				ZipEntry entry = archive.getEntry(entryName);
				try (var inputStream = archive.getInputStream(entry)) {
					return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
				}
			}
		} finally {
			Files.deleteIfExists(zipFile);
		}
	}

	private void writeEncryptedArchive(TenantSession session, Path backupFile, ZipWriter writer) throws Exception {
		new TenantEncryptionService().writeEncryptedContent(backupFile, session, outputStream -> {
			try (ZipOutputStream zipOutput = new ZipOutputStream(outputStream)) {
				writer.write(zipOutput);
			}
		});
	}

	private void addZipFile(ZipOutputStream outputStream, String entryName, String content) throws IOException {
		outputStream.putNextEntry(new ZipEntry(entryName));
		outputStream.write(content.getBytes(StandardCharsets.UTF_8));
		outputStream.closeEntry();
	}

	@FunctionalInterface
	private interface ZipWriter {

		void write(ZipOutputStream outputStream) throws IOException;
	}

	private static final class RecordingDatabaseOperations implements TenantBackupRestoreService.DatabaseOperations {

		private final List<Boolean> integrityChecks = new ArrayList<>();
		private int closeCount;
		private int openCount;
		private boolean rejectFullIntegrityCheck;
		private boolean failNextOpen;

		@Override
		public void close() {
			closeCount++;
		}

		@Override
		public void validate(Path databaseFile, boolean fullIntegrityCheck) {
			integrityChecks.add(fullIntegrityCheck);
			if (rejectFullIntegrityCheck && fullIntegrityCheck) {
				throw new DatabaseIntegrityException("Invalid restored database");
			}
		}

		@Override
		public void open(TenantSession session) {
			openCount++;
			if (failNextOpen) {
				failNextOpen = false;
				throw new IllegalStateException("Opening restored database failed");
			}
		}
	}
}
