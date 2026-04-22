package de.zft2.gbanking.gui.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import de.zft2.gbanking.gui.panel.moneytransfer.MoneyTransferDetailListTabPanel;
import de.zft2.gbanking.gui.panel.moneytransfer.MoneyTransferInputBasePanel;
import de.zft2.gbanking.gui.panel.moneytransfer.MoneyTransferListPanel;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class MoneyTransferOverviewPanel extends OverviewBasePanel {

	private static final Logger log = LogManager.getLogger(MoneyTransferOverviewPanel.class);
	private static final double ACCOUNT_DIVIDER = 0.22;

	private AccountListPanel accountListPanel;
	private MoneyTransferInputBasePanel moneyTransferInputPanel;
	private MoneyTransferListPanel moneyTransferListPanel;
	private BankAccount selectedAccount;
	private TabPane tabPane;

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.ACCOUNTS_MONEYTRANSFERS);

		accountListPanel = new AccountListPanel(this);

		tabPane = new TabPane();
		tabPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		tabPane.getTabs().setAll(createTab(OrderType.TRANSFER), createTab(OrderType.SCHEDULED_TRANSFER), createTab(OrderType.STANDING_ORDER));
		tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> switchTab(newTab));

		SplitPane splitPane = createSplitPane(ACCOUNT_DIVIDER, accountListPanel, tabPane);
		setOverviewContent("UI_PANEL_MONEYTRANSFERS", splitPane, show);

		tabPane.getSelectionModel().selectFirst();
		switchTab(tabPane.getTabs().get(0));
	}

	private Tab createTab(OrderType type) {
		Tab tab = new Tab(type.getPlural(), new MoneyTransferDetailListTabPanel(type, this));
		tab.setClosable(false);
		return tab;
	}

	private void switchTab(Tab tab) {
		log.info("selected Tab: {}", tab.getText());
		setActivePanels((MoneyTransferDetailListTabPanel) tab.getContent());
		if (selectedAccount != null) {
			moneyTransferInputPanel.updatePanelFieldValues(selectedAccount);
			moneyTransferListPanel.reload();
		}
	}

	private void setActivePanels(MoneyTransferDetailListTabPanel selectedTab) {
		moneyTransferInputPanel = selectedTab.getMoneyTransferInputPanel();
		moneyTransferListPanel = selectedTab.getMoneyTransferListPanel();
		moneyTransferListPanel
				.updatePanelBorder(selectedTab.getOrderType().getPlural() + " " + (selectedAccount != null ? selectedAccount.getAccountName() : ""));
	}

	public AccountListPanel getAccountListPanel() {
		return accountListPanel;
	}

	public MoneyTransferInputBasePanel getMoneyTransferInputPanel() {
		return moneyTransferInputPanel;
	}

	public MoneyTransferListPanel getMoneyTransferListPanel() {
		return moneyTransferListPanel;
	}

	public BankAccount getSelectedAccount() {
		return selectedAccount;
	}

	public void setSelectedAccount(BankAccount selectedAccount) {
		this.selectedAccount = selectedAccount;
	}
}
