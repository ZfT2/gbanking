package de.zft2.gbanking.gui.dialog.stock;

import java.time.LocalDate;
import java.util.function.Consumer;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.service.stock.StockPortfolioService.IncomeRequest;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionSummary;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

public final class StockIncomeDialog extends AbstractStockDialog {

	private final PortfolioSummary portfolio;
	private final PositionSummary position;
	private final Consumer<IncomeRequest> saveAction;
	private final Runnable savedHandler;
	private final ComboBox<StockTransactionType> typeField = new ComboBox<>(
			FXCollections.observableArrayList(StockTransactionType.DIVIDEND, StockTransactionType.INTEREST));
	private final DatePicker dateField = new DatePicker(LocalDate.now());
	private final TextField amountField = new TextField();
	private final TextField taxesField = new TextField();
	private final ComboBox<Currency> currencyField;
	private final TextField exchangeRateField = new TextField();

	public StockIncomeDialog(Window owner, PortfolioSummary portfolio, PositionSummary position,
			Consumer<IncomeRequest> saveAction, Runnable savedHandler) {
		super(owner, "UI_STOCK_INCOME_DIALOG_TITLE");
		this.portfolio = portfolio;
		this.position = position;
		this.saveAction = saveAction;
		this.savedHandler = savedHandler;
		Currency initialCurrency = position.currency();
		currencyField = StockDialogSupport.createCurrencyCombo(initialCurrency);
		initialize();
	}

	private void initialize() {
		typeField.setValue(StockTransactionType.DIVIDEND);
		typeField.setConverter(StockDialogSupport.createTransactionTypeConverter(StockTransactionType.DIVIDEND));
		currencyField.valueProperty().addListener((observable, previous, currency) -> updateExchangeRate(currency));
		updateExchangeRate(currencyField.getValue());

		GridPane form = FormGridHelper.createDefaultGrid();
		FormGridHelper.setEqualGrowColumns(form, 3);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_INCOME_TYPE"), typeField, 0, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_DATE"), dateField, 1, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_CURRENCY"), currencyField, 2, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_GROSS_AMOUNT"), amountField, 0, 1);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_TAXES"), taxesField, 1, 1);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_EXCHANGE_RATE"), exchangeRateField, 2, 1);

		buildScene(getText("UI_STOCK_INCOME_DIALOG_HEADER", position.securityName()), form,
				createActionButton("UI_BUTTON_SAVE", () -> save()), createCancelButton(), 700, 300);
	}

	private void updateExchangeRate(Currency currency) {
		StockDialogSupport.updateExchangeRateField(exchangeRateField, currency != portfolio.settlementCurrency());
	}

	private void save() {
		applyAndClose(() -> {
			IncomeRequest request = new IncomeRequest(typeField.getValue(), dateField.getValue(),
					StockDialogSupport.parseRequiredDecimal(amountField, getText("UI_STOCK_FIELD_GROSS_AMOUNT")),
					StockDialogSupport.parseOptionalDecimal(taxesField, getText("UI_STOCK_FIELD_TAXES")),
					currencyField.getValue(), StockDialogSupport.parseExchangeRate(exchangeRateField,
							getText("UI_STOCK_FIELD_EXCHANGE_RATE")));
			saveAction.accept(request);
			savedHandler.run();
			return true;
		});
	}
}
