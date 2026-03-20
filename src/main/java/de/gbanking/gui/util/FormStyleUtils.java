// src/main/java/de/gbanking/gui/fx/util/FormStyleUtils.java
package de.gbanking.gui.util;

import java.util.Arrays;
import java.util.List;

import de.gbanking.db.dao.enu.Source;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

public final class FormStyleUtils {

	public enum FieldWidth {
		XS(90), S(140), M(220), L(320);

		private final double prefWidth;

		FieldWidth(double prefWidth) {
			this.prefWidth = prefWidth;
		}

		public double getPrefWidth() {
			return prefWidth;
		}
	}

	private static final double PANEL_BUTTON_WIDTH = 120.0;

	private FormStyleUtils() {
	}

	public static <T extends Control> T applyWidth(T control, FieldWidth width) {
		control.setPrefWidth(width.getPrefWidth());
		control.setMinWidth(width.getPrefWidth());
		return control;
	}

	public static TextArea prepareLargeTextArea(TextArea area, int rowCount) {
		area.setWrapText(true);
		area.setPrefRowCount(rowCount);
		area.setPrefWidth(FieldWidth.L.getPrefWidth());
		area.setMaxWidth(Double.MAX_VALUE);
		return area;
	}

	public static void styleButtons(Button... buttons) {
		for (Button button : buttons) {
			button.getStyleClass().add("gbanking-form-button");
			button.setPrefWidth(PANEL_BUTTON_WIDTH);
			button.setMinWidth(PANEL_BUTTON_WIDTH);
			button.setMaxWidth(PANEL_BUTTON_WIDTH);
		}
	}

	public static HBox createButtonBar(Button... buttons) {
		styleButtons(buttons);
		HBox buttonBar = new HBox(10, buttons);
		buttonBar.setAlignment(Pos.CENTER_LEFT);
		buttonBar.getStyleClass().add("gbanking-button-bar");
		return buttonBar;
	}

	public static void styleFormPanel(Region region) {
		region.getStyleClass().add("gbanking-form-panel");
	}

	public static void styleTitledPane(Region region) {
		region.getStyleClass().add("gbanking-form-titled-pane");
	}

	public static void setReadOnlyStyle(boolean readOnly, Control... controls) {
		for (Control control : controls) {
			if (readOnly) {
				if (!control.getStyleClass().contains("gbanking-readonly-control")) {
					control.getStyleClass().add("gbanking-readonly-control");
				}
			} else {
				control.getStyleClass().remove("gbanking-readonly-control");
			}
		}
	}

	public static void setEditable(boolean editable, Control... controls) {
		for (Control control : controls) {
			if (control instanceof TextField textField) {
				textField.setEditable(editable);
				textField.setDisable(!editable);
			} else if (control instanceof TextArea textArea) {
				textArea.setEditable(editable);
				textArea.setDisable(!editable);
			} else if (control instanceof ComboBox<?>) {
				control.setDisable(!editable);
			} else if (control instanceof CheckBox) {
				control.setDisable(!editable);
			} else {
				control.setDisable(!editable);
			}
			setReadOnlyStyle(!editable, control);
		}
	}

	public static boolean isUserEditable(Source source) {
		if (source == null) {
			return true;
		}

		Source baseSource = source.isNew() ? source.getCorresponding() : source;
		return switch (baseSource) {
		case MANUELL, MONEYTRANSFER, IMPORT, IMPORT_INITIAL -> true;
		case ONLINE, ONLINE_PRENO, AUTO_ADJUSTING, AUTO_PRENO -> false;
		default -> false;
		};
	}

	public static List<Control> controls(Control... controls) {
		return Arrays.asList(controls);
	}
}