package de.zft2.gbanking.gui.dialog.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import de.zft2.gbanking.db.dao.enu.StockCorporateActionOperation;
import de.zft2.gbanking.db.dao.enu.StockCorporateActionType;
import de.zft2.gbanking.db.dao.stock.StockCorporateAction;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.util.FormControlUtils;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.service.stock.StockCorporateActionService;
import de.zft2.gbanking.service.stock.StockCorporateActionService.ActionResult;
import de.zft2.gbanking.service.stock.StockCorporateActionService.OperationRequest;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.SecuritySummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.TransactionSummary;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class StockCorporateActionDialog extends AbstractStockDialog {
	private final StockCorporateActionService service;
	private final StockPortfolioService portfolios;
	private final PortfolioSummary portfolio;
	private final Runnable saved;
	private final ComboBox<StockCorporateAction> action;
	private final ComboBox<StockCorporateActionOperation> operation = selection(Arrays.asList(StockCorporateActionOperation.values()), Object::toString);
	private final DatePicker date = new DatePicker(LocalDate.now());
	private final TextField quantity = new TextField();
	private final TextField price = new TextField();
	private final TextField fees = new TextField();
	private final TextField taxes = new TextField();
	private final TextField basis = new TextField();
	private final TextField booking = new TextField();
	private final ComboBox<TransactionSummary> original;
	private final ComboBox<TransactionSummary> related;
	private final TableView<ActionResult> results = new TableView<>();
	private final Label preview = new Label();
	private final TableView<TransactionSummary> history = new TableView<>();
	private final TableView<PositionSummary> holdings = new TableView<>();
	private final Label totals = new Label();

	public StockCorporateActionDialog(Window owner, PortfolioSummary portfolio, Runnable saved) {
		this(owner, new StockCorporateActionService(), new StockPortfolioService(), portfolio, saved);
	}

	StockCorporateActionDialog(Window owner, StockCorporateActionService service, StockPortfolioService portfolios,
			PortfolioSummary portfolio, Runnable saved) {
		super(owner, "UI_STOCK_CORPORATE_TITLE");
		this.service = service;
		this.portfolios = portfolios;
		this.portfolio = portfolio;
		this.saved = saved;
		action = selection(service.getActions(), StockCorporateAction::getName);
		Function<TransactionSummary, String> transactionLabel = row -> "#" + row.transactionId() + " | " + row.date()
				+ " | " + row.securityName() + " | " + Objects.toString(row.isin(), "") + " | " + row.quantity();
		original = selection(portfolios.getTransactions(portfolio.portfolioId()), transactionLabel);
		related = selection(portfolios.getTransactions(portfolio.portfolioId()), transactionLabel);
		TabPane tabs = new TabPane(tab("UI_STOCK_CORPORATE_OPERATIONS", operationForm()),
				tab("UI_STOCK_CORPORATE_DEFINITION", definitionForm()), historyTab());
		configureResults();
		Label help = new Label(getText("UI_STOCK_CORPORATE_HELP"));
		help.setWrapText(true);
		buildScene(getText("UI_STOCK_CORPORATE_TITLE"), List.of(help, tabs, results),
				List.of(createButton("UI_STOCK_CORPORATE_REMOVE", () -> runAction(() -> {
					ActionResult selected = results.getSelectionModel().getSelectedItem();
					if (selected == null) { throw new GBankingException(getText("UI_STOCK_CORPORATE_SELECT_RESULT")); }
					if (!DialogWindowSupport.showConfirmation(dialog(), Alert.AlertType.CONFIRMATION, getText("UI_STOCK_CORPORATE_REMOVE"),
							null, getText("UI_STOCK_CORPORATE_REMOVE_CONFIRM"), ButtonType.OK, ButtonType.CANCEL)) { return; }
					service.unlink(selected.associationId());
					refresh();
				})), createCloseButton("UI_BUTTON_CLOSE")), 1100, 850);
		action.valueProperty().addListener((observable, old, selected) -> {
			if (selected != null) {
				date.setValue(selected.getEffectiveDate());
				operation.getItems().setAll(Arrays.stream(StockCorporateActionOperation.values())
						.filter(value -> value.isPermitted(selected.getActionType())).toList());
				operation.getSelectionModel().selectFirst();
			}
			refreshResults();
		});
		operation.valueProperty().addListener((observable, old, selected) -> updatePreview());
		quantity.textProperty().addListener((observable, old, selected) -> updatePreview());
		refresh();
	}

	private GridPane operationForm() {
		GridPane form = FormGridHelper.createDefaultGrid();
		FormGridHelper.setEqualGrowColumns(form, 3);
		field(form, "UI_STOCK_CORPORATE_ACTION", action, 0, 0);
		field(form, "UI_STOCK_CORPORATE_OPERATION", operation, 1, 0);
		field(form, "UI_STOCK_FIELD_DATE", date, 2, 0);
		field(form, "UI_STOCK_CORPORATE_QUANTITY", quantity, 0, 1);
		field(form, "UI_STOCK_CORPORATE_PRICE", price, 1, 1);
		field(form, "UI_STOCK_CORPORATE_BASIS", basis, 2, 1);
		field(form, "UI_STOCK_FIELD_FEES", fees, 0, 2);
		field(form, "UI_STOCK_FIELD_TAXES", taxes, 1, 2);
		field(form, "UI_STOCK_CORPORATE_BOOKING", booking, 2, 2);
		field(form, "UI_STOCK_CORPORATE_ORIGINAL", original, 0, 3);
		field(form, "UI_STOCK_CORPORATE_RELATED", related, 1, 3);
		Button record = createButton("UI_STOCK_CORPORATE_RECORD", () -> runAction(() -> {
			service.record(portfolio, request());
			refresh();
		}));
		Button link = createButton("UI_STOCK_CORPORATE_LINK", () -> runAction(() -> {
			if (original.getValue() == null) { throw new GBankingException(getText("UI_STOCK_EXCHANGE_SELECT_MOVEMENTS")); }
			service.link(portfolio, request(), original.getValue().transactionId(),
					related.getValue() != null ? related.getValue().transactionId() : null);
			refresh();
		}));
		Button clear = createButton("UI_STOCK_CORPORATE_CLEAR", () -> { original.setValue(null); related.setValue(null); booking.clear(); });
		form.add(new javafx.scene.layout.HBox(8, record, link, clear), 0, 4, 3, 1);
		preview.setWrapText(true);
		form.add(preview, 0, 5, 3, 1);
		return form;
	}

	private GridPane definitionForm() {
		List<SecuritySummary> securities = portfolios.getTransactionSecurities();
		Function<SecuritySummary, String> label = security -> security.name() + " | " + Objects.toString(security.isin(), "")
				+ " | " + Objects.toString(security.wkn(), "");
		ComboBox<SecuritySummary> underlying = selection(securities, label), rights = selection(securities, label),
				target = selection(securities, label);
		ComboBox<StockCorporateActionType> type = selection(Arrays.asList(StockCorporateActionType.values()), Object::toString);
		type.getSelectionModel().selectFirst();
		TextField name = new TextField(), allocationFrom = new TextField("1"), allocationTo = new TextField("1"),
				ratioFrom = new TextField("1"), ratioTo = new TextField("1"), subscription = new TextField(),
				oldNominal = new TextField(), newNominal = new TextField();
		DatePicker effective = new DatePicker(LocalDate.now()), end = new DatePicker();
		GridPane form = FormGridHelper.createDefaultGrid();
		FormGridHelper.setEqualGrowColumns(form, 3);
		field(form, "UI_STOCK_CORPORATE_NAME", name, 0, 0);
		field(form, "UI_STOCK_CORPORATE_TYPE", type, 1, 0);
		field(form, "UI_STOCK_FIELD_DATE", effective, 2, 0);
		field(form, "UI_STOCK_CORPORATE_UNDERLYING", underlying, 0, 1);
		field(form, "UI_STOCK_CORPORATE_RIGHTS", rights, 1, 1);
		field(form, "UI_STOCK_CORPORATE_TARGET", target, 2, 1);
		field(form, "UI_STOCK_CORPORATE_ALLOCATION_FROM", allocationFrom, 0, 2);
		field(form, "UI_STOCK_CORPORATE_ALLOCATION_TO", allocationTo, 1, 2);
		field(form, "UI_STOCK_CORPORATE_END", end, 2, 2);
		field(form, "UI_STOCK_CORPORATE_RATIO_FROM", ratioFrom, 0, 3);
		field(form, "UI_STOCK_CORPORATE_RATIO_TO", ratioTo, 1, 3);
		field(form, "UI_STOCK_CORPORATE_SUBSCRIPTION_PRICE", subscription, 2, 3);
		field(form, "UI_STOCK_CORPORATE_OLD_NOMINAL", oldNominal, 0, 4);
		field(form, "UI_STOCK_CORPORATE_NEW_NOMINAL", newNominal, 1, 4);
		form.add(createButton("UI_STOCK_CORPORATE_CREATE", () -> runAction(() -> {
			if (underlying.getValue() == null) { throw new GBankingException(getText("UI_STOCK_EXCHANGE_SELECT_SECURITIES")); }
			StockCorporateAction definition = new StockCorporateAction();
			definition.setName(name.getText());
			definition.setActionType(type.getValue());
			definition.setUnderlyingSecurityId(underlying.getValue().securityId());
			definition.setRightsSecurityId(rights.getValue() != null ? rights.getValue().securityId() : null);
			definition.setTargetSecurityId(target.getValue() != null ? target.getValue().securityId() : underlying.getValue().securityId());
			definition.setEffectiveDate(effective.getValue());
			definition.setEndDate(end.getValue());
			definition.setAllocationFromE9(number(allocationFrom, 9));
			definition.setAllocationToE9(number(allocationTo, 9));
			definition.setRatioFromE9(number(ratioFrom, 9));
			definition.setRatioToE9(number(ratioTo, 9));
			definition.setSubscriptionPriceE8(optionalNumber(subscription, 8));
			definition.setOldNominalE8(optionalNumber(oldNominal, 8));
			definition.setNewNominalE8(optionalNumber(newNominal, 8));
			StockCorporateAction created = service.createAction(definition);
			action.getItems().setAll(service.getActions());
			action.setValue(created);
			saved.run();
		})), 0, 5);
		return form;
	}

	private OperationRequest request() {
		if (action.getValue() == null || operation.getValue() == null) { throw new GBankingException(getText("UI_STOCK_CORPORATE_SELECT_ACTION")); }
		return new OperationRequest(action.getValue().getId(), operation.getValue(), date.getValue(),
				decimal(quantity), decimal(price), decimal(fees), decimal(taxes), decimal(basis), integer(booking));
	}

	private void configureResults() {
		results.getColumns().add(TableColumnFactory.createCalendarDateColumn(getText("UI_STOCK_COLUMN_DATE"), ActionResult::date, 100));
		results.getColumns().add(TableColumnFactory.createTextColumn(getText("UI_STOCK_CORPORATE_ACTION"), ActionResult::actionName, 140, 180));
		results.getColumns().add(TableColumnFactory.createTextColumn(getText("UI_STOCK_CORPORATE_OPERATION"), row -> row.operation().toString(), 160, 200));
		results.getColumns().add(TableColumnFactory.createTextColumn(getText("UI_STOCK_FIELD_CURRENCY"), row -> row.currency().name(), 60, 80));
		results.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_CORPORATE_OUTGOING"), ActionResult::outgoing));
		results.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_CORPORATE_INCOMING"), ActionResult::incoming));
		results.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_CORPORATE_GROSS"), ActionResult::gross));
		results.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_FIELD_FEES"), ActionResult::fees));
		results.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_FIELD_TAXES"), ActionResult::taxes));
		results.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_CORPORATE_NET"), ActionResult::netCash));
		results.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_CORPORATE_DISPOSED"), ActionResult::disposedBasis));
		results.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_CORPORATE_RESULT"), ActionResult::realizedResult));
		results.setPrefHeight(230);
	}

	private void refresh() {
		refreshResults();
		original.getItems().setAll(portfolios.getTransactions(portfolio.portfolioId()));
		related.getItems().setAll(portfolios.getTransactions(portfolio.portfolioId()));
		saved.run();
	}

	private void refreshResults() {
		results.getItems().setAll(service.getResults(portfolio.portfolioId()).stream()
				.filter(row -> action.getValue() == null || row.actionId() == action.getValue().getId()).toList());
		history.getItems().setAll(action.getValue() == null ? List.of()
				: portfolios.getCorporateActionHistory(portfolio.portfolioId(), action.getValue().getId()));
		Set<Integer> securityIds = action.getValue() != null
				? portfolios.getCorporateActionSecurityIds(portfolio.portfolioId(), action.getValue().getId()) : Set.of();
		holdings.getItems().setAll(portfolios.getPositions(portfolio.portfolioId()).stream()
				.filter(position -> securityIds.contains(position.securityId())).toList());
		totals.setText(action.getValue() == null ? "" : getText("UI_STOCK_CORPORATE_TOTALS",
				holdingTotal(PositionSummary::acquisitionValue), holdingTotal(PositionSummary::totalValue), action.getValue().getCurrency()));
		updatePreview();
	}

	private Tab historyTab() {
		history.getColumns().add(TableColumnFactory.createCalendarDateColumn(getText("UI_STOCK_COLUMN_DATE"), TransactionSummary::date, 100));
		history.getColumns().add(TableColumnFactory.createTextColumn(getText("UI_STOCK_COLUMN_SECURITY"), TransactionSummary::securityName, 150, 200));
		history.getColumns().add(TableColumnFactory.createTextColumn(getText("UI_STOCK_COLUMN_TRANSACTION"),
				row -> row.corporateOperation() != null ? row.corporateOperation().toString() : row.transactionType().toString(), 150, 200));
		history.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_COLUMN_QUANTITY"), TransactionSummary::quantity));
		history.setPrefHeight(180);
		holdings.getColumns().add(TableColumnFactory.createTextColumn(getText("UI_STOCK_COLUMN_SECURITY"), PositionSummary::securityName, 150, 200));
		holdings.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_COLUMN_QUANTITY"), PositionSummary::quantity));
		holdings.getColumns().add(TableColumnFactory.createTextColumn(getText("UI_STOCK_FIELD_CURRENCY"), row -> row.currency().name(), 60, 80));
		holdings.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_CORPORATE_ECONOMIC_BASIS"), PositionSummary::acquisitionValue));
		holdings.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_COLUMN_TOTAL_VALUE"), PositionSummary::totalValue));
		holdings.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_CORPORATE_BANK_BASIS"), position ->
				portfolios.getBankReportedAcquisitionPrice(portfolio.portfolioId(), position.securityId(), position.currency())));
		holdings.setPrefHeight(140);
		Label explanation = new Label(getText("UI_STOCK_CORPORATE_HISTORY_HELP"));
		explanation.setWrapText(true);
		totals.setWrapText(true);
		Tab tab = new Tab(getText("UI_STOCK_CORPORATE_HISTORY"), new VBox(8, explanation, holdings, totals, history));
		tab.setClosable(false);
		return tab;
	}

	private String holdingTotal(Function<PositionSummary, BigDecimal> field) {
		if (holdings.getItems().stream().anyMatch(row -> row.currency() != action.getValue().getCurrency() || field.apply(row) == null)) {
			return "-";
		}
		BigDecimal total = holdings.getItems().stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
		return StockDialogSupport.formatDecimal(total.setScale(2, RoundingMode.HALF_UP));
	}

	private void updatePreview() {
		StockCorporateAction selected = action.getValue();
		if (selected == null) { preview.setText(""); return; }
		String text = getText("UI_STOCK_CORPORATE_TERMS", selected.getName(), selected.getCurrency(),
				BigDecimal.valueOf(selected.getAllocationFromE9(), 9).stripTrailingZeros().toPlainString(),
				BigDecimal.valueOf(selected.getAllocationToE9(), 9).stripTrailingZeros().toPlainString(),
				BigDecimal.valueOf(selected.getRatioFromE9(), 9).stripTrailingZeros().toPlainString(),
				BigDecimal.valueOf(selected.getRatioToE9(), 9).stripTrailingZeros().toPlainString(),
				selected.getOldNominalE8() != null ? BigDecimal.valueOf(selected.getOldNominalE8(), 8) : "-",
				selected.getNewNominalE8() != null ? BigDecimal.valueOf(selected.getNewNominalE8(), 8) : "-");
		if (operation.getValue() == StockCorporateActionOperation.SUBSCRIPTION && !quantity.getText().isBlank()) {
			try {
				BigDecimal amount = StockCorporateActionService.ratio(decimal(quantity), selected.getRatioFromE9(), selected.getRatioToE9());
				text += " | " + getText("UI_STOCK_CORPORATE_ENTITLEMENT", amount.toPlainString());
			} catch (GBankingException exception) { text += " | " + exception.getMessage(); }
		}
		preview.setText(text);
	}

	private Tab tab(String key, GridPane form) {
		Tab tab = new Tab(getText(key), new ScrollPane(form));
		tab.setClosable(false);
		return tab;
	}
	private void field(GridPane form, String key, javafx.scene.Node node, int column, int row) {
		node.setAccessibleText(getText(key));
		FormGridHelper.addFieldAbove(form, getText(key), node, column, row);
	}
	private static <T> ComboBox<T> selection(List<T> values, Function<T, String> label) {
		ComboBox<T> field = new ComboBox<>(FXCollections.observableArrayList(values));
		field.setMaxWidth(Double.MAX_VALUE);
		field.setConverter(FormControlUtils.displayConverter(label));
		return field;
	}
	private static BigDecimal decimal(TextField field) { return StockDialogSupport.parseOptionalDecimal(field, field.getAccessibleText() != null ? field.getAccessibleText() : field.getText()); }
	private static long number(TextField field, int scale) { return StockCorporateActionService.scaled(StockDialogSupport.parseRequiredDecimal(field, field.getText()), scale); }
	private static Long optionalNumber(TextField field, int scale) { BigDecimal value = decimal(field); return value != null ? StockCorporateActionService.scaled(value, scale) : null; }
	private static Integer integer(TextField field) {
		try { return field.getText().isBlank() ? null : Integer.valueOf(field.getText().trim()); }
		catch (NumberFormatException exception) { throw new GBankingException("Ungültige Umsatz-ID", exception); }
	}
}
