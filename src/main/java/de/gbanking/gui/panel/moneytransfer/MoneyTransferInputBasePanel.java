package de.gbanking.gui.panel.moneytransfer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.db.dao.enu.StandingorderMode;
import de.gbanking.gui.components.BankNameLookupField;
import de.gbanking.gui.service.InstituteLookupCache;
import de.gbanking.gui.service.InstituteLookupCache.InstituteLookupEntry;
import de.gbanking.gui.dto.MoneyTransferForm;
import de.gbanking.gui.panel.AbstractTitledFormPanel;
import de.gbanking.gui.util.FormControlUtils;
import de.gbanking.gui.util.FormStyleUtils;
import de.gbanking.gui.util.FormStyleUtils.FieldWidth;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;

public abstract class MoneyTransferInputBasePanel extends AbstractTitledFormPanel {

	protected final TextField tfRecipientName = FormStyleUtils.applyWidth(new TextField(), FieldWidth.L);
	protected final TextField tfIBAN = FormStyleUtils.applyWidth(new TextField(), FieldWidth.L);
	protected final TextField tfBIC = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	protected final BankNameLookupField bankNameLookupField = new BankNameLookupField();
	protected final TextField tfAmount = FormStyleUtils.applyWidth(new TextField(), FieldWidth.S);
	protected final TextArea tfPurpose = FormStyleUtils.prepareLargeTextArea(new TextArea(), 3);
	protected final TextField tfAccountSender = FormStyleUtils.applyWidth(new TextField(), FieldWidth.M);

	protected final Button buttonSubmit = new Button();

	private final MoneyTransferDetailListTabPanel parentPanel;
	private MoneyTransfer currentMoneytransfer;
	private boolean specificFieldsInitialized = false;
	private boolean updatingBicProgrammatically = false;
	private boolean bicProtectedFromLookup = false;

	protected MoneyTransferInputBasePanel(MoneyTransferDetailListTabPanel parentPanel) {
		super("UI_PANEL_MONEYTRANSFER_INPUT");
		this.parentPanel = parentPanel;
		createBasePanel();
	}

	private void createBasePanel() {
		FormControlUtils.prepareWrapping(tfPurpose, 3);
		tfAccountSender.setEditable(false);
		FormStyleUtils.setReadOnlyStyle(true, tfAccountSender);

		addFieldAbove("UI_LABEL_TRANSFER_RECIPIENT", tfRecipientName, 0, 0, 3);
		addFieldAbove("UI_LABEL_TRANSFER_IBAN", tfIBAN, 0, 1, 3);
		addFieldAbove("UI_LABEL_BIC", tfBIC, 0, 2);
		addFieldAbove("UI_LABEL_BANK", bankNameLookupField, 1, 2, 2);
		addFieldAbove("UI_LABEL_CURRENCY", new Label(getText("UI_LABEL_CURRENCY_EUR")), 1, 3);
		addFieldAbove("UI_LABEL_AMOUNT", tfAmount, 2, 3);
		addFieldAbove("UI_LABEL_PURPOSE", tfPurpose, 0, 4, 3);

		Button buttonNew = new Button(getText("UI_BUTTON_NEW"));
		buttonNew.setOnAction(e -> resetTextFields());

		buttonSubmit.setOnAction(e -> saveTransfer());

		tfIBAN.focusedProperty().addListener((observable, oldValue, newValue) -> {
			if (Boolean.FALSE.equals(newValue)) {
				updateBankDataFromIban(false);
			}
		});
		bankNameLookupField.selectedEntryProperty().addListener((observable, oldValue, newValue) -> applyLookupBicIfAllowed(newValue));
		tfBIC.textProperty().addListener((observable, oldValue, newValue) -> {
			if (!updatingBicProgrammatically) {
				bicProtectedFromLookup = !isBlank(newValue);
			}
		});

		Button buttonDelete = new Button(getText("UI_BUTTON_DELETE"));
		buttonDelete.setOnAction(e -> deleteTransfer());

		Button buttonCancel = new Button(getText("UI_BUTTON_CANCEL"));
		buttonCancel.setOnAction(e -> resetTextFields());

		HBox buttonBar = FormStyleUtils.createButtonBar(buttonNew, buttonSubmit, buttonDelete, buttonCancel);
		addContentNode(buttonBar);
	}

	protected final void initializeSpecificFields() {
		if (specificFieldsInitialized) {
			return;
		}
		addSpecificFields();
		specificFieldsInitialized = true;
	}

	protected abstract void addSpecificFields();

	protected void saveTransfer() {
		BankAccount account = parentPanel.getSelectedAccount();

		if (!validateTransferInput(account)) {
			new Alert(Alert.AlertType.WARNING, getText("ALERT_MONEYTRANSFER_REQUIRED_FIELD_MISSING")).showAndWait();
			return;
		}

		MoneyTransferForm moneyTransfer = buildMoneyTransferForm(account);

		bean.saveMoneyTransferToDB(moneyTransfer);
		parentPanel.getMoneyTransferListPanel().reload();
	}

	private boolean validateTransferInput(BankAccount account) {
		if (account == null || !validateInputElement(tfRecipientName) || !validateInputElement(tfIBAN) || !validateInputElement(tfAmount)
				|| !validateInputElement(tfPurpose)) {
			return false;
		}

		try {
			new BigDecimal(tfAmount.getText().trim());
		} catch (NumberFormatException ex) {
			return false;
		}

		return validateSpecificInput();
	}

	private boolean validateInputElement(TextInputControl textField) {
		return textField.getText() != null && !textField.getText().isBlank();
	}


	protected void deleteTransfer() {
		if (currentMoneytransfer != null) {
			bean.deleteMoneyTransferFromDB(currentMoneytransfer);
			parentPanel.getMoneyTransferListPanel().reload();
		}
	}

	protected void resetTextFields() {
		FormControlUtils.clearTextInputs(List.of(tfRecipientName, tfIBAN, tfAmount, tfPurpose, tfAccountSender));
		setBicText("", false);
		bankNameLookupField.clear();
		resetSpecificFields();
	}

	void updatePanelFieldValues(MoneyTransfer selectedMoneytransfer) {
		currentMoneytransfer = selectedMoneytransfer;
		tfAmount.setText(selectedMoneytransfer.getAmount() != null ? selectedMoneytransfer.getAmount().toString() : "");
		tfPurpose.setText(selectedMoneytransfer.getPurpose());
		updateSpecificFieldValues(selectedMoneytransfer);

		if (selectedMoneytransfer.getRecipient() != null) {
			updatePanelFieldValues(selectedMoneytransfer.getRecipient());
		}
	}

	public void updatePanelFieldValues(Recipient selectedRecipient) {
		tfRecipientName.setText(selectedRecipient.getName());
		tfIBAN.setText(selectedRecipient.getIban());
		setBicText(selectedRecipient.getBic(), !isBlank(selectedRecipient.getBic()));
		bankNameLookupField.setManualBankName(selectedRecipient.getBank());
		updateBankDataFromIban(true);
	}

	public void updatePanelFieldValues(BankAccount selectedAccount) {
		tfAccountSender.setText(selectedAccount.getAccountName());
	}

	private void updateBankDataFromIban(boolean ibanPrefilled) {
		String blz = InstituteLookupCache.extractGermanBlzFromIban(tfIBAN.getText());
		if (blz == null) {
			if (ibanPrefilled && isBlank(bankNameLookupField.getSelectedBankName())) {
				bankNameLookupField.clear();
			}
			return;
		}

		List<InstituteLookupEntry> entries = InstituteLookupCache.getEntriesForBlz(blz);
		if (entries.isEmpty()) {
			if (ibanPrefilled && isBlank(bankNameLookupField.getSelectedBankName())) {
				bankNameLookupField.clear();
			}
			return;
		}

		bankNameLookupField.setEntries(entries);
		applyLookupBicIfAllowed(entries.get(0));
	}

	private void applyLookupBicIfAllowed(InstituteLookupEntry selectedEntry) {
		if (selectedEntry == null || bicProtectedFromLookup || isBlank(selectedEntry.bic())) {
			return;
		}
		setBicText(selectedEntry.bic(), false);
	}

	private void setBicText(String bic, boolean protectFromLookup) {
		updatingBicProgrammatically = true;
		try {
			tfBIC.setText(bic == null ? "" : bic);
			bicProtectedFromLookup = protectFromLookup;
		} finally {
			updatingBicProgrammatically = false;
		}
	}

	public OrderType getOrderType() {
		return OrderType.TRANSFER;
	}

	protected LocalDate getExecutionDate() {
		return LocalDate.now();
	}

	protected Integer getExecutionDay() {
		return null;
	}

	protected StandingorderMode getStandingorderMode() {
		return null;
	}

	protected boolean validateSpecificInput() {
		return true;
	}

	protected void resetSpecificFields() {
		// default: no-op
	}

	protected void updateSpecificFieldValues(MoneyTransfer selectedMoneytransfer) {
		// default: no-op
	}

	private MoneyTransferForm buildMoneyTransferForm(BankAccount account) {
		return new MoneyTransferForm(account, getOrderType(), tfRecipientName.getText().trim(), tfIBAN.getText().trim(), trimToNull(tfBIC.getText()),
				trimToNull(bankNameLookupField.getSelectedBankName()), new BigDecimal(tfAmount.getText().trim()), tfPurpose.getText().trim(), getExecutionDate(),
				getExecutionDay(), getStandingorderMode());
	}

	protected MoneyTransferDetailListTabPanel getParentPanel() {
		return parentPanel;
	}
}
