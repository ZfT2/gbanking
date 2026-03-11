package de.gbanking.gui.fx.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.account.AccountDetailPanel;
import de.gbanking.gui.fx.panel.account.AccountListPanel;
import javafx.scene.layout.BorderPane;

public class AllAccountsOverviewPanel extends OverviewBasePanel {

	private static final Logger log = LogManager.getLogger(AllAccountsOverviewPanel.class);

	private AccountListPanel accountListPanel;
	private AccountDetailPanel accountDetailPanel;

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.ALL_ACCOUNTS);
		log.info("Initializing AllAccountsOverviewPanel");

		accountDetailPanel = new AccountDetailPanel(true);
		accountDetailPanel.setPrefHeight(380);
		accountDetailPanel.setMinHeight(320);
		accountDetailPanel.setMaxWidth(Double.MAX_VALUE);

		accountListPanel = new AccountListPanel(this);
		accountListPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		BorderPane content = new BorderPane();
		content.setTop(accountDetailPanel);
		content.setCenter(accountListPanel);
		content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		BorderPane.setMargin(accountListPanel, new javafx.geometry.Insets(8, 0, 0, 0));

		setOverviewContent("UI_PANEL_ALL_ACCOUNTS", content, show);
	}

	public AccountDetailPanel getAccountDetailPanel() {
		return accountDetailPanel;
	}

	public AccountListPanel getAccountListPanel() {
		return accountListPanel;
	}
}