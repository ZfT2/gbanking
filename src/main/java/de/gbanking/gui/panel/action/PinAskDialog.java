package de.gbanking.gui.panel.action;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class PinAskDialog {

	private final Window parentWindow;
	private char[] pin;

	public PinAskDialog(Window parentWindow) {
		this.parentWindow = parentWindow;
	}

	public Stage createNewPinAskDialog() {
		Stage dialog = new Stage();
		dialog.initOwner(parentWindow);
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle("PIN");

		GridPane grid = new GridPane();
		grid.setPadding(new Insets(10));
		grid.setHgap(10);
		grid.setVgap(8);

		Label pinLabel = new Label("PIN");
		PasswordField pinField = new PasswordField();

		Button okButton = new Button("OK");
		Button cancelButton = new Button("Abbrechen");

		okButton.setOnAction(e -> {
			pin = pinField.getText() != null ? pinField.getText().toCharArray() : null;
			dialog.close();
		});

		cancelButton.setOnAction(e -> {
			pin = null;
			dialog.close();
		});

		grid.add(pinLabel, 0, 0);
		grid.add(pinField, 1, 0);
		grid.add(new HBox(10, okButton, cancelButton), 1, 1);

		dialog.setScene(new Scene(grid, 300, 140));
		return dialog;
	}

	public char[] getPin() {
		return pin;
	}
}