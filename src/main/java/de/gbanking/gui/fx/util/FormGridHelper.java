// src/main/java/de/gbanking/gui/fx/util/FormGridHelper.java
package de.gbanking.gui.fx.util;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class FormGridHelper {

	private FormGridHelper() {
	}

	public static GridPane createDefaultGrid() {
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(8);
		grid.setPadding(new Insets(6));
		grid.getStyleClass().add("gbanking-form-grid");
		return grid;
	}

	public static void addFieldAbove(GridPane grid, String labelText, Node field, int col, int rowGroup) {
		int row = rowGroup * 2;
		grid.add(createFieldBox(labelText, field), col, row, 1, 2);
	}

	public static void addFieldAbove(GridPane grid, String labelText, Node field, int col, int rowGroup, int colspan) {
		int row = rowGroup * 2;
		grid.add(createFieldBox(labelText, field), col, row, colspan, 2);
	}

	private static VBox createFieldBox(String labelText, Node field) {
		if (field == null) {
			throw new IllegalArgumentException("field must not be null for labelText=" + labelText);
		}

		Label label = new Label(labelText == null ? "" : labelText);
		label.getStyleClass().add("gbanking-form-label");

		VBox box = new VBox(2);
		box.getStyleClass().add("gbanking-form-field-box");
		box.getChildren().add(label);
		box.getChildren().add(field);

		if (field instanceof Region region) {
			region.setMaxWidth(Double.MAX_VALUE);
		}
		if (field instanceof Control control) {
			VBox.setVgrow(control, Priority.NEVER);
		}

		return box;
	}
}