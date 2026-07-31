package de.zft2.gbanking.gui.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.account.AccountDetailPanel;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import de.zft2.gbanking.gui.panel.layout.DetailListPane;

public class AllAccountsOverviewPanel extends OverviewBasePanel {

	private static final Logger log = LogManager.getLogger(AllAccountsOverviewPanel.class);

	private AccountListPanel accountListPanel;
	private AccountDetailPanel accountDetailPanel;

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.ALL_ACCOUNTS);
		log.info("Initializing AllAccountsOverviewPanel");

		accountDetailPanel = new AccountDetailPanel(true, () -> refreshAccountList());
		accountListPanel = new AccountListPanel(this);

		setOverviewContent("UI_PANEL_ALL_ACCOUNTS", new DetailListPane(accountDetailPanel, accountListPanel), show);
	}

	public AccountDetailPanel getAccountDetailPanel() {
		return accountDetailPanel;
	}

	public AccountListPanel getAccountListPanel() {
		return accountListPanel;
	}

	@Override
	public void refreshOnShow() {
		refreshAccountList();
	}

	private void refreshAccountList() {
		if (accountListPanel != null) {
			accountListPanel.refreshModelAccount();
			if (accountListPanel.getSelectedAccount() == null) {
				accountDetailPanel.updatePanelFieldValues(null);
			}
		}
	}
}
