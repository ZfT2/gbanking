package de.zft2.gbanking.gui.dialog.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Consumer;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.service.stock.StockPortfolioService.IncomeRequest;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionSummary;
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

public final class StockIncomeDialog implements BaseMessages {

	private final PortfolioSummary portfolio;
	private final PositionSummary position;
	private final Consumer<IncomeRequest> saveAction;
	private final Runnable savedHandler;
	private final Stage dialog;
	private final ComboBox<StockTransactionType> typeField = new ComboBox<>(
			FXCollections.observableArrayList(StockTransactionType.DIVIDEND, StockTransactionType.INTEREST));
	private final DatePicker dateField = new DatePicker(LocalDate.now());
	private final TextField amountField = new TextField();
	private final TextField taxesField = new TextField();
	private final ComboBox<Currency> currencyField;
	private final TextField exchangeRateField = new TextField();

	public StockIncomeDialog(Window owner, PortfolioSummary portfolio, PositionSummary position,
			Consumer<IncomeRequest> saveAction, Runnable savedHandler) {
		this.portfolio = portfolio;
		this.position = position;
		this.saveAction = saveAction;
		this.savedHandler = savedHandler;
		Currency initialCurrency = position.currency();
		currencyField = StockDialogSupport.createCurrencyCombo(initialCurrency);
		dialog = DialogWindowSupport.createModalStage(owner, "UI_STOCK_INCOME_DIALOG_TITLE");
		initialize();
	}

	public void show() {
		dialog.showAndWait();
	}

	private void initialize() {
		Label header = new Label(getText("UI_STOCK_INCOME_DIALOG_HEADER", position.securityName()));
		header.getStyleClass().add("gbanking-form-section-title");
		typeField.setValue(StockTransactionType.DIVIDEND);
		typeField.setConverter(transactionTypeConverter());
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

		Button saveButton = new Button(getText("UI_BUTTON_SAVE"));
		Button cancelButton = new Button(getText("UI_BUTTON_CANCEL"));
		saveButton.setDefaultButton(true);
		cancelButton.setCancelButton(true);
		saveButton.setOnAction(event -> save());
		cancelButton.setOnAction(event -> dialog.close());
		VBox root = DialogWindowSupport.createDialogRoot(header, form,
				DialogWindowSupport.createButtonBar(saveButton, cancelButton));
		dialog.setScene(DialogWindowSupport.createScene(root, 700, 300));
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
			BigDecimal exchangeRate = exchangeRateField.isDisabled() ? BigDecimal.ONE
					: StockDialogSupport.parseRequiredDecimal(exchangeRateField, getText("UI_STOCK_FIELD_EXCHANGE_RATE"));
			IncomeRequest request = new IncomeRequest(typeField.getValue(), dateField.getValue(),
					StockDialogSupport.parseRequiredDecimal(amountField, getText("UI_STOCK_FIELD_GROSS_AMOUNT")),
					StockDialogSupport.parseOptionalDecimal(taxesField, getText("UI_STOCK_FIELD_TAXES")),
					currencyField.getValue(), exchangeRate);
			saveAction.accept(request);
			savedHandler.run();
			dialog.close();
		} catch (RuntimeException exception) {
			StockDialogSupport.showError(dialog, exception);
		}
	}

	private StringConverter<StockTransactionType> transactionTypeConverter() {
		return new StringConverter<>() {
			@Override
			public String toString(StockTransactionType type) {
				return type == StockTransactionType.INTEREST ? getText("UI_STOCK_TRANSACTION_INTEREST")
						: getText("UI_STOCK_TRANSACTION_DIVIDEND");
			}

			@Override
			public StockTransactionType fromString(String value) {
				return getText("UI_STOCK_TRANSACTION_INTEREST").equals(value)
						? StockTransactionType.INTEREST : StockTransactionType.DIVIDEND;
			}
		};
	}
}
