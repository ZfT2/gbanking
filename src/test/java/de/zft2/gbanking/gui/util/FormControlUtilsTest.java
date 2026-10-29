package de.zft2.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.JavaFxTestSupport;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

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

	@Test
	void displayConverterShouldRenderLabelsOnly() {
		StringConverter<Integer> converter = FormControlUtils.displayConverter(value -> "#" + value);

		assertEquals("#3", converter.toString(3));
		assertEquals("", converter.toString(null));
		assertNull(converter.fromString("#3"));
	}

	@Test
	void longDateConverterShouldFormatParseAndKeepValueOnInvalidInput() {
		LocalDate initial = LocalDate.of(2024, Month.MARCH, 1);
		DatePicker datePicker = JavaFxTestSupport.callFx(() -> new DatePicker(initial));

		JavaFxTestSupport.runFx(() -> FormControlUtils.applyLongDateConverter(datePicker));
		StringConverter<LocalDate> converter = datePicker.getConverter();

		assertEquals("01.03.2024", converter.toString(initial));
		assertEquals(LocalDate.of(2024, Month.DECEMBER, 24), converter.fromString("24.12.2024"));
		assertNull(converter.fromString(""));
		assertEquals(initial, converter.fromString("31.02.2024"));
	}
}
