package de.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.gbanking.gui.JavaFxTestSupport;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

class FormControlUtilsTest {

	@Test
	void setDisabledShouldApplyToAllControls() {
		TextField first = JavaFxTestSupport.callFx(TextField::new);
		TextField second = JavaFxTestSupport.callFx(TextField::new);

		JavaFxTestSupport.runFx(() -> FormControlUtils.setDisabled(List.of(first, second), true));

		assertTrue(first.isDisable());
		assertTrue(second.isDisable());
	}

	@Test
	void clearTextInputsShouldEmptyAllFields() {
		TextField textField = JavaFxTestSupport.callFx(() -> {
			TextField field = new TextField("abc");
			return field;
		});
		TextArea textArea = JavaFxTestSupport.callFx(() -> {
			TextArea area = new TextArea("def");
			return area;
		});

		JavaFxTestSupport.runFx(() -> FormControlUtils.clearTextInputs(List.of(textField, textArea)));

		assertEquals("", textField.getText());
		assertEquals("", textArea.getText());
	}

	@Test
	void clearComboBoxesShouldResetValue() {
		ComboBox<String> combo = JavaFxTestSupport.callFx(() -> {
			ComboBox<String> value = new ComboBox<>();
			value.setValue("EUR");
			return value;
		});

		JavaFxTestSupport.runFx(() -> FormControlUtils.clearComboBoxes(List.of(combo)));

		assertNull(combo.getValue());
	}

	@Test
	void prepareWrappingShouldConfigureTextArea() {
		TextArea textArea = JavaFxTestSupport.callFx(TextArea::new);

		JavaFxTestSupport.runFx(() -> FormControlUtils.prepareWrapping(textArea, 4));

		assertTrue(textArea.isWrapText());
		assertEquals(4, textArea.getPrefRowCount());
	}
}
