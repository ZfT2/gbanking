package de.gbanking.gui.fx.panel.recipient;

import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.gui.fx.panel.AbstractTitledFormPanel;
import de.gbanking.gui.fx.panel.overview.RecipientOverviewPanel;
import de.gbanking.gui.fx.util.FormStyleUtils;
import de.gbanking.gui.fx.util.FormStyleUtils.FieldWidth;
import de.gbanking.util.TypeConverter;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public class RecipientDetailPanel extends AbstractTitledFormPanel {

	private static final Logger log = LogManager.getLogger(RecipientDetailPanel.class);

	private final TextField nameText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);
	private final TextField ibanText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.L);
	private final TextField bicText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final TextField accountNumberText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final TextField blzText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	private final TextField bankText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);
	private final TextArea noteText = FormStyleUtils.prepareLargeTextArea(new TextArea(), 3);
	private final TextField updatedAtText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);

	private final Button buttonRecipientNew = new Button();
	private final Button buttonRecipientSave = new Button();
	private final Button buttonRecipientDelete = new Button();

	private final RecipientOverviewPanel parentPanel;
	private Recipient selectedRecipient;

	public RecipientDetailPanel(RecipientOverviewPanel parentPanel) {
		super("UI_PANEL_RECIPIENT_DETAILS");
		this.parentPanel = parentPanel;
		createInnerRecipientDetailPanel();
	}

	private void createInnerRecipientDetailPanel() {
		buttonRecipientNew.setText(getText("UI_BUTTON_NEW"));
		buttonRecipientSave.setText(getText("UI_BUTTON_SAVE"));
		buttonRecipientDelete.setText(getText("UI_BUTTON_DELETE"));

		buttonRecipientNew.setOnAction(e -> resetTextFields());
		buttonRecipientSave.setOnAction(e -> saveRecipient());
		buttonRecipientDelete.setOnAction(e -> deleteRecipient());

		updatedAtText.setEditable(false);
		noteText.setPrefRowCount(3);
		noteText.setWrapText(true);

		addFieldAbove("UI_LABEL_NAME", nameText, 0, 0);
		addFieldAbove("UI_LABEL_BANK", bankText, 1, 0);
		addFieldAbove("UI_LABEL_IBAN", ibanText, 0, 1);
		addFieldAbove("UI_LABEL_BIC", bicText, 1, 1);
		addFieldAbove("UI_LABEL_ACCOUNT_NUMBER", accountNumberText, 0, 2);
		addFieldAbove("UI_LABEL_BLZ", blzText, 1, 2);
		addFieldAbove("UI_LABEL_NOTE", noteText, 0, 3);
		addFieldAbove("UI_LABEL_UPDATED_AT", updatedAtText, 1, 3);

		HBox buttonBar = FormStyleUtils.createButtonBar(buttonRecipientNew, buttonRecipientSave, buttonRecipientDelete);
		addContentNode(buttonBar);
	}

	private void saveRecipient() {
		if (nameText.getText().isBlank() || ibanText.getText().isBlank()) {
			new Alert(Alert.AlertType.WARNING, getText("ALERT_RECIPIENT_REQUIRED_FIELD_MISSING")).showAndWait();
			return;
		}

		Recipient recipient = new Recipient(nameText.getText(), ibanText.getText());
		recipient.setSource(Source.MANUELL);

		if (!bicText.getText().isBlank()) {
			recipient.setBic(bicText.getText());
		}
		if (!accountNumberText.getText().isBlank()) {
			recipient.setAccountNumber(accountNumberText.getText());
		}
		if (!blzText.getText().isBlank()) {
			recipient.setBlz(blzText.getText());
		}
		if (!bankText.getText().isBlank()) {
			recipient.setBank(bankText.getText());
		}
		if (!noteText.getText().isBlank()) {
			recipient.setNote(noteText.getText());
		}

		bean.saveRecipientToDB(recipient);
		parentPanel.getRecipientListPanel().refresh();
	}

	private void deleteRecipient() {
		if (selectedRecipient != null) {
			bean.deleteRecipientFromDB(selectedRecipient);
			parentPanel.getRecipientListPanel().refresh();
			resetTextFields();
		}
	}

	private void resetTextFields() {
		for (TextField field : List.of(nameText, ibanText, bicText, accountNumberText, blzText, bankText, updatedAtText)) {
			field.clear();
		}
		noteText.clear();
		enableInputFields(true);
		selectedRecipient = null;
	}

	private void enableInputFields(boolean enable) {
		FormStyleUtils.setEditable(enable, nameText, ibanText, bicText, accountNumberText, blzText, bankText, noteText);

		updatedAtText.setEditable(false);
		updatedAtText.setDisable(true);
		FormStyleUtils.setReadOnlyStyle(true, updatedAtText);
	}

	public void updatePanelFieldValues(Recipient selectedRecipient) {
		log.log(Level.INFO, () -> getText("LOG_RECIPIENT_SELECTED", selectedRecipient.getId()));

		nameText.setText(selectedRecipient.getName());
		ibanText.setText(selectedRecipient.getIban());
		bicText.setText(selectedRecipient.getBic());
		accountNumberText.setText(selectedRecipient.getAccountNumber());
		blzText.setText(selectedRecipient.getBlz());
		bankText.setText(selectedRecipient.getBank());
		noteText.setText(selectedRecipient.getNote());
		updatedAtText.setText(TypeConverter.toDateStringLong(selectedRecipient.getUpdatedAt()));

		parentPanel.setCurrentRecipient(selectedRecipient);

		boolean isRecipientEditable = bean.isRecipientEditable(selectedRecipient);
		buttonRecipientDelete.setDisable(!isRecipientEditable);
		enableInputFields(isRecipientEditable);

		this.selectedRecipient = selectedRecipient;
	}
}