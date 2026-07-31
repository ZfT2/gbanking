package de.zft2.gbanking.tenant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import de.zft2.gbanking.db.DBController;

public class TenantDatabaseLifecycleService {

	private static final ProgressReporter NO_PROGRESS = (completedSteps, totalSteps, messageKey) -> {
	};
	private static final List<String> SQLITE_SIDECAR_SUFFIXES = List.of("-journal", "-wal", "-shm");

	private final TenantEncryptionService encryptionService;

	public TenantDatabaseLifecycleService() {
		this(new TenantEncryptionService());
	}

	TenantDatabaseLifecycleService(TenantEncryptionService encryptionService) {
		this.encryptionService = encryptionService;
	}

	public OpenResult prepareDatabaseForOpen(TenantSession session, ProgressReporter progressReporter) throws IOException {
		ProgressReporter progress = progressReporter != null ? progressReporter : NO_PROGRESS;
		TenantPaths paths = session.paths();
		paths.createDirectories();
		Files.deleteIfExists(paths.databaseDecryptionTempFile());
		Files.deleteIfExists(paths.databaseEncryptionTempFile());

		boolean plaintextExists = Files.isRegularFile(paths.databaseFile());
		if (plaintextExists || !Files.isRegularFile(paths.encryptedDatabaseFile())) {
			boolean recoveredPlaintext = plaintextExists && Files.isRegularFile(paths.encryptedDatabaseFile());
			progress.report(1, 4, recoveredPlaintext ? "UI_TENANT_DB_OPEN_PROGRESS_RECOVERY" : "UI_TENANT_DB_OPEN_PROGRESS_NEW");
			progress.report(2, 4, "UI_TENANT_DB_OPEN_PROGRESS_PLAINTEXT_READY");
			progress.report(3, 4, "UI_TENANT_DB_OPEN_PROGRESS_MOVE_SKIPPED");
			return new OpenResult(recoveredPlaintext, plaintextExists && hasSqliteSidecars(paths.databaseFile()));
		}

		progress.report(0, 4, "UI_TENANT_DB_OPEN_PROGRESS_DECRYPT");
		encryptionService.decryptFile(paths.encryptedDatabaseFile(), paths.databaseDecryptionTempFile(), session.dataKey());
		progress.report(1, 4, "UI_TENANT_DB_OPEN_PROGRESS_AUTHENTICATED");
		progress.report(2, 4, "UI_TENANT_DB_OPEN_PROGRESS_MOVE");
		encryptionService.moveAtomically(paths.databaseDecryptionTempFile(), paths.databaseFile());
		progress.report(3, 4, "UI_TENANT_DB_OPEN_PROGRESS_MOVED");
		return new OpenResult(false, false);
	}

	public void closeAndEncryptDatabase(TenantSession session, ProgressReporter progressReporter) throws IOException {
		ProgressReporter progress = progressReporter != null ? progressReporter : NO_PROGRESS;
		TenantPaths paths = session.paths();
		progress.report(0, 5, "UI_TENANT_DB_CLOSE_PROGRESS_CLOSE");
		DBController.resetConnection();
		progress.report(1, 5, "UI_TENANT_DB_CLOSE_PROGRESS_SIDECARS");
		removeSqliteSidecars(paths.databaseFile());
		Files.deleteIfExists(paths.databaseEncryptionTempFile());

		if (!Files.isRegularFile(paths.databaseFile())) {
			progress.report(5, 5, "UI_TENANT_DB_CLOSE_PROGRESS_NO_DATABASE");
			return;
		}

		progress.report(2, 5, "UI_TENANT_DB_CLOSE_PROGRESS_ENCRYPT");
		encryptionService.encryptFile(paths.databaseFile(), paths.databaseEncryptionTempFile(), session);
		progress.report(3, 5, "UI_TENANT_DB_CLOSE_PROGRESS_VERIFY");
		encryptionService.verifyFile(paths.databaseEncryptionTempFile(), session.dataKey());
		encryptionService.moveAtomically(paths.databaseEncryptionTempFile(), paths.encryptedDatabaseFile());
		progress.report(4, 5, "UI_TENANT_DB_CLOSE_PROGRESS_DELETE");
		Files.delete(paths.databaseFile());
		progress.report(5, 5, "UI_TENANT_DB_CLOSE_PROGRESS_FINISHED");
	}

	static void removeSqliteSidecars(Path databaseFile) throws IOException {
		for (String suffix : SQLITE_SIDECAR_SUFFIXES) {
			Path sidecar = databaseFile.resolveSibling(databaseFile.getFileName() + suffix);
			Files.deleteIfExists(sidecar);
			if (Files.exists(sidecar)) {
				throw new IOException("SQLite sidecar file could not be removed: " + sidecar.getFileName());
			}
		}
	}

	private boolean hasSqliteSidecars(Path databaseFile) {
		for (String suffix : SQLITE_SIDECAR_SUFFIXES) {
			if (Files.exists(databaseFile.resolveSibling(databaseFile.getFileName() + suffix))) {
				return true;
			}
		}
		return false;
	}

	public record OpenResult(boolean recoveredPlaintext, boolean sqliteRecoveryRequired) {
	}

	@FunctionalInterface
	public interface ProgressReporter {

		void report(int completedSteps, int totalSteps, String messageKey);
	}
}
