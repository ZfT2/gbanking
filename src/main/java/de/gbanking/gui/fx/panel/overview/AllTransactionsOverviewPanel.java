package de.gbanking.gui.fx.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.transaction.TransactionDetailPanel;
import de.gbanking.gui.fx.panel.transaction.TransactionListPanel;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;

public class AllTransactionsOverviewPanel extends TransactionsOverviewBasePanel {

	private static final Logger log = LogManager.getLogger(AllTransactionsOverviewPanel.class);

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.ALL_TRANSACTIONS);
		log.info("Initializing AllTransactionsOverviewPanel");

		transactionDetailPanel = new TransactionDetailPanel();
		transactionDetailPanel.setMinHeight(Region.USE_PREF_SIZE);
		transactionDetailPanel.setPrefHeight(Region.USE_COMPUTED_SIZE);
		transactionDetailPanel.setMaxWidth(Double.MAX_VALUE);

		transactionListPanel = new TransactionListPanel(this);
		transactionListPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		BorderPane content = new BorderPane();
		content.setTop(transactionDetailPanel);
		content.setCenter(transactionListPanel);
		content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		BorderPane.setMargin(transactionListPanel, new javafx.geometry.Insets(8, 0, 0, 0));

		setOverviewContent("UI_PANEL_ALL_TRANSACTIONS", content, show);
	}
}