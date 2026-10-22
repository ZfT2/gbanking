package de.zft2.gbanking.gui.dialog.stock;

import java.time.LocalDate;
import java.util.function.Consumer;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.TradeRequest;
import de.zft2.gbanking.service.stock.StockPortfolioService.TransactionPrefill;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

public final class StockTradeDialog extends AbstractStockDialog {

	private final PortfolioSummary portfolio;
	private final PositionSummary position;
	private final TransactionPrefill prefill;
	private final Consumer<TradeRequest> saveAction;
	private final Runnable savedHandler;
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
		super(owner, "UI_STOCK_TRADE_DIALOG_TITLE");
		this.portfolio = portfolio;
		this.position = position;
		this.prefill = prefill;
		this.saveAction = saveAction;
		this.savedHandler = savedHandler;
		currencyField = StockDialogSupport.createCurrencyCombo(position.currency());
		initialize();
	}

	private void initialize() {
		typeField.setValue(prefill != null ? prefill.transactionType() : StockTransactionType.BUY);
		typeField.setConverter(StockDialogSupport.createTransactionTypeConverter(StockTransactionType.BUY));
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

		buildScene(getText("UI_STOCK_TRADE_DIALOG_HEADER", position.securityName()), form,
				createActionButton("UI_BUTTON_SAVE", () -> save()), createCancelButton(), 720, 390);
	}

	private void configureExchangeRate() {
		currencyField.valueProperty().addListener((observable, previous, currency) -> updateExchangeRate(currency));
		updateExchangeRate(currencyField.getValue());
	}

	private void updateExchangeRate(Currency currency) {
		StockDialogSupport.updateExchangeRateField(exchangeRateField, currency != portfolio.settlementCurrency());
	}

	private void save() {
		applyAndClose(() -> {
			TradeRequest request = new TradeRequest(typeField.getValue(), tradeDateField.getValue(), settlementDateField.getValue(),
					StockDialogSupport.parseRequiredDecimal(quantityField, getText("UI_STOCK_FIELD_QUANTITY")),
					StockDialogSupport.parseRequiredDecimal(priceField, getText("UI_STOCK_FIELD_UNIT_PRICE")),
					currencyField.getValue(), StockDialogSupport.parseExchangeRate(exchangeRateField,
							getText("UI_STOCK_FIELD_EXCHANGE_RATE")),
					StockDialogSupport.parseOptionalDecimal(feesField, getText("UI_STOCK_FIELD_FEES")),
					StockDialogSupport.parseOptionalDecimal(taxesField, getText("UI_STOCK_FIELD_TAXES")),
					StockDialogSupport.parseOptionalDecimal(accruedInterestField, getText("UI_STOCK_FIELD_ACCRUED_INTEREST")));
			saveAction.accept(request);
			savedHandler.run();
			return true;
		});
	}
}
