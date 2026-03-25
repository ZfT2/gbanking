package de.gbanking.gui.panel.action;

import de.gbanking.gui.dialog.DialogWindowSupport;
import de.gbanking.messages.Messages;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public class PinAskDialog {

	private final Window parentWindow;
	private final Messages messages;
	private String bankCode;
	private String bankName;
	private char[] pin;

	public PinAskDialog(Window parentWindow) {
		this.parentWindow = parentWindow;
		this.messages = Messages.getInstance();
	}

	public Stage createNewPinAskDialog() {
		Stage dialog = DialogWindowSupport.createModalStage(parentWindow, "UI_DIALOG_PIN_TITLE");

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(8);

		Label bankLabel = new Label(messages.getFormattedMessage("UI_DIALOG_PIN_BANK", new Object[] { getBankCodeForDisplay(), getBankNameForDisplay() }));
		Label pinLabel = new Label(messages.getMessage("UI_LABEL_PIN"));
		PasswordField pinField = new PasswordField();

		Button okButton = new Button(messages.getMessage("UI_BUTTON_OK"));
		Button cancelButton = new Button(messages.getMessage("UI_BUTTON_CANCEL"));

		okButton.setDefaultButton(true);
		okButton.setOnAction(e -> {
			pin = pinField.getText() != null ? pinField.getText().toCharArray() : null;
			dialog.close();
		});

		cancelButton.setCancelButton(true);
		cancelButton.setOnAction(e -> {
			pin = null;
			dialog.close();
		});

		grid.add(bankLabel, 0, 0, 2, 1);
		grid.add(pinLabel, 0, 1);
		grid.add(pinField, 1, 1);
		grid.add(new HBox(10, okButton, cancelButton), 1, 2);

		dialog.setScene(DialogWindowSupport.createScene(DialogWindowSupport.createDialogRoot(grid), 360, 160));
		return dialog;
	}

	public void setBankInfo(String bankCode, String bankName) {
		this.bankCode = bankCode;
		this.bankName = bankName;
	}

	public char[] getPin() {
		return pin;
	}

	private String getBankCodeForDisplay() {
		return bankCode != null ? bankCode : "";
	}

	private String getBankNameForDisplay() {
		return bankName != null ? bankName : "";
	}
}
