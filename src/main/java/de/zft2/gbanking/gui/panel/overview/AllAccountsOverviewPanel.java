package de.zft2.gbanking.gui.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.account.AccountDetailPanel;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;

public class AllAccountsOverviewPanel extends OverviewBasePanel {

	private static final Logger log = LogManager.getLogger(AllAccountsOverviewPanel.class);

	private AccountListPanel accountListPanel;
	private AccountDetailPanel accountDetailPanel;

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.ALL_ACCOUNTS);
		log.info("Initializing AllAccountsOverviewPanel");

		accountDetailPanel = new AccountDetailPanel(true);
		prepareDetailPanel(accountDetailPanel);

		accountListPanel = new AccountListPanel(this);

		setOverviewContent("UI_PANEL_ALL_ACCOUNTS", createTopCenterLayout(accountDetailPanel, accountListPanel), show);
	}

	public AccountDetailPanel getAccountDetailPanel() {
		return accountDetailPanel;
	}

	public AccountListPanel getAccountListPanel() {
		return accountListPanel;
	}
}