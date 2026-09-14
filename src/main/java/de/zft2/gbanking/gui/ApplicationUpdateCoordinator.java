package de.zft2.gbanking.gui;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.concurrent.CancellationSupport;
import de.zft2.gbanking.db.BuildInfo;
import de.zft2.gbanking.gui.BackgroundActionCoordinator.ActionScope;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.dialog.tenant.TenantLoginDialog.BackupOperationResult;
import de.zft2.gbanking.gui.util.FxNodeSupport;
import de.zft2.gbanking.gui.util.FxThreadSupport;
import de.zft2.gbanking.messages.Messages;
import de.zft2.gbanking.update.PreparedUpdate;
import de.zft2.gbanking.update.UpdateManager;
import de.zft2.gbanking.update.UpdateProgressListener;
import de.zft2.gbanking.update.UpdateRelease;
import javafx.concurrent.Task;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;

final class ApplicationUpdateCoordinator implements BaseGui {

	private static final Logger log = LogManager.getLogger(ApplicationUpdateCoordinator.class);
	private static final double BYTES_PER_MEGABYTE = 1024d * 1024d;
	private static final double MAX_PROGRESS = 1d;

	private final GBankingGui gui;
	private final Label statusLabel;
	private final ProgressBar progressBar;
	private final Label progressLabel;
	private final UpdateManager updateManager = new UpdateManager();

	ApplicationUpdateCoordinator(GBankingGui gui, Label statusLabel, ProgressBar progressBar, Label progressLabel) {
		this.gui = gui;
		this.statusLabel = statusLabel;
		this.progressBar = progressBar;
		this.progressLabel = progressLabel;
	}

	void cleanupSuccessfulUpdateBackups() {
		updateManager.cleanupSuccessfulUpdateBackups();
	}

	void checkForUpdates() {
		log.info("Checking for application updates.");
		hideDownloadProgress();
		if (!updateManager.canInstallUpdates()) {
			showWarning(gui.getStage(), getText("UI_UPDATE_UNSUPPORTED_LAYOUT"));
			return;
		}

		Task<Optional<UpdateRelease>> updateCheckTask = new Task<>() {
			@Override
			protected Optional<UpdateRelease> call() throws Exception {
				updateMessage(getText("UI_UPDATE_CHECKING"));
				return updateManager.findUpdate();
			}
		};
		bindStatus(updateCheckTask);
		updateCheckTask.setOnSucceeded(event -> handleUpdateCheckResult(updateCheckTask.getValue()));
		updateCheckTask.setOnFailed(event -> showUpdateFailure(updateCheckTask.getException()));
		startBackgroundTask(updateCheckTask, "gbanking-update-check", ActionScope.INDEPENDENT);
	}

	private void handleUpdateCheckResult(Optional<UpdateRelease> updateRelease) {
		if (updateRelease.isEmpty()) {
			log.info("No application update available.");
			showInfo(gui.getStage(), getText("UI_UPDATE_NO_UPDATE", BuildInfo.getProgramVersion()));
			return;
		}

		UpdateRelease release = updateRelease.get();
		log.info("Application update available. currentVersion={}, latestVersion={}", BuildInfo.getProgramVersion(), release.version());
		if (DialogWindowSupport.showConfirmation(gui.getStage(),
				getText("UI_UPDATE_AVAILABLE", release.version(), BuildInfo.getProgramVersion()), ButtonType.OK, ButtonType.CANCEL)) {
			installUpdate(release);
		}
	}

	private void installUpdate(UpdateRelease release) {
		Task<PreparedUpdate> installTask = new Task<>() {
			@Override
			protected PreparedUpdate call() throws Exception {
				CancellationSupport.throwIfCancellationRequested();
				updateMessage(getText("UI_UPDATE_PREPARING"));
				updateDownloadProgress(0L, release.applicationAsset().size());
				PreparedUpdate preparedUpdate = updateManager.downloadAndPrepare(release, new UpdateProgressListener() {
					@Override
					public void onProgress(String message) {
						CancellationSupport.throwIfCancellationRequested();
						updateMessage(getText("UI_UPDATE_PREPARING"));
					}

					@Override
					public void onDownloadProgress(long downloadedBytes, long totalBytes) {
						CancellationSupport.throwIfCancellationRequested();
						long effectiveTotalBytes = totalBytes > 0 ? totalBytes : release.applicationAsset().size();
						if (effectiveTotalBytes > 0) {
							updateProgress(downloadedBytes, effectiveTotalBytes);
						} else {
							updateProgress(-1, 1);
						}
						updateDownloadProgress(downloadedBytes, effectiveTotalBytes);
					}
				});
				CancellationSupport.throwIfCancellationRequested();
				updateMessage(getText("UI_UPDATE_EXECUTING"));
				return preparedUpdate;
			}
		};
		bindStatus(installTask);
		installTask.setOnSucceeded(event -> launchPreparedUpdate(installTask.getValue()));
		installTask.setOnFailed(event -> showUpdateFailure(installTask.getException()));
		startBackgroundTask(installTask, "gbanking-update-install", ActionScope.INDEPENDENT);
	}

	private void launchPreparedUpdate(PreparedUpdate preparedUpdate) {
		hideDownloadProgress();
		statusLabel.setText(getText("UI_UPDATE_EXECUTING"));
		gui.beginUpdateLifecycleTransition(() -> backupAndLaunchPreparedUpdate(preparedUpdate));
	}

	private void backupAndLaunchPreparedUpdate(PreparedUpdate preparedUpdate) {
		BackupOperationResult backupResult = gui.runTenantBackupForUpdate();
		if (!backupResult.succeeded()) {
			gui.abortLifecycleTransition();
			String messageKey = backupResult.integrityCheckFailed() ? "UI_UPDATE_BACKUP_INTEGRITY_ERROR" : "UI_UPDATE_BACKUP_ERROR";
			showWarning(gui.getStage(), getText(messageKey));
			return;
		}

		try {
			updateManager.launchInstaller(preparedUpdate);
			log.info("Shutting down GBanking for application update.");
			gui.completeShutdown();
		} catch (Exception e) {
			gui.abortLifecycleTransition();
			showUpdateFailure(e);
		}
	}

	private void bindStatus(Task<?> task) {
		task.messageProperty().addListener((observable, oldMessage, newMessage) -> {
			if (newMessage != null && !newMessage.isBlank()) {
				statusLabel.setText(newMessage);
			}
		});
	}

	private void updateDownloadProgress(long downloadedBytes, long totalBytes) {
		FxThreadSupport.run(() -> {
			FxNodeSupport.setVisibleManaged(progressBar, true);
			FxNodeSupport.setVisibleManaged(progressLabel, true);
			if (totalBytes > 0) {
				double progress = Math.min(MAX_PROGRESS, Math.max(0d, downloadedBytes / (double) totalBytes));
				progressBar.setProgress(progress);
				progressLabel.setText(getText("UI_UPDATE_DOWNLOAD_PROGRESS", formatMegabytes(downloadedBytes), formatMegabytes(totalBytes)));
			} else {
				progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
				progressLabel.setText(getText("UI_UPDATE_DOWNLOAD_PROGRESS_UNKNOWN", formatMegabytes(downloadedBytes)));
			}
		});
	}

	void hideDownloadProgress() {
		FxThreadSupport.run(() -> {
			progressBar.setProgress(0d);
			FxNodeSupport.setVisibleManaged(progressBar, false);
			progressLabel.setText("");
			FxNodeSupport.setVisibleManaged(progressLabel, false);
		});
	}

	private String formatMegabytes(long bytes) {
		double megabytes = Math.max(0L, bytes) / BYTES_PER_MEGABYTE;
		return String.format(Messages.getLocale(), "%.1f", megabytes);
	}

	private void showUpdateFailure(Throwable throwable) {
		hideDownloadProgress();
		log.error("Application update failed", throwable);
		String message = throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "unknown";
		showWarning(gui.getStage(), getText("UI_UPDATE_ERROR", message));
	}
}
