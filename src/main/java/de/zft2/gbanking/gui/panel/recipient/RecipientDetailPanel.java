package de.zft2.gbanking.gui.panel.recipient;

import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.KeyboardShortcutDispatcher;
import de.zft2.gbanking.gui.panel.AbstractTitledFormPanel;
import de.zft2.gbanking.gui.panel.overview.RecipientOverviewPanel;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.gui.util.FormStyleUtils.FieldWidth;
import de.zft2.gbanking.service.recipient.RecipientService;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.util.TypeConverter;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
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
	private final CheckBox defaultRecipientCheckBox = new CheckBox();
	private final TextField updatedAtText = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);

	private final Button buttonRecipientNew = new Button();
	private final Button buttonRecipientSave = new Button();
	private final Button buttonRecipientDelete = new Button();

	private final RecipientOverviewPanel parentPanel;
	private final RecipientService recipientService;
	private Recipient selectedRecipient;

	public RecipientDetailPanel(RecipientOverviewPanel parentPanel) {
		this(parentPanel, ServiceRegistry.getService(RecipientService.class));
	}

	RecipientDetailPanel(RecipientOverviewPanel parentPanel, RecipientService recipientService) {
		super("UI_PANEL_RECIPIENT_DETAILS");
		this.parentPanel = parentPanel;
		this.recipientService = recipientService;
		createInnerRecipientDetailPanel();
	}

	private void createInnerRecipientDetailPanel() {
		buttonRecipientNew.setText(getText("UI_BUTTON_NEW"));
		buttonRecipientSave.setText(getText("UI_BUTTON_SAVE"));
		buttonRecipientDelete.setText(getText("UI_BUTTON_DELETE"));

		buttonRecipientNew.setOnAction(e -> resetTextFields());
		buttonRecipientSave.setOnAction(e -> saveRecipient());
		buttonRecipientDelete.setOnAction(e -> deleteRecipient());
		KeyboardShortcutDispatcher.registerForm(this, buttonRecipientSave, this::resetTextFields);

		updatedAtText.setEditable(false);
		noteText.setPrefRowCount(3);
		noteText.setWrapText(true);

		addFieldAbove("UI_LABEL_NAME", nameText, 0, 0);
		addFieldAbove("UI_LABEL_BANK", bankText, 1, 0);
		addFieldAbove("UI_LABEL_IBAN", ibanText, 0, 1);
		addFieldAbove("UI_LABEL_BIC", bicText, 1, 1);
		addFieldAbove("UI_LABEL_ACCOUNT_NUMBER", accountNumberText, 0, 2);
		addFieldAbove("UI_LABEL_BLZ", blzText, 1, 2);
		addFieldAbove("UI_LABEL_RECIPIENT_DEFAULT", defaultRecipientCheckBox, 0, 3);
		addFieldAbove("UI_LABEL_UPDATED_AT", updatedAtText, 1, 3);
		addFieldAbove("UI_LABEL_NOTE", noteText, 0, 4, 2);

		HBox buttonBar = FormStyleUtils.createButtonBar(buttonRecipientNew, buttonRecipientSave, buttonRecipientDelete);
		addContentNode(buttonBar);
	}

	private void saveRecipient() {
		Recipient recipient = createRecipientFromForm();
		if (!isRecipientComplete(recipient)) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_RECIPIENT_REQUIRED_FIELD_MISSING"));
			return;
		}
		if (recipient.isDefault() && !confirmDefaultReplacement(recipient)) {
			return;
		}

		Recipient savedRecipient = recipientService.saveRecipientToDB(recipient);
		selectedRecipient = savedRecipient;
		parentPanel.setCurrentRecipient(savedRecipient);
		parentPanel.getRecipientListPanel().refresh();
	}

	private Recipient createRecipientFromForm() {
		Recipient recipient = new Recipient();
		if (selectedRecipient != null && selectedRecipient.getId() > 0) {
			recipient.setId(selectedRecipient.getId());
		}
		recipient.setName(trimToNull(nameText.getText()));
		recipient.setIban(trimToNull(ibanText.getText()));
		recipient.setBic(trimToNull(bicText.getText()));
		recipient.setAccountNumber(trimToNull(accountNumberText.getText()));
		recipient.setBlz(trimToNull(blzText.getText()));
		recipient.setBank(trimToNull(bankText.getText()));
		recipient.setNote(trimToNull(noteText.getText()));
		recipient.setDefault(defaultRecipientCheckBox.isSelected());
		recipient.setSource(Source.MANUELL);
		return recipient;
	}

	private boolean isRecipientComplete(Recipient recipient) {
		return !isBlank(recipient.getName()) && (!isBlank(recipient.getIban()) || !isBlank(recipient.getAccountNumber()));
	}

	private boolean confirmDefaultReplacement(Recipient recipient) {
		Recipient existingDefault = recipientService.findDefaultRecipientForSameAccountIdentifier(recipient);
		if (existingDefault == null) {
			return true;
		}

		ButtonType yesButton = new ButtonType(getText("UI_BUTTON_YES"), ButtonBar.ButtonData.YES);
		ButtonType noButton = new ButtonType(getText("UI_BUTTON_NO"), ButtonBar.ButtonData.NO);
		return DialogWindowSupport.showConfirmation(getOwnerWindow(), AlertType.CONFIRMATION, getText("ALERT_RECIPIENT_DEFAULT_CONFLICT_TITLE"),
				getText("ALERT_RECIPIENT_DEFAULT_CONFLICT_HEADER"),
				getText("ALERT_RECIPIENT_DEFAULT_CONFLICT_TEXT", recipientDisplayName(existingDefault), recipientDisplayName(recipient)), yesButton, noButton);
	}

	private String recipientDisplayName(Recipient recipient) {
		String name = trimToNull(recipient.getName());
		if (name != null) {
			return name;
		}
		String iban = trimToNull(recipient.getIban());
		if (iban != null) {
			return iban;
		}
		String accountNumber = trimToNull(recipient.getAccountNumber());
		return accountNumber != null ? accountNumber : "";
	}

	private void deleteRecipient() {
		if (selectedRecipient != null) {
			recipientService.deleteRecipientFromDB(selectedRecipient);
			parentPanel.getRecipientListPanel().refresh();
			resetTextFields();
		}
	}

	private void resetTextFields() {
		for (TextField field : List.of(nameText, ibanText, bicText, accountNumberText, blzText, bankText, updatedAtText)) {
			field.clear();
		}
		noteText.clear();
		defaultRecipientCheckBox.setSelected(false);
		enableInputFields(true);
		selectedRecipient = null;
	}

	private void enableInputFields(boolean enable) {
		FormStyleUtils.setEditable(enable, nameText, ibanText, bicText, accountNumberText, blzText, bankText, noteText);

		updatedAtText.setEditable(false);
		updatedAtText.setDisable(true);
		FormStyleUtils.setReadOnlyStyle(true, updatedAtText);
		defaultRecipientCheckBox.setDisable(false);
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
		defaultRecipientCheckBox.setSelected(selectedRecipient.isDefault());
		updatedAtText.setText(TypeConverter.toDateStringLong(selectedRecipient.getUpdatedAt()));

		parentPanel.setCurrentRecipient(selectedRecipient);

		buttonRecipientDelete.setDisable(!recipientService.isRecipientDeletable(selectedRecipient));
		enableInputFields(recipientService.isRecipientEditable(selectedRecipient));

		this.selectedRecipient = selectedRecipient;
	}
}
