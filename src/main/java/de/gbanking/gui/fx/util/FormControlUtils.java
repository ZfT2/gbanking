package de.gbanking.gui.fx.util;

import java.util.List;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;

public final class FormControlUtils {

	private FormControlUtils() {
	}

	public static void setDisabled(List<? extends Control> controls, boolean disabled) {
		for (Control control : controls) {
			control.setDisable(disabled);
		}
	}

	public static void clearTextInputs(List<? extends TextInputControl> controls) {
		for (TextInputControl control : controls) {
			control.clear();
		}
	}

	public static void clearComboBoxes(List<? extends ComboBox<?>> controls) {
		for (ComboBox<?> control : controls) {
			control.setValue(null);
		}
	}

	public static void prepareWrapping(TextArea textArea, int rowCount) {
		textArea.setWrapText(true);
		textArea.setPrefRowCount(rowCount);
	}
}