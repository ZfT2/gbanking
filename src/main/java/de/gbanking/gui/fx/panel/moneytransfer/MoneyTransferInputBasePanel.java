package de.gbanking.gui.fx.panel.moneytransfer;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.Recipient;
import de.gbanking.gui.fx.panel.BasePanelHolder;
import de.gbanking.gui.fx.util.FormControlUtils;
import de.gbanking.gui.fx.util.FormGridHelper;
import de.gbanking.gui.swing.model.dto.MoneyTransferForm;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;

public abstract class MoneyTransferInputBasePanel extends BasePanelHolder {

	protected final TextField tfRecipientName = new TextField();
	protected final TextField tfIBAN = new TextField();
	protected final TextField tfBIC = new TextField();
	protected final TextField tfBank = new TextField();
	protected final TextField tfAmount = new TextField();
	protected final TextArea tfPurpose = new TextArea();
	protected final TextField tfAccountSender = new TextField();

	protected final Button buttonSubmit = new Button();

	private final MoneyTransferDetailListTabPanel parentPanel;
	private final GridPane formGrid = FormGridHelper.createDefaultGrid();
	private MoneyTransfer currentMoneytransfer;
	private boolean specificFieldsInitialized = false;

	protected MoneyTransferInputBasePanel(MoneyTransferDetailListTabPanel parentPanel) {
		this.parentPanel = parentPanel;
		createBasePanel();
	}

	private void createBasePanel() {
		FormControlUtils.prepareWrapping(tfPurpose, 3);
		tfAccountSender.setEditable(false);

		FormGridHelper.addFieldAbove(formGrid, getText("UI_LABEL_TRANSFER_RECIPIENT"), tfRecipientName, 0, 0, 3);
		FormGridHelper.addFieldAbove(formGrid, getText("UI_LABEL_TRANSFER_IBAN"), tfIBAN, 0, 1, 3);
		FormGridHelper.addFieldAbove(formGrid, getText("UI_LABEL_BIC"), tfBIC, 0, 2);
		FormGridHelper.addFieldAbove(formGrid, getText("UI_LABEL_BANK"), tfBank, 1, 2, 2);
		FormGridHelper.addFieldAbove(formGrid, getText("UI_LABEL_CURRENCY"), new Label(getText("UI_LABEL_CURRENCY_EUR")), 1, 3);
		FormGridHelper.addFieldAbove(formGrid, getText("UI_LABEL_AMOUNT"), tfAmount, 2, 3);
		FormGridHelper.addFieldAbove(formGrid, getText("UI_LABEL_PURPOSE"), tfPurpose, 0, 4, 3);

		Button buttonNew = new Button(getText("UI_BUTTON_NEW"));
		buttonNew.setOnAction(e -> resetTextFields());

		buttonSubmit.setOnAction(e -> saveTransfer());

		Button buttonDelete = new Button(getText("UI_BUTTON_DELETE"));
		buttonDelete.setOnAction(e -> deleteTransfer());

		Button buttonCancel = new Button(getText("UI_BUTTON_CANCEL"));
		buttonCancel.setOnAction(e -> resetTextFields());

		HBox buttonBar = new HBox(10, buttonNew, buttonSubmit, buttonDelete, buttonCancel);

		VBox content = new VBox(8, formGrid, buttonBar);
		content.setPadding(new Insets(6));

		TitledPane titledPane = new TitledPane(getText("UI_PANEL_MONEYTRANSFER_INPUT"), content);
		titledPane.setCollapsible(false);

		getChildren().clear();
		getChildren().add(titledPane);
	}

	protected final void initializeSpecificFields() {
		if (specificFieldsInitialized) {
			return;
		}
		addSpecificFields();
		specificFieldsInitialized = true;
	}

	protected abstract void addSpecificFields();

	protected void addFieldAbove(String key, Node field, int col, int rowGroup) {
		FormGridHelper.addFieldAbove(formGrid, getText(key), field, col, rowGroup);
	}

	protected void addFieldAbove(String key, Node field, int col, int rowGroup, int colspan) {
		FormGridHelper.addFieldAbove(formGrid, getText(key), field, col, rowGroup, colspan);
	}

	protected void saveTransfer() {
		BankAccount account = parentPanel.getSelectedAccount();

		if (account == null || tfRecipientName.getText().isBlank() || tfIBAN.getText().isBlank() || tfBank.getText().isBlank() || tfAmount.getText().isBlank()
				|| tfPurpose.getText().isBlank()) {

			new Alert(Alert.AlertType.WARNING, getText("ALERT_MONEYTRANSFER_REQUIRED_FIELD_MISSING")).showAndWait();
			return;
		}

		MoneyTransferForm moneyTransfer = new MoneyTransferForm(account, tfRecipientName.getText(), tfIBAN.getText(), tfBIC.getText(), tfBank.getText(),
				new BigDecimal(tfAmount.getText()), tfPurpose.getText());

		bean.saveMoneyTransferToDB(moneyTransfer);
		parentPanel.getMoneyTransferListPanel().reload();
	}

	protected void deleteTransfer() {
		if (currentMoneytransfer != null) {
			bean.deleteMoneyTransferFromDB(currentMoneytransfer);
			parentPanel.getMoneyTransferListPanel().reload();
		}
	}

	protected void resetTextFields() {
		FormControlUtils.clearTextInputs(List.of(tfRecipientName, tfIBAN, tfBIC, tfBank, tfAmount, tfPurpose, tfAccountSender));
	}

	void updatePanelFieldValues(MoneyTransfer selectedMoneytransfer) {
		currentMoneytransfer = selectedMoneytransfer;
		tfAmount.setText(selectedMoneytransfer.getAmount() != null ? selectedMoneytransfer.getAmount().toString() : "");
		tfPurpose.setText(selectedMoneytransfer.getPurpose());

		if (selectedMoneytransfer.getRecipient() != null) {
			updatePanelFieldValues(selectedMoneytransfer.getRecipient());
		}
	}

	public void updatePanelFieldValues(Recipient selectedRecipient) {
		tfRecipientName.setText(selectedRecipient.getName());
		tfIBAN.setText(selectedRecipient.getIban());
		tfBIC.setText(selectedRecipient.getBic());
		tfBank.setText(selectedRecipient.getBank());
	}

	public void updatePanelFieldValues(BankAccount selectedAccount) {
		tfAccountSender.setText(selectedAccount.getAccountName());
	}
}