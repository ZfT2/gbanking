package de.gbanking.gui.dialog;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import de.gbanking.messages.Messages;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public class HbciCallbackMessageDialog {

	private final Window parentWindow;
	private final Messages messages;
	private Stage dialog;
	private Label statusLabel;
	private ProgressBar progressBar;
	private Label progressLabel;
	private TextArea messageArea;
	private Label detailsLabel;
	private TextArea detailsArea;
	private Button detailsButton;
	private Button closeButton;
	private VBox detailsBox;
	private VBox interactionBox;
	private Label interactionLabel;
	private TextField interactionTextField;
	private PasswordField interactionSecretField;
	private ComboBox<DialogOption> interactionChoiceBox;
	private Button interactionConfirmButton;
	private Button interactionCancelButton;
	private boolean detailsVisible;
	private boolean finished;
	private boolean interactionActive;

	public HbciCallbackMessageDialog(Window parentWindow) {
		this.parentWindow = parentWindow;
		this.messages = Messages.getInstance();
	}

	public void showDialog() {
		runOnFxThread(() -> {
			Stage stage = getOrCreateDialog();
			if (!stage.isShowing()) {
				stage.show();
			}
			stage.toFront();
		});
	}

	public void appendMessages(String message) {
		if (message == null || message.isBlank()) {
			return;
		}
		runOnFxThread(() -> {
			Stage stage = getOrCreateDialog();
			appendText(messageArea, message);
			statusLabel.setText(getLastLine(message));
			if (!stage.isShowing()) {
				stage.show();
			}
		});
	}

	public void appendDetails(String details) {
		if (details == null || details.isBlank()) {
			return;
		}
		runOnFxThread(() -> {
			Stage stage = getOrCreateDialog();
			appendText(detailsArea, details);
			if (!stage.isShowing()) {
				stage.show();
			}
		});
	}

	public void updateProgress(double progress) {
		double boundedProgress = Math.max(0d, Math.min(1d, progress));
		runOnFxThread(() -> {
			getOrCreateDialog();
			progressBar.setProgress(boundedProgress);
			progressLabel.setText(String.format("%.0f %%", boundedProgress * 100.0d));
		});
	}

	public void markFinished(boolean success) {
		runOnFxThread(() -> {
			Stage stage = getOrCreateDialog();
			finished = true;
			hideInteraction();
			progressBar.setProgress(1d);
			progressLabel.setText("100 %");
			statusLabel.setText(messages.getMessage(success ? "UI_DIALOG_HBCI_FEEDBACK_STATUS_FINISHED"
					: "UI_DIALOG_HBCI_FEEDBACK_STATUS_FINISHED_WITH_ERRORS"));
			closeButton.setDisable(false);
			closeButton.requestFocus();
			if (!stage.isShowing()) {
				stage.show();
			}
		});
	}

	public boolean requestConfirmation(String prompt, String details, String confirmLabel, String cancelLabel) {
		Boolean result = requestInteraction(InteractionMode.CONFIRMATION, prompt, details, List.of(), confirmLabel, cancelLabel, "");
		return Boolean.TRUE.equals(result);
	}

	public String requestSecretInput(String prompt, String details, String confirmLabel, String cancelLabel) {
		return requestInteraction(InteractionMode.SECRET, prompt, details, List.of(), confirmLabel, cancelLabel, "");
	}

	public String requestSelection(String prompt, String details, List<DialogOption> options, String confirmLabel, String cancelLabel) {
		return requestInteraction(InteractionMode.SELECTION, prompt, details, options, confirmLabel, cancelLabel, "");
	}

	private Stage getOrCreateDialog() {
		if (dialog != null) {
			return dialog;
		}

		dialog = new Stage();
		Window owner = parentWindow != null ? parentWindow : DialogWindowSupport.findBestOwnerWindow().orElse(null);
		if (owner != null) {
			dialog.initOwner(owner);
		}
		dialog.setTitle(messages.getMessage("UI_DIALOG_HBCI_FEEDBACK_TITLE"));
		dialog.setOnCloseRequest(event -> {
			if (!finished || interactionActive) {
				event.consume();
			}
		});

		Label headerLabel = new Label(messages.getMessage("UI_DIALOG_HBCI_FEEDBACK_HEADER"));
		headerLabel.setWrapText(true);

		statusLabel = new Label(messages.getMessage("UI_DIALOG_HBCI_FEEDBACK_STATUS_RUNNING"));
		statusLabel.setWrapText(true);

		Label progressTitleLabel = new Label(messages.getMessage("UI_DIALOG_HBCI_FEEDBACK_PROGRESS"));

		progressBar = new ProgressBar(0d);
		progressBar.setMinWidth(250d);
		progressBar.setMaxWidth(Double.MAX_VALUE);
		progressBar.setPrefWidth(Double.MAX_VALUE);
		progressLabel = new Label("0 %");
		progressLabel.setMinWidth(45d);

		HBox progressBox = new HBox(10, progressBar, progressLabel);
		progressBox.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(progressBar, Priority.ALWAYS);

		messageArea = createReadOnlyTextArea();
		messageArea.setPrefRowCount(10);

		detailsLabel = new Label(messages.getMessage("UI_DIALOG_HBCI_FEEDBACK_DETAILS"));
		detailsArea = createReadOnlyTextArea();
		detailsArea.setPrefRowCount(10);
		detailsBox = new VBox(8, detailsLabel, detailsArea);
		detailsBox.setVisible(false);
		detailsBox.setManaged(false);

		detailsButton = new Button(messages.getMessage("UI_BUTTON_DETAILS_SHOW"));
		detailsButton.setOnAction(event -> toggleDetails());

		interactionLabel = new Label();
		interactionLabel.setWrapText(true);
		interactionTextField = new TextField();
		interactionSecretField = new PasswordField();
		interactionChoiceBox = new ComboBox<>();
		interactionChoiceBox.setMaxWidth(Double.MAX_VALUE);

		interactionConfirmButton = new Button(messages.getMessage("UI_BUTTON_OK"));
		interactionCancelButton = new Button(messages.getMessage("UI_BUTTON_CANCEL"));
		HBox interactionButtonBar = DialogWindowSupport.createButtonBar(interactionCancelButton, interactionConfirmButton);

		interactionBox = new VBox(8, interactionLabel, interactionTextField, interactionSecretField, interactionChoiceBox, interactionButtonBar);
		interactionBox.setVisible(false);
		interactionBox.setManaged(false);

		closeButton = new Button(messages.getMessage("UI_BUTTON_CLOSE"));
		closeButton.setDisable(true);
		closeButton.setOnAction(event -> dialog.close());

		HBox buttonBar = DialogWindowSupport.createButtonBar(detailsButton, closeButton);
		Parent root = DialogWindowSupport.createDialogRoot(headerLabel, statusLabel, progressTitleLabel, progressBox, messageArea, detailsBox, interactionBox,
				buttonBar);
		DialogWindowSupport.setVgrowAlways(messageArea, detailsArea);

		dialog.setScene(DialogWindowSupport.createScene(root, 760, 420));
		return dialog;
	}

	private void toggleDetails() {
		detailsVisible = !detailsVisible;
		detailsBox.setVisible(detailsVisible);
		detailsBox.setManaged(detailsVisible);
		detailsButton.setText(messages.getMessage(detailsVisible ? "UI_BUTTON_DETAILS_HIDE" : "UI_BUTTON_DETAILS_SHOW"));
		if (dialog != null) {
			dialog.sizeToScene();
		}
	}

	private TextArea createReadOnlyTextArea() {
		TextArea textArea = new TextArea();
		textArea.setEditable(false);
		textArea.setWrapText(true);
		textArea.setFocusTraversable(false);
		return textArea;
	}

	private void appendText(TextArea textArea, String text) {
		if (!textArea.getText().isBlank()) {
			textArea.appendText(System.lineSeparator() + System.lineSeparator());
		}
		textArea.appendText(text);
		textArea.positionCaret(textArea.getText().length());
	}

	private String getLastLine(String text) {
		String[] lines = text.split("\\R");
		return lines.length == 0 ? text : lines[lines.length - 1];
	}

	private void runOnFxThread(Runnable action) {
		if (Platform.isFxApplicationThread()) {
			action.run();
		} else {
			Platform.runLater(action);
		}
	}

	private <T> T requestInteraction(InteractionMode mode, String prompt, String details, List<DialogOption> options, String confirmLabel, String cancelLabel,
			String initialValue) {
		AtomicReference<T> result = new AtomicReference<>();
		CountDownLatch latch = new CountDownLatch(1);

		runOnFxThread(() -> {
			Stage stage = getOrCreateDialog();
			interactionActive = true;
			interactionLabel.setText(prompt == null ? "" : prompt);
			if (details != null && !details.isBlank()) {
				appendText(detailsArea, details);
				detailsVisible = true;
				detailsBox.setVisible(true);
				detailsBox.setManaged(true);
				detailsButton.setText(messages.getMessage("UI_BUTTON_DETAILS_HIDE"));
			}

			interactionTextField.clear();
			interactionTextField.setText(initialValue == null ? "" : initialValue);
			interactionSecretField.clear();
			interactionChoiceBox.getItems().setAll(options);
			if (!options.isEmpty()) {
				interactionChoiceBox.getSelectionModel().selectFirst();
			}

			interactionTextField.setVisible(mode == InteractionMode.TEXT);
			interactionTextField.setManaged(mode == InteractionMode.TEXT);
			interactionSecretField.setVisible(mode == InteractionMode.SECRET);
			interactionSecretField.setManaged(mode == InteractionMode.SECRET);
			interactionChoiceBox.setVisible(mode == InteractionMode.SELECTION);
			interactionChoiceBox.setManaged(mode == InteractionMode.SELECTION);

			interactionConfirmButton.setText(confirmLabel == null || confirmLabel.isBlank() ? messages.getMessage("UI_BUTTON_OK") : confirmLabel);
			interactionCancelButton.setText(cancelLabel == null || cancelLabel.isBlank() ? messages.getMessage("UI_BUTTON_CANCEL") : cancelLabel);

			interactionConfirmButton.setOnAction(event -> {
				Object value = switch (mode) {
				case CONFIRMATION -> Boolean.TRUE;
				case SECRET -> interactionSecretField.getText();
				case TEXT -> interactionTextField.getText();
				case SELECTION -> {
					DialogOption option = interactionChoiceBox.getSelectionModel().getSelectedItem();
					yield option != null ? option.value() : null;
				}
				};
				result.set((T) value);
				hideInteraction();
				latch.countDown();
			});
			interactionCancelButton.setOnAction(event -> {
				Object value = mode == InteractionMode.CONFIRMATION ? Boolean.FALSE : null;
				result.set((T) value);
				hideInteraction();
				latch.countDown();
			});

			interactionBox.setVisible(true);
			interactionBox.setManaged(true);
			if (!stage.isShowing()) {
				stage.show();
			}
			stage.toFront();
			dialog.sizeToScene();
		});

		awaitLatch(latch);
		return result.get();
	}

	private void hideInteraction() {
		interactionActive = false;
		if (interactionBox != null) {
			interactionBox.setVisible(false);
			interactionBox.setManaged(false);
		}
		if (interactionTextField != null) {
			interactionTextField.clear();
		}
		if (interactionSecretField != null) {
			interactionSecretField.clear();
		}
		if (interactionChoiceBox != null) {
			interactionChoiceBox.getItems().clear();
		}
	}

	private void awaitLatch(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	public record DialogOption(String value, String label) {
		@Override
		public String toString() {
			return label;
		}
	}

	private enum InteractionMode {
		CONFIRMATION,
		SECRET,
		TEXT,
		SELECTION
	}
}
