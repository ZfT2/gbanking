package de.zft2.gbanking.gui.dialog.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockTransactionEditField;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.SecuritySummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.TransactionEditData;
import de.zft2.gbanking.service.stock.StockPortfolioService.TransactionEditRequest;
import de.zft2.gbanking.service.stock.StockPortfolioService.TransactionPrefill;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

public final class StockTransactionDialog implements BaseMessages {

	private final PortfolioSummary portfolio;
	private final TransactionEditData editData;
	private final List<TransactionPrefill> prefills;
	private final Integer preferredSecurityId;
	private final Consumer<TransactionEditRequest> saveAction;
	private final Runnable savedHandler;
	private final Stage dialog;
	private final ComboBox<StockTransactionType> typeField = new ComboBox<>(
			FXCollections.observableArrayList(StockTransactionType.BUY, StockTransactionType.SELL));
	private final DatePicker tradeDateField = new DatePicker();
	private final DatePicker settlementDateField = new DatePicker();
	private final ComboBox<SecuritySummary> securityField;
	private final TextField quantityField = new TextField();
	private final TextField priceField = new TextField();
	private final ComboBox<Currency> currencyField = StockDialogSupport.createCurrencyCombo(null);
	private final TextField exchangeRateField = new TextField();
	private final TextField feesField = new TextField();
	private final TextField taxesField = new TextField();
	private final TextField accruedInterestField = new TextField();
	private final TextField noteField = new TextField();

	public StockTransactionDialog(Window owner, PortfolioSummary portfolio, List<SecuritySummary> securities,
			TransactionEditData editData, List<TransactionPrefill> prefills, Integer preferredSecurityId,
			Consumer<TransactionEditRequest> saveAction, Runnable savedHandler) {
		this.portfolio = portfolio;
		this.editData = editData;
		this.prefills = List.copyOf(prefills);
		this.preferredSecurityId = preferredSecurityId;
		this.saveAction = saveAction;
		this.savedHandler = savedHandler;
		securityField = new ComboBox<>(FXCollections.observableArrayList(securities));
		dialog = DialogWindowSupport.createModalStage(owner,
				editData == null ? "UI_STOCK_TRANSACTION_DIALOG_NEW_TITLE" : "UI_STOCK_TRANSACTION_DIALOG_EDIT_TITLE");
		initialize();
	}

	public void show() {
		dialog.showAndWait();
	}

	private void initialize() {
		typeField.setConverter(transactionTypeConverter());
		securityField.setMaxWidth(Double.MAX_VALUE);
		securityField.valueProperty().addListener((observable, previous, security) -> applySecurityDefaults(security));
		currencyField.valueProperty().addListener((observable, previous, currency) -> updateExchangeRateState());
		tradeDateField.valueProperty().addListener((observable, previous, date) -> applyDefaultSettlementDate(date));
		if (editData != null) {
			populateFields();
			applyEditability();
		} else {
			applyInitialPrefill();
		}
		updateExchangeRateState();

		Label header = new Label(getText(editData == null
				? "UI_STOCK_TRANSACTION_DIALOG_NEW_HEADER" : "UI_STOCK_TRANSACTION_DIALOG_EDIT_HEADER"));
		header.getStyleClass().add("gbanking-form-section-title");
		GridPane form = createForm();
		Button saveButton = new Button(getText("UI_BUTTON_SAVE"));
		Button cancelButton = new Button(getText("UI_BUTTON_CANCEL"));
		saveButton.setDefaultButton(true);
		cancelButton.setCancelButton(true);
		saveButton.setOnAction(event -> save());
		cancelButton.setOnAction(event -> dialog.close());
		VBox root = DialogWindowSupport.createDialogRoot(header, form,
				DialogWindowSupport.createButtonBar(saveButton, cancelButton));
		dialog.setScene(DialogWindowSupport.createScene(root, 820, 480));
	}

	private GridPane createForm() {
		GridPane form = FormGridHelper.createDefaultGrid();
		FormGridHelper.setEqualGrowColumns(form, 3);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_TRANSACTION"), typeField, 0, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_TRADE_DATE"), tradeDateField, 1, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_SETTLEMENT_DATE"), settlementDateField, 2, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_COLUMN_SECURITY"), securityField, 0, 1);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_QUANTITY"), quantityField, 1, 1);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_UNIT_PRICE"), priceField, 2, 1);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_CURRENCY"), currencyField, 0, 2);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_EXCHANGE_RATE"), exchangeRateField, 1, 2);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_FEES"), feesField, 2, 2);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_TAXES"), taxesField, 0, 3);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_ACCRUED_INTEREST"), accruedInterestField, 1, 3);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_NOTE"), noteField, 0, 4, 3);
		return form;
	}

	private void populateFields() {
		typeField.setValue(editData.transactionType());
		tradeDateField.setValue(editData.tradeDate());
		settlementDateField.setValue(editData.settlementDate());
		securityField.getItems().stream().filter(security -> security.securityId() == editData.securityId())
				.findFirst().ifPresent(securityField::setValue);
		setDecimal(quantityField, editData.quantity());
		setDecimal(priceField, editData.unitPrice());
		currencyField.setValue(editData.currency());
		setDecimal(exchangeRateField, editData.exchangeRate());
		setDecimal(feesField, editData.fees());
		setDecimal(taxesField, editData.taxes());
		setDecimal(accruedInterestField, editData.accruedInterest());
		noteField.setText(editData.note());
	}

	private void applyEditability() {
		typeField.setDisable(!editData.isEditable(StockTransactionEditField.TRANSACTION_TYPE));
		tradeDateField.setDisable(!editData.isEditable(StockTransactionEditField.TRADE_DATE));
		settlementDateField.setDisable(!editData.isEditable(StockTransactionEditField.SETTLEMENT_DATE));
		securityField.setDisable(!editData.isEditable(StockTransactionEditField.SECURITY));
		quantityField.setDisable(!editData.isEditable(StockTransactionEditField.QUANTITY));
		priceField.setDisable(!editData.isEditable(StockTransactionEditField.PRICE_OR_AMOUNT));
		currencyField.setDisable(!editData.isEditable(StockTransactionEditField.CURRENCY));
		feesField.setDisable(!editData.isEditable(StockTransactionEditField.FEES));
		taxesField.setDisable(!editData.isEditable(StockTransactionEditField.TAXES));
		accruedInterestField.setDisable(!editData.isEditable(StockTransactionEditField.ACCRUED_INTEREST));
		noteField.setDisable(!editData.isEditable(StockTransactionEditField.NOTE));
	}

	private void applySecurityDefaults(SecuritySummary security) {
		if (editData != null || security == null) {
			return;
		}
		if (currencyField.getValue() == null) {
			currencyField.setValue(security.currency());
		}
		TransactionPrefill prefill = prefillFor(security);
		if (prefill != null) {
			typeField.setValue(prefill.transactionType());
			setDecimal(quantityField, prefill.quantity());
		} else {
			typeField.setValue(null);
			quantityField.clear();
		}
	}

	private void applyInitialPrefill() {
		TransactionPrefill prefill = prefills.stream()
				.filter(candidate -> preferredSecurityId != null && candidate.securityId() == preferredSecurityId)
				.findFirst().orElse(prefills.size() == 1 ? prefills.get(0) : null);
		if (prefill == null) {
			return;
		}
		securityField.getItems().stream().filter(security -> security.securityId() == prefill.securityId())
				.findFirst().ifPresent(securityField::setValue);
	}

	private TransactionPrefill prefillFor(SecuritySummary security) {
		return prefills.stream().filter(candidate -> candidate.securityId() == security.securityId()
				&& candidate.quantityType() == security.quantityType()).findFirst().orElse(null);
	}

	private void applyDefaultSettlementDate(LocalDate tradeDate) {
		if (editData == null && tradeDate != null && settlementDateField.getValue() == null) {
			settlementDateField.setValue(StockPortfolioService.defaultSettlementDate(tradeDate));
		}
	}

	private void updateExchangeRateState() {
		boolean sameCurrency = currencyField.getValue() != null
				&& currencyField.getValue() == portfolio.settlementCurrency();
		boolean editable = editData == null || editData.isEditable(StockTransactionEditField.EXCHANGE_RATE);
		exchangeRateField.setDisable(sameCurrency || !editable);
		if (sameCurrency) {
			exchangeRateField.setText("1");
		}
	}

	private void save() {
		try {
			SecuritySummary security = securityField.getValue();
			if (typeField.getValue() == null || tradeDateField.getValue() == null
					|| settlementDateField.getValue() == null || security == null || currencyField.getValue() == null) {
				throw new GBankingException(getText("UI_STOCK_TRANSACTION_INCOMPLETE"));
			}
			TransactionEditRequest request = new TransactionEditRequest(editData != null ? editData.transactionId() : null,
					typeField.getValue(), tradeDateField.getValue(), settlementDateField.getValue(), security.securityId(),
					StockDialogSupport.parseRequiredDecimal(quantityField, getText("UI_STOCK_FIELD_QUANTITY")),
					StockDialogSupport.parseRequiredDecimal(priceField, getText("UI_STOCK_FIELD_UNIT_PRICE")),
					currencyField.getValue(), parseExchangeRate(),
					StockDialogSupport.parseOptionalDecimal(feesField, getText("UI_STOCK_FIELD_FEES")),
					StockDialogSupport.parseOptionalDecimal(taxesField, getText("UI_STOCK_FIELD_TAXES")),
					StockDialogSupport.parseOptionalDecimal(accruedInterestField,
							getText("UI_STOCK_FIELD_ACCRUED_INTEREST")), noteField.getText());
			saveAction.accept(request);
			savedHandler.run();
			dialog.close();
		} catch (RuntimeException exception) {
			StockDialogSupport.showError(dialog, exception);
		}
	}

	private BigDecimal parseExchangeRate() {
		return exchangeRateField.isDisabled() && currencyField.getValue() == portfolio.settlementCurrency()
				? BigDecimal.ONE
				: StockDialogSupport.parseRequiredDecimal(exchangeRateField, getText("UI_STOCK_FIELD_EXCHANGE_RATE"));
	}

	private static void setDecimal(TextField field, BigDecimal value) {
		field.setText(value != null ? StockDialogSupport.formatDecimal(value) : null);
	}

	private StringConverter<StockTransactionType> transactionTypeConverter() {
		return new StringConverter<>() {
			@Override
			public String toString(StockTransactionType type) {
				if (type == null) {
					return "";
				}
				return type == StockTransactionType.SELL ? getText("UI_STOCK_TRANSACTION_SELL")
						: getText("UI_STOCK_TRANSACTION_BUY");
			}

			@Override
			public StockTransactionType fromString(String value) {
				return getText("UI_STOCK_TRANSACTION_SELL").equals(value)
						? StockTransactionType.SELL : StockTransactionType.BUY;
			}
		};
	}
}
