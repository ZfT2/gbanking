package de.gbanking.gui.swing.panel.overview;

import de.gbanking.gui.swing.panel.transaction.TransactionDetailPanel;
import de.gbanking.gui.swing.panel.transaction.TransactionListPanel;

public abstract class TransactionsOverviewBasePanel extends OverviewBasePanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2596774929336401820L;
	
	protected TransactionListPanel transactionListPanel;
	protected TransactionDetailPanel transactionDetailPanel;
	
	
	public TransactionListPanel getTransactionListPanel() {
		return transactionListPanel;
	}

	public TransactionDetailPanel getTransactionDetailPanel() {
		return transactionDetailPanel;
	}

}
