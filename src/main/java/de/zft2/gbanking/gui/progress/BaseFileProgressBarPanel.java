package de.zft2.gbanking.gui.progress;

import java.util.Locale;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.gui.BackgroundActionCoordinator;
import de.zft2.gbanking.gui.BackgroundActionCoordinator.ActionScope;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import de.zft2.gbanking.gui.util.OperationDurationLabel;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public abstract class BaseFileProgressBarPanel implements BaseMessages {

	protected ProgressBar progressBar;
	protected Label progressLabel;
	protected Label statusLabel;
	protected TextArea taskOutput;
	protected Button closeButton;
	protected OperationDurationLabel durationLabel;
	protected VBox contentBox;
	protected Task<Void> task;
	protected final Window parentWindow;
	protected Stage dialogStage;
	protected AccountListPanel accountListPanel;

	protected BaseFileProgressBarPanel(Window parentWindow) {
		this.parentWindow = parentWindow;
	}

	public Stage createNewFileImportProgressBarWindow() {
		dialogStage = DialogWindowSupport.createModalStageWithTitle(parentWindow, getWindowTitle());
		createProgressPanel();
		return dialogStage;
	}

	protected String getWindowTitle() {
		return "Import";
	}

	private void createProgressPanel() {
		progressBar = new ProgressBar(0);
		progressBar.setMinWidth(280);
		progressBar.setMaxWidth(Double.MAX_VALUE);
		progressBar.setPrefWidth(280);

		progressLabel = new Label("0 %");
		progressLabel.setMinWidth(48);
		statusLabel = new Label();

		taskOutput = new TextArea();
		taskOutput.setEditable(false);
		taskOutput.setPrefRowCount(12);
		VBox.setVgrow(taskOutput, Priority.ALWAYS);

		closeButton = new Button(getText("UI_BUTTON_CLOSE"));
		closeButton.setVisible(false);
		closeButton.setManaged(false);
		closeButton.setOnAction(event -> closeDialog());
		durationLabel = new OperationDurationLabel();

		HBox progressBox = new HBox(10, progressBar, progressLabel);
		progressBox.setAlignment(Pos.CENTER_LEFT);
		progressBox.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(progressBar, Priority.ALWAYS);

		VBox topBox = new VBox(8, statusLabel, progressBox);
		contentBox = new VBox(8, taskOutput);
		BorderPane footer = new BorderPane();
		footer.setLeft(durationLabel);
		footer.setRight(closeButton);
		BorderPane.setAlignment(durationLabel, Pos.CENTER_LEFT);
		BorderPane.setAlignment(closeButton, Pos.CENTER_RIGHT);

		BorderPane root = new BorderPane();
		root.setPadding(new Insets(12));
		root.setTop(topBox);
		root.setCenter(contentBox);
		root.setBottom(footer);

		dialogStage.setScene(new Scene(root, 520, 320));
		dialogStage.setOnHidden(event -> durationLabel.stop());
	}

	protected abstract void startTask(String fileName, ExportType exportType, AccountListPanel accountListPanel);

	protected void startTask(AccountListPanel accountListPanel) {
		configureTask(accountListPanel);
		BackgroundActionCoordinator.getInstance().start(task, "gbanking-file-operation", getActionScope());
	}

	protected void startTaskDirectly(String threadName) {
		configureTask(null);
		Thread workerThread = new Thread(task, threadName);
		workerThread.setDaemon(true);
		workerThread.start();
	}

	private void configureTask(AccountListPanel accountListPanel) {
		this.accountListPanel = accountListPanel;

		progressBar.progressProperty().unbind();
		progressBar.progressProperty().bind(task.progressProperty());

		progressLabel.textProperty().unbind();
		progressLabel.textProperty().bind(Bindings.createStringBinding(() -> {
			double progress = task.getProgress();
			if (progress < 0) {
				return "0 %";
			}
			return String.format(Locale.ROOT, "%.0f %%", progress * 100.0);
		}, task.progressProperty()));

		statusLabel.textProperty().unbind();
		statusLabel.textProperty().bind(task.messageProperty());

		task.messageProperty().addListener((obs, oldMsg, newMsg) -> {
			if (newMsg != null && !newMsg.isBlank()) {
				Platform.runLater(() -> taskOutput.appendText(newMsg + System.lineSeparator()));
			}
		});

		task.stateProperty().addListener((obs, oldState, newState) -> {
			switch (newState) {
			case SUCCEEDED -> {
				durationLabel.stop();
				onTaskSucceeded();
				if (keepDialogOpenOnSuccess()) {
					showCloseButton();
				} else {
					closeDialog();
				}
			}
			case FAILED -> {
				durationLabel.stop();
				Throwable ex = task.getException();
				if (ex != null) {
					Platform.runLater(() -> taskOutput.appendText("Error: " + ex.getMessage() + System.lineSeparator()));
				}
				onTaskFailed(ex);
				closeDialog();
			}
			case CANCELLED -> {
				durationLabel.stop();
				closeDialog();
			}
			case RUNNING -> durationLabel.start();
			case READY, SCHEDULED -> {
				// Intermediate JavaFX task states do not require dialog actions.
			}
			}
		});

	}

	protected ActionScope getActionScope() {
		return ActionScope.DATABASE;
	}

	protected void onTaskSucceeded() {
		// Optional override
	}

	protected void onTaskFailed(Throwable ex) {
		// Optional override
	}

	protected boolean keepDialogOpenOnSuccess() {
		return false;
	}

	protected void showCloseButton() {
		progressBar.progressProperty().unbind();
		progressBar.setProgress(1d);
		progressLabel.textProperty().unbind();
		progressLabel.setText("100 %");
		closeButton.setVisible(true);
		closeButton.setManaged(true);
		closeButton.requestFocus();
		dialogStage.sizeToScene();
	}

	protected void closeDialog() {
		Platform.runLater(() -> {
			if (dialogStage != null) {
				dialogStage.close();
			}
		});
	}
}
