package de.zft2.gbanking.gui.dialog.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Consumer;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.TradeRequest;
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

public final class StockTradeDialog implements BaseMessages {

	private final PortfolioSummary portfolio;
	private final PositionSummary position;
	private final TransactionPrefill prefill;
	private final Consumer<TradeRequest> saveAction;
	private final Runnable savedHandler;
	private final Stage dialog;
	private final ComboBox<StockTransactionType> typeField = new ComboBox<>(
			FXCollections.observableArrayList(StockTransactionType.BUY, StockTransactionType.SELL));
	private final DatePicker tradeDateField = new DatePicker(LocalDate.now());
	private final DatePicker settlementDateField = new DatePicker(StockPortfolioService.defaultSettlementDate(LocalDate.now()));
	private final TextField quantityField = new TextField();
	private final TextField priceField = new TextField();
	private final ComboBox<Currency> currencyField;
	private final TextField exchangeRateField = new TextField();
	private final TextField feesField = new TextField();
	private final TextField taxesField = new TextField();
	private final TextField accruedInterestField = new TextField();

	public StockTradeDialog(Window owner, PortfolioSummary portfolio, PositionSummary position,
			TransactionPrefill prefill, Consumer<TradeRequest> saveAction, Runnable savedHandler) {
		this.portfolio = portfolio;
		this.position = position;
		this.prefill = prefill;
		this.saveAction = saveAction;
		this.savedHandler = savedHandler;
		currencyField = StockDialogSupport.createCurrencyCombo(position.currency());
		dialog = DialogWindowSupport.createModalStage(owner, "UI_STOCK_TRADE_DIALOG_TITLE");
		initialize();
	}

	public void show() {
		dialog.showAndWait();
	}

	private void initialize() {
		Label header = new Label(getText("UI_STOCK_TRADE_DIALOG_HEADER", position.securityName()));
		header.getStyleClass().add("gbanking-form-section-title");
		typeField.setValue(prefill != null ? prefill.transactionType() : StockTransactionType.BUY);
		typeField.setConverter(transactionTypeConverter());
		quantityField.setText(StockDialogSupport.formatDecimal(prefill != null ? prefill.quantity() : position.quantity()));
		configureExchangeRate();
		tradeDateField.valueProperty().addListener((observable, previous, date) -> {
			if (date != null) {
				settlementDateField.setValue(StockPortfolioService.defaultSettlementDate(date));
			}
		});

		GridPane form = FormGridHelper.createDefaultGrid();
		FormGridHelper.setEqualGrowColumns(form, 3);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_TRANSACTION"), typeField, 0, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_TRADE_DATE"), tradeDateField, 1, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_SETTLEMENT_DATE"), settlementDateField, 2, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_QUANTITY"), quantityField, 0, 1);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_UNIT_PRICE"), priceField, 1, 1);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_CURRENCY"), currencyField, 2, 1);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_EXCHANGE_RATE"), exchangeRateField, 0, 2);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_FEES"), feesField, 1, 2);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_TAXES"), taxesField, 2, 2);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_ACCRUED_INTEREST"), accruedInterestField, 0, 3);

		Button saveButton = new Button(getText("UI_BUTTON_SAVE"));
		Button cancelButton = new Button(getText("UI_BUTTON_CANCEL"));
		saveButton.setDefaultButton(true);
		cancelButton.setCancelButton(true);
		saveButton.setOnAction(event -> save());
		cancelButton.setOnAction(event -> dialog.close());
		VBox root = DialogWindowSupport.createDialogRoot(header, form,
				DialogWindowSupport.createButtonBar(saveButton, cancelButton));
		dialog.setScene(DialogWindowSupport.createScene(root, 720, 390));
	}

	private void configureExchangeRate() {
		currencyField.valueProperty().addListener((observable, previous, currency) -> updateExchangeRate(currency));
		updateExchangeRate(currencyField.getValue());
	}

	private void updateExchangeRate(Currency currency) {
		Currency settlementCurrency = portfolio.settlementCurrency();
		boolean required = currency != settlementCurrency;
		exchangeRateField.setDisable(!required);
		if (!required) {
			exchangeRateField.setText("1");
		} else if ("1".equals(exchangeRateField.getText())) {
			exchangeRateField.clear();
		}
	}

	private void save() {
		try {
			TradeRequest request = new TradeRequest(typeField.getValue(), tradeDateField.getValue(), settlementDateField.getValue(),
					StockDialogSupport.parseRequiredDecimal(quantityField, getText("UI_STOCK_FIELD_QUANTITY")),
					StockDialogSupport.parseRequiredDecimal(priceField, getText("UI_STOCK_FIELD_UNIT_PRICE")),
					currencyField.getValue(), parseExchangeRate(),
					StockDialogSupport.parseOptionalDecimal(feesField, getText("UI_STOCK_FIELD_FEES")),
					StockDialogSupport.parseOptionalDecimal(taxesField, getText("UI_STOCK_FIELD_TAXES")),
					StockDialogSupport.parseOptionalDecimal(accruedInterestField, getText("UI_STOCK_FIELD_ACCRUED_INTEREST")));
			saveAction.accept(request);
			savedHandler.run();
			dialog.close();
		} catch (RuntimeException exception) {
			StockDialogSupport.showError(dialog, exception);
		}
	}

	private BigDecimal parseExchangeRate() {
		return exchangeRateField.isDisabled() ? BigDecimal.ONE
				: StockDialogSupport.parseRequiredDecimal(exchangeRateField, getText("UI_STOCK_FIELD_EXCHANGE_RATE"));
	}

	private StringConverter<StockTransactionType> transactionTypeConverter() {
		return new StringConverter<>() {
			@Override
			public String toString(StockTransactionType type) {
				return type == StockTransactionType.SELL ? getText("UI_STOCK_TRANSACTION_SELL")
						: getText("UI_STOCK_TRANSACTION_BUY");
			}

			@Override
			public StockTransactionType fromString(String value) {
				return getText("UI_STOCK_TRANSACTION_SELL").equals(value) ? StockTransactionType.SELL : StockTransactionType.BUY;
			}
		};
	}
}
