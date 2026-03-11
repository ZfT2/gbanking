package de.gbanking.gui.fx.panel.overview;

import de.gbanking.gui.fx.panel.transaction.TransactionDetailPanel;
import de.gbanking.gui.fx.panel.transaction.TransactionListPanel;

public abstract class TransactionsOverviewBasePanel extends OverviewBasePanel {

	protected TransactionListPanel transactionListPanel;
	protected TransactionDetailPanel transactionDetailPanel;

	public TransactionListPanel getTransactionListPanel() {
		return transactionListPanel;
	}

	public TransactionDetailPanel getTransactionDetailPanel() {
		return transactionDetailPanel;
	}
}