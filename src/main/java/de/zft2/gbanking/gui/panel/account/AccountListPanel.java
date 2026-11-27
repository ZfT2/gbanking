package de.zft2.gbanking.gui.panel.account;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.gui.GuiContext;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.enu.AccountGrouping;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.model.AccountTableModel;
import de.zft2.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.zft2.gbanking.gui.util.BookingFileActionSupport;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class AccountListPanel extends AbstractFilterableTablePanel<BankAccount> {

	private static final double ACCOUNT_LIST_MIN_WIDTH = 280;
	private static final double ACCOUNT_LIST_PREF_WIDTH = 335;
	private static final double ACCOUNT_LIST_MAX_WIDTH = 400;
	private static final String SQL_SELECT_ALL_ONLINE_BANKACCOUNTS = "SQL_SELECT_ALL_ONLINE_BANKACCOUNTS";
	private static final String UI_PANEL_ACCOUNT = "UI_PANEL_ACCOUNT";
	private static final String UI_PANEL_ACCOUNT_ONLINE_ONLY_SUFFIX = "UI_PANEL_ACCOUNT_ONLINE_ONLY_SUFFIX";

	private final AccountSelectionTarget selectionTarget;
	private final AccountTableModel modelAccount;
	private final AccountListScope accountListScope;
	private final String panelTitleKey;
	private final ComboBox<AccountGrouping> grouping = new ComboBox<>();
	private final Label accountCount = new Label();
	private boolean textFilterActive;

	public AccountListPanel(AccountSelectionTarget selectionTarget) {
		this(selectionTarget, AccountListScope.FOLLOW_VIEW_SETTING, UI_PANEL_ACCOUNT);
	}

	public AccountListPanel(AccountSelectionTarget selectionTarget, AccountListScope accountListScope) {
		this(selectionTarget, accountListScope, UI_PANEL_ACCOUNT);
	}

	public AccountListPanel(AccountSelectionTarget selectionTarget, AccountListScope accountListScope, String panelTitleKey) {
		this(selectionTarget, accountListScope, panelTitleKey, createModel(accountListScope));
	}

	AccountListPanel(AccountSelectionTarget selectionTarget, AccountListScope accountListScope, String panelTitleKey,
			AccountTableModel modelAccount) {
		super(modelAccount.getAccounts());
		this.selectionTarget = selectionTarget;
		this.modelAccount = modelAccount;
		this.accountListScope = accountListScope;
		this.panelTitleKey = panelTitleKey;
		createInnerAccountListPanel();
	}

	private void createInnerAccountListPanel() {
		applyWidthProfile();
		configureColumns();
		configureTableLayout("accounts." + selectionTarget.getPageContext().name());
		configureContextMenu();
		configureGrouping();
		tableView.setEditable(true);
		updatePanelTitle();
		configureSelection();
	}

	private void configureGrouping() {
		grouping.getItems().setAll(AccountGrouping.values());
		grouping.setId("account-grouping");
		grouping.setButtonCell(new ListCell<>() {
			@Override
			protected void updateItem(AccountGrouping item, boolean empty) {
				super.updateItem(item, empty);
				setText(AccountListPanel.this.getText("UI_ACCOUNT_GROUPING_CAPTION"));
			}
		});
		grouping.setTooltip(new Tooltip(getText("UI_MENU_VIEW_ACCOUNT_GROUPING")));
		grouping.setAccessibleText(getText("UI_MENU_VIEW_ACCOUNT_GROUPING"));
		grouping.getStyleClass().add("account-grouping-choice");
		grouping.valueProperty().bindBidirectional(GuiLayoutState.accountGroupingProperty());
		accountCount.setId("account-count");
		accountCount.setMinWidth(USE_PREF_SIZE);
		Region spacer = new Region();
		HBox.setHgrow(titleLabel, Priority.NEVER);
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox header = new HBox(4, titleLabel, accountCount, spacer, grouping);
		header.setAlignment(Pos.CENTER_LEFT);
		setTop(header);

		sortedData.comparatorProperty().unbind();
		sortedData.comparatorProperty().bind(Bindings.createObjectBinding(
				() -> grouping.getValue().comparator(tableView.getComparator()),
				grouping.valueProperty(), tableView.comparatorProperty()));
		// The default sort policy requires the table comparator alone, without a grouping prefix.
		tableView.setSortPolicy(table -> true);
		sortedData.addListener((ListChangeListener<BankAccount>) change -> {
			updatePanelTitle();
			tableView.refresh();
		});
		grouping.valueProperty().addListener((obs, previous, current) -> tableView.refresh());
	}

	@Override
	protected void updateRowStyle(TableRow<BankAccount> row, BankAccount account) {
		if (!(row.getSkin() instanceof AccountGroupRowSkin)) {
			row.setSkin(new AccountGroupRowSkin(row));
			row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
				if (!event.isConsumed() && event.getButton() == MouseButton.PRIMARY && !row.isEmpty() && row.isSelected()) {
					selectionTarget.handleAccountReselection(row.getItem());
				}
			});
		}
		((AccountGroupRowSkin) row.getSkin()).updateGroup(grouping.getValue());
	}

	@Override
	protected void applyFilter() {
		textFilterActive = !filterText.getText().isBlank();
		super.applyFilter();
		updatePanelTitle();
	}

	private void configureSelection() {
		onGuardedSelection(() -> selectionTarget.canChangeAccountSelection(), account -> handleSelection(account));
	}

	private void configureContextMenu() {
		ContextMenu contextMenu = createContextMenu();
		installRowContextMenu(contextMenu);
	}

	private ContextMenu createContextMenu() {
		Menu importMenu = new Menu(getText("UI_MENU_FILE_IMPORT"));
		MenuItem importCsvItem = new MenuItem(getText("UI_MENU_FILE_CSV_FORMATS"));
		MenuItem importFp3Item = new MenuItem(getText("UI_MENU_FILE_FP3"));
		MenuItem importMt940Item = new MenuItem(getText("UI_MENU_FILE_MT940"));
		MenuItem importXmlItem = new MenuItem(getText("UI_MENU_FILE_XML"));
		importMenu.getItems().addAll(importCsvItem, importFp3Item, importMt940Item, importXmlItem);

		Menu exportMenu = new Menu(getText("UI_MENU_FILE_EXPORT"));
		MenuItem exportCsvItem = new MenuItem(getText("UI_MENU_FILE_CSV"));
		MenuItem exportFp3Item = new MenuItem(getText("UI_MENU_FILE_FP3"));
		MenuItem exportMt940Item = new MenuItem(getText("UI_MENU_FILE_MT940"));
		MenuItem exportXmlItem = new MenuItem(getText("UI_MENU_FILE_XML"));
		exportMenu.getItems().addAll(exportCsvItem, exportFp3Item, exportMt940Item, exportXmlItem);

		importCsvItem.setOnAction(event -> importSelectedAccountBookings(ExportType.BOOKINGS_CSV));
		importFp3Item.setOnAction(event -> importSelectedAccountBookings(ExportType.BOOKINGS_FP3));
		importMt940Item.setOnAction(event -> importSelectedAccountBookings(ExportType.BOOKINGS_MT940));
		importXmlItem.setOnAction(event -> importSelectedAccountBookings(ExportType.BOOKINGS_XML));
		exportCsvItem.setOnAction(event -> exportSelectedAccountBookings(ExportType.BOOKINGS_CSV));
		exportFp3Item.setOnAction(event -> exportSelectedAccountBookings(ExportType.BOOKINGS_FP3));
		exportMt940Item.setOnAction(event -> exportSelectedAccountBookings(ExportType.BOOKINGS_MT940));
		exportXmlItem.setOnAction(event -> exportSelectedAccountBookings(ExportType.BOOKINGS_XML));

		ContextMenu contextMenu = new ContextMenu(importMenu, exportMenu);
		contextMenu.setOnShowing(event -> {
			boolean noSelection = getSelectedAccount() == null;
			importMenu.setDisable(noSelection);
			exportMenu.setDisable(noSelection);
		});
		return contextMenu;
	}

	private void applyWidthProfile() {
		if (selectionTarget.getPageContext() == PageContext.ALL_ACCOUNTS) {
			setMinWidth(0);
			setPrefWidth(USE_COMPUTED_SIZE);
			setMaxWidth(Double.MAX_VALUE);
			return;
		}

		setMinWidth(ACCOUNT_LIST_MIN_WIDTH);
		setPrefWidth(ACCOUNT_LIST_PREF_WIDTH);
		setMaxWidth(ACCOUNT_LIST_MAX_WIDTH);
	}

	private void configureColumns() {
		setColumns(selectionTarget.getPageContext() == PageContext.ALL_ACCOUNTS ? createAllAccountsColumns() : createCompactColumns());
	}

	private List<TableColumn<BankAccount, ?>> createCompactColumns() {
		TableColumn<BankAccount, Boolean> selectedCol = createSelectAllSelectionColumn(
				account -> account.isSelected(), (account, selected) -> account.setSelected(selected));
		TableColumn<BankAccount, String> nameCol = createAccountNameColumn(100, 220);
		AccountUpdatedAtColumn updatedCol = createUpdatedAtColumn();

		return List.of(selectedCol, nameCol, updatedCol);
	}

	private List<TableColumn<BankAccount, ?>> createAllAccountsColumns() {
		TableColumn<BankAccount, Boolean> selectedCol = createSelectAllSelectionColumn(
				account -> account.isSelected(), (account, selected) -> account.setSelected(selected));
		TableColumn<BankAccount, String> nameCol = createAccountNameColumn(180, 220);
		TableColumn<BankAccount, String> ibanCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_IBAN"), account -> account.getIban(), 220, 240);
		TableColumn<BankAccount, String> bankCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_BANK"), account -> account.getBankName(), 140, 170);
		TableColumn<BankAccount, String> typeCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_ACCOUNT_TYPE"),
				account -> account.getAccountType() != null ? account.getAccountType().toString() : "", 90);
		TableColumn<BankAccount, BigDecimal> balanceCol = TableColumnFactory.createAmountColumn(getText("UI_TABLE_BALANCE"),
				account -> account.getBalance());
		AccountUpdatedAtColumn updatedCol = createUpdatedAtColumn();

		return List.of(selectedCol, nameCol, ibanCol, bankCol, typeCol, balanceCol, updatedCol);
	}

	private AccountUpdatedAtColumn createUpdatedAtColumn() {
		return new AccountUpdatedAtColumn(getText("UI_TABLE_UPDATED_AT"));
	}

	private TableColumn<BankAccount, String> createAccountNameColumn(double minWidth, double prefWidth) {
		TableColumn<BankAccount, String> nameCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_ACCOUNT_NAME"),
				account -> account.getAccountName(), minWidth, prefWidth);
		nameCol.setCellFactory(column -> new TableCell<>() {
			@Override
			protected void updateItem(String accountName, boolean empty) {
				super.updateItem(accountName, empty);
				setText(empty ? null : accountName);

				BankAccount account = empty || getTableRow() == null ? null : getTableRow().getItem();
				String bankName = account != null ? trimToNull(account.getBankName()) : null;
				setTooltip(bankName != null ? new Tooltip(bankName) : null);
			}
		});
		return nameCol;
	}

	@Override
	protected boolean matchesFilter(BankAccount account, String filter) {
		return matchesAny(filter, account.getAccountName(), account.getIban(), account.getBankName(), account.getBic(), account.getBlz(),
				account.getNumber(), account.getSubnumber(), account.getCurrency(), account.getOwnerName(), account.getOwnerName2(),
				account.getAccountType() != null ? account.getAccountType().toString() : null,
				account.getAccountState() != null ? account.getAccountState().toString() : null,
				account.getBalance() != null ? account.getBalance().toString() : null);
	}

	private void handleSelection(BankAccount selectedAccount) {
		GuiContext.setSelectedAccount(selectedAccount);
		selectionTarget.handleAccountSelection(selectedAccount);
	}

	public AccountTableModel getModelAccount() {
		return modelAccount;
	}

	public BankAccount getSelectedAccount() {
		return getSelectedItem();
	}

	public void setCheckedAccounts(List<BankAccount> accountsToCheck, boolean checkAllAccounts) {
		for (BankAccount account : masterData) {
			account.setSelected(checkAllAccounts || containsAccountId(accountsToCheck, account.getId()));
		}
		tableView.refresh();
	}

	private boolean containsAccountId(List<BankAccount> accounts, int accountId) {
		return accounts != null && accounts.stream().anyMatch(account -> account != null && account.getId() == accountId);
	}

	private void exportSelectedAccountBookings(ExportType exportType) {
		BankAccount selectedAccount = getSelectedAccount();
		BookingFileActionSupport.exportBookings(getTableWindow(), selectedAccount, exportType, this);
	}

	private void importSelectedAccountBookings(ExportType importType) {
		BankAccount selectedAccount = getSelectedAccount();
		BookingFileActionSupport.importBookings(getTableWindow(), selectedAccount, importType, this, () -> handleSelection(selectedAccount));
	}

	public void selectAccount(BankAccount account) {
		if (account == null) {
			return;
		}

		BankAccount accountToSelect = masterData.stream()
				.filter(candidate -> candidate.getId() == account.getId())
				.findFirst()
				.orElse(account);
		applyFilter();
		if (!filteredData.contains(accountToSelect)) {
			filterText.clear();
			applyFilter();
		}
		if (tableView.getSelectionModel().getSelectedItem() == accountToSelect) {
			if (!selectionTarget.canChangeAccountSelection()) {
				return;
			}
			handleSelection(accountToSelect);
		} else {
			tableView.getSelectionModel().select(accountToSelect);
		}
		tableView.scrollTo(accountToSelect);
	}

	private static AccountTableModel createModel(AccountListScope accountListScope) {
		return new AccountTableModel(loadAccounts(accountListScope));
	}

	public void reload() {
		Integer selectedAccountId = accountIdToRestore();
		LocalDate today = LocalDate.now(ZoneId.systemDefault());
		Map<Integer, LocalDateTime> retrievalTimes = new HashMap<>();
		masterData.stream()
				.filter(account -> account.getSessionRetrievalAt() != null
						&& account.getSessionRetrievalAt().toLocalDate().equals(today))
				.forEach(account -> retrievalTimes.put(account.getId(), account.getSessionRetrievalAt()));
		replaceItemsFrom(() -> loadAccounts(accountListScope));
		masterData.forEach(account -> account.setSessionRetrievalAt(retrievalTimes.get(account.getId())));
		updatePanelTitle();
		restoreSelectedAccount(selectedAccountId);
	}

	private static List<BankAccount> loadAccounts(AccountListScope accountListScope) {
		List<BankAccount> accounts = accountListScope.usesOnlineFilter(GuiContext.isOnlyOnlineAccountsVisible())
				? DBController.getInstance(".").getAll(BankAccount.class, SQL_SELECT_ALL_ONLINE_BANKACCOUNTS)
				: DBController.getInstance(".").getAll(BankAccount.class);
		return accounts.stream().filter(AccountListPanel::isGeneralAccount).toList();
	}

	static boolean isGeneralAccount(BankAccount account) {
		return account != null && account.getAccountType() != AccountType.DEPOT;
	}

	private void updatePanelTitle() {
		String title = getText(panelTitleKey);
		if (accountListScope == AccountListScope.FOLLOW_VIEW_SETTING && GuiContext.isOnlyOnlineAccountsVisible()) {
			title += " " + getText(UI_PANEL_ACCOUNT_ONLINE_ONLY_SUFFIX);
		}
		setPanelTitle(title);
		accountCount.setText("(" + (textFilterActive ? filteredData.size() + "/" : "") + masterData.size() + ")");
	}

	public void refreshModelAccount() {
		reload();
	}

	private Integer accountIdToRestore() {
		Integer selectedAccountId = GuiContext.getSelectedAccountId();
		if (selectedAccountId != null) {
			return selectedAccountId;
		}

		BankAccount selectedAccount = getSelectedAccount();
		return selectedAccount != null ? selectedAccount.getId() : null;
	}

	private void restoreSelectedAccount(Integer accountId) {
		if (accountId == null) {
			return;
		}

		BankAccount accountToSelect = masterData.stream()
				.filter(candidate -> candidate.getId() == accountId)
				.findFirst()
				.orElse(null);
		if (accountToSelect != null) {
			selectAccount(accountToSelect);
		} else if (accountId.equals(GuiContext.getSelectedAccountId())) {
			GuiContext.clearSelectedAccount();
		}
	}
}
