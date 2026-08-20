package de.zft2.gbanking.gui.panel.account;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountRetrievalStatus;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.dialog.AccountIdentifiersDialog;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.GuiContext;
import de.zft2.gbanking.gui.KeyboardShortcutDispatcher;
import de.zft2.gbanking.gui.panel.AbstractReadonlyDetailPanel;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.DetailFormEditMode;
import de.zft2.gbanking.gui.util.FormFields;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.gui.util.FxTableUtils;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;

public class AccountDetailPanel extends AbstractReadonlyDetailPanel {

	private static final String DEFAULT_CURRENCY = "EUR";
	private static final String UI_BUTTON_CANCEL = "UI_BUTTON_CANCEL";
	private static final String UI_LABEL_ACCOUNT_TYPE = "UI_LABEL_ACCOUNT_TYPE";
	private static final String UI_LABEL_CURRENCY = "UI_LABEL_CURRENCY";

	private final boolean fullDetails;
	private final Runnable afterChange;

	private final TextField accountNameText = FormFields.textM();
	private final TextField accountIbanText = FormFields.textM();

	private final TextField bankNameText = FormFields.textM();
	private final TextField accountTypText = FormFields.textS();
	private final ComboBox<AccountType> accountTypeCombo = FormFields.comboS(FXCollections.observableArrayList(AccountType.values()));

	private final TextField numberText = FormFields.textS();
	private final TextField subnumberText = FormFields.textXs();

	private final TextField bankAccessText = FormFields.textS();
	private final TextField currencyText = FormFields.textXs();
	private final TextField bankBalanceText = FormFields.textXs();

	private final TextField bicText = FormFields.textS();
	private final TextField blzText = FormFields.textS();

	private final TextField ownerNameText = FormFields.textM();
	private final TextField ownerName2Text = FormFields.textM();

	private final CheckBox isSEPAAccount = FormFields.checkBox();

	private final TextField createdAtText = FormFields.textS();
	private final TextField updatedAtText = FormFields.textS();
	private final TextField retrievalAtText = FormFields.textS();
	private final TextField retrievalResultText = FormFields.textM();
	private final TextField retrievalCountsText = FormFields.textS();

	private CheckBox isOfflineAccount;
	private ComboBox<AccountState> accountStateCombo;

	private final Button buttonAccountEdit = new Button(getText("UI_BUTTON_EDIT"));
	private final Button buttonAccountNew = new Button(getText("UI_BUTTON_ACCOUNT_NEW"));
	private final Button buttonAccountDelete = new Button(getText("UI_BUTTON_DELETE"));
	private final Button buttonAccountIdentifiers = new Button(getText("UI_BUTTON_ACCOUNT_IDENTIFIERS"));
	private final Button buttonAccountSave = new Button(getText("UI_BUTTON_SAVE"));
	private final Button buttonAccountCancel = new Button(getText(UI_BUTTON_CANCEL));

	private BankAccount currentAccount;
	private boolean creatingNewAccount;
	private DetailFormEditMode editModeController;
	private Node bankBalanceRow;
	private final DecimalFormat amountFormat = FxTableUtils.createGermanDecimalFormat();

	public AccountDetailPanel(boolean fullDetails) {
		this(fullDetails, null);
	}

	public AccountDetailPanel(boolean fullDetails, Runnable afterChange) {
		super("UI_PANEL_ACCOUNT_DETAILS");
		this.fullDetails = fullDetails;
		this.afterChange = afterChange;
		configureGrid();
		createPanel();
	}

	private void configureGrid() {
		formGrid.getColumnConstraints().clear();
		formGrid.getColumnConstraints().addAll(createGrowColumn(), createGrowColumn(), createGrowColumn());
	}

	private ColumnConstraints createGrowColumn() {
		ColumnConstraints constraints = new ColumnConstraints();
		constraints.setHgrow(Priority.ALWAYS);
		constraints.setFillWidth(true);
		return constraints;
	}

	private void createPanel() {
		if (fullDetails) {
			addFullDetailsFields();
		} else {
			addReadonlyDetailsFields();
		}

		makeReadOnly(accountIbanText, bankNameText, accountTypText, bankAccessText, currencyText, bankBalanceText, bicText, blzText, numberText, subnumberText,
				ownerNameText, ownerName2Text, createdAtText, updatedAtText, retrievalAtText, retrievalResultText, retrievalCountsText);

		FormStyleUtils.setReadOnlyStyle(true, accountIbanText, bankNameText, accountTypText, bankAccessText, currencyText, bankBalanceText, bicText, blzText,
				numberText, subnumberText, ownerNameText, ownerName2Text, createdAtText, updatedAtText, retrievalAtText, retrievalResultText,
				retrievalCountsText);
		setBankBalanceVisible(false);

		disable(isSEPAAccount);

		if (isOfflineAccount != null) {
			disable(isOfflineAccount);
		}
		if (accountStateCombo != null) {
			disable(accountStateCombo);
			FormStyleUtils.setReadOnlyStyle(true, accountStateCombo);
		}

		if (fullDetails) {
			configureButtons();
			KeyboardShortcutDispatcher.registerForm(this, buttonAccountSave, buttonAccountCancel);
			KeyboardShortcutDispatcher.blockRefreshWhile(this, buttonAccountSave::isVisible);
			addContentNode(FormStyleUtils.createButtonBar(buttonAccountEdit, buttonAccountNew, buttonAccountDelete, buttonAccountIdentifiers,
					buttonAccountSave, buttonAccountCancel));
			editModeController = new DetailFormEditMode(editableControls(), List.of(), List.of(buttonAccountNew),
					List.of(buttonAccountEdit, buttonAccountDelete, buttonAccountIdentifiers),
					List.of(buttonAccountSave, buttonAccountCancel));
			setEditMode(false);
		}
	}

	private void addFullDetailsFields() {
		addFieldInline("UI_LABEL_ACCOUNT_NAME", accountNameText, 0, 0);
		addFieldInline("UI_LABEL_OWNER", ownerNameText, 1, 0);
		addFieldInline("UI_LABEL_IBAN", accountIbanText, 2, 0);

		addFieldInline("UI_LABEL_OWNER_2", ownerName2Text, 0, 1);
		addFieldInline("UI_LABEL_ACCOUNT_NUMBER", numberText, 1, 1);
		addFieldInline("UI_LABEL_BIC", bicText, 2, 1);

		addFieldInline(UI_LABEL_ACCOUNT_TYPE, accountTypeCombo, 0, 2);
		addFieldInline("UI_LABEL_SUBNUMBER", subnumberText, 1, 2);
		addFieldInline("UI_LABEL_BLZ", blzText, 2, 2);

		addFieldInline(UI_LABEL_CURRENCY, currencyText, 0, 3);
		addFieldInline("UI_LABEL_SEPA_ACCOUNT", isSEPAAccount, 1, 3);
		addFieldInline("UI_LABEL_BANK", bankNameText, 2, 3);

		addBankBalanceField(0, 4);
		addFieldInline("UI_LABEL_BANK_ACCESS", bankAccessText, 1, 4);
		addFieldInline("UI_LABEL_CREATED_AT", createdAtText, 2, 4);

		isOfflineAccount = FormFields.checkBox();
		accountStateCombo = FormFields.comboM(FXCollections.observableArrayList(AccountState.values()));

		addFieldInline("UI_LABEL_ACCOUNT_STATE", accountStateCombo, 0, 5);
		addFieldInline("UI_LABEL_OFFLINE_ACCOUNT", isOfflineAccount, 1, 5);
		addFieldInline("UI_LABEL_UPDATED_AT", updatedAtText, 2, 5);

		addRetrievalStatusFields(6);
	}

	private void addReadonlyDetailsFields() {
		addFieldInline("UI_LABEL_OWNER", ownerNameText, 0, 0);
		addFieldInline("UI_LABEL_IBAN", accountIbanText, 1, 0);
		addFieldInline("UI_LABEL_BIC", bicText, 2, 0);

		addFieldInline("UI_LABEL_OWNER_2", ownerName2Text, 0, 1);
		addFieldInline("UI_LABEL_ACCOUNT_NUMBER", numberText, 1, 1);
		addFieldInline("UI_LABEL_BLZ", blzText, 2, 1);

		addFieldInline(UI_LABEL_ACCOUNT_TYPE, accountTypText, 0, 2);
		addFieldInline("UI_LABEL_SUBNUMBER", subnumberText, 1, 2);
		addFieldInline("UI_LABEL_BANK", bankNameText, 2, 2);

		addFieldInline(UI_LABEL_CURRENCY, currencyText, 0, 3);
		addFieldInline("UI_LABEL_SEPA_ACCOUNT", isSEPAAccount, 1, 3);
		addFieldInline("UI_LABEL_BANK_ACCESS", bankAccessText, 2, 3);

		addBankBalanceField(0, 4);
		addFieldInline("UI_LABEL_UPDATED_AT", updatedAtText, 2, 4);

		addRetrievalStatusFields(5);
	}

	private void addRetrievalStatusFields(int row) {
		addFieldInline("UI_LABEL_ACCOUNT_RETRIEVAL_AT", retrievalAtText, 0, row);
		addFieldInline("UI_LABEL_ACCOUNT_RETRIEVAL_RESULT", retrievalResultText, 1, row);
		addFieldInline("UI_LABEL_ACCOUNT_RETRIEVAL_COUNTS", retrievalCountsText, 2, row);
	}

	private void addBankBalanceField(int col, int row) {
		addFieldInline("UI_LABEL_BANK_BALANCE", bankBalanceText, col, row);
		bankBalanceRow = bankBalanceText.getParent();
	}

	public void updatePanelFieldValues(BankAccount bankAccount) {
		creatingNewAccount = false;
		currentAccount = bankAccount;
		fillForm(bankAccount);
		if (fullDetails) {
			setEditMode(false);
		}
	}

	private void fillForm(BankAccount bankAccount) {
		if (bankAccount == null) {
			clearForm();
			return;
		}
		updateTitle(bankAccount.getAccountName());

		accountNameText.setText(bankAccount.getAccountName());
		accountIbanText.setText(bankAccount.getIban());
		accountTypText.setText(bankAccount.getAccountType() != null ? bankAccount.getAccountType().toString() : "");
		accountTypeCombo.setValue(bankAccount.getAccountType());
		bankNameText.setText(bankAccount.getBankName());
		bankAccessText.setText(getBankAccessDisplayName(bankAccount.getBankAccessId()));
		currencyText.setText(bankAccount.getCurrency());
		bankBalanceText.setText(formatAmount(bankAccount.getBalance()));
		setBankBalanceVisible(isOnlineAccount(bankAccount));
		bicText.setText(bankAccount.getBic());
		blzText.setText(bankAccount.getBlz());
		numberText.setText(bankAccount.getNumber());
		subnumberText.setText(bankAccount.getSubnumber());
		ownerNameText.setText(bankAccount.getOwnerName());
		ownerName2Text.setText(bankAccount.getOwnerName2());
		isSEPAAccount.setSelected(bankAccount.isSEPAAccount());

		if (fullDetails) {
			if (isOfflineAccount != null) {
				isOfflineAccount.setSelected(bankAccount.isOfflineAccount());
			}
			if (accountStateCombo != null) {
				accountStateCombo.setValue(bankAccount.getAccountState());
			}
		}

		createdAtText.setText(DateFormatUtils.formatShort(bankAccount.getCreatedAt()));
		updatedAtText.setText(DateFormatUtils.formatShort(bankAccount.getUpdatedAt()));
		fillRetrievalStatus(bankAccount.getId());
	}

	private void fillRetrievalStatus(int bankAccountId) {
		BankAccountRetrievalStatus retrievalStatus = dbController.getBankAccountRetrievalStatus(bankAccountId);
		if (retrievalStatus == null) {
			clearRetrievalStatus();
			return;
		}

		retrievalAtText.setText(DateFormatUtils.formatDateTime(retrievalStatus.retrievedAt()));
		String error = retrievalStatus.lastError();
		String result = retrievalStatus.result().toString();
		retrievalResultText.setText(error == null || error.isBlank() ? result : result + " - " + error);
		retrievalResultText.setTooltip(error == null || error.isBlank() ? null : new Tooltip(error));
		retrievalCountsText.setText(retrievalStatus.newBookingCount() + " / " + retrievalStatus.pendingBookingCount());
	}

	private void clearForm() {
		resetTitle();
		clearTextFields(accountNameText, accountIbanText, accountTypText, bankNameText, bankAccessText, currencyText, bankBalanceText, bicText, blzText,
				numberText, subnumberText, ownerNameText, ownerName2Text, createdAtText, updatedAtText, retrievalAtText, retrievalResultText,
				retrievalCountsText);
		retrievalResultText.setTooltip(null);
		accountTypeCombo.setValue(null);
		isSEPAAccount.setSelected(false);
		setBankBalanceVisible(false);
		if (fullDetails) {
			if (isOfflineAccount != null) {
				isOfflineAccount.setSelected(false);
			}
			if (accountStateCombo != null) {
				accountStateCombo.setValue(null);
			}
		}
	}

	private void clearRetrievalStatus() {
		clearTextFields(retrievalAtText, retrievalResultText, retrievalCountsText);
		retrievalResultText.setTooltip(null);
	}

	private static void clearTextFields(TextField... fields) {
		for (TextField field : fields) {
			field.clear();
		}
	}

	private void configureButtons() {
		buttonAccountEdit.setOnAction(e -> enableEdit());
		buttonAccountNew.setOnAction(e -> startNewAccount());
		buttonAccountDelete.setOnAction(e -> deleteCurrentAccount());
		buttonAccountIdentifiers.setOnAction(e -> AccountIdentifiersDialog.showAndWait(getOwnerWindow(), currentAccount));
		buttonAccountSave.setOnAction(e -> saveChanges());
		buttonAccountCancel.setOnAction(e -> cancelChanges());
	}

	private void startNewAccount() {
		creatingNewAccount = true;
		clearForm();
		currencyText.setText(DEFAULT_CURRENCY);
		isOfflineAccount.setSelected(true);
		accountStateCombo.setValue(AccountState.ACTIVE);
		setEditMode(true);
		accountNameText.requestFocus();
	}

	private void enableEdit() {
		if (currentAccount == null || isLinkedBankAccessEditCancelled()) {
			return;
		}

		setEditMode(true);
	}

	private boolean isLinkedBankAccessEditCancelled() {
		Integer bankAccessId = currentAccount.getBankAccessId();
		return bankAccessId != null && bankAccessId > 0 && !showLinkedBankAccessEditWarning();
	}

	private boolean showLinkedBankAccessEditWarning() {
		ButtonType editButton = new ButtonType(getText("UI_BUTTON_EDIT"));
		ButtonType cancelButton = new ButtonType(getText(UI_BUTTON_CANCEL), ButtonBar.ButtonData.CANCEL_CLOSE);
		return DialogWindowSupport.showConfirmation(getOwnerWindow(), AlertType.WARNING, getText("ALERT_ACCOUNT_BANK_ACCESS_EDIT_TITLE"),
				getText("ALERT_ACCOUNT_BANK_ACCESS_EDIT_HEADER"), getText("ALERT_ACCOUNT_BANK_ACCESS_EDIT_TEXT"), editButton, cancelButton);
	}

	private void saveChanges() {
		if (!creatingNewAccount && currentAccount == null) {
			return;
		}

		String missingFieldKey = findMissingRequiredFieldKey();
		if (missingFieldKey != null) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_ACCOUNT_REQUIRED_FIELD_MISSING", getText(missingFieldKey)));
			return;
		}

		BankAccount accountToSave = creatingNewAccount ? createManualAccount() : currentAccount;
		applyFormTo(accountToSave);
		if (creatingNewAccount && hasExistingIdentifier(accountToSave)) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_ACCOUNT_ALREADY_EXISTS"));
			return;
		}

		BankAccount savedAccount = dbController.insertOrUpdate(accountToSave);
		if (savedAccount == null || savedAccount.getId() <= 0) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.ERROR, getText("ALERT_ACCOUNT_SAVE_FAILED"));
			return;
		}

		BankAccount persistedAccount = dbController.getById(BankAccount.class, savedAccount.getId());
		currentAccount = persistedAccount != null ? persistedAccount : savedAccount;
		creatingNewAccount = false;
		GuiContext.setSelectedAccount(currentAccount);
		fillForm(currentAccount);
		setEditMode(false);
		notifyAfterChange();
	}

	private String findMissingRequiredFieldKey() {
		if (trimToNull(accountNameText.getText()) == null) {
			return "UI_LABEL_ACCOUNT_NAME";
		}
		if (accountTypeCombo.getValue() == null) {
			return UI_LABEL_ACCOUNT_TYPE;
		}
		if (trimToNull(currencyText.getText()) == null) {
			return UI_LABEL_CURRENCY;
		}
		return accountStateCombo.getValue() == null ? "UI_LABEL_ACCOUNT_STATE" : null;
	}

	private boolean hasExistingIdentifier(BankAccount accountToSave) {
		String iban = trimToNull(accountToSave.getIban());
		String number = trimToNull(accountToSave.getNumber());
		if (iban == null && number == null) {
			return false;
		}

		return dbController.getAll(BankAccount.class).stream().anyMatch(account ->
				iban != null && iban.equalsIgnoreCase(trimToNull(account.getIban()))
						|| number != null && number.equals(trimToNull(account.getNumber())));
	}

	private BankAccount createManualAccount() {
		BankAccount account = new BankAccount();
		account.setSource(Source.MANUELL);
		account.setBankAccessId(null);
		account.setCurrency(DEFAULT_CURRENCY);
		account.setOfflineAccount(true);
		account.setAccountState(AccountState.ACTIVE);
		return account;
	}

	private void applyFormTo(BankAccount bankAccount) {
		bankAccount.setAccountName(trimToNull(accountNameText.getText()));
		bankAccount.setIban(trimToNull(accountIbanText.getText()));
		bankAccount.setAccountType(accountTypeCombo.getValue());
		bankAccount.setBankName(trimToNull(bankNameText.getText()));
		bankAccount.setCurrency(trimToNull(currencyText.getText()));
		bankAccount.setBic(trimToNull(bicText.getText()));
		bankAccount.setBlz(trimToNull(blzText.getText()));
		bankAccount.setNumber(trimToNull(numberText.getText()));
		bankAccount.setSubnumber(trimToNull(subnumberText.getText()));
		bankAccount.setOwnerName(trimToNull(ownerNameText.getText()));
		bankAccount.setOwnerName2(trimToNull(ownerName2Text.getText()));
		bankAccount.setSEPAAccount(isSEPAAccount.isSelected());

		if (fullDetails) {
			if (isOfflineAccount != null) {
				bankAccount.setOfflineAccount(isOfflineAccount.isSelected());
			}
			if (accountStateCombo != null) {
				bankAccount.setAccountState(accountStateCombo.getValue());
			}
		}
	}

	private void cancelChanges() {
		creatingNewAccount = false;
		if (currentAccount != null) {
			fillForm(currentAccount);
		} else {
			clearForm();
		}
		setEditMode(false);
	}

	private void deleteCurrentAccount() {
		if (currentAccount == null || !confirmAccountDeletion()) {
			return;
		}

		if (!dbController.delete(currentAccount, null)) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.ERROR, getText("ALERT_ACCOUNT_DELETE_FAILED"));
			return;
		}

		currentAccount = null;
		creatingNewAccount = false;
		GuiContext.clearSelectedAccount();
		clearForm();
		setEditMode(false);
		notifyAfterChange();
	}

	private boolean confirmAccountDeletion() {
		String bankAccessInfo = getText(isOnlineAccount(currentAccount) ? "ALERT_ACCOUNT_DELETE_LINKED" : "ALERT_ACCOUNT_DELETE_NOT_LINKED");
		ButtonType deleteButton = new ButtonType(getText("UI_BUTTON_DELETE"), ButtonBar.ButtonData.OK_DONE);
		ButtonType cancelButton = new ButtonType(getText(UI_BUTTON_CANCEL), ButtonBar.ButtonData.CANCEL_CLOSE);
		return DialogWindowSupport.showConfirmation(getOwnerWindow(), AlertType.WARNING, getText("ALERT_ACCOUNT_DELETE_TITLE"),
				getText("ALERT_ACCOUNT_DELETE_HEADER"), getText("ALERT_ACCOUNT_DELETE_TEXT", currentAccount.getAccountName(), bankAccessInfo), deleteButton,
				cancelButton);
	}

	private void notifyAfterChange() {
		if (afterChange != null) {
			afterChange.run();
		}
	}

	private void setEditMode(boolean editMode) {
		if (!fullDetails) {
			return;
		}

		editModeController.apply(editMode, currentAccount != null);
		boolean createMode = editMode && creatingNewAccount;
		isOfflineAccount.setDisable(!editMode || createMode);
		FormStyleUtils.setReadOnlyStyle(!editMode || createMode, isOfflineAccount);
	}

	private List<Control> editableControls() {
		List<Control> controls = new ArrayList<>();
		controls.add(accountNameText);
		controls.add(accountIbanText);
		controls.add(accountTypeCombo);
		controls.add(bankNameText);
		controls.add(currencyText);
		controls.add(bicText);
		controls.add(blzText);
		controls.add(numberText);
		controls.add(subnumberText);
		controls.add(ownerNameText);
		controls.add(ownerName2Text);
		controls.add(isSEPAAccount);
		if (isOfflineAccount != null) {
			controls.add(isOfflineAccount);
		}
		if (accountStateCombo != null) {
			controls.add(accountStateCombo);
		}
		return controls;
	}

	private String getBankAccessDisplayName(Integer bankAccessId) {
		if (bankAccessId == null || bankAccessId <= 0) {
			return "";
		}

		BankAccess bankAccess = dbController.getBankAccessById(bankAccessId);
		if (bankAccess == null) {
			return "";
		}

		String bankName = trimToNull(bankAccess.getBankName());
		String userId = switch (bankAccess.getAccessType()) {
		case HBCI -> trimToNull(bankAccess.getFints().getUserId());
		case PAYPAL -> trimToNull(bankAccess.getPaypal().getUserId());
		case ENABLEBANKING -> null;
		};
		if (bankName != null && userId != null) {
			return bankName + " (" + userId + ")";
		}
		return bankName != null ? bankName : getUserId(userId);
	}

	private String getUserId(String userId) {
		return userId != null ? userId : "";
	}

	private String formatAmount(BigDecimal amount) {
		return amount != null ? amountFormat.format(amount) : "";
	}

	private boolean isOnlineAccount(BankAccount bankAccount) {
		Integer bankAccessId = bankAccount.getBankAccessId();
		return bankAccessId != null && bankAccessId > 0;
	}

	private void setBankBalanceVisible(boolean visible) {
		if (bankBalanceRow != null) {
			bankBalanceRow.setVisible(visible);
			bankBalanceRow.setManaged(visible);
		}
	}

}
