package de.zft2.gbanking.gui.panel.stock;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.KeyboardShortcutDispatcher;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.panel.AbstractReadonlyDetailPanel;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.DetailFormEditMode;
import de.zft2.gbanking.gui.util.FormFields;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.service.stock.StockPortfolioAdministrationService;
import de.zft2.gbanking.service.stock.StockPortfolioAdministrationService.PortfolioDetails;
import de.zft2.gbanking.service.stock.StockPortfolioAdministrationService.PortfolioSaveRequest;
import de.zft2.gbanking.service.stock.StockPortfolioAdministrationService.SettlementAccountOption;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;

public class StockPortfolioManagementDetailPanel extends AbstractReadonlyDetailPanel {

	private static final String UI_BUTTON_CANCEL = "UI_BUTTON_CANCEL";

	private final StockPortfolioAdministrationService service;
	private final Consumer<Integer> afterSave;

	private final TextField nameText = FormFields.textM();
	private final TextField ownerText = FormFields.textM();
	private final TextField accountNumberText = FormFields.textS();
	private final TextField owner2Text = FormFields.textM();
	private final TextField subnumberText = FormFields.textXs();
	private final TextField bankText = FormFields.textM();
	private final TextField ibanText = FormFields.textM();
	private final TextField bicText = FormFields.textS();
	private final TextField blzText = FormFields.textS();
	private final ComboBox<Currency> currencyCombo = FormFields.comboXs(FXCollections.observableArrayList(Currency.values()));
	private final ComboBox<AccountState> stateCombo = FormFields.comboM(FXCollections.observableArrayList(AccountState.values()));
	private final ComboBox<SettlementAccountOption> settlementAccountCombo = FormFields.comboL();
	private final DatePicker openedAtPicker = new DatePicker();
	private final DatePicker closedAtPicker = new DatePicker();
	private final DatePicker settlementValidFromPicker = new DatePicker();
	private final CheckBox offlineCheck = FormFields.checkBox();
	private final TextField bankAccessText = FormFields.textM();
	private final TextField createdAtText = FormFields.textS();
	private final TextField updatedAtText = FormFields.textS();

	private final Button editButton = new Button(getText("UI_BUTTON_EDIT"));
	private final Button newButton = new Button(getText("UI_BUTTON_STOCK_PORTFOLIO_NEW"));
	private final Button saveButton = new Button(getText("UI_BUTTON_SAVE"));
	private final Button cancelButton = new Button(getText(UI_BUTTON_CANCEL));

	private PortfolioDetails currentPortfolio;
	private boolean creatingNewPortfolio;
	private final DetailFormEditMode editModeController;

	public StockPortfolioManagementDetailPanel(StockPortfolioAdministrationService service, Consumer<Integer> afterSave) {
		super("UI_PANEL_STOCK_PORTFOLIO_DETAILS");
		this.service = service;
		this.afterSave = afterSave;
		FormGridHelper.setEqualGrowColumns(formGrid, 3);
		createFields();
		settlementAccountCombo.valueProperty().addListener((observable, previous, selected) -> updateSettlementDateEditability());
		configureButtons();
		makeReadOnly(nameText, ownerText, accountNumberText, owner2Text, subnumberText, bankText,
				ibanText, bicText, blzText, bankAccessText, createdAtText, updatedAtText);
		addContentNode(FormStyleUtils.createButtonBar(editButton, newButton, saveButton, cancelButton));
		editModeController = new DetailFormEditMode(editableControls(),
				List.of(bankAccessText, createdAtText, updatedAtText), List.of(newButton), List.of(editButton),
				List.of(saveButton, cancelButton));
		KeyboardShortcutDispatcher.registerForm(this, saveButton, cancelButton);
		KeyboardShortcutDispatcher.blockRefreshWhile(this, saveButton::isVisible);
		setEditMode(false);
	}

	private void createFields() {
		addFieldInline("UI_STOCK_FIELD_PORTFOLIO", nameText, 0, 0);
		addFieldInline("UI_LABEL_OWNER", ownerText, 1, 0);
		addFieldInline("UI_STOCK_FIELD_ACCOUNT_NUMBER", accountNumberText, 2, 0);

		addFieldInline("UI_LABEL_OWNER_2", owner2Text, 0, 1);
		addFieldInline("UI_LABEL_SUBNUMBER", subnumberText, 1, 1);
		addFieldInline("UI_STOCK_FIELD_BANK", bankText, 2, 1);

		addFieldInline("UI_LABEL_IBAN", ibanText, 0, 2);
		addFieldInline("UI_LABEL_BIC", bicText, 1, 2);
		addFieldInline("UI_LABEL_BLZ", blzText, 2, 2);

		addFieldInline("UI_STOCK_FIELD_CURRENCY", currencyCombo, 0, 3);
		addFieldInline("UI_LABEL_ACCOUNT_STATE", stateCombo, 1, 3);
		addFieldInline("UI_LABEL_OFFLINE_ACCOUNT", offlineCheck, 2, 3);

		addFieldInline("UI_STOCK_FIELD_OPENED_AT", openedAtPicker, 0, 4);
		addFieldInline("UI_STOCK_FIELD_CLOSED_AT", closedAtPicker, 1, 4);
		addFieldInline("UI_LABEL_BANK_ACCESS", bankAccessText, 2, 4);

		addFieldInline("UI_STOCK_FIELD_SETTLEMENT_ACCOUNT", settlementAccountCombo, 0, 5);
		addFieldInline("UI_STOCK_FIELD_SETTLEMENT_VALID_FROM", settlementValidFromPicker, 1, 5);
		addFieldInline("UI_LABEL_CREATED_AT", createdAtText, 0, 6);
		addFieldInline("UI_LABEL_UPDATED_AT", updatedAtText, 1, 6);
	}

	private void configureButtons() {
		editButton.setOnAction(event -> enableEdit());
		newButton.setOnAction(event -> startNewPortfolio());
		saveButton.setOnAction(event -> saveChanges());
		cancelButton.setOnAction(event -> cancelChanges());
	}

	public void updatePanel(PortfolioDetails portfolio) {
		creatingNewPortfolio = false;
		currentPortfolio = portfolio;
		fillForm(portfolio);
		setEditMode(false);
	}

	private void startNewPortfolio() {
		creatingNewPortfolio = true;
		clearForm();
		refreshSettlementAccounts(null);
		LocalDate today = LocalDate.now();
		currencyCombo.setValue(Currency.EUR);
		stateCombo.setValue(AccountState.ACTIVE);
		offlineCheck.setSelected(true);
		openedAtPicker.setValue(today);
		settlementValidFromPicker.setValue(today);
		setEditMode(true);
		nameText.requestFocus();
	}

	private void enableEdit() {
		if (currentPortfolio == null || isLinkedBankAccessEditCancelled()) {
			return;
		}
		refreshSettlementAccounts(currentPortfolio.settlementAccountId());
		setEditMode(true);
	}

	private boolean isLinkedBankAccessEditCancelled() {
		if (currentPortfolio.bankAccessId() == null || currentPortfolio.bankAccessId() <= 0) {
			return false;
		}
		ButtonType edit = new ButtonType(getText("UI_BUTTON_EDIT"));
		ButtonType cancel = new ButtonType(getText(UI_BUTTON_CANCEL), ButtonBar.ButtonData.CANCEL_CLOSE);
		return !DialogWindowSupport.showConfirmation(getOwnerWindow(), AlertType.WARNING,
				getText("ALERT_STOCK_PORTFOLIO_BANK_ACCESS_EDIT_TITLE"),
				getText("ALERT_STOCK_PORTFOLIO_BANK_ACCESS_EDIT_HEADER"),
				getText("ALERT_STOCK_PORTFOLIO_BANK_ACCESS_EDIT_TEXT"), edit, cancel);
	}

	private void saveChanges() {
		SettlementAccountOption settlementAccount = settlementAccountCombo.getValue();
		try {
			PortfolioDetails saved = service.save(new PortfolioSaveRequest(
					creatingNewPortfolio ? null : currentPortfolio.portfolioId(), nameText.getText(), ownerText.getText(),
					owner2Text.getText(), ibanText.getText(), accountNumberText.getText(), subnumberText.getText(),
					bankText.getText(), bicText.getText(), blzText.getText(), currencyCombo.getValue(),
					stateCombo.getValue(), offlineCheck.isSelected(), openedAtPicker.getValue(), closedAtPicker.getValue(),
					settlementAccount != null ? settlementAccount.accountId() : 0, settlementValidFromPicker.getValue()));
			currentPortfolio = saved;
			creatingNewPortfolio = false;
			fillForm(saved);
			setEditMode(false);
			if (afterSave != null) {
				afterSave.accept(saved.portfolioId());
			}
		} catch (GBankingException exception) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, exception.getMessage());
		}
	}

	private void cancelChanges() {
		creatingNewPortfolio = false;
		fillForm(currentPortfolio);
		setEditMode(false);
	}

	private void fillForm(PortfolioDetails portfolio) {
		if (portfolio == null) {
			clearForm();
			return;
		}
		updateTitle(portfolio.name());
		nameText.setText(portfolio.name());
		ownerText.setText(portfolio.ownerName());
		owner2Text.setText(portfolio.ownerName2());
		ibanText.setText(portfolio.iban());
		accountNumberText.setText(portfolio.accountNumber());
		subnumberText.setText(portfolio.subnumber());
		bankText.setText(portfolio.bankName());
		bicText.setText(portfolio.bic());
		blzText.setText(portfolio.blz());
		currencyCombo.setValue(portfolio.currency());
		stateCombo.setValue(portfolio.accountState());
		offlineCheck.setSelected(portfolio.offline());
		bankAccessText.setText(portfolio.bankAccessName());
		openedAtPicker.setValue(portfolio.openedAt());
		closedAtPicker.setValue(portfolio.closedAt());
		settlementValidFromPicker.setValue(portfolio.settlementValidFrom());
		createdAtText.setText(DateFormatUtils.formatShort(portfolio.createdAt()));
		updatedAtText.setText(DateFormatUtils.formatShort(portfolio.updatedAt()));
		refreshSettlementAccounts(portfolio.settlementAccountId());
	}

	private void clearForm() {
		resetTitle();
		nameText.clear();
		ownerText.clear();
		owner2Text.clear();
		ibanText.clear();
		accountNumberText.clear();
		subnumberText.clear();
		bankText.clear();
		bicText.clear();
		blzText.clear();
		bankAccessText.clear();
		createdAtText.clear();
		updatedAtText.clear();
		currencyCombo.setValue(null);
		stateCombo.setValue(null);
		settlementAccountCombo.setValue(null);
		offlineCheck.setSelected(false);
		openedAtPicker.setValue(null);
		closedAtPicker.setValue(null);
		settlementValidFromPicker.setValue(null);
	}

	private void refreshSettlementAccounts(Integer selectedAccountId) {
		List<SettlementAccountOption> options = service.getSettlementAccountOptions(selectedAccountId);
		settlementAccountCombo.setItems(FXCollections.observableArrayList(options));
		settlementAccountCombo.setValue(selectedAccountId != null ? options.stream()
				.filter(option -> option.accountId() == selectedAccountId).findFirst().orElse(null) : null);
	}

	private void setEditMode(boolean editMode) {
		editModeController.apply(editMode, currentPortfolio != null);
		boolean createMode = editMode && creatingNewPortfolio;
		offlineCheck.setDisable(!editMode || createMode);
		FormStyleUtils.setReadOnlyStyle(!editMode || createMode, offlineCheck);
		updateSettlementDateEditability();
	}

	private void updateSettlementDateEditability() {
		SettlementAccountOption selected = settlementAccountCombo.getValue();
		boolean accountChanged = currentPortfolio != null && selected != null
				&& selected.accountId() != currentPortfolio.settlementAccountId();
		boolean editable = saveButton.isVisible() && (creatingNewPortfolio || accountChanged);
		settlementValidFromPicker.setDisable(!editable);
		FormStyleUtils.setReadOnlyStyle(!editable, settlementValidFromPicker);
	}

	private List<Control> editableControls() {
		return List.of(nameText, ownerText, accountNumberText, owner2Text, subnumberText, bankText,
				ibanText, bicText, blzText, currencyCombo, stateCombo, settlementAccountCombo, openedAtPicker,
				closedAtPicker, settlementValidFromPicker, offlineCheck);
	}
}
