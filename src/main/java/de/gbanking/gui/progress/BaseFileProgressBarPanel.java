package de.gbanking.gui.progress;

import de.gbanking.file.BaseFileTask;
import de.gbanking.gui.enu.FileType;
import de.gbanking.gui.panel.account.AccountListPanel;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public abstract class BaseFileProgressBarPanel {

	protected ProgressBar progressBar;
	protected Label progressLabel;
	protected Label statusLabel;
	protected TextArea taskOutput;
	protected BaseFileTask task;
	protected final Window parentWindow;
	protected Stage dialogStage;
	protected AccountListPanel accountListPanel;

	protected BaseFileProgressBarPanel(Window parentWindow) {
		this.parentWindow = parentWindow;
	}

	public Stage createNewFileImportProgressBarWindow() {
		dialogStage = new Stage();
		dialogStage.initOwner(parentWindow);
		dialogStage.initModality(Modality.APPLICATION_MODAL);
		dialogStage.setTitle("Import");
		createProgressPanel();
		return dialogStage;
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

		HBox progressBox = new HBox(10, progressBar, progressLabel);
		progressBox.setAlignment(Pos.CENTER_LEFT);
		progressBox.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(progressBar, Priority.ALWAYS);

		VBox topBox = new VBox(8, statusLabel, progressBox);

		BorderPane root = new BorderPane();
		root.setPadding(new Insets(12));
		root.setTop(topBox);
		root.setCenter(taskOutput);

		dialogStage.setScene(new Scene(root, 520, 320));
	}

	protected abstract void startTask(String fileName, FileType fileType, AccountListPanel accountListPanel) throws Exception;

	protected void startTask(AccountListPanel accountListPanel) throws Exception {
		this.accountListPanel = accountListPanel;

		progressBar.progressProperty().unbind();
		progressBar.progressProperty().bind(task.progressProperty());

		progressLabel.textProperty().unbind();
		progressLabel.textProperty().bind(Bindings.createStringBinding(() -> {
			double progress = task.getProgress();
			if (progress < 0) {
				return "0 %";
			}
			return String.format("%.0f %%", progress * 100.0);
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
				onTaskSucceeded();
				closeDialog();
			}
			case FAILED -> {
				Throwable ex = task.getException();
				if (ex != null) {
					Platform.runLater(() -> taskOutput.appendText("Error: " + ex.getMessage() + System.lineSeparator()));
				}
				closeDialog();
			}
			case CANCELLED -> closeDialog();
			default -> {
			}
			}
		});

		Thread thread = new Thread(task);
		thread.setDaemon(true);
		thread.start();
	}

	protected void onTaskSucceeded() {
		// Optional override
	}

	protected void closeDialog() {
		Platform.runLater(() -> {
			if (dialogStage != null) {
				dialogStage.close();
			}
		});
	}
}
