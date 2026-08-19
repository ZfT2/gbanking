package de.zft2.gbanking.tenant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DbRuntimeContext;

public final class TenantBackupCoordinator {

	private static final Logger log = LogManager.getLogger(TenantBackupCoordinator.class);
	private static final int MAX_ARCHIVE_ENTRIES = 100_000;
	private static final long MIN_MAX_EXTRACTED_BYTES = 256L * 1024L * 1024L;
	private static final long MAX_COMPRESSION_RATIO = 200L;
	private static final Path DATABASE_ARCHIVE_PATH = Path.of(TenantPaths.DATABASE_DIRECTORY_NAME, TenantPaths.DATABASE_FILE_NAME);
	private static final Path DATABASE_ARCHIVE_DIRECTORY = Path.of(TenantPaths.DATABASE_DIRECTORY_NAME);
	private static final Path STATEMENTS_ARCHIVE_DIRECTORY = Path.of(TenantPaths.ACCOUNT_STATEMENTS_DIRECTORY_NAME);
	private static final ProgressReporter NO_PROGRESS = (completedSteps, totalSteps, messageKey) -> {
	};

	private final TenantBackupManager backupManager;
	private final TenantEncryptionManager encryptionManager;
	private final DatabaseOperations databaseOperations;

	public TenantBackupCoordinator() {
		this(new TenantBackupManager(), new TenantEncryptionManager(), new DefaultDatabaseOperations());
	}

	TenantBackupCoordinator(TenantBackupManager backupManager, TenantEncryptionManager encryptionManager,
			DatabaseOperations databaseOperations) {
		this.backupManager = backupManager;
		this.encryptionManager = encryptionManager;
		this.databaseOperations = databaseOperations;
	}

	public Path createManualBackup(TenantSession session, ProgressReporter progressReporter) throws IOException {
		ProgressReporter progress = progressReporter != null ? progressReporter : NO_PROGRESS;
		boolean databaseClosed = false;
		try {
			progress.report(0, 4, "UI_TENANT_DB_CLOSE_PROGRESS_CLOSE");
			databaseOperations.close();
			databaseClosed = true;
			progress.report(1, 4, "UI_TENANT_DB_OPEN_PROGRESS_INTEGRITY_QUICK");
			databaseOperations.validate(session.paths().databaseFile(), false);
			TenantDatabaseLifecycleManager.removeSqliteSidecars(session.paths().databaseFile());
			progress.report(2, 4, "UI_TENANT_MANUAL_BACKUP_PROGRESS_CREATE");
			Path backupFile = backupManager.createManualBackup(session);
			progress.report(3, 4, "UI_TENANT_DB_OPEN_PROGRESS_SQLITE");
			databaseOperations.open(session);
			databaseClosed = false;
			progress.report(4, 4, "UI_TENANT_MANUAL_BACKUP_PROGRESS_FINISHED");
			return backupFile;
		} catch (IOException | RuntimeException failure) {
			if (databaseClosed) {
				reopenAfterFailure(session, failure);
			}
			throw failure;
		}
	}

	public RestoreResult restoreBackup(TenantSession session, Path backupFile, ProgressReporter progressReporter) throws IOException {
		ProgressReporter progress = progressReporter != null ? progressReporter : NO_PROGRESS;
		PreparedBackup preparedBackup = prepareBackup(session, backupFile, progress);
		boolean databaseClosed = false;
		RestoreSwap restoreSwap = null;
		try {
			progress.report(3, 8, "UI_TENANT_DB_CLOSE_PROGRESS_CLOSE");
			databaseOperations.close();
			databaseClosed = true;
			TenantDatabaseLifecycleManager.removeSqliteSidecars(session.paths().databaseFile());
			progress.report(4, 8, "UI_TENANT_RESTORE_PROGRESS_SAFETY_BACKUP");
			Path safetyBackup = backupManager.createRestorePointBackup(session);
			progress.report(5, 8, "UI_TENANT_RESTORE_PROGRESS_REPLACE");
			restoreSwap = new RestoreSwap(session.paths(), preparedBackup);
			restoreSwap.install();
			progress.report(6, 8, "UI_TENANT_DB_OPEN_PROGRESS_SQLITE");
			databaseOperations.open(session);
			databaseClosed = false;
			boolean cleanupComplete = deleteQuietly(session.paths().encryptedDatabaseFile());
			cleanupComplete &= restoreSwap.discardPreviousState();
			progress.report(8, 8, "UI_TENANT_RESTORE_PROGRESS_FINISHED");
			return new RestoreResult(safetyBackup, cleanupComplete);
		} catch (IOException | RuntimeException failure) {
			if (restoreSwap != null) {
				rollbackAndReopen(session, restoreSwap, failure);
			} else if (databaseClosed) {
				reopenAfterFailure(session, failure);
			}
			throw failure;
		} finally {
			preparedBackup.deleteTemporaryFiles();
		}
	}

	private PreparedBackup prepareBackup(TenantSession session, Path backupFile, ProgressReporter progress) throws IOException {
		Path normalizedBackup = backupFile != null ? backupFile.toAbsolutePath().normalize() : null;
		if (!isReadableBackupFile(normalizedBackup)) {
			throw new IOException("Selected tenant backup is not a readable .gbbackup file");
		}

		TenantPaths tenantPaths = session.paths();
		tenantPaths.createDirectories();
		Path workDirectory = Files.createTempDirectory(tenantPaths.tenantDirectory(), ".restore-");
		Path preparedDatabase = null;
		try {
			progress.report(0, 8, "UI_TENANT_RESTORE_PROGRESS_DECRYPT");
			Path zipFile = workDirectory.resolve("backup.zip");
			encryptionManager.decryptFile(normalizedBackup, zipFile, session.dataKey());
			Path extractionDirectory = workDirectory.resolve("content");
			Files.createDirectory(extractionDirectory);
			extractArchive(zipFile, extractionDirectory, maximumExtractedBytes(normalizedBackup));
			Files.deleteIfExists(zipFile);
			progress.report(1, 8, "UI_TENANT_RESTORE_PROGRESS_ARCHIVE_VALID");

			Path extractedDatabase = extractionDirectory.resolve(TenantPaths.DATABASE_DIRECTORY_NAME)
					.resolve(TenantPaths.DATABASE_FILE_NAME);
			if (!Files.isRegularFile(extractedDatabase, LinkOption.NOFOLLOW_LINKS)) {
				throw new IOException("Tenant backup does not contain db/gbanking.db");
			}
			Path statementsDirectory = extractionDirectory.resolve(TenantPaths.ACCOUNT_STATEMENTS_DIRECTORY_NAME);
			Files.createDirectories(statementsDirectory);
			preparedDatabase = Files.createTempFile(tenantPaths.databaseDirectory(), "gbanking.db.restore-", ".tmp");
			Files.copy(extractedDatabase, preparedDatabase, StandardCopyOption.REPLACE_EXISTING);
			progress.report(2, 8, "UI_TENANT_DB_OPEN_PROGRESS_INTEGRITY_FULL");
			databaseOperations.validate(preparedDatabase, true);
			TenantDatabaseLifecycleManager.removeSqliteSidecars(preparedDatabase);
			return new PreparedBackup(workDirectory, preparedDatabase, statementsDirectory);
		} catch (IOException | RuntimeException failure) {
			deleteQuietly(preparedDatabase);
			deleteTreeQuietly(workDirectory);
			throw failure;
		}
	}

	private boolean isReadableBackupFile(Path backupFile) {
		if (backupFile == null || !Files.isRegularFile(backupFile)) {
			return false;
		}
		Path fileName = backupFile.getFileName();
		return fileName != null && fileName.toString().endsWith(TenantBackupManager.BACKUP_SUFFIX);
	}

	private void extractArchive(Path zipFile, Path extractionDirectory, long maximumBytes) throws IOException {
		Path normalizedExtractionDirectory = extractionDirectory.toAbsolutePath().normalize();
		Set<Path> extractedPaths = new HashSet<>();
		long extractedBytes = 0L;
		try (ZipFile archive = new ZipFile(zipFile.toFile(), StandardCharsets.UTF_8)) {
			if (archive.size() > MAX_ARCHIVE_ENTRIES) {
				throw new IOException("Tenant backup contains too many archive entries");
			}
			var entries = archive.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				extractedBytes = extractArchiveEntry(archive, entry, normalizedExtractionDirectory, extractedPaths,
						extractedBytes, maximumBytes);
			}
		}
	}

	private long extractArchiveEntry(ZipFile archive, ZipEntry entry, Path extractionDirectory, Set<Path> extractedPaths,
			long extractedBytes, long maximumBytes) throws IOException {
		Path outputPath = extractionDirectory.resolve(validateArchivePath(entry)).normalize();
		if (!outputPath.startsWith(extractionDirectory) || !extractedPaths.add(outputPath)) {
			throw new IOException("Tenant backup contains an invalid or duplicate path: " + entry.getName());
		}
		if (entry.isDirectory()) {
			Files.createDirectories(outputPath);
			return extractedBytes;
		}

		Path outputDirectory = outputPath.getParent();
		if (outputDirectory == null) {
			throw new IOException("Tenant backup contains a file without a parent directory");
		}
		long entrySize = entry.getSize();
		if (entrySize < 0 || extractedBytes > maximumBytes - entrySize) {
			throw new IOException("Tenant backup exceeds the permitted extracted size");
		}
		Files.createDirectories(outputDirectory);
		try (var inputStream = archive.getInputStream(entry)) {
			if (Files.copy(inputStream, outputPath) != entrySize) {
				throw new IOException("Tenant backup entry has an invalid size: " + entry.getName());
			}
		}
		return extractedBytes + entrySize;
	}

	private Path validateArchivePath(ZipEntry entry) throws IOException {
		String entryName = entry.getName();
		if (entryName == null || entryName.isBlank()) {
			throw new IOException("Tenant backup contains an unnamed archive entry");
		}
		try {
			Path archivePath = Path.of(entryName.replace('\\', '/'));
			Path relativePath = archivePath.normalize();
			boolean unsafe = archivePath.isAbsolute() || archivePath.getNameCount() == 0;
			for (Path segment : archivePath) {
				unsafe |= "..".equals(segment.toString());
			}
			boolean databaseEntry = entry.isDirectory() ? relativePath.equals(DATABASE_ARCHIVE_DIRECTORY)
					: relativePath.equals(DATABASE_ARCHIVE_PATH);
			boolean statementEntry = relativePath.startsWith(STATEMENTS_ARCHIVE_DIRECTORY)
					&& (entry.isDirectory() || relativePath.getNameCount() > 1);
			if (unsafe || (!databaseEntry && !statementEntry)) {
				throw new IOException("Tenant backup contains an unsafe or unsupported path: " + entryName);
			}
			return relativePath;
		} catch (RuntimeException failure) {
			throw new IOException("Tenant backup contains an invalid path: " + entryName, failure);
		}
	}

	private long maximumExtractedBytes(Path backupFile) throws IOException {
		long backupSize = Files.size(backupFile);
		if (backupSize > Long.MAX_VALUE / MAX_COMPRESSION_RATIO) {
			return Long.MAX_VALUE;
		}
		return Math.max(MIN_MAX_EXTRACTED_BYTES, backupSize * MAX_COMPRESSION_RATIO);
	}

	private void rollbackAndReopen(TenantSession session, RestoreSwap restoreSwap, Throwable originalFailure) {
		try {
			databaseOperations.close();
			TenantDatabaseLifecycleManager.removeSqliteSidecars(session.paths().databaseFile());
		} catch (IOException | RuntimeException rollbackFailure) {
			originalFailure.addSuppressed(rollbackFailure);
			return;
		}
		restoreSwap.rollback(originalFailure);
		try {
			databaseOperations.open(session);
		} catch (RuntimeException reopenFailure) {
			originalFailure.addSuppressed(reopenFailure);
		}
	}

	private void reopenAfterFailure(TenantSession session, Throwable originalFailure) {
		try {
			databaseOperations.open(session);
		} catch (RuntimeException reopenFailure) {
			originalFailure.addSuppressed(reopenFailure);
		}
	}

	private static boolean deleteQuietly(Path path) {
		if (path == null) {
			return true;
		}
		try {
			Files.deleteIfExists(path);
			return true;
		} catch (IOException failure) {
			log.warn("Could not delete temporary tenant restore file {}", path.getFileName(), failure);
			return false;
		}
	}

	private static boolean deleteTreeQuietly(Path directory) {
		try {
			deleteTree(directory);
			return true;
		} catch (IOException failure) {
			log.warn("Could not delete temporary tenant restore directory {}", directory, failure);
			return false;
		}
	}

	private static void deleteTree(Path directory) throws IOException {
		if (directory == null || !Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(directory)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	public record RestoreResult(Path safetyBackupFile, boolean cleanupComplete) {
	}

	@FunctionalInterface
	public interface ProgressReporter {

		void report(int completedSteps, int totalSteps, String messageKey);
	}

	interface DatabaseOperations {

		void close();

		void validate(Path databaseFile, boolean fullIntegrityCheck);

		void open(TenantSession session);
	}

	@FunctionalInterface
	private interface IoOperation {

		void run() throws IOException;
	}

	private static final class DefaultDatabaseOperations implements DatabaseOperations {

		@Override
		public void close() {
			DBController.resetConnection();
		}

		@Override
		public void validate(Path databaseFile, boolean fullIntegrityCheck) {
			DBController.validateDatabaseIntegrity(databaseFile, fullIntegrityCheck);
		}

		@Override
		public void open(TenantSession session) {
			DbRuntimeContext.setCurrentTenantPaths(session.paths());
			DBController.getInstance(".");
		}
	}

	private record PreparedBackup(Path workDirectory, Path databaseFile, Path statementsDirectory) {

		private void deleteTemporaryFiles() {
			deleteQuietly(databaseFile);
			deleteTreeQuietly(workDirectory);
		}
	}

	private final class RestoreSwap {

		private final Path databaseFile;
		private final Path previousDatabase;
		private final Path statementsDirectory;
		private final Path previousStatements;
		private final PreparedBackup preparedBackup;
		private final Deque<IoOperation> rollbackSteps = new ArrayDeque<>();
		private boolean statementsExisted;

		private RestoreSwap(TenantPaths paths, PreparedBackup preparedBackup) {
			databaseFile = paths.databaseFile();
			previousDatabase = databaseFile.resolveSibling("gbanking.db.before-restore-" + UUID.randomUUID() + ".tmp");
			statementsDirectory = paths.accountStatementsDirectory();
			previousStatements = paths.tenantDirectory().resolve(".accountStatements-before-restore-" + UUID.randomUUID());
			this.preparedBackup = preparedBackup;
		}

		private void install() throws IOException {
			if (!Files.isRegularFile(databaseFile, LinkOption.NOFOLLOW_LINKS)) {
				throw new IOException("Current tenant database does not exist");
			}
			encryptionManager.moveAtomically(databaseFile, previousDatabase);
			rollbackSteps.push(() -> encryptionManager.moveAtomically(previousDatabase, databaseFile));
			if (Files.exists(statementsDirectory, LinkOption.NOFOLLOW_LINKS)) {
				if (!Files.isDirectory(statementsDirectory, LinkOption.NOFOLLOW_LINKS)) {
					throw new IOException("Current account statements path is not a directory");
				}
				statementsExisted = true;
				encryptionManager.moveAtomically(statementsDirectory, previousStatements);
				rollbackSteps.push(() -> encryptionManager.moveAtomically(previousStatements, statementsDirectory));
			}
			encryptionManager.moveAtomically(preparedBackup.statementsDirectory(), statementsDirectory);
			rollbackSteps.push(() -> {
				deleteTree(statementsDirectory);
				if (!statementsExisted) {
					Files.createDirectories(statementsDirectory);
				}
			});
			encryptionManager.moveAtomically(preparedBackup.databaseFile(), databaseFile);
			rollbackSteps.push(() -> Files.deleteIfExists(databaseFile));
		}

		private void rollback(Throwable originalFailure) {
			IOException rollbackFailure = null;
			while (!rollbackSteps.isEmpty()) {
				try {
					rollbackSteps.pop().run();
				} catch (IOException failure) {
					if (rollbackFailure == null) {
						rollbackFailure = failure;
					} else {
						rollbackFailure.addSuppressed(failure);
					}
				}
			}
			if (rollbackFailure != null) {
				originalFailure.addSuppressed(rollbackFailure);
			}
		}

		private boolean discardPreviousState() {
			boolean databaseDeleted = deleteQuietly(previousDatabase);
			boolean statementsDeleted = !statementsExisted || deleteTreeQuietly(previousStatements);
			return databaseDeleted && statementsDeleted;
		}
	}
}
