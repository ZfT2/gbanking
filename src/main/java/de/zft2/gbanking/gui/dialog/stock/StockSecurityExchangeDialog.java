package de.zft2.gbanking.gui.dialog.stock;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.FormControlUtils;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.SecurityExchangeRequest;
import de.zft2.gbanking.service.stock.StockPortfolioService.SecuritySummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.TransactionSummary;
import javafx.collections.FXCollections;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

public final class StockSecurityExchangeDialog extends AbstractStockDialog {

	public StockSecurityExchangeDialog(Window owner, StockPortfolioService service, PortfolioSummary portfolio,
			PositionSummary position, boolean linkImported, Runnable savedHandler) {
		super(owner, "UI_STOCK_EXCHANGE_TITLE");
		List<SecuritySummary> securities = service.getTransactionSecurities();
		Function<SecuritySummary, String> securityLabel = security -> security.name() + " | "
				+ Objects.toString(security.isin(), "") + " | " + Objects.toString(security.wkn(), "");
		ComboBox<SecuritySummary> source = selectionField(securities, securityLabel);
		ComboBox<SecuritySummary> target = selectionField(securities, securityLabel);
		TextField quantity = new TextField(position != null ? StockDialogSupport.formatDecimal(position.quantity()) : "");
		DatePicker date = new DatePicker(LocalDate.now());
		TextField note = new TextField();
		if (position != null) {
			securities.stream().filter(security -> security.securityId() == position.securityId())
					.findFirst().ifPresent(source::setValue);
		}
		GridPane manual = FormGridHelper.createDefaultGrid();
		FormGridHelper.setEqualGrowColumns(manual, 2);
		FormGridHelper.addFieldAbove(manual, getText("UI_STOCK_EXCHANGE_SOURCE"), source, 0, 0);
		FormGridHelper.addFieldAbove(manual, getText("UI_STOCK_EXCHANGE_TARGET"), target, 1, 0);
		FormGridHelper.addFieldAbove(manual, getText("UI_STOCK_FIELD_QUANTITY"), quantity, 0, 1);
		FormGridHelper.addFieldAbove(manual, getText("UI_STOCK_FIELD_DATE"), date, 1, 1);
		FormGridHelper.addFieldAbove(manual, getText("UI_STOCK_FIELD_NOTE"), note, 0, 2, 2);

		Function<TransactionSummary, String> movementLabel = movement -> DateFormatUtils.formatLong(movement.date()) + " | "
				+ movement.securityName() + " | " + Objects.toString(movement.isin(), "") + " | "
				+ Objects.toString(movement.wkn(), "") + " | " + StockDialogSupport.formatDecimal(movement.quantity())
				+ " (#" + movement.transactionId() + ")";
		ComboBox<TransactionSummary> outgoing = selectionField(
				service.getSecurityExchangeCandidates(portfolio.portfolioId(), true), movementLabel);
		ComboBox<TransactionSummary> incoming = selectionField(
				service.getSecurityExchangeCandidates(portfolio.portfolioId(), false), movementLabel);
		GridPane imported = FormGridHelper.createDefaultGrid();
		FormGridHelper.setEqualGrowColumns(imported, 1);
		FormGridHelper.addFieldAbove(imported, getText("UI_STOCK_EXCHANGE_OUTGOING"), outgoing, 0, 0);
		FormGridHelper.addFieldAbove(imported, getText("UI_STOCK_EXCHANGE_INCOMING"), incoming, 0, 1);
		CheckBox useImported = new CheckBox(getText("UI_STOCK_EXCHANGE_USE_IMPORTED"));
		useImported.setSelected(linkImported);
		manual.visibleProperty().bind(useImported.selectedProperty().not());
		manual.managedProperty().bind(manual.visibleProperty());
		imported.visibleProperty().bind(useImported.selectedProperty());
		imported.managedProperty().bind(imported.visibleProperty());
		Label explanation = new Label(getText("UI_STOCK_EXCHANGE_EXPLANATION"));
		explanation.setWrapText(true);
		buildScene(getText("UI_STOCK_EXCHANGE_HEADER", portfolio.displayName()),
				List.of(explanation, useImported, manual, imported),
				List.of(createActionButton("UI_BUTTON_SAVE", () -> applyAndClose(() -> {
					if (useImported.isSelected()) {
						if (outgoing.getValue() == null || incoming.getValue() == null) {
							throw new GBankingException(getText("UI_STOCK_EXCHANGE_SELECT_MOVEMENTS"));
						}
						service.linkSecurityExchange(portfolio, outgoing.getValue().transactionId(), incoming.getValue().transactionId());
					} else {
						if (source.getValue() == null || target.getValue() == null) {
							throw new GBankingException(getText("UI_STOCK_EXCHANGE_SELECT_SECURITIES"));
						}
						service.recordSecurityExchange(portfolio, new SecurityExchangeRequest(source.getValue().securityId(),
								target.getValue().securityId(),
								StockDialogSupport.parseRequiredDecimal(quantity, getText("UI_STOCK_FIELD_QUANTITY")),
								date.getValue(), note.getText()));
					}
					savedHandler.run();
					return true;
				})), createCancelButton()), 850, 430);
	}

	private static <T> ComboBox<T> selectionField(List<T> values, Function<T, String> label) {
		ComboBox<T> field = new ComboBox<>(FXCollections.observableArrayList(values));
		field.setMaxWidth(Double.MAX_VALUE);
		field.setConverter(FormControlUtils.displayConverter(label));
		return field;
	}
}
