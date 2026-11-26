package de.zft2.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.JavaFxTestSupport;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;

class FxNodeSupportTest {

	@Test
	void disclosureShouldKeepVisibilityTooltipAndAccessibilityInSync() {
		JavaFxTestSupport.runFx(() -> {
			VBox target = new VBox();
			FxNodeSupport.setVisibleManaged(target, false);
			ToggleButton toggle = FxNodeSupport.createDisclosureToggle(target, "Show details", "Hide details");
			assertFalse(toggle.isSelected());
			assertEquals("\u25bc", toggle.getText());
			assertEquals("Show details", toggle.getTooltip().getText());
			assertEquals("Show details", toggle.getAccessibleText());

			toggle.fire();
			assertTrue(target.isVisible());
			assertTrue(target.isManaged());
			assertEquals("\u25b2", toggle.getText());
			assertEquals("Hide details", toggle.getTooltip().getText());
			assertEquals("Hide details", toggle.getAccessibleText());

			toggle.fire();
			assertFalse(target.isVisible());
			assertFalse(target.isManaged());
			assertEquals("\u25bc", toggle.getText());
			assertEquals("Show details", toggle.getTooltip().getText());
			assertEquals("Show details", toggle.getAccessibleText());
		});
	}
}
