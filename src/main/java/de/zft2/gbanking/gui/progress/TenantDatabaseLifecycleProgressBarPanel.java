package de.zft2.gbanking.gui.progress;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DatabaseIntegrityException;
import de.zft2.gbanking.db.DbRuntimeContext;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import de.zft2.gbanking.tenant.TenantBackupCoordinator;
import de.zft2.gbanking.tenant.TenantBackupCoordinator.RestoreResult;
import de.zft2.gbanking.tenant.TenantBackupManager;
import de.zft2.gbanking.tenant.TenantDatabaseLifecycleManager;
import de.zft2.gbanking.tenant.TenantDatabaseLifecycleManager.OpenResult;
import de.zft2.gbanking.tenant.TenantSession;
import javafx.concurrent.Task;
import javafx.stage.Stage;
import javafx.stage.Window;

public class TenantDatabaseLifecycleProgressBarPanel extends BaseFileProgressBarPanel {

	private static final Logger log = LogManager.getLogger(TenantDatabaseLifecycleProgressBarPanel.class);
	private static final int OPEN_TOTAL_STEPS = 11;

	private final Operation operation;
	private final TenantSession session;
	private final Path restoreFile;
	private final boolean allowMissingInstituteDatabase;
	private final TenantDatabaseLifecycleManager lifecycleManager = new TenantDatabaseLifecycleManager();
	private final TenantBackupManager backupManager = new TenantBackupManager();
	private final TenantBackupCoordinator backupCoordinator = new TenantBackupCoordinator();
	private volatile boolean failed;
	private volatile boolean backupFailed;
	private volatile boolean integrityCheckFailed;
	private OpenResult openResult;
	private Path createdBackupFile;
	private RestoreResult restoreResult;

	public TenantDatabaseLifecycleProgressBarPanel(Window parentWindow, boolean opening, TenantSession session) {
		this(parentWindow, opening, session, false);
	}

	public TenantDatabaseLifecycleProgressBarPanel(Window parentWindow, boolean opening, TenantSession session,
			boolean allowMissingInstituteDatabase) {
		this(parentWindow, session, opening ? Operation.OPEN : Operation.CLOSE, null, allowMissingInstituteDatabase);
	}

	public TenantDatabaseLifecycleProgressBarPanel(Window parentWindow, TenantSession session) {
		this(parentWindow, session, Operation.BACKUP, null, false);
	}

	public TenantDatabaseLifecycleProgressBarPanel(Window parentWindow, TenantSession session, Path restoreFile) {
		this(parentWindow, session, Operation.RESTORE, restoreFile, false);
	}

	private TenantDatabaseLifecycleProgressBarPanel(Window parentWindow, TenantSession session, Operation operation, Path restoreFile,
			boolean allowMissingInstituteDatabase) {
		super(parentWindow);
		this.session = session;
		this.operation = operation;
		this.restoreFile = restoreFile;
		this.allowMissingInstituteDatabase = allowMissingInstituteDatabase;
	}

	@Override
	protected String getWindowTitle() {
		return getText(switch (operation) {
		case OPEN -> "UI_TENANT_DB_OPEN_TITLE";
		case CLOSE -> "UI_TENANT_DB_CLOSE_TITLE";
		case BACKUP -> "UI_TENANT_MANUAL_BACKUP_TITLE";
		case RESTORE -> "UI_TENANT_RESTORE_TITLE";
		});
	}

	@Override
	public Stage createNewFileImportProgressBarWindow() {
		Stage stage = super.createNewFileImportProgressBarWindow();
		stage.setOnCloseRequest(event -> {
			if (task != null && task.isRunning()) {
				event.consume();
			}
		});
		return stage;
	}

	public void startTask() {
		task = new LifecycleTask();
		startTaskDirectly("tenant-" + operation.name().toLowerCase(java.util.Locale.ROOT));
	}

	@Override
	protected void startTask(String fileName, ExportType exportType, AccountListPanel accountListPanel) {
		startTask();
	}

	@Override
	protected void onTaskFailed(Throwable ex) {
		failed = true;
		integrityCheckFailed = containsIntegrityFailure(ex);
		log.error("Tenant database lifecycle operation failed: {}", operation, ex);
	}

	public boolean hasFailed() {
		return failed;
	}

	public boolean hasBackupFailed() {
		return backupFailed;
	}

	public boolean hasIntegrityCheckFailed() {
		return integrityCheckFailed;
	}

	public OpenResult getOpenResult() {
		return openResult;
	}

	public Path getCreatedBackupFile() {
		return createdBackupFile;
	}

	public RestoreResult getRestoreResult() {
		return restoreResult;
	}

	private boolean containsIntegrityFailure(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (current instanceof DatabaseIntegrityException) {
				return true;
			}
		}
		return false;
	}

	private enum Operation {
		OPEN,
		CLOSE,
		BACKUP,
		RESTORE
	}

	private final class LifecycleTask extends Task<Void> {

		@Override
		protected Void call() throws Exception {
			switch (operation) {
			case OPEN -> openTenantDatabase();
			case CLOSE -> lifecycleManager.closeAndEncryptDatabase(session,
					(completedSteps, totalSteps, messageKey) -> updateStatus(completedSteps, totalSteps, messageKey));
			case BACKUP -> createdBackupFile = backupCoordinator.createManualBackup(session,
					(completedSteps, totalSteps, messageKey) -> updateStatus(completedSteps, totalSteps, messageKey));
			case RESTORE -> restoreResult = backupCoordinator.restoreBackup(session, restoreFile,
					(completedSteps, totalSteps, messageKey) -> updateStatus(completedSteps, totalSteps, messageKey));
			}
			return null;
		}

		private void openTenantDatabase() throws IOException {
			openResult = lifecycleManager.prepareDatabaseForOpen(session,
					(completedSteps, totalSteps, messageKey) -> updateOpenPreparationProgress(completedSteps, messageKey));
			validateDatabaseBeforeBackup();
			backupDatabase();

			updateProgress(10, OPEN_TOTAL_STEPS);
			updateMessage(getText("UI_TENANT_DB_OPEN_PROGRESS_SQLITE"));
			DbRuntimeContext.setCurrentTenantPaths(session.paths());
			DBController.getInstance(".", (migrationVersion, completed, completedSteps, totalSteps) ->
					updateMigrationProgress(migrationVersion, completed, completedSteps, totalSteps), allowMissingInstituteDatabase);
			updateProgress(OPEN_TOTAL_STEPS, OPEN_TOTAL_STEPS);
			updateMessage(getText("UI_TENANT_DB_OPEN_PROGRESS_FINISHED"));
		}

		private void validateDatabaseBeforeBackup() {
			updateProgress(3, OPEN_TOTAL_STEPS);
			if (!backupManager.hasTenantDatabase(session)) {
				updateProgress(4, OPEN_TOTAL_STEPS);
				updateMessage(getText("UI_TENANT_DB_OPEN_PROGRESS_INTEGRITY_SKIPPED"));
				return;
			}

			boolean fullIntegrityCheck = openResult.recoveredPlaintext();
			String messageKey;
			if (openResult.sqliteRecoveryRequired()) {
				messageKey = "UI_TENANT_DB_OPEN_PROGRESS_RECOVER_SQLITE";
			} else {
				messageKey = fullIntegrityCheck ? "UI_TENANT_DB_OPEN_PROGRESS_INTEGRITY_FULL" : "UI_TENANT_DB_OPEN_PROGRESS_INTEGRITY_QUICK";
			}
			updateMessage(getText(messageKey));
			DBController.validateDatabaseIntegrity(session.paths().databaseFile(), fullIntegrityCheck);
			updateProgress(4, OPEN_TOTAL_STEPS);
			updateMessage(getText("UI_TENANT_DB_OPEN_PROGRESS_INTEGRITY_OK"));
		}

		private void backupDatabase() {
			if (!backupManager.hasTenantDatabase(session)) {
				updateProgress(9, OPEN_TOTAL_STEPS);
				return;
			}
			try {
				backupManager.backupTenantDatabase(session,
						(completedSteps, totalSteps, messageKey) -> updateOpenBackupProgress(completedSteps, totalSteps, messageKey));
			} catch (IOException | RuntimeException e) {
				backupFailed = true;
				log.error("Could not create encrypted tenant backup", e);
				updateMessage(getText("UI_TENANT_BACKUP_PROGRESS_FAILED_CONTINUE"));
			}
		}

		private void updateOpenPreparationProgress(int completedSteps, String messageKey) {
			updateProgress(Math.min(completedSteps, 3), OPEN_TOTAL_STEPS);
			updateMessage(getText(messageKey));
		}

		private void updateOpenBackupProgress(int completedSteps, int totalSteps, String messageKey) {
			double progress = totalSteps > 0 ? (double) completedSteps / totalSteps : 1d;
			updateProgress(4d + 5d * progress, OPEN_TOTAL_STEPS);
			updateMessage(getText(messageKey));
		}

		private void updateMigrationProgress(String migrationVersion, boolean completed, int completedSteps, int totalSteps) {
			double progress = totalSteps > 0 ? (double) completedSteps / totalSteps : 1d;
			updateProgress(10d + progress, OPEN_TOTAL_STEPS);
			updateMessage(getText(completed ? "UI_DB_MIGRATION_PROGRESS_APPLIED" : "UI_DB_MIGRATION_PROGRESS_APPLYING", migrationVersion));
		}

		private void updateStatus(int completedSteps, int totalSteps, String messageKey) {
			updateProgress(completedSteps, totalSteps);
			updateMessage(getText(messageKey));
		}
	}
}
