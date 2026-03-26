package de.gbanking.gui.dialog;

import java.util.Comparator;
import java.util.Optional;

import de.gbanking.messages.Messages;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class DialogWindowSupport {

	private static final Messages MESSAGES = Messages.getInstance();

	private DialogWindowSupport() {
	}

	public static Stage createModalStage(Window parentWindow, String titleKey) {
		Stage dialog = new Stage();
		Window owner = parentWindow != null ? parentWindow : findBestOwnerWindow().orElse(null);
		if (owner != null) {
			dialog.initOwner(owner);
		}
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle(MESSAGES.getMessage(titleKey));
		return dialog;
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

	public static void setVgrowAlways(Node... nodes) {
		for (Node node : nodes) {
			VBox.setVgrow(node, Priority.ALWAYS);
		}
	}

	public static Optional<Window> findBestOwnerWindow() {
		return Window.getWindows().stream().filter(Window::isShowing)
				.sorted(Comparator.comparing(Window::isFocused).reversed()).findFirst();
	}
}
