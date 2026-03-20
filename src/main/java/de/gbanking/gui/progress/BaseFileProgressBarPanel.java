package de.gbanking.gui.progress;

import de.gbanking.file.BaseFileTask;
import de.gbanking.gui.enu.FileType;
import de.gbanking.gui.panel.account.AccountListPanel;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public abstract class BaseFileProgressBarPanel {

	protected ProgressBar progressBar;
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
		dialogStage.setTitle("Importiere...");

		createProgressPanel();

		return dialogStage;
	}

	private void createProgressPanel() {
		progressBar = new ProgressBar(0);
		progressBar.setPrefWidth(Double.MAX_VALUE);

		taskOutput = new TextArea();
		taskOutput.setEditable(false);
		taskOutput.setPrefRowCount(12);

		BorderPane root = new BorderPane();
		root.setPadding(new Insets(12));
		root.setTop(progressBar);
		root.setCenter(taskOutput);

		dialogStage.setScene(new Scene(root, 480, 280));
	}

	protected abstract void startTask(String fileName, FileType fileType, AccountListPanel accountListPanel) throws Exception;

	protected void startTask(AccountListPanel accountListPanel) throws Exception {
		this.accountListPanel = accountListPanel;

		progressBar.progressProperty().unbind();
		progressBar.progressProperty().bind(task.progressProperty());

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
					Platform.runLater(() -> taskOutput.appendText("Fehler: " + ex.getMessage() + System.lineSeparator()));
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
		// optional override
	}

	protected void closeDialog() {
		Platform.runLater(() -> {
			if (dialogStage != null) {
				dialogStage.close();
			}
		});
	}
}