package de.zft2.gbanking.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TenantBackupManagerTest {

	private static final ZoneId TEST_ZONE = ZoneId.of("Europe/Berlin");

	@TempDir
	private Path tempDirectory;

	@Test
	void shouldCreateEncryptedBackupsWithDatabaseAndAccountStatements() throws Exception {
		try (TenantSession session = createSession()) {
			TenantPaths tenantPaths = session.paths();
			Files.writeString(tenantPaths.databaseFile(), "db-content");
			Path statementDirectory = tenantPaths.accountStatementsDirectory().resolve("2026");
			Files.createDirectories(statementDirectory);
			Files.writeString(statementDirectory.resolve("statement.pdf"), "pdf-content");

			serviceAt("2026-05-12T10:15:00Z").backupTenantDatabase(session);

			Path openBackup = tenantPaths.backupDirectory().resolve("gbanking.db.backup_on_open.gbbackup");
			assertFalse(new String(Files.readAllBytes(openBackup), StandardCharsets.ISO_8859_1).contains("db-content"));
			assertEquals("db-content", readEntry(openBackup, "db/gbanking.db"));
			assertEquals("pdf-content", readEntry(openBackup, "accountStatements/2026/statement.pdf"));
			assertEquals(Set.of("db/gbanking.db", "accountStatements/", "accountStatements/2026/",
					"accountStatements/2026/statement.pdf"), entryNames(openBackup));
			assertEquals("db-content", readEntry(
					tenantPaths.backupDirectory().resolve("gbanking.db.weekly.20260512_1215.gbbackup"), "db/gbanking.db"));
			assertEquals("db-content", readEntry(
					tenantPaths.backupDirectory().resolve("gbanking.db.bimonthly.20260512_1215.gbbackup"), "db/gbanking.db"));
			assertEquals("db-content", readEntry(
					tenantPaths.backupDirectory().resolve("gbanking.db.yearly.20260512_1215.gbbackup"), "db/gbanking.db"));
		}
	}

	@Test
	void shouldBackUpLocalWorkingDatabaseIntoSharedTenantDirectory() throws Exception {
		Path sharedDataDirectory = tempDirectory.resolve("shared-data");
		Path localWorkDirectory = tempDirectory.resolve("local-work");
		TenantStore tenantStore = new TenantStore(sharedDataDirectory, localWorkDirectory);
		TenantProfile profile = tenantStore.createTenant("external-tenant", "secret".toCharArray());
		try (TenantSession session = tenantStore.authenticateSession(profile.id(), "secret".toCharArray()).orElseThrow()) {
			Files.writeString(session.paths().databaseFile(), "local-db-content");
			Files.writeString(session.paths().accountStatementsDirectory().resolve("statement.pdf"), "shared-statement");

			serviceAt("2026-05-12T10:15:00Z").backupTenantDatabase(session);

			Path backupFile = session.paths().backupDirectory().resolve("gbanking.db.backup_on_open.gbbackup");
			assertTrue(backupFile.startsWith(sharedDataDirectory));
			assertEquals("local-db-content", readEntry(backupFile, "db/gbanking.db"));
			assertEquals("shared-statement", readEntry(backupFile, "accountStatements/statement.pdf"));
		}
	}

	@Test
	void shouldReportBackupProgress() throws Exception {
		try (TenantSession session = createSession()) {
			Files.writeString(session.paths().databaseFile(), "db-content");
			List<String> progressMessages = new ArrayList<>();

			serviceAt("2026-05-12T10:15:00Z").backupTenantDatabase(session,
					(completedSteps, totalSteps, messageKey) -> progressMessages.add(completedSteps + "/" + totalSteps + ":" + messageKey));

			assertEquals(List.of("0/5:UI_TENANT_BACKUP_PROGRESS_START", "0/5:UI_TENANT_BACKUP_PROGRESS_OPEN",
					"1/5:UI_TENANT_BACKUP_PROGRESS_WEEKLY", "2/5:UI_TENANT_BACKUP_PROGRESS_BIMONTHLY",
					"3/5:UI_TENANT_BACKUP_PROGRESS_YEARLY", "5/5:UI_TENANT_BACKUP_PROGRESS_FINISHED"), progressMessages);
		}
	}

	@Test
	void shouldSkipPeriodicBackupsWhenLatestBackupIsTooRecent() throws Exception {
		try (TenantSession session = createSession()) {
			TenantPaths tenantPaths = session.paths();
			Files.writeString(tenantPaths.databaseFile(), "new-db-content");
			writeBackupPlaceholder(tenantPaths, "gbanking.db.weekly.20260501_1215.gbbackup");
			writeBackupPlaceholder(tenantPaths, "gbanking.db.weekly.20260511_1215.gbbackup");
			writeBackupPlaceholder(tenantPaths, "gbanking.db.bimonthly.20251101_1215.gbbackup");
			writeBackupPlaceholder(tenantPaths, "gbanking.db.bimonthly.20251201_1215.gbbackup");
			writeBackupPlaceholder(tenantPaths, "gbanking.db.bimonthly.20260201_1215.gbbackup");
			writeBackupPlaceholder(tenantPaths, "gbanking.db.bimonthly.20260412_1215.gbbackup");
			writeBackupPlaceholder(tenantPaths, "gbanking.db.yearly.20240101_1215.gbbackup");
			writeBackupPlaceholder(tenantPaths, "gbanking.db.yearly.20260101_1215.gbbackup");

			serviceAt("2026-05-12T10:15:00Z").backupTenantDatabase(session);

			assertEquals("new-db-content", readEntry(
					tenantPaths.backupDirectory().resolve("gbanking.db.backup_on_open.gbbackup"), "db/gbanking.db"));
			assertEquals(1, countBackups(tenantPaths, "gbanking.db.weekly."));
			assertEquals(3, countBackups(tenantPaths, "gbanking.db.bimonthly."));
			assertEquals(1, countBackups(tenantPaths, "gbanking.db.yearly."));
			assertFalse(Files.exists(tenantPaths.backupDirectory().resolve("gbanking.db.bimonthly.20251101_1215.gbbackup")));
		}
	}

	@Test
	void shouldApplyRetentionRulesAfterCreatingDueBackups() throws Exception {
		try (TenantSession session = createSession()) {
			TenantPaths tenantPaths = session.paths();
			Files.writeString(tenantPaths.databaseFile(), "db-content");
			writeBackupPlaceholder(tenantPaths, "gbanking.db.weekly.20260501_1215.gbbackup");
			writeBackupPlaceholder(tenantPaths, "gbanking.db.bimonthly.20251001_1215.gbbackup");
			writeBackupPlaceholder(tenantPaths, "gbanking.db.bimonthly.20251201_1215.gbbackup");
			writeBackupPlaceholder(tenantPaths, "gbanking.db.bimonthly.20260201_1215.gbbackup");
			writeBackupPlaceholder(tenantPaths, "gbanking.db.yearly.20240101_1215.gbbackup");

			serviceAt("2026-05-12T10:15:00Z").backupTenantDatabase(session);

			assertFalse(Files.exists(tenantPaths.backupDirectory().resolve("gbanking.db.weekly.20260501_1215.gbbackup")));
			assertTrue(Files.exists(tenantPaths.backupDirectory().resolve("gbanking.db.weekly.20260512_1215.gbbackup")));
			assertFalse(Files.exists(tenantPaths.backupDirectory().resolve("gbanking.db.bimonthly.20251001_1215.gbbackup")));
			assertEquals(3, countBackups(tenantPaths, "gbanking.db.bimonthly."));
			assertFalse(Files.exists(tenantPaths.backupDirectory().resolve("gbanking.db.yearly.20240101_1215.gbbackup")));
			assertTrue(Files.exists(tenantPaths.backupDirectory().resolve("gbanking.db.yearly.20260512_1215.gbbackup")));
		}
	}

	@Test
	void shouldNotCreateBackupsWhenDatabaseDoesNotExist() throws Exception {
		try (TenantSession session = createSession()) {
			serviceAt("2026-05-12T10:15:00Z").backupTenantDatabase(session);
			try (var files = Files.list(session.paths().backupDirectory())) {
				assertEquals(0, files.count());
			}
		}
	}

	@Test
	void shouldFindDatabaseOnlyInTenantDatabaseDirectory() throws Exception {
		try (TenantSession session = createSession()) {
			TenantPaths tenantPaths = session.paths();
			TenantBackupManager service = serviceAt("2026-05-12T10:15:00Z");
			assertFalse(service.hasTenantDatabase(session));
			Files.writeString(tenantPaths.tenantDirectory().resolve("gbanking.db"), "wrong-location");
			assertFalse(service.hasTenantDatabase(session));
			Files.writeString(tenantPaths.databaseFile(), "db-content");
			assertTrue(service.hasTenantDatabase(session));
		}
	}

	private TenantSession createSession() {
		TenantStore tenantStore = new TenantStore(tempDirectory.resolve("data-" + UUID.randomUUID()));
		TenantProfile profile = tenantStore.createTenant("tenant", "secret".toCharArray());
		return tenantStore.authenticateSession(profile.id(), "secret".toCharArray()).orElseThrow();
	}

	private void writeBackupPlaceholder(TenantPaths tenantPaths, String fileName) throws Exception {
		Files.writeString(tenantPaths.backupDirectory().resolve(fileName), "placeholder");
	}

	private TenantBackupManager serviceAt(String instant) {
		return new TenantBackupManager(Clock.fixed(Instant.parse(instant), TEST_ZONE));
	}

	private long countBackups(TenantPaths tenantPaths, String prefix) throws Exception {
		try (var files = Files.list(tenantPaths.backupDirectory())) {
			return files.map(path -> Objects.requireNonNull(path.getFileName(), "file name").toString())
					.filter(name -> name.startsWith(prefix) && name.endsWith(".gbbackup"))
					.count();
		}
	}

	private Set<String> entryNames(Path backupPath) throws Exception {
		Path zipPath = decryptBackup(backupPath);
		try (ZipFile zipFile = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
			return zipFile.stream().map(entry -> entry.getName()).collect(Collectors.toSet());
		} finally {
			Files.deleteIfExists(zipPath);
		}
	}

	private String readEntry(Path backupPath, String entryName) throws Exception {
		Path zipPath = decryptBackup(backupPath);
		try (ZipFile zipFile = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
			var entry = zipFile.getEntry(entryName);
			assertNotNull(entry);
			try (var inputStream = zipFile.getInputStream(entry)) {
				return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
			}
		} finally {
			Files.deleteIfExists(zipPath);
		}
	}

	private Path decryptBackup(Path backupPath) throws Exception {
		Path zipPath = tempDirectory.resolve(UUID.randomUUID() + ".zip");
		char[] password = "secret".toCharArray();
		try {
			new TenantEncryptionManager().decryptContainer(backupPath, zipPath, password);
		} finally {
			Arrays.fill(password, '\0');
		}
		return zipPath;
	}
}
