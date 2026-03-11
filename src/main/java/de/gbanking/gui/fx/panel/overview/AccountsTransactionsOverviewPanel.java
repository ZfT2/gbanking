package de.gbanking.gui.fx.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.account.AccountDetailPanel;
import de.gbanking.gui.fx.panel.account.AccountListPanel;
import de.gbanking.gui.fx.panel.transaction.TransactionDetailPanel;
import de.gbanking.gui.fx.panel.transaction.TransactionListPanel;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AccountsTransactionsOverviewPanel extends TransactionsOverviewBasePanel {

	private static final Logger log = LogManager.getLogger(AccountsTransactionsOverviewPanel.class);

	private AccountListPanel accountListPanel;
	private AccountDetailPanel accountDetailPanel;

	private BorderPane rightPane;
	private SplitPane mainSplit;
	private VBox rightContentBox;

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.ACCOUNTS_TRANSACTIONS);

		log.info("Creating AccountsTransactionsOverviewPanel");

		Label title = new Label(getText("UI_PANEL_ACCOUNTS_TRANSACTIONS"));
		title.setStyle("-fx-font-size:16px; -fx-font-weight:bold;");

		mainSplit = new SplitPane();
		accountListPanel = new AccountListPanel(this);

		rightPane = new BorderPane();
		rightContentBox = new VBox(8);

		accountDetailPanel = new AccountDetailPanel(false);
		accountDetailPanel.setPrefHeight(260);
		accountDetailPanel.setMinHeight(220);

		transactionDetailPanel = new TransactionDetailPanel();
		transactionDetailPanel.setPrefHeight(360);
		transactionDetailPanel.setMinHeight(280);

		transactionListPanel = new TransactionListPanel(this);
		VBox.setVgrow(transactionListPanel, Priority.ALWAYS);

		rightContentBox.getChildren().addAll(accountDetailPanel, transactionListPanel);
		rightPane.setCenter(rightContentBox);

		mainSplit.getItems().addAll(accountListPanel, rightPane);
		mainSplit.setDividerPositions(0.22);

		VBox root = new VBox(8, title, mainSplit);
		root.setPadding(new Insets(5));
		VBox.setVgrow(mainSplit, Priority.ALWAYS);

		getChildren().clear();
		getChildren().add(root);

		setDisable(!show);
	}

	public AccountListPanel getAccountListPanel() {
		return accountListPanel;
	}

	public AccountDetailPanel getAccountDetailPanel() {
		return accountDetailPanel;
	}

	public void enableTransactionDetailPanel() {
		if (rightContentBox.getChildren().isEmpty() || rightContentBox.getChildren().get(0) != transactionDetailPanel) {
			rightContentBox.getChildren().set(0, transactionDetailPanel);
		}
		transactionDetailPanel.setDisable(false);
	}
}