package de.gbanking.gui.dialog;

import de.gbanking.messages.Messages;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public class HbciCallbackMessageDialog {

	private final Window parentWindow;
	private final Messages messages;

	public HbciCallbackMessageDialog(Window parentWindow) {
		this.parentWindow = parentWindow;
		this.messages = Messages.getInstance();
	}

	public Stage createDialog(String message, String details) {
		Stage dialog = DialogWindowSupport.createModalStage(parentWindow, "UI_DIALOG_HBCI_FEEDBACK_TITLE");

		Label headerLabel = new Label(messages.getMessage("UI_DIALOG_HBCI_FEEDBACK_HEADER"));
		headerLabel.setWrapText(true);

		TextArea messageArea = createReadOnlyTextArea(message);
		messageArea.setPrefRowCount(6);

		TextArea detailsArea = createReadOnlyTextArea(details);
		detailsArea.setPrefRowCount(10);

		Label detailsLabel = new Label(messages.getMessage("UI_DIALOG_HBCI_FEEDBACK_DETAILS"));

		Button okButton = new Button(messages.getMessage("UI_BUTTON_OK"));
		okButton.setDefaultButton(true);
		okButton.setOnAction(event -> dialog.close());

		VBox root = DialogWindowSupport.createDialogRoot(headerLabel, messageArea, detailsLabel, detailsArea,
				DialogWindowSupport.createButtonBar(okButton));
		DialogWindowSupport.setVgrowAlways(messageArea, detailsArea);

		dialog.setScene(DialogWindowSupport.createScene(root, 700, 420));
		return dialog;
	}

	private TextArea createReadOnlyTextArea(String text) {
		TextArea textArea = new TextArea(text == null ? "" : text);
		textArea.setEditable(false);
		textArea.setWrapText(true);
		textArea.setFocusTraversable(false);
		return textArea;
	}
}
