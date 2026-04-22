package de.zft2.gbanking.gui.panel.overview;

import de.zft2.gbanking.gui.panel.transaction.TransactionDetailPanel;
import de.zft2.gbanking.gui.panel.transaction.TransactionListPanel;

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