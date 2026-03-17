package de.gbanking.gui.fx.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.account.AccountDetailPanel;
import de.gbanking.gui.fx.panel.account.AccountListPanel;
import de.gbanking.gui.fx.panel.transaction.TransactionDetailPanel;
import de.gbanking.gui.fx.panel.transaction.TransactionListPanel;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class AccountsTransactionsOverviewPanel extends TransactionsOverviewBasePanel {

	private static final Logger log = LogManager.getLogger(AccountsTransactionsOverviewPanel.class);

	private AccountListPanel accountListPanel;
	private AccountDetailPanel accountDetailPanel;
	private VBox rightContentBox;

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.ACCOUNTS_TRANSACTIONS);
		log.info("Creating AccountsTransactionsOverviewPanel");

		accountListPanel = new AccountListPanel(this);

		accountDetailPanel = new AccountDetailPanel(false);
		accountDetailPanel.setMinHeight(Region.USE_PREF_SIZE);
		accountDetailPanel.setPrefHeight(Region.USE_COMPUTED_SIZE);
		accountDetailPanel.setMaxWidth(Double.MAX_VALUE);

		transactionDetailPanel = new TransactionDetailPanel();
		transactionDetailPanel.setMinHeight(Region.USE_PREF_SIZE);
		transactionDetailPanel.setPrefHeight(Region.USE_COMPUTED_SIZE);
		transactionDetailPanel.setMaxWidth(Double.MAX_VALUE);

		transactionListPanel = new TransactionListPanel(this);
		transactionListPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		rightContentBox = new VBox(8, accountDetailPanel, transactionListPanel);
		rightContentBox.setFillWidth(true);
		rightContentBox.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		VBox.setVgrow(transactionListPanel, Priority.ALWAYS);

		BorderPane rightPane = new BorderPane(rightContentBox);
		rightPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		SplitPane mainSplit = new SplitPane(accountListPanel, rightPane);
		mainSplit.setDividerPositions(0.22);
		mainSplit.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		setOverviewContent("UI_PANEL_ACCOUNTS_TRANSACTIONS", mainSplit, show);
	}

	public AccountListPanel getAccountListPanel() {
		return accountListPanel;
	}

	public AccountDetailPanel getAccountDetailPanel() {
		return accountDetailPanel;
	}

	public void enableAccountDetailPanel() {
		if (rightContentBox.getChildren().isEmpty() || rightContentBox.getChildren().get(0) != accountDetailPanel) {
			rightContentBox.getChildren().set(0, accountDetailPanel);
		}
		accountDetailPanel.setDisable(false);
	}

	public void enableTransactionDetailPanel() {
		if (rightContentBox.getChildren().isEmpty() || rightContentBox.getChildren().get(0) != transactionDetailPanel) {
			rightContentBox.getChildren().set(0, transactionDetailPanel);
		}
		transactionDetailPanel.setDisable(false);
	}

	@Override
	public void refreshOnShow() {
		if (accountListPanel != null) {
			accountListPanel.reload();
		}
		if (transactionListPanel != null) {
			transactionListPanel.updateModelBooking(java.util.List.of());
			transactionListPanel.updatePanelBorder(getText("UI_PANEL_TRANSACTIONS"));
		}
		if (rightContentBox != null && accountDetailPanel != null) {
			rightContentBox.getChildren().set(0, accountDetailPanel);
		}
	}
}