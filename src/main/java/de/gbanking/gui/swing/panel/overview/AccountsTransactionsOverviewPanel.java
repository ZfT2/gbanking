package de.gbanking.gui.swing.panel.overview;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.border.Border;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.gui.swing.enu.PageContext;
import de.gbanking.gui.swing.panel.account.AccountDetailPanel;
import de.gbanking.gui.swing.panel.account.AccountListPanel;
import de.gbanking.gui.swing.panel.transaction.TransactionDetailPanel;
import de.gbanking.gui.swing.panel.transaction.TransactionListPanel;

public class AccountsTransactionsOverviewPanel extends TransactionsOverviewBasePanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7298653905060329248L;

	private static Logger log = LogManager.getLogger(AccountsTransactionsOverviewPanel.class);

	private AccountListPanel accountListPanel;
	private AccountDetailPanel accountDetailPanel;

	@Override
	public void createOverallPanel(boolean show) {

		setPageContext(PageContext.ACCOUNTS_TRANSACTIONS);

		Border mainPanelBorder = BorderFactory.createTitledBorder("Konten / Umsätze");
		setBorder(mainPanelBorder);
		log.info("main Panel Width / Height: {} / {}", getWidth(), getHeight());
		GridBagLayout mainlayout = new GridBagLayout();
		setLayout(mainlayout);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 2, 2, 2);

		accountListPanel = new AccountListPanel(this);

		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridheight = 2;
		gbc.weightx = 0.05;

		add(accountListPanel, gbc);

		accountDetailPanel = new AccountDetailPanel(false);

		gbc.anchor = GridBagConstraints.NORTHEAST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.gridheight = 1;
		gbc.weightx = 0.95;
		gbc.weighty = 0.1;
		add(accountDetailPanel, gbc);

		transactionDetailPanel = new TransactionDetailPanel(this);

		transactionListPanel = new TransactionListPanel(this);

		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 1;
		gbc.gridy = 1;
		gbc.weighty = 0.9;

		add(transactionListPanel, gbc);

		setEnabled(show);
	}

	public AccountListPanel getAccountListPanel() {
		return accountListPanel;
	}

	public AccountDetailPanel getAccountDetailPanel() {
		return accountDetailPanel;
	}

	public void enableTransactionDetailPanel() {
		GridBagLayout layout = (GridBagLayout) getLayout();
		GridBagConstraints gbc = layout.getConstraints(accountDetailPanel);
		remove(accountDetailPanel);
		transactionDetailPanel.setEnabled(true);
		add(transactionDetailPanel, gbc);
		this.revalidate();
		this.repaint();
	}

}
