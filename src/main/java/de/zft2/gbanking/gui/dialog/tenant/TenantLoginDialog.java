package de.zft2.gbanking.gui.dialog.tenant;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.cache.InstituteLookupCache;
import de.zft2.gbanking.demo.DemoDataInstaller;
import de.zft2.gbanking.demo.DemoTenantService;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DbRuntimeContext;
import de.zft2.gbanking.gui.BaseGui;
import de.zft2.gbanking.gui.EnvironmentOptions;
import de.zft2.gbanking.gui.GBankingGui;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.progress.TenantDatabaseLifecycleProgressBarPanel;
import de.zft2.gbanking.logging.SensitiveDataMasker;
import de.zft2.gbanking.messages.Messages;
import de.zft2.gbanking.service.importproperties.ImportPropertiesSynchronizationService;
import de.zft2.gbanking.tenant.TenantFileEncryptionContext;
import de.zft2.gbanking.tenant.TenantLock;
import de.zft2.gbanking.tenant.TenantProfile;
import de.zft2.gbanking.tenant.TenantSession;
import de.zft2.gbanking.tenant.TenantStore;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

public class TenantLoginDialog implements BaseGui {

	private static final Logger log = LogManager.getLogger(TenantLoginDialog.class);

	private static final String LAST_TENANT_ID = "lastTenantId";

	private GBankingGui gui;
	private TenantLock tenantLock;
	private TenantSession activeSession;
	private TenantStore activeTenantStore;
	private final DemoDataInstaller demoDataInstaller = new DemoDataInstaller();

	public TenantLoginDialog(GBankingGui gui) {
		this.gui = gui;
	}

	public boolean loginTenant() {

		Map<String, String> optionsMap = gui.getOptionsMap();

		Messages.setLocale(Messages.localeFromCode(gui.getOptionsMap().get(LANGUAGE)));

		Path dataDirectory = EnvironmentOptions.resolveDataDirectory(optionsMap);
		Path workDirectory = EnvironmentOptions.usesExternalDataDirectory(optionsMap) ? EnvironmentOptions.resolveWorkDirectory() : null;
		if (!acquireDataDirectoryLock(dataDirectory)) {
			return false;
		}
		TenantStore tenantStore = new TenantStore(dataDirectory, workDirectory);
		log.info("Opening tenant login dialog.");
		log.debug("Using tenant data directory {} and local work directory {}", dataDirectory, workDirectory);
		TenantSelectionDialog tenantDialog = new TenantSelectionDialog(gui.getStage(), tenantStore);
		while (true) {
			Optional<TenantSelectionDialog.TenantLoginResult> loginResult = tenantDialog.showAndWait(optionsMap.get(LAST_TENANT_ID), optionsMap.get(LANGUAGE));
			if (loginResult.isEmpty()) {
				optionsMap.put(LANGUAGE, tenantDialog.getSelectedLanguageCode());
				gui.storeOptionsQuietly();
				log.info("Tenant login cancelled.");
				return false;
			}

			TenantSelectionDialog.TenantLoginResult result = loginResult.get();
			log.info("Tenant credentials accepted. tenantId={}", () -> SensitiveDataMasker.maskIdentifier(result.tenant().id()));
			optionsMap.put(LAST_TENANT_ID, result.lastSelectedTenantId());
			optionsMap.put(LANGUAGE, result.languageCode());
			boolean databaseOpened = openTenantDatabase(result.session(), result.tenant());
			if (!databaseOpened) {
				result.session().close();
				cleanupFailedDemoTenant(result, tenantStore);
			}
			boolean demoDataInstalled = databaseOpened && (!result.demoInitializationRequired()
					|| installDemoData(result.session(), tenantStore));
			if (demoDataInstalled) {
				initializeImportProperties();
				activateTenant(result.session(), tenantStore);
				log.info("Tenant activated. tenantId={}", () -> SensitiveDataMasker.maskIdentifier(result.tenant().id()));
				return true;
			}
		}
	}

	private void initializeImportProperties() {
		try {
			new ImportPropertiesSynchronizationService().initializeAndSynchronize();
		} catch (RuntimeException exception) {
			log.error("Could not initialize booking recognition properties", exception);
			showWarning(gui.getStage(), getText("ALERT_PATTERN_SETTINGS_SYNC_FAILED"));
		}
	}

	private boolean installDemoData(TenantSession session, TenantStore tenantStore) {
		try {
			demoDataInstaller.install();
			return true;
		} catch (RuntimeException exception) {
			log.error("Could not initialize demo data", exception);
			resetFailedDatabaseOpen();
			session.close();
			removeDemoTenant(tenantStore, exception);
			showWarning(gui.getStage(), getText("UI_ERROR_DEMO_INITIALIZATION_FAILED"));
			return false;
		}
	}

	private void cleanupFailedDemoTenant(TenantSelectionDialog.TenantLoginResult result, TenantStore tenantStore) {
		if (result.demoInitializationRequired()) {
			removeDemoTenant(tenantStore, null);
		}
	}

	private void removeDemoTenant(TenantStore tenantStore, RuntimeException originalFailure) {
		try {
			new DemoTenantService(tenantStore).removeDemoTenant();
		} catch (RuntimeException cleanupFailure) {
			if (originalFailure != null) {
				originalFailure.addSuppressed(cleanupFailure);
			}
			log.error("Could not remove failed demo tenant", cleanupFailure);
		}
	}

	private boolean acquireDataDirectoryLock(Path dataDirectory) {
		if (tenantLock != null && tenantLock.isFor(dataDirectory)) {
			return true;
		}

		try {
			Optional<TenantLock> acquiredLock = TenantLock.tryAcquire(dataDirectory);
			if (acquiredLock.isEmpty()) {
				log.info("Tenant data directory is already active in another application instance.");
				showInfo(gui.getStage(), getText("UI_INFO_DATA_DIRECTORY_ALREADY_ACTIVE"));
				return false;
			}
			releaseLockOnly();
			tenantLock = acquiredLock.get();
			log.debug("Tenant data directory lock acquired for {}", dataDirectory);
			return true;
		} catch (IOException e) {
			log.error("Could not acquire tenant data directory lock", e);
			log.debug("Tenant data directory lock path: {}", dataDirectory);
			showWarning(gui.getStage(), getText("UI_ERROR_DATA_DIRECTORY_LOCK"));
			return false;
		}
	}

	private void activateTenant(TenantSession session, TenantStore tenantStore) {
		TenantSession previousSession = activeSession;
		activeSession = session;
		activeTenantStore = tenantStore;
		TenantFileEncryptionContext.activate(session);
		if (previousSession != null && previousSession != session) {
			previousSession.close();
		}
	}

	private boolean openTenantDatabase(TenantSession session, TenantProfile tenant) {
		boolean instituteDatabaseAvailable = DBController.prepareInstituteDatabase(session.paths().dataDirectory());
		if (!instituteDatabaseAvailable && !confirmMissingInstituteDatabase()) {
			return false;
		}
		InstituteLookupCache.clear();
		TenantDatabaseLifecycleProgressBarPanel progressPanel = new TenantDatabaseLifecycleProgressBarPanel(gui.getStage(), true, session,
				!instituteDatabaseAvailable);
		showLifecycleProgress(progressPanel);
		if (progressPanel.hasFailed()) {
			if (log.isErrorEnabled()) {
				log.error("Could not open encrypted tenant database. tenantId={}", SensitiveDataMasker.maskIdentifier(tenant.id()));
			}
			resetFailedDatabaseOpen();
			String messageKey = progressPanel.hasIntegrityCheckFailed() ? "UI_ERROR_TENANT_DB_INTEGRITY" : "UI_ERROR_TENANT_DB_OPEN";
			showWarning(gui.getStage(), getText(messageKey, tenant.username()));
			return false;
		}
		if (progressPanel.hasBackupFailed()) {
			if (log.isErrorEnabled()) {
				log.error("Could not create encrypted tenant backup. tenantId={}", SensitiveDataMasker.maskIdentifier(tenant.id()));
			}
			showWarning(gui.getStage(), getText("UI_ERROR_TENANT_BACKUP", tenant.username()));
		}
		if (progressPanel.getOpenResult() != null && progressPanel.getOpenResult().recoveredPlaintext()) {
			showWarning(gui.getStage(), getText("UI_WARNING_TENANT_DB_PLAINTEXT_RECOVERY", tenant.username()));
		}
		return true;
	}

	private boolean confirmMissingInstituteDatabase() {
		ButtonType continueButton = new ButtonType(getText("UI_BUTTON_CONTINUE"), ButtonBar.ButtonData.OK_DONE);
		ButtonType cancelButton = new ButtonType(getText("UI_BUTTON_CANCEL"), ButtonBar.ButtonData.CANCEL_CLOSE);
		return DialogWindowSupport.showConfirmation(gui.getStage(), Alert.AlertType.WARNING, getText("UI_INSTITUTE_DB_MISSING_TITLE"),
				getText("UI_INSTITUTE_DB_MISSING_HEADER"), getText("UI_INSTITUTE_DB_MISSING_TEXT"), continueButton, cancelButton);
	}

	private void resetFailedDatabaseOpen() {
		try {
			DBController.resetConnection();
		} catch (RuntimeException resetFailure) {
			log.error("Could not reset database connection after failed tenant open", resetFailure);
		}
	}

	public boolean closeTenantDatabase() {
		if (activeSession == null) {
			return true;
		}
		refreshActiveTenantProfile();
		TenantDatabaseLifecycleProgressBarPanel progressPanel = new TenantDatabaseLifecycleProgressBarPanel(gui.getStage(), false, activeSession);
		showLifecycleProgress(progressPanel);
		return !progressPanel.hasFailed();
	}

	public boolean reopenActiveTenant() {
		if (activeSession == null) {
			return false;
		}
		if (isActiveDatabaseAlreadyOpen()) {
			return true;
		}
		refreshActiveTenantProfile();
		return openTenantDatabase(activeSession, activeSession.profile());
	}

	public Optional<Path> getActiveBackupDirectory() {
		return activeSession != null ? Optional.of(activeSession.paths().backupDirectory()) : Optional.empty();
	}

	public BackupOperationResult runBackupOperation(Path backupFile) {
		if (activeSession == null) {
			return BackupOperationResult.failed(false);
		}
		refreshActiveTenantProfile();
		TenantDatabaseLifecycleProgressBarPanel progressPanel = backupFile == null
				? new TenantDatabaseLifecycleProgressBarPanel(gui.getStage(), activeSession)
				: new TenantDatabaseLifecycleProgressBarPanel(gui.getStage(), activeSession, backupFile);
		showLifecycleProgress(progressPanel);
		var restoreResult = progressPanel.getRestoreResult();
		return new BackupOperationResult(!progressPanel.hasFailed(), progressPanel.hasIntegrityCheckFailed(),
				restoreResult != null ? restoreResult.safetyBackupFile() : progressPanel.getCreatedBackupFile(),
				restoreResult == null || restoreResult.cleanupComplete());
	}

	private void showLifecycleProgress(TenantDatabaseLifecycleProgressBarPanel progressPanel) {
		Stage progressWindow = progressPanel.createNewFileImportProgressBarWindow();
		progressWindow.setOnShown(event -> progressPanel.startTask());
		progressWindow.showAndWait();
	}

	private boolean isActiveDatabaseAlreadyOpen() {
		Path activeDatabaseDirectory = activeSession.paths().databaseDirectory().toAbsolutePath().normalize();
		Path currentDatabaseDirectory = Path.of(DbRuntimeContext.getCurrentDbDirectory()).toAbsolutePath().normalize();
		return activeDatabaseDirectory.equals(currentDatabaseDirectory) && DBController.hasOpenConnection();
	}

	private void refreshActiveTenantProfile() {
		if (activeSession == null || activeTenantStore == null) {
			return;
		}
		activeTenantStore.findById(activeSession.profile().id()).ifPresent(profile -> activeSession.updateProfile(profile));
	}

	public void releaseTenantLock() {
		if (activeSession != null) {
			TenantFileEncryptionContext.deactivate();
			activeSession.close();
			activeSession = null;
			activeTenantStore = null;
		}
		releaseLockOnly();
	}

	private void releaseLockOnly() {
		if (tenantLock == null) {
			return;
		}
		try {
			tenantLock.close();
			log.debug("Tenant lock released.");
		} catch (IOException e) {
			log.warn("Could not release tenant lock", e);
		} finally {
			tenantLock = null;
		}
	}

	public record BackupOperationResult(boolean succeeded, boolean integrityCheckFailed, Path backupFile, boolean cleanupComplete) {

		private static BackupOperationResult failed(boolean integrityCheckFailed) {
			return new BackupOperationResult(false, integrityCheckFailed, null, true);
		}
	}

}
