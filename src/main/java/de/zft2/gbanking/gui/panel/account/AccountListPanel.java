package de.zft2.gbanking.gui.panel.account;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.gui.GBankingContext;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.model.AccountTableModel;
import de.zft2.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.zft2.gbanking.gui.panel.overview.AccountsTransactionsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.AllAccountsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.CategoryOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.MoneyTransferOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.OverviewBasePanel;
import de.zft2.gbanking.gui.panel.transaction.TransactionListPanel;
import de.zft2.gbanking.gui.util.BookingFileActionSupport;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;

public class AccountListPanel extends AbstractFilterableTablePanel<BankAccount> {

	private static final Logger log = LogManager.getLogger(AccountListPanel.class);

	private static final double ACCOUNT_LIST_MIN_WIDTH = 280;
	private static final double ACCOUNT_LIST_PREF_WIDTH = 335;
	private static final double ACCOUNT_LIST_MAX_WIDTH = 400;
	private static final String SQL_SELECT_ALL_ONLINE_BANKACCOUNTS = "SQL_SELECT_ALL_ONLINE_BANKACCOUNTS";
	private static final String UI_PANEL_ACCOUNT = "UI_PANEL_ACCOUNT";
	private static final String UI_PANEL_ACCOUNT_ONLINE_ONLY_SUFFIX = "UI_PANEL_ACCOUNT_ONLINE_ONLY_SUFFIX";

	private final OverviewBasePanel parentPanel;
	private final AccountTableModel modelAccount;
	private final AccountListScope accountListScope;
	private final String panelTitleKey;
	private boolean restoringSelection;

	public AccountListPanel(OverviewBasePanel parentPanel) {
		this(parentPanel, AccountListScope.FOLLOW_VIEW_SETTING, UI_PANEL_ACCOUNT);
	}

	public AccountListPanel(OverviewBasePanel parentPanel, AccountListScope accountListScope, String panelTitleKey) {
		this(parentPanel, accountListScope, panelTitleKey, createModel(accountListScope));
	}

	private AccountListPanel(OverviewBasePanel parentPanel, AccountListScope accountListScope, String panelTitleKey,
			AccountTableModel modelAccount) {
		super(modelAccount.getAccounts());
		this.parentPanel = parentPanel;
		this.modelAccount = modelAccount;
		this.accountListScope = accountListScope;
		this.panelTitleKey = panelTitleKey;
		createInnerAccountListPanel();
	}

	private void createInnerAccountListPanel() {
		applyWidthProfile();
		configureColumns();
		configureTableLayout("accounts." + parentPanel.getPageContext().name());
		configureContextMenu();
		tableView.setEditable(true);
		updatePanelTitle();
		configureSelection();
	}

	private void configureSelection() {
		tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldAccount, selectedAccount) -> {
			if (restoringSelection || selectedAccount == null) {
				return;
			}
			if (parentPanel.getPageContext() == PageContext.ACCOUNTS_TRANSACTIONS && !canLeaveTransactionDetails()) {
				restoreSelection(oldAccount);
				return;
			}
			handleSelection(selectedAccount);
		});
	}

	private boolean canLeaveTransactionDetails() {
		AccountsTransactionsOverviewPanel parent = (AccountsTransactionsOverviewPanel) parentPanel;
		return parent.getTransactionDetailPanel().confirmDiscardUnsavedSplitBookings();
	}

	private void restoreSelection(BankAccount account) {
		restoringSelection = true;
		try {
			if (account != null) {
				tableView.getSelectionModel().select(account);
			} else {
				tableView.getSelectionModel().clearSelection();
			}
		} finally {
			restoringSelection = false;
		}
	}

	private void configureContextMenu() {
		ContextMenu contextMenu = createContextMenu();
		installRowContextMenu(contextMenu);
	}

	private ContextMenu createContextMenu() {
		Menu importMenu = new Menu(getText("UI_MENU_FILE_IMPORT"));
		MenuItem importCsvItem = new MenuItem(getText("UI_MENU_FILE_CSV"));
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
		if (parentPanel.getPageContext() == PageContext.ALL_ACCOUNTS) {
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
		setColumns(parentPanel.getPageContext() == PageContext.ALL_ACCOUNTS ? createAllAccountsColumns() : createCompactColumns());
	}

	private List<TableColumn<BankAccount, ?>> createCompactColumns() {
		TableColumn<BankAccount, Boolean> selectedCol = createSelectAllSelectionColumn(
				account -> account.isSelected(), (account, selected) -> account.setSelected(selected));
		TableColumn<BankAccount, String> nameCol = createAccountNameColumn(180, 220);
		TableColumn<BankAccount, LocalDate> updatedCol = TableColumnFactory.createUpdatedAtColumn(getText("UI_TABLE_UPDATED_AT"),
				account -> account.getUpdatedAt(), 90);

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
		TableColumn<BankAccount, LocalDate> updatedCol = TableColumnFactory.createUpdatedAtColumn(getText("UI_TABLE_UPDATED_AT"),
				account -> account.getUpdatedAt(), 90);

		return List.of(selectedCol, nameCol, ibanCol, bankCol, typeCol, balanceCol, updatedCol);
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
		GBankingContext.setSelectedAccount(selectedAccount);
		PageContext context = parentPanel.getPageContext();

		if (context == PageContext.ACCOUNTS_TRANSACTIONS) {
			handleAccountsTransactionsSelection(selectedAccount);
		} else if (context == PageContext.ALL_ACCOUNTS) {
			((AllAccountsOverviewPanel) parentPanel).getAccountDetailPanel().updatePanelFieldValues(selectedAccount);
		} else if (context == PageContext.ACCOUNTS_MONEYTRANSFERS) {
			handleMoneyTransfersSelection(selectedAccount);
		} else if (context == PageContext.CATEGORIES) {
			handleCategoriesSelection(selectedAccount);
		}
	}

	private void handleAccountsTransactionsSelection(BankAccount selectedAccount) {
		log.log(Level.INFO, () -> getText("LOG_ACCOUNT_SELECTED", selectedAccount.getId()));

		AccountsTransactionsOverviewPanel parent = (AccountsTransactionsOverviewPanel) parentPanel;
		parent.getTransactionDetailPanel().clearDisplayedBooking();

		List<Booking> bookingList = dbController.getAllByParentFull(Booking.class, selectedAccount.getId());
		TransactionListPanel transactionListPanel = parent.getTransactionListPanel();

		transactionListPanel.updatePanelBorder(getText("UI_PANEL_TRANSACTIONS") + " - " + selectedAccount.getAccountName());
		transactionListPanel.updateModelBooking(bookingList);
		parent.enableAccountDetailPanel();
		parent.getAccountDetailPanel().updatePanelFieldValues(selectedAccount);
		parent.getTransactionDetailPanel().setCurrentAccount(selectedAccount);
		parent.getAccountStatementPanel().updateAccount(selectedAccount);
	}

	private void handleMoneyTransfersSelection(BankAccount selectedAccount) {
		MoneyTransferOverviewPanel parent = (MoneyTransferOverviewPanel) parentPanel;
		parent.handleAccountSelection(selectedAccount);
	}

	private void handleCategoriesSelection(BankAccount selectedAccount) {
		CategoryOverviewPanel parent = (CategoryOverviewPanel) parentPanel;
		parent.handleAccountSelection(selectedAccount);
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
		if (tableView.getSelectionModel().getSelectedItem() == accountToSelect) {
			if (parentPanel.getPageContext() == PageContext.ACCOUNTS_TRANSACTIONS && !canLeaveTransactionDetails()) {
				return;
			}
			handleSelection(accountToSelect);
		} else {
			tableView.getSelectionModel().select(accountToSelect);
		}
	}

	private static AccountTableModel createModel(AccountListScope accountListScope) {
		return new AccountTableModel(loadAccounts(accountListScope));
	}

	public void reload() {
		Integer selectedAccountId = accountIdToRestore();
		replaceItemsFrom(() -> loadAccounts(accountListScope));
		updatePanelTitle();
		restoreSelectedAccount(selectedAccountId);
	}

	private static List<BankAccount> loadAccounts(AccountListScope accountListScope) {
		return accountListScope == AccountListScope.ONLINE_ONLY || GBankingContext.isOnlyOnlineAccountsVisible()
				? GBankingContext.getDbController().getAll(BankAccount.class, SQL_SELECT_ALL_ONLINE_BANKACCOUNTS)
				: GBankingContext.getDbController().getAll(BankAccount.class);
	}

	private void updatePanelTitle() {
		String title = getText(panelTitleKey);
		if (accountListScope == AccountListScope.FOLLOW_VIEW_SETTING && GBankingContext.isOnlyOnlineAccountsVisible()) {
			title += " " + getText(UI_PANEL_ACCOUNT_ONLINE_ONLY_SUFFIX);
		}
		setPanelTitle(title);
	}

	public void refreshModelAccount() {
		reload();
	}

	private Integer accountIdToRestore() {
		Integer selectedAccountId = GBankingContext.getSelectedAccountId();
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
		} else if (accountId.equals(GBankingContext.getSelectedAccountId())) {
			GBankingContext.clearSelectedAccount();
		}
	}
}
