package de.gbanking.gui.panel.account;

import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.gui.enu.PageContext;
import de.gbanking.gui.model.AccountTableModel;
import de.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.gbanking.gui.panel.overview.AccountsTransactionsOverviewPanel;
import de.gbanking.gui.panel.overview.AllAccountsOverviewPanel;
import de.gbanking.gui.panel.overview.CategoryOverviewPanel;
import de.gbanking.gui.panel.overview.MoneyTransferOverviewPanel;
import de.gbanking.gui.panel.overview.OverviewBasePanel;
import de.gbanking.gui.panel.transaction.TransactionListPanel;
import de.gbanking.gui.util.DateFormatUtils;
import de.gbanking.gui.util.FxTableUtils;
import de.gbanking.gui.util.TableColumnFactory;
import javafx.scene.control.TableColumn;

public class AccountListPanel extends AbstractFilterableTablePanel<BankAccount> {

	private static final Logger log = LogManager.getLogger(AccountListPanel.class);

	private static final double ACCOUNT_LIST_MIN_WIDTH = 280;
	private static final double ACCOUNT_LIST_PREF_WIDTH = 335;
	private static final double ACCOUNT_LIST_MAX_WIDTH = 400;

	private final OverviewBasePanel parentPanel;
	private final AccountTableModel modelAccount;

	public AccountListPanel(OverviewBasePanel parentPanel) {
		this(parentPanel, createModel());
	}

	private AccountListPanel(OverviewBasePanel parentPanel, AccountTableModel modelAccount) {
		super(modelAccount.getAccounts());
		this.parentPanel = parentPanel;
		this.modelAccount = modelAccount;
		createInnerAccountListPanel();
	}

	private void createInnerAccountListPanel() {
		applyWidthProfile();
		configureColumns();
		tableView.setEditable(true);
		setPanelTitleByKey("UI_PANEL_ACCOUNT");
		onSelection(this::handleSelection);
		tableView.setOnMouseClicked(event -> {
			BankAccount selectedAccount = (BankAccount) tableView.getSelectionModel().getSelectedItem();
			if (selectedAccount != null) {
				handleSelection(selectedAccount);
			}
		});
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
		TableColumn<BankAccount, Boolean> selectedCol = FxTableUtils.createSelectionColumn(getText("UI_TABLE_SELECTED"), BankAccount::isSelected,
				BankAccount::setSelected);
		TableColumn<BankAccount, String> nameCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_ACCOUNT_NAME"), BankAccount::getAccountName, 180, 220);
		TableColumn<BankAccount, String> updatedCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_UPDATED_AT"),
				account -> DateFormatUtils.formatShort(account.getUpdatedAt()), 90);

		return List.of(selectedCol, nameCol, updatedCol);
	}

	private List<TableColumn<BankAccount, ?>> createAllAccountsColumns() {
		TableColumn<BankAccount, Boolean> selectedCol = FxTableUtils.createSelectionColumn(getText("UI_TABLE_SELECTED"), BankAccount::isSelected,
				BankAccount::setSelected);
		TableColumn<BankAccount, String> nameCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_ACCOUNT_NAME"), BankAccount::getAccountName, 180, 220);
		TableColumn<BankAccount, String> ibanCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_IBAN"), BankAccount::getIban, 220, 240);
		TableColumn<BankAccount, String> bankCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_BANK"), BankAccount::getBankName, 140, 170);
		TableColumn<BankAccount, String> typeCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_ACCOUNT_TYPE"),
				account -> account.getAccountType() != null ? account.getAccountType().toString() : "", 90);
		TableColumn<BankAccount, String> balanceCol = TableColumnFactory.createAmountColumn(getText("UI_TABLE_BALANCE"),
				account -> account.getBalance() != null ? account.getBalance().toString() : "", 110);
		TableColumn<BankAccount, String> updatedCol = TableColumnFactory.createUpdatedAtColumn(getText("UI_TABLE_UPDATED_AT"), BankAccount::getUpdatedAt, 90);

		return List.of(selectedCol, nameCol, ibanCol, bankCol, typeCol, balanceCol, updatedCol);
	}

	@Override
	protected boolean matchesFilter(BankAccount account, String filter) {
		if (filter == null || filter.isBlank()) {
			return true;
		}

		return contains(account.getAccountName(), filter) || contains(account.getIban(), filter) || contains(account.getBankName(), filter)
				|| contains(account.getBic(), filter) || contains(account.getBlz(), filter) || contains(account.getNumber(), filter)
				|| contains(account.getSubnumber(), filter) || contains(account.getCurrency(), filter) || contains(account.getOwnerName(), filter)
				|| contains(account.getOwnerName2(), filter) || contains(account.getAccountType() != null ? account.getAccountType().toString() : null, filter)
				|| contains(account.getAccountState() != null ? account.getAccountState().toString() : null, filter)
				|| contains(account.getBalance() != null ? account.getBalance().toString() : null, filter);
	}

	private void handleSelection(BankAccount selectedAccount) {
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

		List<Booking> bookingList = dbController.getAllByParent(Booking.class, selectedAccount.getId());
		AccountsTransactionsOverviewPanel parent = (AccountsTransactionsOverviewPanel) parentPanel;
		TransactionListPanel transactionListPanel = parent.getTransactionListPanel();

		transactionListPanel.updatePanelBorder(getText("UI_PANEL_TRANSACTIONS") + " - " + selectedAccount.getAccountName());
		transactionListPanel.updateModelBooking(bookingList);
		parent.enableAccountDetailPanel();
		parent.getAccountDetailPanel().updatePanelFieldValues(selectedAccount);
	}

	private void handleMoneyTransfersSelection(BankAccount selectedAccount) {
		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, selectedAccount.getId());
		MoneyTransferOverviewPanel parent = (MoneyTransferOverviewPanel) parentPanel;

		parent.setSelectedAccount(selectedAccount);
		parent.getMoneyTransferListPanel().updateModelMoneytransfer(transfers);
		parent.getMoneyTransferListPanel().updatePanelBorder(OrderType.TRANSFER.getPlural() + " " + selectedAccount.getAccountName());
		parent.getMoneyTransferInputPanel().updatePanelFieldValues(selectedAccount);
	}

	private void handleCategoriesSelection(BankAccount selectedAccount) {
		CategoryOverviewPanel parent = (CategoryOverviewPanel) parentPanel;
		parent.handleAccountSelection(selectedAccount);
	}

	public AccountTableModel getModelAccount() {
		return modelAccount;
	}

	private static AccountTableModel createModel() {
		return new AccountTableModel(dbController.getAll(BankAccount.class));
	}

	public void reload() {
		replaceItems(dbController.getAll(BankAccount.class));
	}

	public void refreshModelAccount() {
		reload();
	}
}