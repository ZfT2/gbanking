package de.zft2.gbanking.gui.dialog.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import de.zft2.gbanking.BaseMessages;
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
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class StockPortfolioTransferDialog implements BaseMessages {

	private final PortfolioSummary source;
	private final TransferHandler transferHandler;
	private final Runnable savedHandler;
	private final Stage dialog;
	private final ComboBox<PortfolioSummary> targetField;
	private final DatePicker dateField = new DatePicker(LocalDate.now());
	private final TextField exchangeRateField = new TextField();

	public StockPortfolioTransferDialog(Window owner, PortfolioSummary source, List<PortfolioSummary> targets,
			TransferHandler transferHandler, Runnable savedHandler) {
		this.source = source;
		this.transferHandler = transferHandler;
		this.savedHandler = savedHandler;
		targetField = new ComboBox<>(FXCollections.observableArrayList(targets));
		dialog = DialogWindowSupport.createModalStage(owner, "UI_STOCK_PORTFOLIO_TRANSFER_DIALOG_TITLE");
		initialize();
	}

	public void show() {
		dialog.showAndWait();
	}

	private void initialize() {
		Label header = new Label(getText("UI_STOCK_PORTFOLIO_TRANSFER_DIALOG_HEADER", source.displayName()));
		header.getStyleClass().add("gbanking-form-section-title");
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

		Button transferButton = new Button(getText("UI_STOCK_ACTION_TRANSFER"));
		Button cancelButton = new Button(getText("UI_BUTTON_CANCEL"));
		transferButton.setDefaultButton(true);
		transferButton.setDisable(targetField.getItems().isEmpty());
		cancelButton.setCancelButton(true);
		transferButton.setOnAction(event -> transfer());
		cancelButton.setOnAction(event -> dialog.close());
		VBox root = DialogWindowSupport.createDialogRoot(header, form,
				DialogWindowSupport.createButtonBar(transferButton, cancelButton));
		dialog.setScene(DialogWindowSupport.createScene(root, 720, 250));
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
		try {
			PortfolioSummary target = targetField.getValue();
			BigDecimal exchangeRate = exchangeRateField.isDisabled() ? BigDecimal.ONE
					: StockDialogSupport.parseRequiredDecimal(exchangeRateField, getText("UI_STOCK_FIELD_TRANSFER_EXCHANGE_RATE"));
			boolean confirmed = DialogWindowSupport.showConfirmation(dialog, Alert.AlertType.WARNING,
					getText("UI_STOCK_PORTFOLIO_TRANSFER_CONFIRM_TITLE"),
					getText("UI_STOCK_PORTFOLIO_TRANSFER_CONFIRM_HEADER"),
					getText("UI_STOCK_PORTFOLIO_TRANSFER_CONFIRM_TEXT", source.displayName(), target.displayName()),
					new ButtonType(getText("UI_STOCK_ACTION_TRANSFER")), ButtonType.CANCEL);
			if (!confirmed) {
				return;
			}
			transferHandler.transfer(target, dateField.getValue(), exchangeRate);
			savedHandler.run();
			dialog.close();
		} catch (RuntimeException exception) {
			StockDialogSupport.showError(dialog, exception);
		}
	}

	@FunctionalInterface
	public interface TransferHandler {

		void transfer(PortfolioSummary target, LocalDate date, BigDecimal exchangeRate);
	}
}
