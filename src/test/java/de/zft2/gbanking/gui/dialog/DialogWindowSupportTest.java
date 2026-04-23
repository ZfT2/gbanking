package de.zft2.gbanking.gui.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.JavaFxTestSupport;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;

class DialogWindowSupportTest {

	@BeforeAll
	static void initJavaFx() throws Exception {
		CountDownLatch latch = new CountDownLatch(1);
		Platform.startup(latch::countDown);
		latch.await();
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
	void findBestOwnerWindowShouldReturnFocusedShowingWindow() {
		Stage stage = JavaFxTestSupport.callFx(() -> {
			Stage value = new Stage();
			value.show();
			value.requestFocus();
			return value;
		});

		var owner = JavaFxTestSupport.callFx(DialogWindowSupport::findBestOwnerWindow);

		assertTrue(owner.isPresent());
		assertEquals(stage, owner.get());
	}
}
