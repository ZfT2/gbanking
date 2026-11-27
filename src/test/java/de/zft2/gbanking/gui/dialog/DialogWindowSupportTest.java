package de.zft2.gbanking.gui.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.JavaFxTestSupport;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

class DialogWindowSupportTest {

	@BeforeAll
	static void initJavaFx() {
		JavaFxTestSupport.runFx(() -> {
		});
	}

	@AfterEach
	void cleanupWindows() {
		JavaFxTestSupport.runFx(() -> java.util.List.copyOf(javafx.stage.Window.getWindows()).forEach(window -> {
			if (window instanceof Stage stage) {
				stage.close();
			}
		}));
	}

	@Test
	void createModalStageShouldSetTitleAndModality() {
		Stage stage = JavaFxTestSupport.callFx(() -> DialogWindowSupport.createModalStage(null, "UI_DIALOG_PIN_TITLE"));

		assertNotNull(stage);
		assertEquals(Modality.APPLICATION_MODAL, stage.getModality());
		assertFalse(stage.getTitle().isBlank());
		assertFalse(stage.getIcons().isEmpty());
	}

	@Test
	void createModalStageShouldUseShowingWindowWhenPreferredOwnerHasNoScene() {
		Stage showingOwner = JavaFxTestSupport.callFx(() -> {
			Stage stage = new Stage();
			stage.setScene(DialogWindowSupport.createScene(new VBox(), 120, 80));
			stage.show();
			return stage;
		});
		Stage ownerWithoutScene = JavaFxTestSupport.callFx(() -> new Stage());

		Stage dialog = JavaFxTestSupport.callFx(() -> DialogWindowSupport.createModalStage(ownerWithoutScene, "UI_DIALOG_PIN_TITLE"));

		assertEquals(showingOwner, dialog.getOwner());
	}

	@Test
	void createDialogRootAndButtonBarShouldAddContent() {
		Label label = JavaFxTestSupport.callFx(() -> new Label("Header"));
		Button ok = JavaFxTestSupport.callFx(() -> new Button("OK"));
		Button cancel = JavaFxTestSupport.callFx(() -> new Button("Cancel"));

		VBox root = JavaFxTestSupport.callFx(() -> DialogWindowSupport.createDialogRoot(label));
		var bar = JavaFxTestSupport.callFx(() -> DialogWindowSupport.createButtonBar(ok, cancel));

		assertEquals(1, root.getChildren().size());
		assertEquals(2, bar.getChildren().size());
	}

	@Test
	void createSceneAndSetVgrowAlwaysShouldConfigureNodes() {
		VBox root = JavaFxTestSupport.callFx(VBox::new);
		Rectangle rectangle = JavaFxTestSupport.callFx(() -> new Rectangle(20, 20));

		var scene = JavaFxTestSupport.callFx(() -> DialogWindowSupport.createScene(root, 320, 180));
		JavaFxTestSupport.runFx(() -> DialogWindowSupport.setVgrowAlways(rectangle));

		assertEquals(320.0, scene.getWidth());
		assertEquals(180.0, scene.getHeight());
		assertEquals(Priority.ALWAYS, VBox.getVgrow(rectangle));
	}

	@Test
	void modalScenesShouldWrapNestedLabelsWithoutChangingOtherControls() {
		JavaFxTestSupport.runFx(() -> {
			Stage dialog = DialogWindowSupport.createModalStage(null, "UI_DIALOG_PIN_TITLE");
			Label hint = new Label("An explanatory message that needs several lines in a narrow dialog. ".repeat(5));
			Label progress = new Label("100 %");
			progress.setMinWidth(48);
			TextArea editor = new TextArea(hint.getText());
			ScrollPane scroll = new ScrollPane(new VBox(hint, editor));
			scroll.setFitToWidth(true);
			TabPane tabs = new TabPane(new Tab("Details", scroll));
			TableView<Object> table = new TableView<>();
			Label placeholder = new Label("No data");
			table.setPlaceholder(placeholder);
			Button ok = new Button("OK");
			VBox root = DialogWindowSupport.createDialogRoot(tabs, table, progress, ok);
			dialog.setScene(new Scene(root, 360, 500));
			root.applyCss();
			root.layout();

			assertTrue(hint.getHeight() >= hint.prefHeight(hint.getWidth()));
			assertTrue(hint.getHeight() > 40);
			assertEquals(48, progress.getMinWidth());
			assertFalse(editor.isWrapText());
			assertFalse(placeholder.isWrapText());
			assertFalse(ok.isWrapText());

			Label addedHint = new Label(hint.getText());
			((VBox) scroll.getContent()).getChildren().set(0, addedHint);
			root.applyCss();
			root.layout();
			assertTrue(addedHint.getHeight() > 40);

			Label nextStep = new Label(hint.getText());
			dialog.setScene(new Scene(new VBox(nextStep), 360, 160));
			assertTrue(nextStep.isWrapText());
		});
	}

	@Test
	void confirmationShouldWrapCustomContentBeforeLayout() {
		JavaFxTestSupport.runFx(() -> {
			Label hint = new Label("A long confirmation message that must remain fully readable. ".repeat(5));
			VBox content = new VBox(hint);
			content.setPrefWidth(300);
			AtomicBoolean readable = new AtomicBoolean();
			Platform.runLater(() -> {
				readable.set(hint.getHeight() > 40 && hint.getHeight() >= hint.prefHeight(hint.getWidth()));
				fireDialogButton(ButtonType.OK);
			});
			assertTrue(DialogWindowSupport.showConfirmation(null, Alert.AlertType.CONFIRMATION, "Title", null, content,
					ButtonType.OK, ButtonType.CANCEL));
			assertTrue(readable.get());
		});
	}

	@Test
	void findBestOwnerWindowShouldReturnFocusedShowingWindow() {
		Stage stage = JavaFxTestSupport.callFx(() -> {
			Stage value = new Stage();
			value.setScene(DialogWindowSupport.createScene(new VBox(), 120, 80));
			value.show();
			value.requestFocus();
			return value;
		});

		var owner = JavaFxTestSupport.callFx(DialogWindowSupport::findBestOwnerWindow);

		assertTrue(owner.isPresent());
		assertEquals(stage, owner.get());
	}

	@Test
	void showConfirmationShouldRecognizeCustomConfirmButton() {
		ButtonType confirmButton = new ButtonType("Continue", ButtonBar.ButtonData.OK_DONE);

		boolean confirmed = JavaFxTestSupport.callFx(() -> {
			Platform.runLater(() -> fireDialogButton(confirmButton));
			return DialogWindowSupport.showConfirmation(null, "Question", confirmButton, ButtonType.CANCEL);
		});

		assertTrue(confirmed);
	}

	@Test
	void showSelectionShouldReturnSelectedValue() {
		var selection = JavaFxTestSupport.callFx(() -> {
			Platform.runLater(() -> fireDialogButton(ButtonType.OK));
			return DialogWindowSupport.showSelection(null, "Title", "Header", "Text", "Second",
					java.util.List.of("First", "Second"));
		});

		assertEquals("Second", selection.orElseThrow());
	}

	private static void fireDialogButton(ButtonType buttonType) {
		for (Window window : Window.getWindows()) {
			if (window.isShowing() && window.getScene() != null && window.getScene().getRoot() instanceof DialogPane dialogPane
					&& dialogPane.lookupButton(buttonType) instanceof Button button) {
				button.fire();
				return;
			}
		}
		throw new IllegalStateException("No open JavaFX dialog found");
	}
}
