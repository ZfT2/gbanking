package de.zft2.gbanking.gui.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.file.BaseFileTask;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
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
		javafx.scene.layout.BorderPane footer = (javafx.scene.layout.BorderPane) ((javafx.scene.layout.BorderPane) stage.getScene().getRoot()).getBottom();
		assertSame(panel.durationLabel, footer.getLeft());
		assertSame(panel.closeButton, footer.getRight());
		assertTrue(panel.durationLabel.getText().endsWith("0s"));
	}

	@Test
	void exportPanelShouldStayOpenAfterSuccessfulExport() {
		FileExportProgressBarPanel panel = new FileExportProgressBarPanel(null, List.of());

		assertTrue(panel.keepDialogOpenOnSuccess());
	}

	private static final class TestProgressPanel extends BaseFileProgressBarPanel {

		private TestProgressPanel() {
			super(null);
		}

		@Override
		protected void startTask(String fileName, ExportType exportType, AccountListPanel accountListPanel) {
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
