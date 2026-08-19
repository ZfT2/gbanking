package de.zft2.gbanking.gui.panel.overview;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.gui.component.GBankingTableView;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.model.PendingStatementReceipts;
import de.zft2.gbanking.gui.model.SelectableOpenAction;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import de.zft2.gbanking.gui.panel.account.AccountListScope;
import de.zft2.gbanking.gui.panel.layout.MasterContentPane;
import de.zft2.gbanking.gui.util.FxTableUtils;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.service.account.AccountStatement;
import de.zft2.gbanking.service.account.AccountStatementService;
import de.zft2.gbanking.service.action.OpenAccountAction;
import de.zft2.gbanking.service.action.OpenActionsSelection;
import de.zft2.gbanking.service.action.OpenBankingAction;
import de.zft2.gbanking.service.action.OpenTransferAction;
import de.zft2.gbanking.service.BankingCapabilityService;
import de.zft2.gbanking.service.moneytransfer.MoneyTransferService;
import de.zft2.gbanking.service.ServiceRegistry;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;

public class OpenActionsOverviewPanel extends OverviewBasePanel {

	private static final Logger log = LogManager.getLogger(OpenActionsOverviewPanel.class);

	private static final double ACCOUNT_DIVIDER = 0.22;
	private static final double ACTION_TABLE_HEIGHT = 200;
	private static final String UI_ACCOUNT_NAME = "UI_TABLE_ACCOUNT_NAME";

	private final ObservableList<SelectableOpenAction<OpenTransferAction>> transferActions = FXCollections.observableArrayList();
	private final ObservableList<SelectableOpenAction<OpenTransferAction>> scheduledTransferActions = FXCollections.observableArrayList();
	private final ObservableList<SelectableOpenAction<OpenTransferAction>> standingOrderActions = FXCollections.observableArrayList();
	private final ObservableList<SelectableOpenAction<OpenAccountAction<Void>>> scheduledTransferInventoryActions = FXCollections.observableArrayList();
	private final ObservableList<SelectableOpenAction<OpenAccountAction<Void>>> standingOrderInventoryActions = FXCollections.observableArrayList();
	private final ObservableList<SelectableOpenAction<OpenAccountAction<Void>>> accountStatementActions = FXCollections.observableArrayList();
	private final ObservableList<SelectableOpenAction<OpenAccountAction<PendingStatementReceipts>>> statementReceiptActions = FXCollections
			.observableArrayList();

	private AccountListPanel accountListPanel;

	private final BankingCapabilityService bankingCapabilityService;
	private final MoneyTransferService moneyTransferService;
	private final AccountStatementService accountStatementService;

	public OpenActionsOverviewPanel() {
		this(ServiceRegistry.getService(BankingCapabilityService.class), ServiceRegistry.getService(MoneyTransferService.class),
				ServiceRegistry.getService(AccountStatementService.class));
	}

	OpenActionsOverviewPanel(BankingCapabilityService bankingCapabilityService, MoneyTransferService moneyTransferService,
			AccountStatementService accountStatementService) {
		this.bankingCapabilityService = bankingCapabilityService;
		this.moneyTransferService = moneyTransferService;
		this.accountStatementService = accountStatementService;
	}

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.OPEN_ACTIONS);
		log.info("Creating OpenActionsOverviewPanel");

		accountListPanel = new AccountListPanel(this, AccountListScope.ONLINE_ONLY, "UI_PANEL_OPEN_ACTIONS_ACCOUNT_UPDATES");
		GridPane actionPanels = createActionPanels();
		ScrollPane actionScrollPane = new ScrollPane(actionPanels);
		actionScrollPane.setFitToWidth(true);
		actionScrollPane.setPannable(true);
		actionScrollPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		MasterContentPane mainPane = new MasterContentPane(accountListPanel, actionScrollPane, "openActions.main", ACCOUNT_DIVIDER);
		setOverviewContent("UI_PANEL_OPEN_ACTIONS", mainPane, show);
	}

	private GridPane createActionPanels() {
		GridPane panels = new GridPane();
		panels.setHgap(8);
		panels.setVgap(8);
		panels.setPadding(new Insets(0, 6, 0, 0));
		panels.setMaxWidth(Double.MAX_VALUE);

		ColumnConstraints leftColumn = createPanelColumn();
		ColumnConstraints rightColumn = createPanelColumn();
		panels.getColumnConstraints().setAll(leftColumn, rightColumn);

		panels.add(createActionPanel("UI_PANEL_OPEN_ACTIONS_TRANSFERS", transferActions, createTransferColumns(transferActions), "openActions.transfers"), 0, 0,
				2, 1);
		panels.add(createActionPanel("UI_PANEL_OPEN_ACTIONS_SCHEDULED_INVENTORY", scheduledTransferInventoryActions,
				createAccountActionColumns(scheduledTransferInventoryActions), "openActions.scheduledInventory"), 0, 1);
		panels.add(createActionPanel("UI_PANEL_OPEN_ACTIONS_SCHEDULED_ORDERS", scheduledTransferActions, createOrderColumns(scheduledTransferActions),
				"openActions.scheduledOrders"), 1, 1);
		panels.add(createActionPanel("UI_PANEL_OPEN_ACTIONS_STANDING_INVENTORY", standingOrderInventoryActions,
				createAccountActionColumns(standingOrderInventoryActions), "openActions.standingInventory"), 0, 2);
		panels.add(createActionPanel("UI_PANEL_OPEN_ACTIONS_STANDING_ORDERS", standingOrderActions, createOrderColumns(standingOrderActions),
				"openActions.standingOrders"), 1, 2);
		panels.add(createActionPanel("UI_PANEL_OPEN_ACTIONS_STATEMENTS", accountStatementActions, createAccountActionColumns(accountStatementActions),
				"openActions.statements"), 0, 3);
		panels.add(
				createActionPanel("UI_PANEL_OPEN_ACTIONS_STATEMENT_RECEIPTS", statementReceiptActions, createReceiptColumns(), "openActions.statementReceipts"),
				1, 3);
		return panels;
	}

	private static ColumnConstraints createPanelColumn() {
		ColumnConstraints column = new ColumnConstraints();
		column.setPercentWidth(50);
		return column;
	}

	private <T> TitledPane createActionPanel(String titleKey, ObservableList<T> rows, List<TableColumn<T, ?>> columns, String layoutKey) {
		GBankingTableView<T> table = new GBankingTableView<>();
		table.setItems(rows);
		table.getColumns().setAll(columns);
		table.setEditable(true);
		table.setMinHeight(ACTION_TABLE_HEIGHT);
		table.setPrefHeight(ACTION_TABLE_HEIGHT);
		table.setMaxSize(Double.MAX_VALUE, ACTION_TABLE_HEIGHT);
		GuiLayoutState.configureTable(table, layoutKey);

		TitledPane panel = new TitledPane(getText(titleKey), table);
		panel.setCollapsible(false);
		panel.setMaxWidth(Double.MAX_VALUE);
		return panel;
	}

	private List<TableColumn<SelectableOpenAction<OpenTransferAction>, ?>> createTransferColumns(
			ObservableList<SelectableOpenAction<OpenTransferAction>> actions) {
		List<TableColumn<SelectableOpenAction<OpenTransferAction>, ?>> columns = new ArrayList<>(createOrderColumns(actions, 140, 180));
		TableColumn<SelectableOpenAction<OpenTransferAction>, String> typeColumn = TableColumnFactory
				.createFixedTextColumn(getText("UI_TABLE_OPEN_ACTION_TYPE"), row -> text(row.getValue().moneyTransfer().getOrderType()), 130);
		TableColumn<SelectableOpenAction<OpenTransferAction>, String> purposeColumn = TableColumnFactory.createTextColumn(getText("UI_TABLE_PURPOSE"),
				row -> row.getValue().moneyTransfer().getPurpose(), 140, 240);
		columns.add(2, typeColumn);
		columns.add(purposeColumn);
		return columns;
	}

	private List<TableColumn<SelectableOpenAction<OpenTransferAction>, ?>> createOrderColumns(
			ObservableList<SelectableOpenAction<OpenTransferAction>> actions) {
		return createOrderColumns(actions, 120, 160);
	}

	private List<TableColumn<SelectableOpenAction<OpenTransferAction>, ?>> createOrderColumns(ObservableList<SelectableOpenAction<OpenTransferAction>> actions,
			double textMinWidth, double textPrefWidth) {
		TableColumn<SelectableOpenAction<OpenTransferAction>, Boolean> selectedColumn = createSelectionColumn(actions);
		TableColumn<SelectableOpenAction<OpenTransferAction>, String> accountColumn = TableColumnFactory.createTextColumn(getText(UI_ACCOUNT_NAME),
				row -> row.getAccount().getAccountName(), textMinWidth, textPrefWidth);
		TableColumn<SelectableOpenAction<OpenTransferAction>, String> recipientColumn = TableColumnFactory.createTextColumn(getText("UI_TABLE_RECIPIENT"),
				row -> recipientName(row.getValue().moneyTransfer()), textMinWidth, textPrefWidth);
		TableColumn<SelectableOpenAction<OpenTransferAction>, java.math.BigDecimal> amountColumn = TableColumnFactory
				.createAmountColumn(getText("UI_TABLE_AMOUNT"), row -> row.getValue().moneyTransfer().getAmount());
		FxTableUtils.setFixedWidth(amountColumn, 95);
		TableColumn<SelectableOpenAction<OpenTransferAction>, LocalDate> dateColumn = TableColumnFactory.createCalendarDateColumn(getText("UI_TABLE_DATE"),
				row -> row.getValue().moneyTransfer().getExecutionDate(), 95);
		TableColumn<SelectableOpenAction<OpenTransferAction>, String> statusColumn = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_STATUS"),
				row -> text(row.getValue().moneyTransfer().getMoneytransferStatus()), 100);
		return List.of(selectedColumn, accountColumn, recipientColumn, amountColumn, dateColumn, statusColumn);
	}

	private <T> List<TableColumn<SelectableOpenAction<OpenAccountAction<T>>, ?>> createAccountActionColumns(
			ObservableList<SelectableOpenAction<OpenAccountAction<T>>> actions) {
		TableColumn<SelectableOpenAction<OpenAccountAction<T>>, Boolean> selectedColumn = createSelectionColumn(actions);
		TableColumn<SelectableOpenAction<OpenAccountAction<T>>, String> accountColumn = TableColumnFactory.createTextColumn(getText(UI_ACCOUNT_NAME),
				row -> row.getAccount().getAccountName(), 170, 220);
		TableColumn<SelectableOpenAction<OpenAccountAction<T>>, String> ibanColumn = TableColumnFactory.createTextColumn(getText("UI_TABLE_IBAN"),
				row -> row.getAccount().getIban(), 190, 230);
		TableColumn<SelectableOpenAction<OpenAccountAction<T>>, String> bankColumn = TableColumnFactory.createTextColumn(getText("UI_TABLE_BANK"),
				row -> row.getAccount().getBankName(), 140, 180);
		TableColumn<SelectableOpenAction<OpenAccountAction<T>>, LocalDate> updatedColumn = TableColumnFactory
				.createUpdatedAtColumn(getText("UI_TABLE_UPDATED_AT"), row -> row.getAccount().getUpdatedAt(), 100);
		return List.of(selectedColumn, accountColumn, ibanColumn, bankColumn, updatedColumn);
	}

	private List<TableColumn<SelectableOpenAction<OpenAccountAction<PendingStatementReceipts>>, ?>> createReceiptColumns() {
		TableColumn<SelectableOpenAction<OpenAccountAction<PendingStatementReceipts>>, Boolean> selectedColumn = createSelectionColumn(statementReceiptActions);
		TableColumn<SelectableOpenAction<OpenAccountAction<PendingStatementReceipts>>, String> accountColumn = TableColumnFactory
				.createTextColumn(getText(UI_ACCOUNT_NAME), row -> row.getAccount().getAccountName(), 170, 220);
		TableColumn<SelectableOpenAction<OpenAccountAction<PendingStatementReceipts>>, Integer> countColumn = new TableColumn<>(
				getText("UI_TABLE_OPEN_ACTION_RECEIPT_COUNT"));
		countColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getValue().details().count()));
		FxTableUtils.setFixedWidth(countColumn, 100);
		TableColumn<SelectableOpenAction<OpenAccountAction<PendingStatementReceipts>>, LocalDate> dateColumn = TableColumnFactory
				.createCalendarDateColumn(getText("UI_TABLE_OPEN_ACTION_LATEST_STATEMENT"), row -> row.getValue().details().latestStatementDate(), 130);
		TableColumn<SelectableOpenAction<OpenAccountAction<PendingStatementReceipts>>, String> ibanColumn = TableColumnFactory
				.createTextColumn(getText("UI_TABLE_IBAN"), row -> row.getAccount().getIban(), 190, 230);
		return List.of(selectedColumn, accountColumn, countColumn, dateColumn, ibanColumn);
	}

	private <T extends OpenBankingAction> TableColumn<SelectableOpenAction<T>, Boolean> createSelectionColumn(ObservableList<SelectableOpenAction<T>> actions) {
		return FxTableUtils.createSelectAllSelectionColumn(getText("UI_TABLE_SELECT_ALL"), actions, row -> row.isSelected(),
				(row, selected) -> row.setSelected(selected));
	}

	@Override
	public void refreshOnShow() {
		if (accountListPanel == null) {
			return;
		}
		accountListPanel.reload();
		reloadActionTables();
	}

	private void reloadActionTables() {
		List<BankAccount> onlineAccounts = List.copyOf(accountListPanel.getModelAccount().getAccounts());
		reloadTransferActions();
		scheduledTransferInventoryActions
				.setAll(loadAccountActions(onlineAccounts, account -> bankingCapabilityService.supportsOrderInventory(account, OrderType.SCHEDULED_TRANSFER)));
		standingOrderInventoryActions
				.setAll(loadAccountActions(onlineAccounts, account -> bankingCapabilityService.supportsOrderInventory(account, OrderType.STANDING_ORDER)));
		accountStatementActions.setAll(loadAccountActions(onlineAccounts, account -> bankingCapabilityService.supportsAccountStatements(account)));
		statementReceiptActions.setAll(loadReceiptActions(onlineAccounts));
	}

	private void reloadTransferActions() {
		transferActions.clear();
		scheduledTransferActions.clear();
		standingOrderActions.clear();
		for (MoneyTransfer transfer : moneyTransferService.retrieveOpenTransfers()) {
			BankAccount account = moneyTransferService.getAccountForOpenMoneytransfers(transfer.getAccountId());
			if (account == null) {
				continue;
			}
			ObservableList<SelectableOpenAction<OpenTransferAction>> targetActions = transferActions;
			OrderType orderType = transfer.getOrderType();
			if (orderType == OrderType.SCHEDULED_TRANSFER) {
				targetActions = scheduledTransferActions;
			} else if (orderType == OrderType.STANDING_ORDER) {
				targetActions = standingOrderActions;
			}
			targetActions.add(new SelectableOpenAction<>(new OpenTransferAction(account, transfer)));
		}
	}

	private List<SelectableOpenAction<OpenAccountAction<Void>>> loadAccountActions(List<BankAccount> accounts, Predicate<BankAccount> supported) {
		return accounts.stream().filter(supported).map(account -> new SelectableOpenAction<>(new OpenAccountAction<Void>(account, null))).toList();
	}

	private List<SelectableOpenAction<OpenAccountAction<PendingStatementReceipts>>> loadReceiptActions(List<BankAccount> accounts) {
		List<SelectableOpenAction<OpenAccountAction<PendingStatementReceipts>>> actions = new ArrayList<>();
		for (BankAccount account : accounts) {
			List<AccountStatement> pendingReceipts = accountStatementService.listAccountStatements(account).stream()
					.filter(statement -> statement.receiptAvailable() && !statement.acknowledged()).toList();
			if (!pendingReceipts.isEmpty()) {
				LocalDate latestDate = pendingReceipts.stream().map(statement -> statement.statementDate()).filter(date -> date != null)
						.max(Comparator.naturalOrder()).orElse(null);
				PendingStatementReceipts receiptDetails = new PendingStatementReceipts(pendingReceipts.size(), latestDate);
				actions.add(new SelectableOpenAction<>(new OpenAccountAction<>(account, receiptDetails)));
			}
		}
		return actions;
	}

	public OpenActionsSelection getSelectedActions() {
		List<OpenTransferAction> selectedTransfers = new ArrayList<>(selectedValues(transferActions));
		selectedTransfers.addAll(selectedValues(scheduledTransferActions));
		selectedTransfers.addAll(selectedValues(standingOrderActions));
		return new OpenActionsSelection(accountListPanel.getModelAccount().getCheckedAccounts(), selectedTransfers,
				selectedAccounts(scheduledTransferInventoryActions), selectedAccounts(standingOrderInventoryActions), selectedAccounts(accountStatementActions),
				selectedAccounts(statementReceiptActions));
	}

	private <T extends OpenBankingAction> List<T> selectedValues(List<SelectableOpenAction<T>> actions) {
		return actions.stream().filter(action -> action.isSelected()).map(action -> action.getValue()).toList();
	}

	private List<BankAccount> selectedAccounts(List<? extends SelectableOpenAction<?>> actions) {
		return actions.stream().filter(action -> action.isSelected()).map(action -> action.getAccount()).toList();
	}

	private static String recipientName(MoneyTransfer transfer) {
		return transfer.getRecipient() != null ? transfer.getRecipient().getName() : "";
	}

	private static String text(Object value) {
		return value != null ? value.toString() : "";
	}

}
