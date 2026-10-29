package de.zft2.gbanking.tenant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.SqliteSidecarFiles;

public class TenantDatabaseLifecycleManager {

	private static final ProgressReporter NO_PROGRESS = (completedSteps, totalSteps, messageKey) -> {
	};

	private final TenantEncryptionManager encryptionManager;

	public TenantDatabaseLifecycleManager() {
		this(new TenantEncryptionManager());
	}

	TenantDatabaseLifecycleManager(TenantEncryptionManager encryptionManager) {
		this.encryptionManager = encryptionManager;
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
		encryptionManager.decryptFile(paths.encryptedDatabaseFile(), paths.databaseDecryptionTempFile(), session.dataKey());
		progress.report(1, 4, "UI_TENANT_DB_OPEN_PROGRESS_AUTHENTICATED");
		progress.report(2, 4, "UI_TENANT_DB_OPEN_PROGRESS_MOVE");
		encryptionManager.moveAtomically(paths.databaseDecryptionTempFile(), paths.databaseFile());
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
		encryptionManager.encryptFile(paths.databaseFile(), paths.databaseEncryptionTempFile(), session);
		progress.report(3, 5, "UI_TENANT_DB_CLOSE_PROGRESS_VERIFY");
		encryptionManager.verifyFile(paths.databaseEncryptionTempFile(), session.dataKey());
		encryptionManager.moveAtomically(paths.databaseEncryptionTempFile(), paths.encryptedDatabaseFile());
		progress.report(4, 5, "UI_TENANT_DB_CLOSE_PROGRESS_DELETE");
		Files.delete(paths.databaseFile());
		progress.report(5, 5, "UI_TENANT_DB_CLOSE_PROGRESS_FINISHED");
	}

	static void removeSqliteSidecars(Path databaseFile) throws IOException {
		for (Path sidecar : SqliteSidecarFiles.of(databaseFile)) {
			Files.deleteIfExists(sidecar);
			if (Files.exists(sidecar)) {
				throw new IOException("SQLite sidecar file could not be removed: " + sidecar.getFileName());
			}
		}
	}

	private boolean hasSqliteSidecars(Path databaseFile) {
		return SqliteSidecarFiles.of(databaseFile).stream().anyMatch(sidecar -> Files.exists(sidecar));
	}

	public record OpenResult(boolean recoveredPlaintext, boolean sqliteRecoveryRequired) {
	}

	@FunctionalInterface
	public interface ProgressReporter {

		void report(int completedSteps, int totalSteps, String messageKey);
	}
}
