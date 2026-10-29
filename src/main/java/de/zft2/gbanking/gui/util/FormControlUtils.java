package de.zft2.gbanking.gui.util;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Function;

import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.util.StringConverter;

public final class FormControlUtils {

	private FormControlUtils() {
	}

	public static void clearTextInputs(List<? extends TextInputControl> controls) {
		for (TextInputControl control : controls) {
			control.clear();
		}
	}

	public static void prepareWrapping(TextArea textArea, int rowCount) {
		textArea.setWrapText(true);
		textArea.setPrefRowCount(rowCount);
	}

	/**
	 * Creates a converter that only renders values, e.g. for non-editable combo boxes. Typed text is
	 * never converted back into a value.
	 *
	 * @param <T>   the value type
	 * @param label supplies the text of a non-null value
	 * @return the display-only converter, rendering {@code null} as an empty text
	 */
	public static <T> StringConverter<T> displayConverter(Function<? super T, String> label) {
		return new StringConverter<>() {
			@Override
			public String toString(T value) {
				return value == null ? "" : label.apply(value);
			}

			@Override
			public T fromString(String text) {
				return null;
			}
		};
	}

	/**
	 * Shows the dates of the picker as dd.MM.yyyy and keeps the current value if the typed text is
	 * not a valid date.
	 *
	 * @param datePicker the date picker to configure
	 */
	public static void applyLongDateConverter(DatePicker datePicker) {
		datePicker.setConverter(new StringConverter<>() {
			@Override
			public String toString(LocalDate date) {
				return DateFormatUtils.formatLong(date);
			}

			@Override
			public LocalDate fromString(String text) {
				try {
					return DateFormatUtils.parseLong(text);
				} catch (DateTimeParseException ex) {
					return datePicker.getValue();
				}
			}
		});
	}
}