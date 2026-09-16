package de.zft2.gbanking.gui.panel.overview;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.component.GBankingTableView;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.dialog.stock.SecurityPriceDialog;
import de.zft2.gbanking.gui.dialog.stock.StockIncomeDialog;
import de.zft2.gbanking.gui.dialog.stock.StockPortfolioTransferDialog;
import de.zft2.gbanking.gui.dialog.stock.StockPositionTransferDialog;
import de.zft2.gbanking.gui.dialog.stock.StockSettlementAccountDialog;
import de.zft2.gbanking.gui.dialog.stock.StockTradeDialog;
import de.zft2.gbanking.gui.dialog.stock.StockTransactionDialog;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.layout.MasterContentPane;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.gui.util.FxTableUtils;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.SecuritySummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.TransactionPrefill;
import de.zft2.gbanking.service.stock.StockPortfolioService.TransactionSummary;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class StockPortfolioOverviewPanel extends OverviewBasePanel {
	private static final String AMOUNT_POSITIVE = "amount-positive";
	private static final String AMOUNT_NEGATIVE = "amount-negative";
	private static final String AMOUNT_NEUTRAL = "amount-neutral";
	private static final String MUTED_TRANSACTION_STYLE = "booking-prenotification";
	private static final double MASTER_DIVIDER = 0.22;
	private static final double TABLE_DIVIDER = 0.5;

	private final StockPortfolioService service;
	private final TreeTableView<NavigationEntry> navigationTree = new TreeTableView<>();
	private final CheckBox portfolioSelectAll = new CheckBox();
	private final Set<Integer> selectedPortfolioIds = new HashSet<>();
	private final TabPane detailTabs = new TabPane();
	private final Tab portfolioTab;
	private final Tab settlementAccountTab;
	private final GBankingTableView<PositionSummary> positionTable = new GBankingTableView<>();
	private final GBankingTableView<TransactionSummary> transactionTable = new GBankingTableView<>();
	private final GBankingTableView<Booking> accountTransactionTable = new GBankingTableView<>();
	private final StackPane tableContent = new StackPane();
	private final SplitPane portfolioTables;
	private final VBox accountTransactions;
	private final Label portfolioNameValue = new Label();
	private final Label portfolioBankValue = new Label();
	private final Label portfolioNumberValue = new Label();
	private final Label portfolioOpenedValue = new Label();
	private final Label portfolioSettlementValue = new Label();
	private final Label settlementNameValue = new Label();
	private final Label settlementBankValue = new Label();
	private final Label settlementIbanValue = new Label();
	private final Label settlementCurrencyValue = new Label();
	private final Label settlementBalanceValue = new Label();
	private final Button transactionNewButton = new Button();
	private final Button transactionEditButton = new Button();
	private final Button transactionDeleteButton = new Button();
	private final Button settlementEditButton = new Button();
	private List<PortfolioSummary> portfolios = List.of();
	private PortfolioSummary selectedPortfolio;

	public StockPortfolioOverviewPanel() {
		this(ServiceRegistry.getService(StockPortfolioService.class));
	}

	StockPortfolioOverviewPanel(StockPortfolioService service) {
		this.service = service;
		setPageContext(PageContext.STOCK_PORTFOLIOS);
		configureNavigation();
		configurePositionTable();
		configureTransactionTable();
		configureAccountTransactionTable();

		portfolioTab = createTab("UI_STOCK_TAB_PORTFOLIO", createPortfolioDetails());
		settlementAccountTab = createTab("UI_STOCK_TAB_SETTLEMENT_ACCOUNT", createSettlementDetails());
		detailTabs.getTabs().setAll(portfolioTab, settlementAccountTab);
		detailTabs.setMinHeight(Region.USE_PREF_SIZE);
		detailTabs.setMaxHeight(Region.USE_PREF_SIZE);
		detailTabs.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> updateTableMode());

		VBox positionSection = createTableSection("UI_STOCK_POSITIONS", positionTable);
		VBox transactionSection = createTableSection("UI_STOCK_PORTFOLIO_TRANSACTIONS", transactionTable);
		portfolioTables = new SplitPane(positionSection, transactionSection);
		portfolioTables.setOrientation(javafx.geometry.Orientation.VERTICAL);
		portfolioTables.setDividerPositions(TABLE_DIVIDER);
		GuiLayoutState.configureSplitPane(portfolioTables, "stockPortfolios.tables");
		accountTransactions = createTableSection("UI_STOCK_SETTLEMENT_TRANSACTIONS", accountTransactionTable);
		tableContent.getChildren().addAll(portfolioTables, accountTransactions);

		VBox right = new VBox(detailTabs, tableContent);
		VBox.setVgrow(tableContent, Priority.ALWAYS);
		MasterContentPane content = new MasterContentPane(createNavigationPane(), right, "stockPortfolios.main", MASTER_DIVIDER);
		setOverviewContent("UI_PANEL_STOCK_PORTFOLIOS", content);
		updateTableMode();
	}

	@Override
	public void refreshOnShow() {
		reloadPortfolios();
	}

	public PortfolioSummary getSelectedPortfolio() {
		return selectedPortfolio;
	}

	private void configureNavigation() {
		navigationTree.setShowRoot(false);
		navigationTree.setRoot(new TreeItem<>());
		navigationTree.setEditable(true);
		navigationTree.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		navigationTree.getColumns().setAll(createPortfolioSelectionColumn(), createNavigationNameColumn());
		navigationTree.setRowFactory(tree -> createNavigationRow());
		navigationTree.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
			if (selected == null || selected.getValue() == null) {
				selectPortfolio(null);
				return;
			}
			NavigationEntry entry = selected.getValue();
			selectPortfolio(entry.portfolio());
			detailTabs.getSelectionModel().select(entry.accountNode() ? settlementAccountTab : portfolioTab);
		});
	}

	private TreeTableColumn<NavigationEntry, Boolean> createPortfolioSelectionColumn() {
		portfolioSelectAll.setAccessibleText(getText("UI_TABLE_SELECT_ALL"));
		portfolioSelectAll.setTooltip(new Tooltip(getText("UI_TABLE_SELECT_ALL")));
		portfolioSelectAll.setAllowIndeterminate(true);
		portfolioSelectAll.setOnAction(event -> selectAllPortfolios());
		TreeTableColumn<NavigationEntry, Boolean> column = new TreeTableColumn<>();
		column.setGraphic(portfolioSelectAll);
		column.setCellValueFactory(data -> data.getValue().getValue().selectedProperty());
		column.setCellFactory(ignored -> new TreeTableCell<>() {
			private final CheckBox checkBox = new CheckBox();
			private BooleanProperty boundProperty;

			@Override
			protected void updateItem(Boolean item, boolean empty) {
				super.updateItem(item, empty);
				if (boundProperty != null) {
					checkBox.selectedProperty().unbindBidirectional(boundProperty);
					boundProperty = null;
				}
				NavigationEntry entry = getTreeTableRow() != null ? getTreeTableRow().getItem() : null;
				if (!empty && entry != null && !entry.accountNode()) {
					boundProperty = entry.selectedProperty();
					checkBox.selectedProperty().bindBidirectional(boundProperty);
				}
				setGraphic(boundProperty != null ? checkBox : null);
				setAlignment(Pos.CENTER);
			}
		});
		column.setEditable(true);
		column.setSortable(false);
		column.setMinWidth(32);
		column.setPrefWidth(32);
		column.setMaxWidth(32);
		return column;
	}

	private TreeTableColumn<NavigationEntry, String> createNavigationNameColumn() {
		TreeTableColumn<NavigationEntry, String> column = new TreeTableColumn<>(getText("UI_STOCK_FIELD_PORTFOLIO"));
		column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getValue().displayName()));
		column.setMinWidth(140);
		column.setPrefWidth(240);
		return column;
	}

	private TreeTableRow<NavigationEntry> createNavigationRow() {
		TreeTableRow<NavigationEntry> row = new TreeTableRow<>();
		row.itemProperty().addListener((observable, previous, entry) -> row.setContextMenu(
				entry != null && !entry.accountNode() ? createPortfolioContextMenu(entry.portfolio()) : null));
		return row;
	}

	private ContextMenu createPortfolioContextMenu(PortfolioSummary portfolio) {
		MenuItem transfer = new MenuItem(getText("UI_STOCK_ACTION_TRANSFER_PORTFOLIO"));
		transfer.setOnAction(event -> openPortfolioTransfer(portfolio));
		return new ContextMenu(transfer);
	}

	private VBox createNavigationPane() {
		Label title = new Label(getText("UI_STOCK_PORTFOLIO_LIST"));
		title.getStyleClass().add("gbanking-form-section-title");
		VBox result = new VBox(6, title, navigationTree);
		result.setPadding(new Insets(4));
		VBox.setVgrow(navigationTree, Priority.ALWAYS);
		return result;
	}

	private GridPane createPortfolioDetails() {
		GridPane grid = FormGridHelper.createDefaultGrid();
		FormGridHelper.setEqualGrowColumns(grid, 3);
		FormGridHelper.addFieldAbove(grid, getText("UI_STOCK_FIELD_PORTFOLIO"), portfolioNameValue, 0, 0);
		FormGridHelper.addFieldAbove(grid, getText("UI_STOCK_FIELD_BANK"), portfolioBankValue, 1, 0);
		FormGridHelper.addFieldAbove(grid, getText("UI_STOCK_FIELD_ACCOUNT_NUMBER"), portfolioNumberValue, 2, 0);
		FormGridHelper.addFieldAbove(grid, getText("UI_STOCK_FIELD_OPENED_AT"), portfolioOpenedValue, 0, 1);
		FormGridHelper.addFieldAbove(grid, getText("UI_STOCK_FIELD_SETTLEMENT_ACCOUNT"), portfolioSettlementValue, 1, 1, 2);
		configureTransactionButtons();
		grid.add(FormStyleUtils.createButtonBar(transactionNewButton, transactionEditButton,
				transactionDeleteButton), 0, 4, 3, 1);
		return grid;
	}

	private void configureTransactionButtons() {
		transactionNewButton.setText(getText("UI_BUTTON_NEW"));
		transactionEditButton.setText(getText("UI_BUTTON_EDIT"));
		transactionDeleteButton.setText(getText("UI_BUTTON_DELETE"));
		transactionNewButton.setOnAction(event -> openNewTransaction());
		transactionEditButton.setOnAction(event -> openSelectedTransaction());
		transactionDeleteButton.setOnAction(event -> deleteSelectedTransaction());
		transactionTable.getSelectionModel().selectedItemProperty().addListener(
				(observable, previous, selected) -> updateTransactionButtons());
		updateTransactionButtons();
	}

	private GridPane createSettlementDetails() {
		GridPane grid = FormGridHelper.createDefaultGrid();
		FormGridHelper.setEqualGrowColumns(grid, 3);
		FormGridHelper.addFieldAbove(grid, getText("UI_STOCK_FIELD_ACCOUNT"), settlementNameValue, 0, 0);
		FormGridHelper.addFieldAbove(grid, getText("UI_STOCK_FIELD_BANK"), settlementBankValue, 1, 0);
		FormGridHelper.addFieldAbove(grid, getText("UI_STOCK_FIELD_IBAN"), settlementIbanValue, 2, 0);
		FormGridHelper.addFieldAbove(grid, getText("UI_STOCK_FIELD_CURRENCY"), settlementCurrencyValue, 0, 1);
		FormGridHelper.addFieldAbove(grid, getText("UI_STOCK_FIELD_BALANCE"), settlementBalanceValue, 1, 1);
		settlementEditButton.setText(getText("UI_STOCK_ACTION_EDIT_SETTLEMENT_ACCOUNT"));
		settlementEditButton.setDisable(true);
		settlementEditButton.setOnAction(event -> openSettlementAccountEditor());
		grid.add(FormStyleUtils.createButtonBar(settlementEditButton), 0, 4, 3, 1);
		return grid;
	}

	private void configurePositionTable() {
		positionTable.getColumns().add(createSecurityLinkColumn(getText("UI_STOCK_COLUMN_SECURITY"),
				PositionSummary::securityName, PositionSummary::securityId, PositionSummary::currency, 180));
		positionTable.getColumns().add(TableColumnFactory.createFixedTextColumn(getText("UI_STOCK_COLUMN_ISIN"),
				PositionSummary::isin, 115));
		positionTable.getColumns().add(TableColumnFactory.createFixedTextColumn(getText("UI_STOCK_COLUMN_WKN"),
				PositionSummary::wkn, 85));
		positionTable.getColumns().add(createQuantityColumn(getText("UI_STOCK_COLUMN_QUANTITY"), PositionSummary::quantity));
		positionTable.getColumns().add(createAcquisitionPriceColumn());
		positionTable.getColumns().add(createNeutralAmountColumn(getText("UI_STOCK_COLUMN_ACQUISITION_VALUE"),
				PositionSummary::acquisitionValue));
		positionTable.getColumns().add(createPerformanceAmountColumn(getText("UI_STOCK_COLUMN_UNIT_PRICE"),
				PositionSummary::unitPrice));
		positionTable.getColumns().add(createPerformanceAmountColumn(getText("UI_STOCK_COLUMN_TOTAL_VALUE"),
				PositionSummary::totalValue));
		positionTable.getColumns().add(TableColumnFactory.createFixedTextColumn(getText("UI_STOCK_FIELD_CURRENCY"),
				position -> currencyName(position.currency()), 85));
		positionTable.getColumns().add(createPercentageColumn(getText("UI_STOCK_COLUMN_PERFORMANCE"),
				PositionSummary::performancePercent));
		positionTable.setEditable(false);
		positionTable.setRowFactory(table -> createPositionRow());
	}

	private TableRow<PositionSummary> createPositionRow() {
		TableRow<PositionSummary> row = positionTable.createDefaultRow();
		row.itemProperty().addListener((observable, previous, item) -> row.setContextMenu(item != null ? createPositionContextMenu(row) : null));
		return row;
	}

	private ContextMenu createPositionContextMenu(TableRow<PositionSummary> row) {
		MenuItem trade = new MenuItem(getText("UI_STOCK_ACTION_TRADE"));
		trade.setOnAction(event -> openTrade(row.getItem()));
		MenuItem income = new MenuItem(getText("UI_STOCK_ACTION_INCOME"));
		income.setOnAction(event -> openIncome(row.getItem()));
		MenuItem transfer = new MenuItem(getText("UI_STOCK_ACTION_TRANSFER_POSITION"));
		transfer.setOnAction(event -> openPositionTransfer(row.getItem()));
		return new ContextMenu(trade, income, transfer);
	}

	private void configureTransactionTable() {
		transactionTable.getColumns().add(TableColumnFactory.createCalendarDateColumn(getText("UI_STOCK_COLUMN_DATE"),
				TransactionSummary::date, 100));
		transactionTable.getColumns().add(TableColumnFactory.createFixedTextColumn(getText("UI_STOCK_COLUMN_TRANSACTION"),
				summary -> transactionName(summary.transactionType()), 190));
		transactionTable.getColumns().add(createSecurityLinkColumn(getText("UI_STOCK_COLUMN_SECURITY_NAME"),
				TransactionSummary::securityName, TransactionSummary::securityId, TransactionSummary::currency, 180));
		transactionTable.getColumns().add(TableColumnFactory.createFixedTextColumn(getText("UI_STOCK_COLUMN_ISIN"),
				TransactionSummary::isin, 115));
		transactionTable.getColumns().add(TableColumnFactory.createFixedTextColumn(getText("UI_STOCK_COLUMN_WKN"),
				TransactionSummary::wkn, 85));
		transactionTable.getColumns().add(createQuantityColumn(getText("UI_STOCK_COLUMN_QUANTITY"),
				TransactionSummary::quantity));
		transactionTable.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_COLUMN_PRICE"),
				TransactionSummary::unitPrice));
		transactionTable.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_COLUMN_TOTAL_PRICE"),
				TransactionSummary::totalPrice));
		transactionTable.getColumns().add(TableColumnFactory.createFixedTextColumn(getText("UI_STOCK_FIELD_CURRENCY"),
				summary -> currencyName(summary.currency()), 80));
		transactionTable.setRowFactory(table -> createTransactionRow());
	}

	private TableRow<TransactionSummary> createTransactionRow() {
		TableRow<TransactionSummary> row = transactionTable.createDefaultRow();
		row.itemProperty().addListener((observable, previous, item) -> {
			row.getStyleClass().remove(MUTED_TRANSACTION_STYLE);
			if (item != null && item.transactionType() == StockTransactionType.RECONCILIATION_ADJUSTMENT) {
				row.getStyleClass().add(MUTED_TRANSACTION_STYLE);
			}
		});
		row.setOnMouseClicked(event -> {
			if (event.getClickCount() == 2 && !row.isEmpty() && canEdit(row.getItem())
					&& !isHyperlinkTarget(event.getTarget())) {
				openTransaction(row.getItem());
			}
		});
		return row;
	}

	private static boolean isHyperlinkTarget(Object target) {
		Node node = target instanceof Node ? (Node) target : null;
		while (node != null) {
			if (node instanceof Hyperlink) {
				return true;
			}
			node = node.getParent();
		}
		return false;
	}

	private void configureAccountTransactionTable() {
		accountTransactionTable.getColumns().add(TableColumnFactory.createCalendarDateColumn(getText("UI_STOCK_COLUMN_DATE"),
				Booking::getDate, 100));
		accountTransactionTable.getColumns().add(TableColumnFactory.createTextColumn(getText("UI_PANEL_PURPOSE"),
				Booking::getPurpose, 180, 320));
		accountTransactionTable.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_COLUMN_AMOUNT"),
				Booking::getAmount));
		accountTransactionTable.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_FIELD_BALANCE"),
				Booking::getBalance));
	}

	private <T> TableColumn<T, String> createSecurityLinkColumn(String title, Function<T, String> nameProvider,
			ToIntFunction<T> idProvider, Function<T, Currency> currencyProvider, double width) {
		TableColumn<T, String> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new SimpleStringProperty(nameProvider.apply(data.getValue())));
		column.setCellFactory(ignored -> new TableCell<>() {
			private final Hyperlink link = new Hyperlink();

			{
				link.setOnAction(event -> {
					T row = getTableRow() != null ? getTableRow().getItem() : null;
					if (row != null) {
						openPrices(idProvider.applyAsInt(row), nameProvider.apply(row), currencyProvider.apply(row));
					}
				});
			}

			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				link.setText(empty ? null : item);
				setGraphic(empty ? null : link);
				setText(null);
				setAlignment(Pos.CENTER_LEFT);
			}
		});
		FxTableUtils.setPreferredWidth(column, 130, width);
		return column;
	}

	private TableColumn<PositionSummary, BigDecimal> createAcquisitionPriceColumn() {
		TableColumn<PositionSummary, BigDecimal> column = createNeutralAmountColumn(
				getText("UI_STOCK_COLUMN_ACQUISITION_PRICE"), PositionSummary::acquisitionPrice);
		column.setEditable(false);
		FxTableUtils.setPreferredWidth(column, 95, 120);
		return column;
	}

	private static TableColumn<PositionSummary, BigDecimal> createNeutralAmountColumn(String title,
			Function<PositionSummary, BigDecimal> valueProvider) {
		return createPositionAmountColumn(title, valueProvider, position -> null);
	}

	private static TableColumn<PositionSummary, BigDecimal> createPerformanceAmountColumn(String title,
			Function<PositionSummary, BigDecimal> valueProvider) {
		return createPositionAmountColumn(title, valueProvider, PositionSummary::performancePercent);
	}

	private static TableColumn<PositionSummary, BigDecimal> createPositionAmountColumn(String title,
			Function<PositionSummary, BigDecimal> valueProvider,
			Function<PositionSummary, BigDecimal> performanceProvider) {
		TableColumn<PositionSummary, BigDecimal> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(valueProvider.apply(data.getValue())));
		column.setCellFactory(ignored -> new TableCell<>() {
			private final DecimalFormat format = FxTableUtils.createGermanDecimalFormat();

			@Override
			protected void updateItem(BigDecimal item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : format.format(item));
				setAlignment(Pos.CENTER_RIGHT);
				PositionSummary position = rowItem(this);
				applyPerformanceStyle(this, position != null ? performanceProvider.apply(position) : null);
			}
		});
		return column;
	}

	private static <T> T rowItem(TableCell<T, ?> cell) {
		T item = cell.getTableRow() != null ? cell.getTableRow().getItem() : null;
		if (item == null && cell.getTableView() != null && cell.getIndex() >= 0
				&& cell.getIndex() < cell.getTableView().getItems().size()) {
			item = cell.getTableView().getItems().get(cell.getIndex());
		}
		return item;
	}

	private static <T> TableColumn<T, BigDecimal> createPercentageColumn(String title,
			Function<T, BigDecimal> valueProvider) {
		TableColumn<T, BigDecimal> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(valueProvider.apply(data.getValue())));
		column.setCellFactory(ignored -> new TableCell<>() {
			private final DecimalFormat format = createDecimalFormat("#,##0.00;-#,##0.00");

			@Override
			protected void updateItem(BigDecimal item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : format.format(item) + " %");
				setAlignment(Pos.CENTER_RIGHT);
				applyPerformanceStyle(this, empty ? null : item);
			}
		});
		FxTableUtils.setPreferredWidth(column, 100, 125);
		return column;
	}

	static void applyPerformanceStyle(TableCell<?, ?> cell, BigDecimal performance) {
		cell.getStyleClass().removeAll(AMOUNT_POSITIVE, AMOUNT_NEGATIVE, AMOUNT_NEUTRAL);
		if (performance != null && performance.signum() > 0) {
			cell.getStyleClass().add(AMOUNT_POSITIVE);
		} else if (performance != null && performance.signum() < 0) {
			cell.getStyleClass().add(AMOUNT_NEGATIVE);
		} else {
			cell.getStyleClass().add(AMOUNT_NEUTRAL);
		}
	}

	private static <T> TableColumn<T, BigDecimal> createQuantityColumn(String title,
			Function<T, BigDecimal> valueProvider) {
		TableColumn<T, BigDecimal> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(valueProvider.apply(data.getValue())));
		column.setCellFactory(ignored -> new TableCell<>() {
			private final DecimalFormat format = createQuantityFormat();

			@Override
			protected void updateItem(BigDecimal item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : format.format(item));
				setAlignment(Pos.CENTER_RIGHT);
			}
		});
		FxTableUtils.setPreferredWidth(column, 80, 105);
		return column;
	}

	static DecimalFormat createQuantityFormat() {
		return createDecimalFormat("#,##0.#########;-#,##0.#########");
	}

	private static DecimalFormat createDecimalFormat(String pattern) {
		DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.GERMAN);
		symbols.setGroupingSeparator('.');
		return new DecimalFormat(pattern, symbols);
	}

	private VBox createTableSection(String titleKey, Node table) {
		Label title = new Label(getText(titleKey));
		title.getStyleClass().add("gbanking-form-section-title");
		VBox result = new VBox(4, title, table);
		result.setPadding(new Insets(4));
		VBox.setVgrow(table, Priority.ALWAYS);
		return result;
	}

	private Tab createTab(String titleKey, Node content) {
		Tab tab = new Tab(getText(titleKey), content);
		tab.setClosable(false);
		return tab;
	}

	private void reloadPortfolios() {
		int selectedId = selectedPortfolio != null ? selectedPortfolio.portfolioId() : 0;
		portfolios = service.getPortfolios();
		selectedPortfolioIds.retainAll(portfolios.stream().map(PortfolioSummary::portfolioId).toList());
		TreeItem<NavigationEntry> root = new TreeItem<>();
		for (PortfolioSummary portfolio : portfolios) {
			TreeItem<NavigationEntry> portfolioItem = new TreeItem<>(createPortfolioEntry(portfolio));
			portfolioItem.setExpanded(true);
			if (portfolio.settlementAccountId() > 0) {
				portfolioItem.getChildren().add(new TreeItem<>(NavigationEntry.account(portfolio)));
			}
			root.getChildren().add(portfolioItem);
		}
		navigationTree.setRoot(root);
		updateSelectAllState();
		TreeItem<NavigationEntry> itemToSelect = findPortfolioItem(root, selectedId);
		if (itemToSelect == null && !root.getChildren().isEmpty()) {
			itemToSelect = root.getChildren().get(0);
		}
		if (itemToSelect != null) {
			navigationTree.getSelectionModel().select(itemToSelect);
		} else {
			selectPortfolio(null);
		}
	}

	private NavigationEntry createPortfolioEntry(PortfolioSummary portfolio) {
		BooleanProperty selected = new SimpleBooleanProperty(selectedPortfolioIds.contains(portfolio.portfolioId()));
		selected.addListener((observable, previous, current) -> {
			if (Boolean.TRUE.equals(current)) {
				selectedPortfolioIds.add(portfolio.portfolioId());
			} else {
				selectedPortfolioIds.remove(portfolio.portfolioId());
			}
			updateSelectAllState();
		});
		return NavigationEntry.portfolio(portfolio, selected);
	}

	private void selectAllPortfolios() {
		List<NavigationEntry> entries = portfolioEntries();
		boolean select = entries.stream().anyMatch(entry -> !entry.selectedProperty().get());
		entries.forEach(entry -> entry.selectedProperty().set(select));
		navigationTree.refresh();
		updateSelectAllState();
	}

	private void updateSelectAllState() {
		List<NavigationEntry> entries = portfolioEntries();
		long selectedCount = entries.stream().filter(entry -> entry.selectedProperty().get()).count();
		portfolioSelectAll.setDisable(entries.isEmpty());
		portfolioSelectAll.setIndeterminate(selectedCount > 0 && selectedCount < entries.size());
		portfolioSelectAll.setSelected(!entries.isEmpty() && selectedCount == entries.size());
	}

	private List<NavigationEntry> portfolioEntries() {
		TreeItem<NavigationEntry> root = navigationTree.getRoot();
		return root == null ? List.of() : root.getChildren().stream().map(TreeItem::getValue).toList();
	}

	public List<BankAccount> getCheckedAccounts() {
		List<BankAccount> result = new ArrayList<>();
		for (PortfolioSummary portfolio : portfolios) {
			if (selectedPortfolioIds.contains(portfolio.portfolioId())) {
				BankAccount account = service.getPortfolioAccount(portfolio);
				if (account != null) {
					result.add(account);
				}
			}
		}
		return List.copyOf(result);
	}

	private TreeItem<NavigationEntry> findPortfolioItem(TreeItem<NavigationEntry> root, int portfolioId) {
		return root.getChildren().stream()
				.filter(item -> item.getValue().portfolio().portfolioId() == portfolioId)
				.findFirst().orElse(null);
	}

	private void selectPortfolio(PortfolioSummary portfolio) {
		selectedPortfolio = portfolio;
		updateDetails();
		if (portfolio == null) {
			positionTable.getItems().clear();
			transactionTable.getItems().clear();
			accountTransactionTable.getItems().clear();
			return;
		}
		positionTable.setItems(FXCollections.observableArrayList(service.getPositions(portfolio.portfolioId())));
		transactionTable.setItems(FXCollections.observableArrayList(service.getTransactions(portfolio.portfolioId())));
		accountTransactionTable.setItems(FXCollections.observableArrayList(
				service.getAccountTransactions(portfolio.settlementAccountId())));
	}

	private void updateDetails() {
		settlementEditButton.setDisable(selectedPortfolio == null);
		portfolioNameValue.setText(selectedPortfolio != null ? selectedPortfolio.displayName() : "");
		portfolioBankValue.setText(selectedPortfolio != null ? selectedPortfolio.bankName() : "");
		portfolioNumberValue.setText(selectedPortfolio != null ? selectedPortfolio.accountNumber() : "");
		portfolioOpenedValue.setText(selectedPortfolio != null ? DateFormatUtils.formatLong(selectedPortfolio.openedAt()) : "");
		portfolioSettlementValue.setText(selectedPortfolio != null ? selectedPortfolio.settlementAccountName() : "");
		settlementNameValue.setText(selectedPortfolio != null ? selectedPortfolio.settlementAccountName() : "");
		settlementBankValue.setText(selectedPortfolio != null ? selectedPortfolio.settlementBankName() : "");
		settlementIbanValue.setText(selectedPortfolio != null ? selectedPortfolio.settlementIban() : "");
		settlementCurrencyValue.setText(selectedPortfolio != null ? currencyName(selectedPortfolio.settlementCurrency()) : "");
		settlementBalanceValue.setText(selectedPortfolio != null
				? formatAmount(selectedPortfolio.settlementBalance(), selectedPortfolio.settlementCurrency()) : "");
		updateTransactionButtons();
	}

	private void updateTableMode() {
		boolean portfolioMode = detailTabs.getSelectionModel().getSelectedItem() != settlementAccountTab;
		portfolioTables.setVisible(portfolioMode);
		portfolioTables.setManaged(portfolioMode);
		accountTransactions.setVisible(!portfolioMode);
		accountTransactions.setManaged(!portfolioMode);
	}

	private void refreshSelectedPortfolio() {
		reloadPortfolios();
	}

	private void updateTransactionButtons() {
		TransactionSummary selected = transactionTable.getSelectionModel().getSelectedItem();
		transactionNewButton.setDisable(selectedPortfolio == null);
		transactionEditButton.setDisable(!canEdit(selected));
		transactionDeleteButton.setDisable(selected == null);
	}

	private static boolean canEdit(TransactionSummary transaction) {
		return transaction != null && transaction.editable()
				&& (transaction.transactionType() == StockTransactionType.BUY
						|| transaction.transactionType() == StockTransactionType.SELL);
	}

	private void openNewTransaction() {
		if (selectedPortfolio == null) {
			return;
		}
		List<SecuritySummary> securities = service.getTransactionSecurities();
		if (securities.isEmpty()) {
			DialogWindowSupport.showAlert(getOwnerWindow(), Alert.AlertType.INFORMATION,
					getText("UI_STOCK_TRANSACTION_NO_SECURITIES"));
			return;
		}
		PositionSummary selectedPosition = positionTable.getSelectionModel().getSelectedItem();
		openTransactionDialog(securities, null, service.getReconciliationPrefills(selectedPortfolio.portfolioId()),
				selectedPosition != null ? selectedPosition.securityId() : null);
	}

	private void openSelectedTransaction() {
		TransactionSummary selected = transactionTable.getSelectionModel().getSelectedItem();
		if (canEdit(selected)) {
			openTransaction(selected);
		}
	}

	private void openTransaction(TransactionSummary transaction) {
		try {
			openTransactionDialog(service.getTransactionSecurities(),
					service.getTransactionEditData(transaction.transactionId()), List.of(), null);
		} catch (RuntimeException exception) {
			DialogWindowSupport.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, exception.getMessage());
		}
	}

	private void openTransactionDialog(List<SecuritySummary> securities,
			StockPortfolioService.TransactionEditData editData, List<TransactionPrefill> prefills,
			Integer preferredSecurityId) {
		new StockTransactionDialog(getOwnerWindow(), selectedPortfolio, securities, editData, prefills,
				preferredSecurityId,
				request -> service.saveTransaction(selectedPortfolio, request), this::refreshSelectedPortfolio).show();
	}

	private void deleteSelectedTransaction() {
		TransactionSummary selected = transactionTable.getSelectionModel().getSelectedItem();
		if (selected == null) {
			return;
		}
		String textKey = selected.externalSource()
				? "UI_STOCK_TRANSACTION_DELETE_IMPORTED_TEXT" : "UI_STOCK_TRANSACTION_DELETE_MANUAL_TEXT";
		if (!DialogWindowSupport.showConfirmation(getOwnerWindow(), Alert.AlertType.CONFIRMATION,
				getText("UI_STOCK_TRANSACTION_DELETE_TITLE"), getText("UI_STOCK_TRANSACTION_DELETE_HEADER"),
				getText(textKey), ButtonType.OK, ButtonType.CANCEL)) {
			return;
		}
		try {
			service.deleteTransaction(selected.transactionId());
			refreshSelectedPortfolio();
		} catch (RuntimeException exception) {
			DialogWindowSupport.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, exception.getMessage());
		}
	}

	private void openPrices(int securityId, String securityName, Currency currency) {
		new SecurityPriceDialog(getOwnerWindow(), securityId, securityName, currency, () -> service.getPrices(securityId),
				(correctedId, date, price, priceCurrency, confirmed) -> service.savePrice(
						securityId, correctedId, date, price, priceCurrency, confirmed),
				(priceId, confirmed) -> service.deletePrice(securityId, priceId, confirmed),
				this::refreshSelectedPortfolio).show();
	}

	private void openTrade(PositionSummary position) {
		if (position != null && selectedPortfolio != null) {
			TransactionPrefill prefill = service.getReconciliationPrefills(selectedPortfolio.portfolioId()).stream()
					.filter(candidate -> candidate.securityId() == position.securityId()
							&& candidate.quantityType() == position.quantityType())
					.findFirst().orElse(null);
			new StockTradeDialog(getOwnerWindow(), selectedPortfolio, position, prefill,
					request -> service.recordTrade(selectedPortfolio, position, request), this::refreshSelectedPortfolio).show();
		}
	}

	private void openIncome(PositionSummary position) {
		if (position != null && selectedPortfolio != null) {
			new StockIncomeDialog(getOwnerWindow(), selectedPortfolio, position,
					request -> service.recordIncome(selectedPortfolio, position, request), this::refreshSelectedPortfolio).show();
		}
	}

	private void openPositionTransfer(PositionSummary position) {
		if (position == null || selectedPortfolio == null) {
			return;
		}
		List<PortfolioSummary> targets = transferTargets(selectedPortfolio);
		if (targets.isEmpty()) {
			showNoTransferTarget();
			return;
		}
		new StockPositionTransferDialog(getOwnerWindow(), position, targets,
				(target, quantity, date) -> service.transferPosition(selectedPortfolio, target, position, quantity, date),
				this::refreshSelectedPortfolio).show();
	}

	private void openPortfolioTransfer(PortfolioSummary portfolio) {
		List<PortfolioSummary> targets = transferTargets(portfolio);
		if (targets.isEmpty()) {
			showNoTransferTarget();
			return;
		}
		new StockPortfolioTransferDialog(getOwnerWindow(), portfolio, targets,
				(target, date, exchangeRate) -> service.transferPortfolio(portfolio, target, date, exchangeRate),
				this::refreshSelectedPortfolio).show();
	}

	private void openSettlementAccountEditor() {
		if (selectedPortfolio == null) {
			return;
		}
		List<BankAccount> candidates = service.getSettlementAccountCandidates();
		if (candidates.isEmpty()) {
			DialogWindowSupport.showAlert(getOwnerWindow(), Alert.AlertType.INFORMATION,
					getText("ALERT_STOCK_NO_SETTLEMENT_ACCOUNT"));
			return;
		}
		new StockSettlementAccountDialog(getOwnerWindow(), selectedPortfolio, candidates,
				(account, validFrom) -> service.changeSettlementAccount(selectedPortfolio, account, validFrom),
				this::refreshSelectedPortfolio).show();
	}

	private List<PortfolioSummary> transferTargets(PortfolioSummary source) {
		return portfolios.stream().filter(candidate -> candidate.portfolioId() != source.portfolioId()).toList();
	}

	private void showNoTransferTarget() {
		DialogWindowSupport.showAlert(getOwnerWindow(), Alert.AlertType.INFORMATION, getText("UI_STOCK_NO_TRANSFER_TARGET"));
	}

	private String transactionName(StockTransactionType type) {
		return type != null ? type.toString() : "";
	}

	private static String currencyName(Currency currency) {
		return currency != null ? currency.name() : "";
	}

	private static String formatAmount(BigDecimal amount, Currency currency) {
		if (amount == null) {
			return "";
		}
		DecimalFormat format = FxTableUtils.createGermanDecimalFormat();
		return format.format(amount) + (currency != null ? " " + currency.name() : "");
	}

	private record NavigationEntry(PortfolioSummary portfolio, boolean accountNode, BooleanProperty selectedProperty) {

		private static NavigationEntry portfolio(PortfolioSummary portfolio, BooleanProperty selectedProperty) {
			return new NavigationEntry(portfolio, false, selectedProperty);
		}

		private static NavigationEntry account(PortfolioSummary portfolio) {
			return new NavigationEntry(portfolio, true, new SimpleBooleanProperty(false));
		}

		private String displayName() {
			return accountNode ? portfolio.settlementAccountName() : portfolio.displayName();
		}
	}
}
