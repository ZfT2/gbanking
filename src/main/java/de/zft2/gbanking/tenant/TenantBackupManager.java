package de.zft2.gbanking.tenant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TenantBackupManager {

	private static final Logger log = LogManager.getLogger(TenantBackupManager.class);
	private static final String DB_FILE_NAME = TenantPaths.DATABASE_FILE_NAME;
	static final String BACKUP_SUFFIX = ".gbbackup";
	private static final String OPEN_BACKUP_NAME = DB_FILE_NAME + ".backup_on_open" + BACKUP_SUFFIX;
	private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");
	private static final DateTimeFormatter MANUAL_BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
	private static final String MANUAL_BACKUP_PREFIX = DB_FILE_NAME + ".manual.";
	private static final String RESTORE_POINT_BACKUP_PREFIX = DB_FILE_NAME + ".before_restore.";

	private static final BackupPlan WEEKLY_BACKUP = new BackupPlan(DB_FILE_NAME + ".weekly.", Period.ofDays(7), 1);
	private static final BackupPlan BIMONTHLY_BACKUP = new BackupPlan(DB_FILE_NAME + ".bimonthly.", Period.ofMonths(2), 3);
	private static final BackupPlan YEARLY_BACKUP = new BackupPlan(DB_FILE_NAME + ".yearly.", Period.ofDays(365), 1);
	private static final ProgressReporter NO_PROGRESS = (completedSteps, totalSteps, messageKey) -> {
	};

	private final Clock clock;
	private final TenantEncryptionManager encryptionManager;

	public TenantBackupManager() {
		this(Clock.systemDefaultZone(), new TenantEncryptionManager());
	}

	TenantBackupManager(Clock clock) {
		this(clock, new TenantEncryptionManager());
	}

	TenantBackupManager(Clock clock, TenantEncryptionManager encryptionManager) {
		this.clock = clock;
		this.encryptionManager = encryptionManager;
	}

	public void backupTenantDatabase(TenantSession session) throws IOException {
		backupTenantDatabase(session, NO_PROGRESS);
	}

	public void backupTenantDatabase(TenantSession session, ProgressReporter progressReporter) throws IOException {
		ProgressReporter progress = progressReporter != null ? progressReporter : NO_PROGRESS;
		TenantPaths tenantPaths = session.paths();
		Path dbFile = tenantPaths.databaseFile();
		progress.report(0, 5, "UI_TENANT_BACKUP_PROGRESS_START");
		log.info("Starting tenant backup process for {}", dbFile.getFileName());
		log.debug("Database backup source path: {}", dbFile);
		if (!Files.isRegularFile(dbFile)) {
			log.info("Skipping tenant backup process, no database file found.");
			log.debug("Database backup source path: {}", dbFile);
			progress.report(5, 5, "UI_TENANT_BACKUP_PROGRESS_SKIPPED");
			return;
		}
		Files.createDirectories(tenantPaths.backupDirectory());
		Files.createDirectories(tenantPaths.accountStatementsDirectory());

		progress.report(0, 5, "UI_TENANT_BACKUP_PROGRESS_OPEN");
		createEncryptedZipBackup(session, tenantPaths.backupDirectory().resolve(OPEN_BACKUP_NAME));
		progress.report(1, 5, "UI_TENANT_BACKUP_PROGRESS_WEEKLY");
		createPeriodicBackup(session, WEEKLY_BACKUP);
		progress.report(2, 5, "UI_TENANT_BACKUP_PROGRESS_BIMONTHLY");
		createPeriodicBackup(session, BIMONTHLY_BACKUP);
		progress.report(3, 5, "UI_TENANT_BACKUP_PROGRESS_YEARLY");
		createPeriodicBackup(session, YEARLY_BACKUP);
		progress.report(5, 5, "UI_TENANT_BACKUP_PROGRESS_FINISHED");
		log.info("Finished tenant backup process for {}", dbFile.getFileName());
	}

	public boolean hasTenantDatabase(TenantSession session) {
		return Files.isRegularFile(session.paths().databaseFile());
	}

	Path createManualBackup(TenantSession session) throws IOException {
		return createTimestampedBackup(session, MANUAL_BACKUP_PREFIX);
	}

	Path createRestorePointBackup(TenantSession session) throws IOException {
		return createTimestampedBackup(session, RESTORE_POINT_BACKUP_PREFIX);
	}

	private Path createTimestampedBackup(TenantSession session, String prefix) throws IOException {
		TenantPaths tenantPaths = session.paths();
		if (!Files.isRegularFile(tenantPaths.databaseFile())) {
			throw new IOException("Tenant database does not exist");
		}
		Files.createDirectories(tenantPaths.backupDirectory());
		Path backupFile = resolveAvailableBackupPath(tenantPaths.backupDirectory(), prefix);
		createEncryptedZipBackup(session, backupFile);
		return backupFile;
	}

	private Path resolveAvailableBackupPath(Path backupDirectory, String prefix) {
		String baseName = prefix + MANUAL_BACKUP_TIMESTAMP_FORMAT.format(LocalDateTime.now(clock));
		Path backupFile = backupDirectory.resolve(baseName + BACKUP_SUFFIX);
		int suffix = 2;
		while (Files.exists(backupFile)) {
			backupFile = backupDirectory.resolve(baseName + "_" + suffix++ + BACKUP_SUFFIX);
		}
		return backupFile;
	}

	private void createPeriodicBackup(TenantSession session, BackupPlan plan) throws IOException {
		TenantPaths tenantPaths = session.paths();
		Path backupDirectory = tenantPaths.backupDirectory();
		List<BackupFile> existingBackups = findBackups(backupDirectory, plan);
		if (isBackupDue(existingBackups, plan.interval())) {
			Path backupPath = backupDirectory.resolve(plan.prefix() + BACKUP_TIMESTAMP_FORMAT.format(now()) + BACKUP_SUFFIX);
			createEncryptedZipBackup(session, backupPath);
		} else {
			log.debug("Skipping tenant backup for prefix {}, latest backup is still within interval {}", plan.prefix(), plan.interval());
		}
		pruneBackups(backupDirectory, plan);
	}

	private void createEncryptedZipBackup(TenantSession session, Path backupFile) throws IOException {
		TenantPaths tenantPaths = session.paths();
		Path tempFile = backupFile.resolveSibling(backupFile.getFileName() + ".tmp");
		try {
			log.info("Creating encrypted tenant backup file {}", backupFile.getFileName());
			log.debug("Tenant backup target path: {}", backupFile);
			Files.deleteIfExists(tempFile);
			encryptionManager.writeEncryptedContent(tempFile, session, outputStream -> {
				try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
					addFile(zipOutputStream, tenantPaths.databaseFile(), TenantPaths.DATABASE_DIRECTORY_NAME + "/" + DB_FILE_NAME);
					addDirectoryTree(zipOutputStream, tenantPaths.accountStatementsDirectory(), tenantPaths.tenantDirectory());
				}
			});
			encryptionManager.moveAtomically(tempFile, backupFile);
			log.info("Successfully created encrypted tenant backup file {}", backupFile.getFileName());
		} catch (IOException | RuntimeException e) {
			log.error("Could not create encrypted tenant backup file {}", backupFile.getFileName(), e);
			log.debug("Tenant backup file creation failed. tenantDirectory={}, backupFile={}, tempFile={}", tenantPaths.tenantDirectory(), backupFile,
					tempFile, e);
			throw e;
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	private void addDirectoryTree(ZipOutputStream zipOutputStream, Path directory, Path archiveRoot) throws IOException {
		if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}

		try (Stream<Path> paths = Files.walk(directory)) {
			for (Path path : paths.sorted().toList()) {
				if (Files.isSymbolicLink(path)) {
					log.warn("Skipping symbolic link in tenant backup: {}", path);
					continue;
				}

				String entryName = archiveRoot.relativize(path).toString().replace('\\', '/');
				if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
					addDirectory(zipOutputStream, path, entryName + "/");
				} else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
					addFile(zipOutputStream, path, entryName);
				}
			}
		}
	}

	private void addDirectory(ZipOutputStream zipOutputStream, Path directory, String entryName) throws IOException {
		ZipEntry entry = new ZipEntry(entryName);
		entry.setTime(Files.getLastModifiedTime(directory, LinkOption.NOFOLLOW_LINKS).toMillis());
		zipOutputStream.putNextEntry(entry);
		zipOutputStream.closeEntry();
	}

	private void addFile(ZipOutputStream zipOutputStream, Path sourceFile, String entryName) throws IOException {
		ZipEntry entry = new ZipEntry(entryName);
		entry.setTime(Files.getLastModifiedTime(sourceFile, LinkOption.NOFOLLOW_LINKS).toMillis());
		zipOutputStream.putNextEntry(entry);
		Files.copy(sourceFile, zipOutputStream);
		zipOutputStream.closeEntry();
	}

	private boolean isBackupDue(List<BackupFile> backups, TemporalAmount interval) {
		Optional<LocalDateTime> latestBackup = backups.stream().map(BackupFile::timestamp).max(Comparator.naturalOrder());
		return latestBackup.isEmpty() || !now().isBefore(latestBackup.get().plus(interval));
	}

	private void pruneBackups(Path tenantDirectory, BackupPlan plan) throws IOException {
		List<BackupFile> backups = findBackups(tenantDirectory, plan).stream()
				.sorted(Comparator.comparing(BackupFile::timestamp).reversed())
				.toList();
		for (BackupFile backup : backups.stream().skip(plan.keepCount()).toList()) {
			log.debug("Deleting old tenant backup file {}", backup.path());
			Files.deleteIfExists(backup.path());
		}
	}

	private List<BackupFile> findBackups(Path tenantDirectory, BackupPlan plan) throws IOException {
		try (Stream<Path> files = Files.list(tenantDirectory)) {
			return files.filter(Files::isRegularFile)
					.flatMap(path -> parseBackupFile(path, plan).stream())
					.toList();
		}
	}

	private Optional<BackupFile> parseBackupFile(Path path, BackupPlan plan) {
		if (path == null)
			return Optional.empty();
		Path pathFileName = path.getFileName();
		if (pathFileName == null)
			return Optional.empty();
		String fileName = pathFileName.toString();
		if (!fileName.startsWith(plan.prefix()) || !fileName.endsWith(BACKUP_SUFFIX)) {
			return Optional.empty();
		}

		try {
			String timestamp = fileName.substring(plan.prefix().length(), fileName.length() - BACKUP_SUFFIX.length());
			return Optional.of(new BackupFile(path, LocalDateTime.parse(timestamp, BACKUP_TIMESTAMP_FORMAT)));
		} catch (DateTimeParseException e) {
			return Optional.empty();
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
	}

	private record BackupPlan(String prefix, TemporalAmount interval, int keepCount) {
	}

	private record BackupFile(Path path, LocalDateTime timestamp) {
	}

	@FunctionalInterface
	public interface ProgressReporter {

		void report(int completedSteps, int totalSteps, String messageKey);
	}
}
