package de.zft2.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.JavaFxTestSupport;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

class FormGridHelperTest {

	@Test
	void createDefaultGridShouldApplyDefaults() {
		GridPane grid = JavaFxTestSupport.callFx(FormGridHelper::createDefaultGrid);

		assertEquals(14.0, grid.getHgap());
		assertEquals(6.0, grid.getVgap());
		assertTrue(grid.getStyleClass().contains("gbanking-form-grid"));
	}

	@Test
	void addFieldAboveShouldWrapFieldAndPlaceItInExpectedRow() {
		GridPane grid = JavaFxTestSupport.callFx(FormGridHelper::createDefaultGrid);
		TextField field = JavaFxTestSupport.callFx(TextField::new);

		JavaFxTestSupport.runFx(() -> FormGridHelper.addFieldAbove(grid, "Name", field, 1, 3));

		assertEquals(1, grid.getChildren().size());
		VBox box = (VBox) grid.getChildren().get(0);
		assertEquals(1, GridPane.getColumnIndex(box));
		assertEquals(6, GridPane.getRowIndex(box));
		assertTrue(box.getStyleClass().contains("gbanking-form-field-box"));
		assertEquals(Priority.NEVER, VBox.getVgrow(field));
		Label label = (Label) box.getChildren().get(0);
		assertEquals("Name", label.getText());
	}

	@Test
	void addFieldInlineShouldCreateInlineLabelAndSetColumnSpan() {
		GridPane grid = JavaFxTestSupport.callFx(FormGridHelper::createDefaultGrid);
		TextField field = JavaFxTestSupport.callFx(TextField::new);

		JavaFxTestSupport.runFx(() -> FormGridHelper.addFieldInline(grid, "Value", field, 2, 4, 3));

		assertEquals(1, grid.getChildren().size());
		HBox box = (HBox) grid.getChildren().get(0);
		assertEquals(2, GridPane.getColumnIndex(box));
		assertEquals(4, GridPane.getRowIndex(box));
		assertEquals(3, GridPane.getColumnSpan(box));
		assertTrue(box.getStyleClass().contains("gbanking-form-inline-field"));
	}

	@Test
	void addFieldAboveShouldRejectNullField() {
		GridPane grid = JavaFxTestSupport.callFx(FormGridHelper::createDefaultGrid);

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> JavaFxTestSupport.runFx(() -> FormGridHelper.addFieldAbove(grid, "Broken", null, 0, 0)));

		assertTrue(exception.getCause().getMessage().contains("field must not be null"));
	}
}
