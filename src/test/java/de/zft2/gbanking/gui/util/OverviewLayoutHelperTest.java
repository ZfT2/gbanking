package de.zft2.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.util.OverviewLayoutHelper;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

class OverviewLayoutHelperTest {

	@Test
	void createOverviewTitleShouldApplyStyleClass() {
		Label title = JavaFxTestSupport.callFx(() -> OverviewLayoutHelper.createOverviewTitle("Accounts"));

		assertEquals("Accounts", title.getText());
		assertTrue(title.getStyleClass().contains("overview-title"));
	}

	@Test
	void createOverviewRootShouldContainTitleAndContent() {
		Label title = JavaFxTestSupport.callFx(() -> new Label("Title"));
		Region content = JavaFxTestSupport.callFx(Region::new);

		VBox root = JavaFxTestSupport.callFx(() -> OverviewLayoutHelper.createOverviewRoot(title, content));

		assertEquals(2, root.getChildren().size());
		assertEquals(Priority.ALWAYS, VBox.getVgrow(content));
	}

	@Test
	void createMainSplitShouldConfigureRegionWidthsAndDivider() {
		Region left = JavaFxTestSupport.callFx(Region::new);
		Region right = JavaFxTestSupport.callFx(Region::new);

		SplitPane split = JavaFxTestSupport.callFx(
				() -> OverviewLayoutHelper.createMainSplit(left, right, 0.35, 120, 180, 240));

		assertEquals(120.0, left.getMinWidth());
		assertEquals(180.0, left.getPrefWidth());
		assertEquals(240.0, left.getMaxWidth());
		assertEquals(0.35, split.getDividerPositions()[0], 0.0001);
	}
}
