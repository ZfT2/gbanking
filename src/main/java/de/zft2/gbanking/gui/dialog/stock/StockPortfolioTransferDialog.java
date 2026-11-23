package de.zft2.gbanking.gui.dialog.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioTransferService;
import de.zft2.gbanking.service.stock.StockPortfolioTransferService.TransferPlan;
import de.zft2.gbanking.service.stock.StockPortfolioTransferService.DeliveryComparison;
import de.zft2.gbanking.service.stock.StockPortfolioTransferService.DeliveryDifference;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class StockPortfolioTransferDialog extends AbstractStockDialog {

	private enum DateChoice { ORIGINAL, ENTERED, CANCEL }

	private final PortfolioSummary source;
	private final StockPortfolioTransferService service;
	private final Runnable savedHandler;
	private final ComboBox<PortfolioSummary> targetField;
	private final DatePicker dateField = new DatePicker();
	private final TextField exchangeRateField = new TextField();

	public StockPortfolioTransferDialog(Window owner, PortfolioSummary source, List<PortfolioSummary> targets,
			StockPortfolioTransferService service, Runnable savedHandler) {
		super(owner, "UI_STOCK_PORTFOLIO_TRANSFER_DIALOG_TITLE");
		this.source = source;
		this.service = service;
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
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_PORTFOLIO_TRANSFER_OPTIONAL_DATE"), dateField, 1, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_TRANSFER_EXCHANGE_RATE"), exchangeRateField, 2, 0);

		Button transferButton = createActionButton("UI_STOCK_ACTION_TRANSFER", () -> transfer());
		transferButton.setDisable(targetField.getItems().isEmpty());
		Label explanation = new Label(getText("UI_STOCK_PORTFOLIO_TRANSFER_HELP"));
		explanation.setWrapText(true);
		buildScene(getText("UI_STOCK_PORTFOLIO_TRANSFER_DIALOG_HEADER", source.displayName()),
				new VBox(12, explanation, form), transferButton, createCancelButton(), 760, 310);
	}

	private void updateExchangeRate(PortfolioSummary target) {
		Currency targetCurrency = target != null ? target.settlementCurrency() : null;
		StockDialogSupport.updateExchangeRateField(exchangeRateField, source.settlementCurrency() != targetCurrency);
	}

	private void transfer() {
		applyAndClose(() -> {
			PortfolioSummary target = targetField.getValue();
			if (target == null) {
				throw new GBankingException(getText("UI_STOCK_PORTFOLIO_TRANSFER_SELECT_TARGET"));
			}
			BigDecimal exchangeRate = StockDialogSupport.parseExchangeRate(exchangeRateField,
					getText("UI_STOCK_FIELD_TRANSFER_EXCHANGE_RATE"));
			TransferPlan plan = service.findExistingDeliveries(source, target, dateField.getValue());
			if (plan == null) {
				return transferManually(target, exchangeRate);
			}
			LocalDate requestedDate = dateField.getValue();
			DateChoice choice = chooseDate(plan, requestedDate);
			if (choice == DateChoice.CANCEL) {
				return false;
			}
			LocalDate overrideDate = choice == DateChoice.ENTERED ? requestedDate : null;
			boolean confirmed = DialogWindowSupport.showConfirmation(dialog(), Alert.AlertType.WARNING,
					getText("UI_STOCK_PORTFOLIO_TRANSFER_CONFIRM_TITLE"),
					getText("UI_STOCK_PORTFOLIO_TRANSFER_CONFIRM_HEADER"),
					getText("UI_STOCK_PORTFOLIO_TRANSFER_REUSE_TEXT", source.displayName(), target.displayName(),
							plan.outgoingIds().size() + plan.incomingIds().size(),
							overrideDate != null ? DateFormatUtils.formatLong(overrideDate) : formatDates(plan)),
					new ButtonType(getText("UI_STOCK_ACTION_TRANSFER")), ButtonType.CANCEL);
			if (!confirmed) {
				return false;
			}
			service.transferExistingDeliveries(source, target, plan, overrideDate, exchangeRate);
			savedHandler.run();
			return true;
		});
	}

	private DateChoice chooseDate(TransferPlan plan, LocalDate requestedDate) {
		if (requestedDate == null || plan.dates().stream().allMatch(date -> date.equals(requestedDate))) {
			return DateChoice.ORIGINAL;
		}
		ButtonType original = new ButtonType(getText("UI_STOCK_PORTFOLIO_TRANSFER_USE_ORIGINAL"), ButtonBar.ButtonData.OK_DONE);
		ButtonType entered = new ButtonType(getText("UI_STOCK_PORTFOLIO_TRANSFER_USE_ENTERED"), ButtonBar.ButtonData.OTHER);
		var choice = DialogWindowSupport.showChoice(dialog(), Alert.AlertType.CONFIRMATION,
				getText("UI_STOCK_PORTFOLIO_TRANSFER_CONFIRM_TITLE"), getText("UI_STOCK_PORTFOLIO_TRANSFER_DATE_CONFLICT"),
				getText("UI_STOCK_PORTFOLIO_TRANSFER_DATE_CHOICE", formatDates(plan), DateFormatUtils.formatLong(requestedDate)),
				original, entered, ButtonType.CANCEL);
		if (choice.isEmpty() || choice.get().equals(ButtonType.CANCEL)) {
			return DateChoice.CANCEL;
		}
		return choice.get().equals(entered) ? DateChoice.ENTERED : DateChoice.ORIGINAL;
	}

	private boolean transferManually(PortfolioSummary target, BigDecimal exchangeRate) {
		DeliveryComparison comparison = service.compareDeliveries(source, target, dateField.getValue());
		DatePicker cutoff = new DatePicker(comparison.cutoff());
		Label hint = new Label(getText("UI_STOCK_PORTFOLIO_TRANSFER_MANUAL_HINT"));
		hint.setWrapText(true);
		TableView<DeliveryDifference> differences = differenceTable(comparison);
		Label identityHint = new Label(getText("UI_STOCK_PORTFOLIO_TRANSFER_IDENTITY_HINT"));
		identityHint.setWrapText(true);
		cutoff.valueProperty().addListener((observable, previous, selected) -> {
			if (selected != null) {
				differences.getItems().setAll(service.compareDeliveries(source, target, selected).differences());
			}
		});
		Label confirmation = new Label(getText("UI_STOCK_PORTFOLIO_TRANSFER_CONFIRM_TEXT", source.displayName(), target.displayName()));
		confirmation.setWrapText(true);
		if (!DialogWindowSupport.showConfirmation(dialog(), Alert.AlertType.WARNING,
				getText("UI_STOCK_PORTFOLIO_TRANSFER_CONFIRM_TITLE"), getText("UI_STOCK_PORTFOLIO_TRANSFER_CONFIRM_HEADER"),
				new VBox(12, hint, differences, identityHint, new Label(getText("UI_STOCK_PORTFOLIO_TRANSFER_CUTOFF")), cutoff, confirmation),
				new ButtonType(getText("UI_STOCK_ACTION_TRANSFER")), ButtonType.CANCEL)) {
			return false;
		}
		if (cutoff.getValue() == null) {
			throw new GBankingException(getText("UI_STOCK_PORTFOLIO_TRANSFER_DATE_REQUIRED"));
		}
		service.transferManually(source, target, cutoff.getValue(), exchangeRate);
		savedHandler.run();
		return true;
	}

	private TableView<DeliveryDifference> differenceTable(DeliveryComparison comparison) {
		TableView<DeliveryDifference> table = new TableView<>(FXCollections.observableArrayList(comparison.differences()));
		table.getColumns().setAll(List.<TableColumn<DeliveryDifference, ?>>of(
				TableColumnFactory.createTextColumn(getText("UI_STOCK_COLUMN_SECURITY"), row -> row.securityName(), 160),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_SECURITY_QUANTITY_TYPE"), row -> row.quantityType().toString(), 85),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_PORTFOLIO_TRANSFER_EXPECTED"),
						row -> StockDialogSupport.formatDecimal(row.expected()), 95),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_PORTFOLIO_TRANSFER_OUTGOING"),
						row -> StockDialogSupport.formatDecimal(row.outgoing()), 95),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_PORTFOLIO_TRANSFER_INCOMING"),
						row -> StockDialogSupport.formatDecimal(row.incoming()), 95),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_PORTFOLIO_TRANSFER_OUT_DIFFERENCE"),
						row -> StockDialogSupport.formatDecimal(row.outgoingDifference()), 95),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_PORTFOLIO_TRANSFER_IN_DIFFERENCE"),
						row -> StockDialogSupport.formatDecimal(row.incomingDifference()), 95),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_PORTFOLIO_TRANSFER_SECURITY_ID"),
						row -> Integer.toString(row.securityId()), 85),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_COLUMN_ISIN"), row -> row.isin(), 120),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_COLUMN_WKN"), row -> row.wkn(), 80),
				TableColumnFactory.createBooleanSymbolColumn(getText("UI_TABLE_STATUS"), row -> row.quantitiesMatch(), 65)));
		table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
		table.setPlaceholder(new Label(getText("UI_STOCK_PORTFOLIO_TRANSFER_NO_DIFFERENCES")));
		table.setPrefSize(760, 160);
		return table;
	}

	private static String formatDates(TransferPlan plan) {
		return plan.dates().stream().map(date -> DateFormatUtils.formatLong(date)).collect(Collectors.joining(", "));
	}
}
