package de.zft2.gbanking.gui.dialog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import de.zft2.gbanking.BaseMessages;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class DialogWindowSupport implements BaseMessages {

	private static final List<String> APP_ICON_RESOURCES = List.of("/logo/GBankingIcon16.png", "/logo/GBankingIcon24.png",
			"/logo/GBankingIcon32.png", "/logo/GBankingIcon48.png", "/logo/GBankingIcon64.png", "/logo/GBankingIcon128.png",
			"/logo/GBankingIcon256.png", "/logo/GBankingIcon.png");
	private static List<Image> applicationIcons;

	private DialogWindowSupport() {
	}

	public static Stage createModalStage(Window parentWindow, String titleKey) {
		return createModalStageWithTitle(parentWindow, BaseMessages.getTextStatic(titleKey));
	}

	public static Stage createModalStageWithTitle(Window parentWindow, String title) {
		Stage dialog = new Stage();
		Optional<Window> owner = resolveDialogOwner(parentWindow);
		if (owner.isPresent()) {
			dialog.initOwner(owner.get());
		}
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle(title);
		applyApplicationIcons(dialog);
		return dialog;
	}

	public static void applyApplicationIcons(Stage stage) {
		if (stage == null) {
			return;
		}

		List<Image> icons = loadApplicationIcons();
		if (!icons.isEmpty()) {
			stage.getIcons().setAll(icons);
		}
	}

	private static List<Image> loadApplicationIcons() {
		if (applicationIcons == null) {
			List<Image> icons = new ArrayList<>();
			for (String iconResource : APP_ICON_RESOURCES) {
				var iconUrl = DialogWindowSupport.class.getResource(iconResource);
				if (iconUrl != null) {
					Image icon = new Image(iconUrl.toExternalForm());
					if (!icon.isError()) {
						icons.add(icon);
					}
				}
			}
			applicationIcons = List.copyOf(icons);
		}
		return applicationIcons;
	}

	public static VBox createDialogRoot(Node... content) {
		VBox root = new VBox(12);
		root.setPadding(new Insets(12));
		root.getChildren().addAll(content);
		return root;
	}

	public static HBox createButtonBar(Button... buttons) {
		HBox buttonBar = new HBox(10);
		buttonBar.setAlignment(Pos.CENTER_RIGHT);
		buttonBar.getChildren().addAll(buttons);
		return buttonBar;
	}

	public static Scene createScene(Parent root, double width, double height) {
		return new Scene(root, width, height);
	}

	public static void showAlert(Window parentWindow, Alert.AlertType alertType, String text) {
		Alert alert = new Alert(alertType);
		initOwner(alert, parentWindow);
		alert.setHeaderText(null);
		alert.setContentText(text);
		alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
		alert.showAndWait();
	}

	public static void showAlert(Window parentWindow, Alert.AlertType alertType, String title, String header, String text) {
		Alert alert = createAlert(parentWindow, alertType, title, header);
		alert.setContentText(text);
		alert.showAndWait();
	}

	public static void showAlert(Window parentWindow, Alert.AlertType alertType, String title, String header, Node content) {
		Alert alert = createAlert(parentWindow, alertType, title, header);
		alert.getDialogPane().setContent(content);
		alert.showAndWait();
	}

	public static boolean showConfirmation(Window parentWindow, String text, ButtonType confirmButton, ButtonType cancelButton) {
		Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, text, confirmButton, cancelButton);
		initOwner(confirmation, parentWindow);
		confirmation.setHeaderText(null);
		confirmation.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
		return confirmation.showAndWait().filter(confirmButton::equals).isPresent();
	}

	public static boolean showConfirmation(Window parentWindow, Alert.AlertType alertType, String title, String header, String text,
			ButtonType confirmButton, ButtonType cancelButton) {
		Alert confirmation = createAlert(parentWindow, alertType, title, header);
		confirmation.setContentText(text);
		confirmation.getButtonTypes().setAll(confirmButton, cancelButton);
		return confirmation.showAndWait().filter(confirmButton::equals).isPresent();
	}

	public static Optional<ButtonType> showChoice(Window parentWindow, Alert.AlertType alertType, String title, String header, String text,
			ButtonType... buttonTypes) {
		Alert alert = createAlert(parentWindow, alertType, title, header);
		alert.setContentText(text);
		alert.getButtonTypes().setAll(buttonTypes);
		return alert.showAndWait();
	}

	public static <T> Optional<T> showSelection(Window parentWindow, String title, String header, String text, T defaultValue,
			Collection<T> values) {
		ChoiceDialog<T> dialog = new ChoiceDialog<>(defaultValue, values);
		initOwner(dialog, parentWindow);
		dialog.setTitle(title);
		dialog.setHeaderText(header);
		dialog.setContentText(text);
		dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
		return dialog.showAndWait();
	}

	public static boolean showConfirmation(Window parentWindow, Alert.AlertType alertType, String title, String header, Node content,
			ButtonType confirmButton, ButtonType cancelButton) {
		Alert confirmation = createAlert(parentWindow, alertType, title, header);
		confirmation.getDialogPane().setContent(content);
		confirmation.getButtonTypes().setAll(confirmButton, cancelButton);
		return confirmation.showAndWait().filter(confirmButton::equals).isPresent();
	}

	public static void setVgrowAlways(Node... nodes) {
		for (Node node : nodes) {
			VBox.setVgrow(node, Priority.ALWAYS);
		}
	}

	public static Optional<Window> findBestOwnerWindow() {
		return Window.getWindows().stream().filter(window -> window.isShowing() && hasScene(window))
				.sorted(Comparator.comparing((Window window) -> window.isFocused()).reversed()).findFirst();
	}

	private static Alert createAlert(Window parentWindow, Alert.AlertType alertType, String title, String header) {
		Alert alert = new Alert(alertType);
		initOwner(alert, parentWindow);
		alert.setTitle(title);
		alert.setHeaderText(header);
		alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
		return alert;
	}

	private static void initOwner(Dialog<?> dialog, Window parentWindow) {
		Optional<Window> owner = resolveDialogOwner(parentWindow);
		if (owner.isPresent()) {
			dialog.initOwner(owner.get());
		}
	}

	private static Optional<Window> resolveDialogOwner(Window parentWindow) {
		if (hasScene(parentWindow)) {
			return Optional.of(parentWindow);
		}
		return findBestOwnerWindow();
	}

	private static boolean hasScene(Window window) {
		return window != null && window.getScene() != null;
	}
}
