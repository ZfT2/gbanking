package de.zft2.gbanking.gui.util;

import java.util.List;

import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;

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
}