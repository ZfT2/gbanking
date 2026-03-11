package de.gbanking.gui.fx.panel.account;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.gui.fx.components.GBankingTableView;
import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.BaseBorderPanePanel;
import de.gbanking.gui.fx.panel.overview.AccountsTransactionsOverviewPanel;
import de.gbanking.gui.fx.panel.overview.AllAccountsOverviewPanel;
import de.gbanking.gui.fx.panel.overview.CategoryOverviewPanel;
import de.gbanking.gui.fx.panel.overview.MoneyTransferOverviewPanel;
import de.gbanking.gui.fx.panel.overview.OverviewBasePanel;
import de.gbanking.gui.fx.panel.transaction.TransactionListPanel;
import de.gbanking.gui.fx.util.DateFormatUtils;
import de.gbanking.gui.fx.util.FxTableUtils;
import de.gbanking.gui.fx.util.TableColumnFactory;
import de.gbanking.gui.fx.model.AccountTableModel;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class AccountListPanel extends BaseBorderPanePanel {

	private static final Logger log = LogManager.getLogger(AccountListPanel.class);

	private static final double MIN_WIDTH = 280;
	private static final double PREF_WIDTH = 335;
	private static final double MAX_WIDTH = 400;

	private final OverviewBasePanel parentPanel;
	private final GBankingTableView<BankAccount> accountTable = new GBankingTableView<>();
	private AccountTableModel modelAccount;

	public AccountListPanel(OverviewBasePanel parentPanel) {
		this.parentPanel = parentPanel;
		createInnerAccountListPanel();
	}

	private void createInnerAccountListPanel() {
		setMinWidth(MIN_WIDTH);
		setPrefWidth(PREF_WIDTH);
		setMaxWidth(MAX_WIDTH);

		modelAccount = new AccountTableModel(dbController.getAll(BankAccount.class));
		configureColumns();

		accountTable.setItems(modelAccount.getAccounts());
		accountTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selectedAccount) -> {
			if (selectedAccount != null) {
				handleSelection(selectedAccount);
			}
		});

		setTop(new Label(getText("UI_PANEL_ACCOUNT")));
		setCenter(accountTable);
	}

	private void configureColumns() {
		if (parentPanel.getPageContext() == PageContext.ALL_ACCOUNTS) {
			configureAllAccountsColumns();
		} else {
			configureCompactColumns();
		}
	}

	private void configureCompactColumns() {
		TableColumn<BankAccount, Boolean> selectedCol = FxTableUtils.createSelectionColumn(getText("UI_TABLE_SELECTED"), BankAccount::isSelected,
				BankAccount::setSelected);

		TableColumn<BankAccount, String> nameCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_ACCOUNT_NAME"), BankAccount::getAccountName, 180, 220);

		TableColumn<BankAccount, String> updatedCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_UPDATED_AT"),
				account -> DateFormatUtils.formatShort(account.getUpdatedAt()), 90);

		accountTable.getColumns().setAll(List.of(selectedCol, nameCol, updatedCol));
	}

	private void configureAllAccountsColumns() {
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

		accountTable.getColumns().setAll(List.of(selectedCol, nameCol, ibanCol, bankCol, typeCol, balanceCol, updatedCol));
	}

	private void handleSelection(BankAccount selectedAccount) {
		PageContext context = parentPanel.getPageContext();

		if (context == PageContext.ACCOUNTS_TRANSACTIONS) {
			handleAccountsTransactionsSelection(selectedAccount);
		} else if (context == PageContext.ALL_ACCOUNTS) {
			handleAllAccountsSelection(selectedAccount);
		} else if (context == PageContext.ACCOUNTS_MONEYTRANSFERS) {
			handleMoneyTransfersSelection(selectedAccount);
		} else if (context == PageContext.CATEGORIES) {
			handleCategoriesSelection(selectedAccount);
		}
	}

	private void handleAccountsTransactionsSelection(BankAccount selectedAccount) {
		int accountId = selectedAccount.getId();
		log.info(messages.getFormattedMessage("LOG_INFO_ACCOUNT_SELECTED", accountId));

		List<Booking> bookingList = dbController.getAllByParent(Booking.class, accountId);

		AccountsTransactionsOverviewPanel parent = (AccountsTransactionsOverviewPanel) parentPanel;
		TransactionListPanel transactionListPanel = parent.getTransactionListPanel();

		transactionListPanel.updatePanelBorder(getText("UI_PANEL_TRANSACTIONS") + " - " + selectedAccount.getAccountName());
		transactionListPanel.updateModelBooking(bookingList);
		parent.getAccountDetailPanel().updatePanelFieldValues(selectedAccount);
	}

	private void handleAllAccountsSelection(BankAccount selectedAccount) {
		((AllAccountsOverviewPanel) parentPanel).getAccountDetailPanel().updatePanelFieldValues(selectedAccount);
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
		parent.setSelectedAccount(selectedAccount);
		parent.getCategoryInputPanel().updatePanelFieldValues(selectedAccount);
		parent.getCategoryListPanel().reload();
	}

	public AccountTableModel getModelAccount() {
		return modelAccount;
	}

	public void reload() {
		ObservableList<BankAccount> accounts = modelAccount.getAccounts();
		accounts.setAll(dbController.getAll(BankAccount.class));
	}

	public void refreshModelAccount() {
		reload();
	}
}