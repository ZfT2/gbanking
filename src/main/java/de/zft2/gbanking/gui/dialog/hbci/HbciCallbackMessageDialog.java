package de.zft2.gbanking.gui.dialog.hbci;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.kapott.hbci.manager.FlickerRenderer;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.util.OperationDurationLabel;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;

public class HbciCallbackMessageDialog implements BaseMessages {

	private static final String UI_BUTTON_DETAILS_HIDE = "UI_BUTTON_DETAILS_HIDE";
	private static final String UI_BUTTON_OK = "UI_BUTTON_OK";
	private static final String UI_BUTTON_CANCEL = "UI_BUTTON_CANCEL";
	private static final String UI_BUTTON_DETAILS_SHOW = "UI_BUTTON_DETAILS_SHOW";
	private static final double FLICKER_WIDTH = 360d;
	private static final double FLICKER_HEIGHT = 96d;
	private static final double FLICKER_BAR_GAP = 4d;
	private static final double TAN_IMAGE_FIT_SIZE = 260d;

	private final Window parentWindow;
	private Stage dialog;
	private Label statusLabel;
	private Label currentActionLabel;
	private ProgressBar progressBar;
	private Label progressLabel;
	private OperationDurationLabel durationLabel;
	private TextArea messageArea;
	private TextArea detailsArea;
	private Button detailsButton;
	private Button closeButton;
	private VBox detailsBox;
	private VBox interactionBox;
	private VBox recipientCheckBox;
	private VBox tanChallengeBox;
	private Label interactionLabel;
	private Label interactionTextFieldLabel;
	private TextField interactionTextField;
	private PasswordField interactionSecretField;
	private ComboBox<DialogOption> interactionChoiceBox;
	private TextField recipientCheckTextField;
	private ComboBox<DialogOption> recipientCheckChoiceBox;
	private Button recipientCheckConfirmButton;
	private Button recipientCheckCancelButton;
	private Button interactionConfirmButton;
	private Button interactionCancelButton;
	private boolean detailsVisible;
	private boolean detailsVisibleBeforeRecipientCheck;
	private boolean finished;
	private boolean interactionActive;
	private FlickerRenderer flickerRenderer;

	public HbciCallbackMessageDialog(Window parentWindow) {
		this.parentWindow = parentWindow;
	}

	public void showDialog() {
		runOnFxThread(() -> {
			Stage stage = getOrCreateDialog();
			durationLabel.start();
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
			progressLabel.setText(String.format(Locale.ROOT, "%.0f %%", boundedProgress * 100.0d));
		});
	}

	public void updateCurrentAction(String currentAction) {
		if (currentAction == null || currentAction.isBlank()) {
			return;
		}
		runOnFxThread(() -> {
			getOrCreateDialog();
			currentActionLabel.setText(currentAction.trim());
		});
	}

	public void markFinished(boolean success) {
		runOnFxThread(() -> {
			Stage stage = getOrCreateDialog();
			finished = true;
			durationLabel.stop();
			hideInteraction();
			progressBar.setProgress(1d);
			progressLabel.setText("100 %");
			statusLabel.setText(getText(success ? "UI_DIALOG_HBCI_FEEDBACK_STATUS_FINISHED"
					: "UI_DIALOG_HBCI_FEEDBACK_STATUS_FINISHED_WITH_ERRORS"));
			closeButton.setDisable(false);
			closeButton.requestFocus();
			if (!stage.isShowing()) {
				stage.show();
			}
		});
	}

	public boolean requestConfirmation(String prompt, String details, String confirmLabel, String cancelLabel) {
		Boolean result = requestInteraction(new InteractionRequest<>(InteractionMode.CONFIRMATION, prompt, details, List.of(), confirmLabel, cancelLabel, "",
				null, () -> Boolean.TRUE, () -> Boolean.FALSE));
		return Boolean.TRUE.equals(result);
	}

	public String requestSecretInput(String prompt, String details, String confirmLabel, String cancelLabel) {
		return requestInteraction(new InteractionRequest<>(InteractionMode.SECRET, prompt, details, List.of(), confirmLabel, cancelLabel, "",
				null, () -> interactionSecretField.getText(), () -> null));
	}

	public String requestSecretInput(String prompt, String details, TanChallenge challenge, String confirmLabel, String cancelLabel) {
		return requestInteraction(new InteractionRequest<>(InteractionMode.SECRET, prompt, details, List.of(), confirmLabel, cancelLabel, "",
				challenge, () -> interactionSecretField.getText(), () -> null));
	}

	public String requestSelection(String prompt, String details, List<DialogOption> options, String confirmLabel, String cancelLabel) {
		return requestInteraction(new InteractionRequest<>(InteractionMode.SELECTION, prompt, details, options, confirmLabel, cancelLabel, "",
				null, this::getSelectedOptionValue, () -> null));
	}

	public RecipientCheckDecision requestRecipientCheckDecision(RecipientCheckRequest request, String confirmLabel, String cancelLabel) {
		AtomicReference<RecipientCheckDecision> result = new AtomicReference<>();
		CountDownLatch latch = new CountDownLatch(1);

		runOnFxThread(() -> {
			Stage stage = getOrCreateDialog();
			interactionActive = true;
			showRecipientCheck(request, confirmLabel, cancelLabel, result, latch);
			if (!stage.isShowing()) {
				stage.show();
			}
			stage.toFront();
			dialog.sizeToScene();
		});

		awaitLatch(latch);
		return result.get();
	}

	private Stage getOrCreateDialog() {
		if (dialog != null) {
			return dialog;
		}

		dialog = DialogWindowSupport.createModalStage(parentWindow, "UI_DIALOG_HBCI_FEEDBACK_TITLE");
		dialog.setOnCloseRequest(event -> {
			if (!finished || interactionActive) {
				event.consume();
			}
		});

		Label headerLabel = new Label(getText("UI_DIALOG_HBCI_FEEDBACK_HEADER"));
		headerLabel.setWrapText(true);

		statusLabel = new Label(getText("UI_DIALOG_HBCI_FEEDBACK_STATUS_RUNNING"));
		statusLabel.setWrapText(true);
		currentActionLabel = new Label(getText("UI_DIALOG_HBCI_STATUS_CONNECTING"));
		currentActionLabel.setWrapText(true);

		Label progressTitleLabel = new Label(getText("UI_DIALOG_HBCI_FEEDBACK_PROGRESS"));

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

		Label detailsLabel = new Label(getText("UI_DIALOG_HBCI_FEEDBACK_DETAILS"));
		detailsArea = createReadOnlyTextArea();
		detailsArea.setPrefRowCount(10);
		detailsBox = new VBox(8, detailsLabel, detailsArea);
		detailsBox.setVisible(false);
		detailsBox.setManaged(false);

		detailsButton = new Button(getText(UI_BUTTON_DETAILS_SHOW));
		detailsButton.setOnAction(event -> toggleDetails());

		interactionLabel = new Label();
		interactionLabel.setWrapText(true);
		interactionTextFieldLabel = new Label(getText("UI_DIALOG_HBCI_VOP_RECIPIENT_NAME_FIELD"));
		interactionTextFieldLabel.setWrapText(true);
		interactionTextField = new TextField();
		interactionSecretField = new PasswordField();
		interactionChoiceBox = new ComboBox<>();
		interactionChoiceBox.setMaxWidth(Double.MAX_VALUE);
		tanChallengeBox = new VBox(8);
		tanChallengeBox.setVisible(false);
		tanChallengeBox.setManaged(false);

		interactionConfirmButton = new Button(getText(UI_BUTTON_OK));
		interactionCancelButton = new Button(getText(UI_BUTTON_CANCEL));
		HBox interactionButtonBar = DialogWindowSupport.createButtonBar(interactionCancelButton, interactionConfirmButton);

		interactionBox = new VBox(8, interactionLabel, tanChallengeBox, interactionTextFieldLabel, interactionTextField, interactionSecretField, interactionChoiceBox,
				interactionButtonBar);
		interactionBox.setVisible(false);
		interactionBox.setManaged(false);

		recipientCheckTextField = new TextField();
		recipientCheckChoiceBox = new ComboBox<>();
		recipientCheckChoiceBox.setMaxWidth(Double.MAX_VALUE);
		recipientCheckConfirmButton = new Button(getText(UI_BUTTON_OK));
		recipientCheckCancelButton = new Button(getText(UI_BUTTON_CANCEL));
		recipientCheckBox = new VBox(8);
		recipientCheckBox.setVisible(false);
		recipientCheckBox.setManaged(false);

		closeButton = new Button(getText("UI_BUTTON_CLOSE"));
		closeButton.setDisable(true);
		closeButton.setOnAction(event -> dialog.close());

		HBox buttonBar = DialogWindowSupport.createButtonBar(detailsButton, closeButton);
		durationLabel = new OperationDurationLabel();
		BorderPane footer = new BorderPane();
		footer.setLeft(durationLabel);
		footer.setRight(buttonBar);
		BorderPane.setAlignment(durationLabel, Pos.CENTER_LEFT);
		BorderPane.setAlignment(buttonBar, Pos.CENTER_RIGHT);
		Parent root = DialogWindowSupport.createDialogRoot(headerLabel, statusLabel, currentActionLabel, progressTitleLabel, progressBox, messageArea,
				recipientCheckBox, detailsBox, interactionBox, footer);
		DialogWindowSupport.setVgrowAlways(messageArea, detailsArea);

		dialog.setScene(DialogWindowSupport.createScene(root, 760, 420));
		return dialog;
	}

	private void toggleDetails() {
		detailsVisible = !detailsVisible;
		detailsBox.setVisible(detailsVisible);
		detailsBox.setManaged(detailsVisible);
		detailsButton.setText(getText(detailsVisible ? UI_BUTTON_DETAILS_HIDE : UI_BUTTON_DETAILS_SHOW));
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

	private <T> T requestInteraction(InteractionRequest<T> request) {
		AtomicReference<T> result = new AtomicReference<>();
		CountDownLatch latch = new CountDownLatch(1);

		runOnFxThread(() -> {
			Stage stage = getOrCreateDialog();
			interactionActive = true;
			interactionLabel.setText(request.prompt() == null ? "" : request.prompt());
			showInteractionDetails(request.details());
			showTanChallenge(request.tanChallenge());
			prepareInteractionFields(request.mode(), request.initialValue(), request.options());
			configureInteractionButtons(request.confirmLabel(), request.cancelLabel(), result, latch, request.confirmValueSupplier(),
					request.cancelValueSupplier());

			interactionBox.setVisible(true);
			interactionBox.setManaged(true);
			recipientCheckBox.setVisible(false);
			recipientCheckBox.setManaged(false);
			if (!stage.isShowing()) {
				stage.show();
			}
			stage.toFront();
			dialog.sizeToScene();
		});

		awaitLatch(latch);
		return result.get();
	}

	private void showInteractionDetails(String details) {
		if (details == null || details.isBlank()) {
			return;
		}
		appendText(detailsArea, details);
		detailsVisible = true;
		detailsBox.setVisible(true);
		detailsBox.setManaged(true);
		detailsButton.setText(getText(UI_BUTTON_DETAILS_HIDE));
	}

	private void showTanChallenge(TanChallenge challenge) {
		stopFlickerRenderer();
		tanChallengeBox.getChildren().clear();
		if (challenge == null) {
			setInteractionControlState(tanChallengeBox, false);
			return;
		}

		Label titleLabel = new Label(challenge.title() == null ? "" : challenge.title());
		titleLabel.setStyle("-fx-font-weight: bold;");
		titleLabel.setWrapText(true);
		tanChallengeBox.getChildren().add(titleLabel);

		if (challenge.type() == TanChallengeType.FLICKER) {
			tanChallengeBox.getChildren().add(createFlickerCanvas(challenge.flickerCode()));
		} else {
			tanChallengeBox.getChildren().add(createImageView(challenge.imageBytes()));
		}
		setInteractionControlState(tanChallengeBox, true);
	}

	private Canvas createFlickerCanvas(String flickerCode) {
		Canvas canvas = new Canvas(FLICKER_WIDTH, FLICKER_HEIGHT);
		drawFlickerFrame(canvas, false, false, false, false, false);
		flickerRenderer = new FlickerRenderer(flickerCode.toUpperCase(Locale.ROOT)) {
			@Override
			public void paint(boolean b1, boolean b2, boolean b3, boolean b4, boolean b5) {
				Platform.runLater(() -> drawFlickerFrame(canvas, b1, b2, b3, b4, b5));
			}
		};
		flickerRenderer.start();
		return canvas;
	}

	private void drawFlickerFrame(Canvas canvas, boolean b1, boolean b2, boolean b3, boolean b4, boolean b5) {
		boolean[] bars = { b1, b2, b3, b4, b5 };
		GraphicsContext graphics = canvas.getGraphicsContext2D();
		graphics.setFill(Color.WHITE);
		graphics.fillRect(0d, 0d, FLICKER_WIDTH, FLICKER_HEIGHT);
		graphics.setStroke(Color.GRAY);
		graphics.strokeRect(0.5d, 0.5d, FLICKER_WIDTH - 1d, FLICKER_HEIGHT - 1d);

		double barWidth = (FLICKER_WIDTH - (FLICKER_BAR_GAP * (bars.length + 1))) / bars.length;
		for (int index = 0; index < bars.length; index++) {
			double x = FLICKER_BAR_GAP + (index * (barWidth + FLICKER_BAR_GAP));
			graphics.setFill(bars[index] ? Color.WHITE : Color.BLACK);
			graphics.fillRect(x, FLICKER_BAR_GAP, barWidth, FLICKER_HEIGHT - (2d * FLICKER_BAR_GAP));
			if (bars[index]) {
				graphics.setStroke(Color.LIGHTGRAY);
				graphics.strokeRect(x, FLICKER_BAR_GAP, barWidth, FLICKER_HEIGHT - (2d * FLICKER_BAR_GAP));
			}
		}
	}

	private Node createImageView(byte[] imageBytes) {
		if (imageBytes == null || imageBytes.length == 0) {
			return createTanImageErrorLabel();
		}

		Image image = new Image(new ByteArrayInputStream(imageBytes));
		if (image.isError()) {
			return createTanImageErrorLabel();
		}

		ImageView imageView = new ImageView(image);
		imageView.setFitWidth(TAN_IMAGE_FIT_SIZE);
		imageView.setFitHeight(TAN_IMAGE_FIT_SIZE);
		imageView.setPreserveRatio(true);
		imageView.setSmooth(false);
		HBox imageBox = new HBox(imageView);
		imageBox.setAlignment(Pos.CENTER_LEFT);
		return imageBox;
	}

	private Label createTanImageErrorLabel() {
		Label errorLabel = new Label(getText("UI_DIALOG_HBCI_TAN_IMAGE_ERROR"));
		errorLabel.setWrapText(true);
		return errorLabel;
	}

	private void showRecipientCheck(RecipientCheckRequest request, String confirmLabel, String cancelLabel, AtomicReference<RecipientCheckDecision> result,
			CountDownLatch latch) {
		detailsVisibleBeforeRecipientCheck = detailsVisible;
		setInteractionControlState(messageArea, false);
		setInteractionControlState(detailsBox, false);
		setInteractionControlState(detailsButton, false);
		setInteractionControlState(interactionBox, false);

		recipientCheckBox.getChildren().setAll(createRecipientCheckContent(request));
		configureRecipientCheckControl(request);
		recipientCheckConfirmButton.setText(confirmLabel == null || confirmLabel.isBlank() ? getText(UI_BUTTON_OK) : confirmLabel);
		recipientCheckCancelButton.setText(cancelLabel == null || cancelLabel.isBlank() ? getText(UI_BUTTON_CANCEL) : cancelLabel);
		recipientCheckConfirmButton.setOnAction(event -> completeRecipientCheck(result, latch, true));
		recipientCheckCancelButton.setOnAction(event -> completeRecipientCheck(result, latch, false));
		recipientCheckBox.getChildren().add(DialogWindowSupport.createButtonBar(recipientCheckCancelButton, recipientCheckConfirmButton));
		recipientCheckBox.setVisible(true);
		recipientCheckBox.setManaged(true);
	}

	private List<javafx.scene.Node> createRecipientCheckContent(RecipientCheckRequest request) {
		GridPane summaryGrid = new GridPane();
		summaryGrid.setHgap(8);
		summaryGrid.setVgap(6);
		addOrderDataRow(summaryGrid, 0, request);
		addLabelValue(summaryGrid, 1, getText("UI_DIALOG_HBCI_VOP_RECIPIENT"), request.recipientName());
		addAccountDataRow(summaryGrid, 2, request);
		addLabelValue(summaryGrid, 3, getText("UI_TABLE_BANK"), request.bank());
		addLabelValue(summaryGrid, 4, getText("UI_TABLE_PURPOSE"), request.purpose());
		addLabelValue(summaryGrid, 5, getText("UI_DIALOG_HBCI_VOP_RESULT"), request.statusText());
		addLabelValue(summaryGrid, 6, getText("UI_DIALOG_HBCI_VOP_ORIGINAL_NAME"), request.originalRecipientName());
		addLabelValue(summaryGrid, 7, getText("UI_DIALOG_HBCI_VOP_BANK_NAME"), request.bankRecipientName());

		Label promptLabel = new Label(request.prompt() == null ? "" : request.prompt());
		promptLabel.setWrapText(true);
		return List.of(summaryGrid, promptLabel, recipientCheckChoiceBox, recipientCheckTextField);
	}

	private void addLabelValue(GridPane grid, int row, String label, String value) {
		grid.add(createFieldLabel(label), 0, row);
		grid.add(createFieldValue(value), 1, row, 5, 1);
	}

	private void addAccountDataRow(GridPane grid, int row, RecipientCheckRequest request) {
		grid.add(createFieldLabel(getText("UI_TABLE_IBAN")), 0, row);
		grid.add(createFieldValue(request.iban()), 1, row);
		grid.add(createFieldLabel(getText("UI_TABLE_BIC")), 2, row);
		grid.add(createFieldValue(request.bic()), 3, row);
	}

	private void addOrderDataRow(GridPane grid, int row, RecipientCheckRequest request) {
		grid.add(createFieldLabel(getText("UI_DIALOG_HBCI_VOP_ORDER")), 0, row);
		grid.add(createFieldValue(request.orderType()), 1, row);
		grid.add(createFieldLabel(getText("UI_TABLE_AMOUNT")), 2, row);
		grid.add(createFieldValue(request.amount()), 3, row);
	}

	private Label createFieldLabel(String text) {
		Label label = new Label((text == null ? "" : text) + ":");
		label.setStyle("-fx-font-weight: bold;");
		return label;
	}

	private Label createFieldValue(String text) {
		Label label = new Label(text == null || text.isBlank() ? "-" : text);
		label.setWrapText(true);
		return label;
	}

	private void configureRecipientCheckControl(RecipientCheckRequest request) {
		List<DialogOption> recipientNameOptions = request.recipientNameOptions() == null ? List.of() : request.recipientNameOptions();
		recipientCheckTextField.clear();
		recipientCheckTextField.setText(request.initialRecipientName() == null ? "" : request.initialRecipientName());
		recipientCheckChoiceBox.getItems().setAll(recipientNameOptions);
		if (!recipientNameOptions.isEmpty()) {
			recipientCheckChoiceBox.getSelectionModel().selectFirst();
		}
		setInteractionControlState(recipientCheckChoiceBox, !recipientNameOptions.isEmpty());
		setInteractionControlState(recipientCheckTextField, request.freeRecipientNameInput());
	}

	private void completeRecipientCheck(AtomicReference<RecipientCheckDecision> result, CountDownLatch latch, boolean continueTransfer) {
		result.set(new RecipientCheckDecision(continueTransfer, continueTransfer ? getRecipientCheckName() : null));
		hideInteraction();
		restoreRecipientCheckHiddenControls();
		latch.countDown();
	}

	private String getRecipientCheckName() {
		if (recipientCheckTextField.isVisible()) {
			return recipientCheckTextField.getText();
		}
		DialogOption option = recipientCheckChoiceBox.getSelectionModel().getSelectedItem();
		return option != null ? option.value() : null;
	}

	private void prepareInteractionFields(InteractionMode mode, String initialValue, List<DialogOption> options) {
		interactionTextField.clear();
		interactionTextField.setText(initialValue == null ? "" : initialValue);
		interactionSecretField.clear();
		interactionChoiceBox.getItems().setAll(options);
		if (!options.isEmpty()) {
			interactionChoiceBox.getSelectionModel().selectFirst();
		}

		setInteractionControlState(interactionTextFieldLabel, mode == InteractionMode.TEXT);
		setInteractionControlState(interactionTextField, mode == InteractionMode.TEXT);
		setInteractionControlState(interactionSecretField, mode == InteractionMode.SECRET);
		setInteractionControlState(interactionChoiceBox, mode == InteractionMode.SELECTION);
	}

	private <T> void configureInteractionButtons(String confirmLabel, String cancelLabel, AtomicReference<T> result, CountDownLatch latch,
			Supplier<T> confirmValueSupplier, Supplier<T> cancelValueSupplier) {
		interactionConfirmButton.setText(confirmLabel == null || confirmLabel.isBlank() ? getText(UI_BUTTON_OK) : confirmLabel);
		interactionCancelButton.setText(cancelLabel == null || cancelLabel.isBlank() ? getText(UI_BUTTON_CANCEL) : cancelLabel);
		interactionConfirmButton.setOnAction(event -> completeInteraction(result, latch, confirmValueSupplier.get()));
		interactionCancelButton.setOnAction(event -> completeInteraction(result, latch, cancelValueSupplier.get()));
	}

	private void setInteractionControlState(javafx.scene.Node control, boolean visible) {
		control.setVisible(visible);
		control.setManaged(visible);
	}

	private String getSelectedOptionValue() {
		DialogOption option = interactionChoiceBox.getSelectionModel().getSelectedItem();
		return option != null ? option.value() : null;
	}

	private <T> void completeInteraction(AtomicReference<T> result, CountDownLatch latch, T value) {
		result.set(value);
		hideInteraction();
		latch.countDown();
	}

	private void hideInteraction() {
		interactionActive = false;
		stopFlickerRenderer();
		if (interactionBox != null) {
			interactionBox.setVisible(false);
			interactionBox.setManaged(false);
		}
		if (recipientCheckBox != null) {
			recipientCheckBox.setVisible(false);
			recipientCheckBox.setManaged(false);
			recipientCheckBox.getChildren().clear();
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
		if (tanChallengeBox != null) {
			tanChallengeBox.getChildren().clear();
			tanChallengeBox.setVisible(false);
			tanChallengeBox.setManaged(false);
		}
		if (recipientCheckTextField != null) {
			recipientCheckTextField.clear();
		}
		if (recipientCheckChoiceBox != null) {
			recipientCheckChoiceBox.getItems().clear();
		}
	}

	private void stopFlickerRenderer() {
		if (flickerRenderer != null) {
			flickerRenderer.stop();
			flickerRenderer = null;
		}
	}

	private void restoreRecipientCheckHiddenControls() {
		setInteractionControlState(messageArea, true);
		setInteractionControlState(detailsButton, true);
		detailsVisible = detailsVisibleBeforeRecipientCheck || !detailsArea.getText().isBlank();
		detailsBox.setVisible(detailsVisible);
		detailsBox.setManaged(detailsVisible);
		detailsButton.setText(getText(detailsVisible ? UI_BUTTON_DETAILS_HIDE : UI_BUTTON_DETAILS_SHOW));
		if (dialog != null) {
			dialog.sizeToScene();
		}
	}

	private void awaitLatch(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	public record TanChallenge(TanChallengeType type, String title, String flickerCode, byte[] imageBytes, String message) {
		public TanChallenge {
			if (type == null) {
				throw new IllegalArgumentException("type must not be null");
			}
			if (type == TanChallengeType.FLICKER && (flickerCode == null || flickerCode.isBlank())) {
				throw new IllegalArgumentException("flickerCode must not be blank for flicker challenges");
			}
			if (type == TanChallengeType.IMAGE && (imageBytes == null || imageBytes.length == 0)) {
				throw new IllegalArgumentException("imageBytes must not be empty for image challenges");
			}
			imageBytes = imageBytes == null ? null : imageBytes.clone();
		}

		public static TanChallenge flicker(String title, String flickerCode) {
			return new TanChallenge(TanChallengeType.FLICKER, title, flickerCode, null, null);
		}

		public static TanChallenge image(String title, byte[] imageBytes, String message) {
			return new TanChallenge(TanChallengeType.IMAGE, title, null, imageBytes, message);
		}

		@Override
		public byte[] imageBytes() {
			return imageBytes == null ? null : imageBytes.clone();
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof TanChallenge that && type == that.type && java.util.Objects.equals(title, that.title)
					&& java.util.Objects.equals(flickerCode, that.flickerCode) && Arrays.equals(imageBytes, that.imageBytes)
					&& java.util.Objects.equals(message, that.message);
		}

		@Override
		public int hashCode() {
			int result = java.util.Objects.hash(type, title, flickerCode, message);
			result = 31 * result + Arrays.hashCode(imageBytes);
			return result;
		}

		@Override
		public String toString() {
			return "TanChallenge[type=%s, title=%s, flickerCode=%s, imageBytes=%s, message=%s]".formatted(type, title, flickerCode,
					Arrays.toString(imageBytes), message);
		}
	}

	public enum TanChallengeType {
		FLICKER,
		IMAGE
	}

	public record DialogOption(String value, String label) {
		@Override
		public String toString() {
			return label;
		}
	}

	public record RecipientCheckRequest(String prompt, String details, String orderType, String amount, String recipientName, String iban, String bic, String bank,
			String purpose, String statusText, String originalRecipientName, String bankRecipientName, String initialRecipientName,
			boolean freeRecipientNameInput, List<DialogOption> recipientNameOptions) {
	}

	public record RecipientCheckDecision(boolean continueTransfer, String recipientName) {
	}

	private enum InteractionMode {
		CONFIRMATION,
		SECRET,
		TEXT,
		SELECTION
	}

	private record InteractionRequest<T>(InteractionMode mode, String prompt, String details, List<DialogOption> options, String confirmLabel,
			String cancelLabel, String initialValue, TanChallenge tanChallenge, Supplier<T> confirmValueSupplier, Supplier<T> cancelValueSupplier) {
	}
}
