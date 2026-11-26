package de.zft2.gbanking.gui.dialog.stock;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.List;

import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.TransactionSummary;
import de.zft2.gbanking.service.stock.StockPortfolioTransferService;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

public final class StockPositionTransferDialog extends AbstractStockDialog {

	private record DeliveryConversion(PortfolioSummary portfolio, TransactionSummary transaction) {
	}

	private final PositionSummary position;
	private final TransferHandler transferHandler;
	private final Runnable savedHandler;
	private final ComboBox<PortfolioSummary> targetField;
	private final TextField quantityField = new TextField();
	private final DatePicker dateField = new DatePicker(LocalDate.now(ZoneId.systemDefault()));
	private final PortfolioSummary deliveryPortfolio;
	private final TransactionSummary delivery;
	private final StockPortfolioTransferService conversionService;

	public StockPositionTransferDialog(Window owner, PositionSummary position, List<PortfolioSummary> targets,
			TransferHandler transferHandler, Runnable savedHandler) {
		this(owner, position, targets, transferHandler, savedHandler, null);
	}

	private StockPositionTransferDialog(Window owner, PositionSummary position, List<PortfolioSummary> targets,
			TransferHandler transferHandler, Runnable savedHandler, DeliveryConversion conversion) {
		super(owner, "UI_STOCK_POSITION_TRANSFER_DIALOG_TITLE");
		this.position = position;
		this.transferHandler = transferHandler;
		this.savedHandler = savedHandler;
		deliveryPortfolio = conversion != null ? conversion.portfolio() : null;
		delivery = conversion != null ? conversion.transaction() : null;
		conversionService = conversion != null ? ServiceRegistry.getService(StockPortfolioTransferService.class) : null;
		targetField = new ComboBox<>(FXCollections.observableArrayList(targets));
		initialize();
	}

	public StockPositionTransferDialog(Window owner, PortfolioSummary portfolio, TransactionSummary transaction,
			List<PortfolioSummary> targets, Runnable savedHandler) {
		this(owner, new PositionSummary(portfolio.portfolioId(), transaction.securityId(), transaction.securityName(),
				transaction.isin(), transaction.wkn(), null, transaction.quantity().abs(), transaction.unitPrice(),
				null, null, null, transaction.currency(), null), targets, null, savedHandler,
				new DeliveryConversion(portfolio, transaction));
		quantityField.setEditable(false);
		quantityField.setDisable(true);
		dateField.setValue(transaction.date());
		dateField.setDisable(true);
	}

	private void initialize() {
		if (!targetField.getItems().isEmpty()) {
			targetField.getSelectionModel().selectFirst();
		}
		quantityField.setText(StockDialogSupport.formatDecimal(position.quantity()));

		GridPane form = FormGridHelper.createDefaultGrid();
		FormGridHelper.setEqualGrowColumns(form, 3);
		FormGridHelper.addFieldAbove(form, getText(delivery != null && delivery.transactionType() == StockTransactionType.DELIVERY_IN
				? "UI_STOCK_FIELD_SOURCE_PORTFOLIO" : "UI_STOCK_FIELD_TARGET_PORTFOLIO"), targetField, 0, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_QUANTITY"), quantityField, 1, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_DATE"), dateField, 2, 0);

		buildScene(getText("UI_STOCK_POSITION_TRANSFER_DIALOG_HEADER", position.securityName()), form,
				createActionButton("UI_STOCK_ACTION_TRANSFER", () -> transfer()), createCancelButton(), 700, 250);
	}

	private void transfer() {
		applyAndClose(() -> {
			if (delivery != null) {
				return convertDelivery();
			}
			transferHandler.transfer(targetField.getValue(),
					StockDialogSupport.parseRequiredDecimal(quantityField, getText("UI_STOCK_FIELD_QUANTITY")), dateField.getValue());
			savedHandler.run();
			return true;
		});
	}

	private boolean convertDelivery() {
		var plan = conversionService.preparePositionTransfer(deliveryPortfolio, targetField.getValue(), delivery.transactionId());
		String message = plan.counterpartId() == null ? "UI_STOCK_DELIVERY_TRANSFER_CREATE" : "UI_STOCK_DELIVERY_TRANSFER_LINK";
		if (!DialogWindowSupport.showConfirmation(dialog(), Alert.AlertType.CONFIRMATION,
				getText("UI_STOCK_POSITION_TRANSFER_DIALOG_TITLE"), getText("UI_STOCK_CONVERT_TO_TRANSFER"),
				getText(message, targetField.getValue().displayName(),
						getText(delivery.transactionType() == StockTransactionType.DELIVERY_IN
								? "UI_STOCK_DELIVERY_TRANSFER_OUT" : "UI_STOCK_DELIVERY_TRANSFER_IN"),
						DateFormatUtils.formatLong(plan.date()),
						plan.outgoing() ? StockTransactionType.DELIVERY_IN : StockTransactionType.DELIVERY_OUT,
						delivery.isin() != null ? delivery.isin() : "", plan.position().securityName()),
				ButtonType.OK, ButtonType.CANCEL)) {
			return false;
		}
		conversionService.convertPositionDelivery(deliveryPortfolio, targetField.getValue(), plan);
		savedHandler.run();
		return true;
	}

	@FunctionalInterface
	public interface TransferHandler {

		void transfer(PortfolioSummary target, BigDecimal quantity, LocalDate date);
	}
}
