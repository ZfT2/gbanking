package de.gbanking.gui.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.gui.enu.PageContext;
import de.gbanking.gui.panel.transaction.TransactionDetailPanel;
import de.gbanking.gui.panel.transaction.TransactionListPanel;

public class AllTransactionsOverviewPanel extends TransactionsOverviewBasePanel {

	private static final Logger log = LogManager.getLogger(AllTransactionsOverviewPanel.class);

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.ALL_TRANSACTIONS);
		log.info("Initializing AllTransactionsOverviewPanel");

		transactionDetailPanel = new TransactionDetailPanel(this);
		prepareDetailPanel(transactionDetailPanel);

		transactionListPanel = new TransactionListPanel(this);

		setOverviewContent("UI_PANEL_ALL_TRANSACTIONS", createTopCenterLayout(transactionDetailPanel, transactionListPanel), show);
	}
}
