package de.zft2.gbanking.gui.panel.stock;

import java.util.List;
import java.util.function.Consumer;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockPriceBasis;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityState;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.KeyboardShortcutDispatcher;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.panel.AbstractReadonlyDetailPanel;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.DetailFormEditMode;
import de.zft2.gbanking.gui.util.FormFields;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService.SecurityDetails;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService.SecuritySaveRequest;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;

public class StockSecurityDetailPanel extends AbstractReadonlyDetailPanel {

	private final StockSecurityAdministrationService service;
	private final Consumer<Integer> afterSave;

	private final TextField nameText = FormFields.textL();
	private final ComboBox<StockSecurityType> typeCombo = FormFields.comboM(
			FXCollections.observableArrayList(StockSecurityType.values()));
	private final ComboBox<StockSecurityState> stateCombo = FormFields.comboM(
			FXCollections.observableArrayList(StockSecurityState.values()));
	private final TextField isinText = FormFields.textM();
	private final TextField wknText = FormFields.textS();
	private final TextField tickerText = FormFields.textS();
	private final TextField marketIdentifierCodeText = FormFields.textXs();
	private final TextField issuerText = FormFields.textL();
	private final TextField domicileCountryText = FormFields.textXs();
	private final ComboBox<StockQuantityType> quantityTypeCombo = FormFields.comboM(
			FXCollections.observableArrayList(StockQuantityType.values()));
	private final ComboBox<Currency> quoteCurrencyCombo = FormFields.comboXs(
			FXCollections.observableArrayList(Currency.values()));
	private final ComboBox<StockQuotationType> quotationTypeCombo = FormFields.comboM(
			FXCollections.observableArrayList(StockQuotationType.values()));
	private final ComboBox<Currency> nominalCurrencyCombo = FormFields.comboXs(
			FXCollections.observableArrayList(Currency.values()));
	private final ComboBox<StockPriceBasis> priceBasisCombo = FormFields.comboM(
			FXCollections.observableArrayList(StockPriceBasis.values()));
	private final DatePicker maturityDatePicker = new DatePicker();
	private final TextField createdAtText = FormFields.textS();
	private final TextField updatedAtText = FormFields.textS();

	private final Button editButton = new Button(getText("UI_BUTTON_EDIT"));
	private final Button newButton = new Button(getText("UI_BUTTON_STOCK_SECURITY_NEW"));
	private final Button saveButton = new Button(getText("UI_BUTTON_SAVE"));
	private final Button cancelButton = new Button(getText("UI_BUTTON_CANCEL"));

	private SecurityDetails currentSecurity;
	private boolean creatingNewSecurity;
	private final DetailFormEditMode editModeController;

	public StockSecurityDetailPanel(StockSecurityAdministrationService service, Consumer<Integer> afterSave) {
		super("UI_PANEL_STOCK_SECURITY_DETAILS");
		this.service = service;
		this.afterSave = afterSave;
		FormGridHelper.setEqualGrowColumns(formGrid, 3);
		createFields();
		configureButtons();
		makeReadOnly(nameText, isinText, wknText, tickerText, marketIdentifierCodeText, issuerText,
				domicileCountryText, createdAtText, updatedAtText);
		addContentNode(FormStyleUtils.createButtonBar(editButton, newButton, saveButton, cancelButton));
		editModeController = new DetailFormEditMode(editableControls(), List.of(createdAtText, updatedAtText),
				List.of(newButton), List.of(editButton), List.of(saveButton, cancelButton));
		quotationTypeCombo.valueProperty().addListener((observable, previous, selected) -> updateQuotationControls());
		KeyboardShortcutDispatcher.registerForm(this, saveButton, cancelButton);
		KeyboardShortcutDispatcher.blockRefreshWhile(this, saveButton::isVisible);
		setEditMode(false);
	}

	private void createFields() {
		addFieldInline("UI_STOCK_SECURITY_NAME", nameText, 0, 0);
		addFieldInline("UI_STOCK_SECURITY_TYPE", typeCombo, 1, 0);
		addFieldInline("UI_STOCK_SECURITY_STATE", stateCombo, 2, 0);

		addFieldInline("UI_STOCK_COLUMN_ISIN", isinText, 0, 1);
		addFieldInline("UI_STOCK_COLUMN_WKN", wknText, 1, 1);
		addFieldInline("UI_STOCK_SECURITY_TICKER", tickerText, 2, 1);

		addFieldInline("UI_STOCK_SECURITY_MIC", marketIdentifierCodeText, 0, 2);
		addFieldInline("UI_STOCK_SECURITY_ISSUER", issuerText, 1, 2);
		addFieldInline("UI_STOCK_SECURITY_DOMICILE", domicileCountryText, 2, 2);

		addFieldInline("UI_STOCK_SECURITY_QUANTITY_TYPE", quantityTypeCombo, 0, 3);
		addFieldInline("UI_STOCK_SECURITY_QUOTE_CURRENCY", quoteCurrencyCombo, 1, 3);
		addFieldInline("UI_STOCK_SECURITY_QUOTATION_TYPE", quotationTypeCombo, 2, 3);

		addFieldInline("UI_STOCK_SECURITY_NOMINAL_CURRENCY", nominalCurrencyCombo, 0, 4);
		addFieldInline("UI_STOCK_SECURITY_PRICE_BASIS", priceBasisCombo, 1, 4);
		addFieldInline("UI_STOCK_SECURITY_MATURITY_DATE", maturityDatePicker, 2, 4);

		addFieldInline("UI_LABEL_CREATED_AT", createdAtText, 0, 5);
		addFieldInline("UI_LABEL_UPDATED_AT", updatedAtText, 1, 5);
	}

	private void configureButtons() {
		editButton.setOnAction(event -> setEditMode(true));
		newButton.setOnAction(event -> startNewSecurity());
		saveButton.setOnAction(event -> saveChanges());
		cancelButton.setOnAction(event -> cancelChanges());
	}

	public void updatePanel(SecurityDetails security) {
		creatingNewSecurity = false;
		currentSecurity = security;
		fillForm(security);
		setEditMode(false);
	}

	private void startNewSecurity() {
		creatingNewSecurity = true;
		clearForm();
		typeCombo.setValue(StockSecurityType.STOCK);
		stateCombo.setValue(StockSecurityState.ACTIVE);
		quantityTypeCombo.setValue(StockQuantityType.UNITS);
		quoteCurrencyCombo.setValue(Currency.EUR);
		quotationTypeCombo.setValue(StockQuotationType.ABSOLUTE);
		setEditMode(true);
		nameText.requestFocus();
	}

	private void saveChanges() {
		try {
			SecurityDetails saved = service.save(new SecuritySaveRequest(
					creatingNewSecurity ? null : currentSecurity.securityId(), nameText.getText(), issuerText.getText(),
					domicileCountryText.getText(), maturityDatePicker.getValue(), typeCombo.getValue(),
					quantityTypeCombo.getValue(), nominalCurrencyCombo.getValue(), quoteCurrencyCombo.getValue(),
					quotationTypeCombo.getValue(), priceBasisCombo.getValue(), stateCombo.getValue(),
					isinText.getText(), wknText.getText(), tickerText.getText(), marketIdentifierCodeText.getText()));
			currentSecurity = saved;
			creatingNewSecurity = false;
			fillForm(saved);
			setEditMode(false);
			if (afterSave != null) {
				afterSave.accept(saved.securityId());
			}
		} catch (GBankingException exception) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, exception.getMessage());
		}
	}

	private void cancelChanges() {
		creatingNewSecurity = false;
		fillForm(currentSecurity);
		setEditMode(false);
	}

	private void fillForm(SecurityDetails security) {
		if (security == null) {
			clearForm();
			return;
		}
		updateTitle(security.name());
		nameText.setText(security.name());
		typeCombo.setValue(security.securityType());
		stateCombo.setValue(security.securityState());
		isinText.setText(security.isin());
		wknText.setText(security.wkn());
		tickerText.setText(security.ticker());
		marketIdentifierCodeText.setText(security.marketIdentifierCode());
		issuerText.setText(security.issuer());
		domicileCountryText.setText(security.domicileCountry());
		quantityTypeCombo.setValue(security.quantityType());
		quoteCurrencyCombo.setValue(security.quoteCurrency());
		quotationTypeCombo.setValue(security.quotationType());
		nominalCurrencyCombo.setValue(security.nominalCurrency());
		priceBasisCombo.setValue(security.priceBasis());
		maturityDatePicker.setValue(security.maturityDate());
		createdAtText.setText(DateFormatUtils.formatDateTime(security.createdAt()));
		updatedAtText.setText(DateFormatUtils.formatDateTime(security.updatedAt()));
	}

	private void clearForm() {
		resetTitle();
		nameText.clear();
		isinText.clear();
		wknText.clear();
		tickerText.clear();
		marketIdentifierCodeText.clear();
		issuerText.clear();
		domicileCountryText.clear();
		createdAtText.clear();
		updatedAtText.clear();
		typeCombo.setValue(null);
		stateCombo.setValue(null);
		quantityTypeCombo.setValue(null);
		quoteCurrencyCombo.setValue(null);
		quotationTypeCombo.setValue(null);
		nominalCurrencyCombo.setValue(null);
		priceBasisCombo.setValue(null);
		maturityDatePicker.setValue(null);
	}

	private void setEditMode(boolean editMode) {
		editModeController.apply(editMode, currentSecurity != null);
		updateQuotationControls();
	}

	private void updateQuotationControls() {
		boolean editMode = saveButton.isVisible();
		boolean percentage = quotationTypeCombo.getValue() == StockQuotationType.PERCENT_OF_NOMINAL;
		if (editMode && percentage) {
			quantityTypeCombo.setValue(StockQuantityType.NOMINAL);
		}
		quantityTypeCombo.setDisable(!editMode || percentage);
		priceBasisCombo.setDisable(!editMode || !percentage);
		FormStyleUtils.setReadOnlyStyle(!editMode || percentage, quantityTypeCombo);
		FormStyleUtils.setReadOnlyStyle(!editMode || !percentage, priceBasisCombo);
		if (!percentage) {
			priceBasisCombo.setValue(null);
		}
	}

	private List<Control> editableControls() {
		return List.of(nameText, typeCombo, stateCombo, isinText, wknText, tickerText,
				marketIdentifierCodeText, issuerText, domicileCountryText, quantityTypeCombo,
				quoteCurrencyCombo, quotationTypeCombo, nominalCurrencyCombo, priceBasisCombo, maturityDatePicker);
	}
}
