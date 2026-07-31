package de.zft2.gbanking.gui.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.account.AccountDetailPanel;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import de.zft2.gbanking.gui.panel.account.AccountStatementPanel;
import de.zft2.gbanking.gui.panel.layout.DetailListPane;
import de.zft2.gbanking.gui.panel.layout.MasterContentPane;
import de.zft2.gbanking.gui.panel.transaction.TransactionDetailPanel;
import de.zft2.gbanking.gui.panel.transaction.TransactionListPanel;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class AccountsTransactionsOverviewPanel extends TransactionsOverviewBasePanel {

	private static final Logger log = LogManager.getLogger(AccountsTransactionsOverviewPanel.class);
	private static final double ACCOUNT_DIVIDER = 0.22;

	private AccountListPanel accountListPanel;
	private AccountDetailPanel accountDetailPanel;
	private AccountStatementPanel accountStatementPanel;
	private DetailListPane transactionContentPane;

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.ACCOUNTS_TRANSACTIONS);
		log.info("Creating AccountsTransactionsOverviewPanel");

		accountListPanel = new AccountListPanel(this);

		accountDetailPanel = new AccountDetailPanel(false);
		transactionDetailPanel = new TransactionDetailPanel(this);
		transactionListPanel = new TransactionListPanel(this);
		transactionContentPane = new DetailListPane(accountDetailPanel, transactionListPanel);

		accountStatementPanel = new AccountStatementPanel();

		TabPane rightTabPane = createRightTabPane();
		MasterContentPane mainPane = new MasterContentPane(accountListPanel, rightTabPane, "accountsTransactions.main", ACCOUNT_DIVIDER);
		setOverviewContent("UI_PANEL_ACCOUNTS_TRANSACTIONS", mainPane, show);
	}

	public AccountListPanel getAccountListPanel() {
		return accountListPanel;
	}

	public AccountDetailPanel getAccountDetailPanel() {
		return accountDetailPanel;
	}

	public AccountStatementPanel getAccountStatementPanel() {
		return accountStatementPanel;
	}

	public BankAccount getSelectedAccount() {
		return accountListPanel != null ? accountListPanel.getSelectedAccount() : null;
	}

	public void enableAccountDetailPanel() {
		showTransactionDetailNode(accountDetailPanel);
		accountDetailPanel.setDisable(false);
	}

	public void enableTransactionDetailPanel() {
		showTransactionDetailNode(transactionDetailPanel);
		transactionDetailPanel.setDisable(false);
	}

	@Override
	public void refreshOnShow() {
		if (accountListPanel != null) {
			accountListPanel.reload();
		}
		if (getSelectedAccount() == null && transactionListPanel != null) {
			transactionListPanel.updateModelBooking(java.util.List.of());
			transactionListPanel.updatePanelBorder(getText("UI_PANEL_TRANSACTIONS"));
			accountDetailPanel.updatePanelFieldValues(null);
			transactionDetailPanel.setCurrentAccount(null);
			accountStatementPanel.clearAccount();
		}
		if (transactionContentPane != null && accountDetailPanel != null) {
			showTransactionDetailNode(accountDetailPanel);
		}
	}

	private TabPane createRightTabPane() {
		Tab transactionsTab = createTab("UI_TAB_ACCOUNT_TRANSACTIONS", transactionContentPane);
		Tab statementsTab = createTab("UI_TAB_ACCOUNT_STATEMENTS", accountStatementPanel);
		TabPane tabPane = new TabPane(transactionsTab, statementsTab);
		tabPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		GuiLayoutState.configureTabPane(tabPane, "accountsTransactions.right");
		return tabPane;
	}

	private Tab createTab(String titleKey, Node content) {
		Tab tab = new Tab(getText(titleKey), content);
		tab.setClosable(false);
		return tab;
	}

	private void showTransactionDetailNode(Node detailNode) {
		transactionContentPane.setDetail(detailNode);
	}
}
