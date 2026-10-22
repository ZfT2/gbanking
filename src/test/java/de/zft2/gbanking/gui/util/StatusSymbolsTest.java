package de.zft2.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

class StatusSymbolsTest {

	@Test
	void textShouldReturnCheckMarkForValidState() {
		assertEquals(StatusSymbols.CHECK, StatusSymbols.text(true));
	}

	@Test
	void textShouldReturnCrossForInvalidState() {
		assertEquals(StatusSymbols.CROSS, StatusSymbols.text(false));
	}

	@Test
	void createLabelShouldApplySymbolAndStateStyleClasses() {
		Label valid = JavaFxTestSupport.callFx(() -> StatusSymbols.createLabel(true));
		Label invalid = JavaFxTestSupport.callFx(() -> StatusSymbols.createLabel(false));

		assertEquals(StatusSymbols.CHECK, valid.getText());
		assertTrue(valid.getStyleClass().contains(StatusSymbols.STYLE_CLASS_VALID));
		assertEquals(StatusSymbols.CROSS, invalid.getText());
		assertTrue(invalid.getStyleClass().contains(StatusSymbols.STYLE_CLASS_INVALID));
	}

	@Test
	void createLabelShouldBeColoredByTheTableStylesheet() {
		JavaFxTestSupport.runFx(() -> {
			Label valid = StatusSymbols.createLabel(true);
			Label invalid = StatusSymbols.createLabel(false);
			StackPane root = new StackPane(valid, invalid);
			Scene scene = new Scene(root);
			scene.getStylesheets().add(getClass().getResource("/css/gbanking-table.css").toExternalForm());
			root.applyCss();
			root.layout();

			assertEquals(Color.rgb(0, 100, 0), valid.getTextFill());
			assertEquals(Color.RED, invalid.getTextFill());
		});
	}
}
