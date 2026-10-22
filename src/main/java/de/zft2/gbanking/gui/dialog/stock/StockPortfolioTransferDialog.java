package de.zft2.gbanking.gui.dialog.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

public final class StockPortfolioTransferDialog extends AbstractStockDialog {

	private final PortfolioSummary source;
	private final TransferHandler transferHandler;
	private final Runnable savedHandler;
	private final ComboBox<PortfolioSummary> targetField;
	private final DatePicker dateField = new DatePicker(LocalDate.now());
	private final TextField exchangeRateField = new TextField();

	public StockPortfolioTransferDialog(Window owner, PortfolioSummary source, List<PortfolioSummary> targets,
			TransferHandler transferHandler, Runnable savedHandler) {
		super(owner, "UI_STOCK_PORTFOLIO_TRANSFER_DIALOG_TITLE");
		this.source = source;
		this.transferHandler = transferHandler;
		this.savedHandler = savedHandler;
		targetField = new ComboBox<>(FXCollections.observableArrayList(targets));
		initialize();
	}

	private void initialize() {
		if (!targetField.getItems().isEmpty()) {
			targetField.getSelectionModel().selectFirst();
		}
		targetField.valueProperty().addListener((observable, previous, selected) -> updateExchangeRate(selected));
		updateExchangeRate(targetField.getValue());

		GridPane form = FormGridHelper.createDefaultGrid();
		FormGridHelper.setEqualGrowColumns(form, 3);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_TARGET_PORTFOLIO"), targetField, 0, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_DATE"), dateField, 1, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_TRANSFER_EXCHANGE_RATE"), exchangeRateField, 2, 0);

		Button transferButton = createActionButton("UI_STOCK_ACTION_TRANSFER", () -> transfer());
		transferButton.setDisable(targetField.getItems().isEmpty());
		buildScene(getText("UI_STOCK_PORTFOLIO_TRANSFER_DIALOG_HEADER", source.displayName()), form, transferButton,
				createCancelButton(), 720, 250);
	}

	private void updateExchangeRate(PortfolioSummary target) {
		Currency sourceCurrency = source.settlementCurrency();
		Currency targetCurrency = target != null ? target.settlementCurrency() : null;
		boolean required = sourceCurrency != targetCurrency;
		exchangeRateField.setDisable(!required);
		if (!required) {
			exchangeRateField.setText("1");
		} else if ("1".equals(exchangeRateField.getText())) {
			exchangeRateField.clear();
		}
	}

	private void transfer() {
		applyAndClose(() -> {
			PortfolioSummary target = targetField.getValue();
			BigDecimal exchangeRate = exchangeRateField.isDisabled() ? BigDecimal.ONE
					: StockDialogSupport.parseRequiredDecimal(exchangeRateField, getText("UI_STOCK_FIELD_TRANSFER_EXCHANGE_RATE"));
			boolean confirmed = DialogWindowSupport.showConfirmation(dialog(), Alert.AlertType.WARNING,
					getText("UI_STOCK_PORTFOLIO_TRANSFER_CONFIRM_TITLE"),
					getText("UI_STOCK_PORTFOLIO_TRANSFER_CONFIRM_HEADER"),
					getText("UI_STOCK_PORTFOLIO_TRANSFER_CONFIRM_TEXT", source.displayName(), target.displayName()),
					new ButtonType(getText("UI_STOCK_ACTION_TRANSFER")), ButtonType.CANCEL);
			if (!confirmed) {
				return false;
			}
			transferHandler.transfer(target, dateField.getValue(), exchangeRate);
			savedHandler.run();
			return true;
		});
	}

	@FunctionalInterface
	public interface TransferHandler {

		void transfer(PortfolioSummary target, LocalDate date, BigDecimal exchangeRate);
	}
}
