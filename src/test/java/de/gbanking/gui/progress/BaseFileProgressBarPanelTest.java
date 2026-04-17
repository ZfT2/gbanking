package de.gbanking.gui.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.gbanking.file.BaseFileTask;
import de.gbanking.gui.JavaFxTestSupport;
import de.gbanking.gui.enu.FileType;
import de.gbanking.gui.panel.account.AccountListPanel;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

class BaseFileProgressBarPanelTest {

	@Test
	void progressPanelShouldKeepPercentageLabelVisibleAndGrowBar() {
		TestProgressPanel panel = new TestProgressPanel();
		Stage stage = JavaFxTestSupport.callFx(panel::createNewFileImportProgressBarWindow);

		assertTrue(stage.getScene().getRoot() instanceof javafx.scene.layout.BorderPane);
		HBox progressBox = (HBox) ((VBox) ((javafx.scene.layout.BorderPane) stage.getScene().getRoot()).getTop()).getChildren().get(1);
		assertEquals(Double.MAX_VALUE, progressBox.getMaxWidth());
		assertEquals(Priority.ALWAYS, HBox.getHgrow(panel.progressBar));
		assertEquals(280.0, panel.progressBar.getMinWidth());
		assertEquals(280.0, panel.progressBar.getPrefWidth());
		assertEquals(48.0, panel.progressLabel.getMinWidth());
		assertEquals("0 %", panel.progressLabel.getText());
		assertFalse(panel.closeButton.isVisible());
		assertFalse(panel.closeButton.isManaged());
	}

	private static final class TestProgressPanel extends BaseFileProgressBarPanel {

		private TestProgressPanel() {
			super(null);
		}

		@Override
		protected void startTask(String fileName, FileType fileType, AccountListPanel accountListPanel) {
			this.task = new TestFileTask();
		}
	}

	private static final class TestFileTask extends BaseFileTask {

		private TestFileTask() {
			super("test.xml");
		}

		@Override
		protected Void call() {
			return null;
		}
	}
}
