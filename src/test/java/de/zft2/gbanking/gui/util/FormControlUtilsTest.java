package de.zft2.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.JavaFxTestSupport;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

class FormControlUtilsTest {

	@Test
	void clearTextInputsShouldEmptyAllFields() {
		TextField textField = JavaFxTestSupport.callFx(() -> {
			return new TextField("abc");
		});
		TextArea textArea = JavaFxTestSupport.callFx(() -> {
			return new TextArea("def");
		});

		JavaFxTestSupport.runFx(() -> FormControlUtils.clearTextInputs(List.of(textField, textArea)));

		assertEquals("", textField.getText());
		assertEquals("", textArea.getText());
	}

	@Test
	void prepareWrappingShouldConfigureTextArea() {
		TextArea textArea = JavaFxTestSupport.callFx(TextArea::new);

		JavaFxTestSupport.runFx(() -> FormControlUtils.prepareWrapping(textArea, 4));

		assertTrue(textArea.isWrapText());
		assertEquals(4, textArea.getPrefRowCount());
	}
}
