package de.gbanking.gui.swing.panel.overview;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.border.Border;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.gui.swing.enu.PageContext;
import de.gbanking.gui.swing.panel.transaction.TransactionDetailPanel;
import de.gbanking.gui.swing.panel.transaction.TransactionListPanel;

public class AllTransactionsOverviewPanel extends TransactionsOverviewBasePanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8202687637410144952L;
	
	private static Logger log = LogManager.getLogger(AllTransactionsOverviewPanel.class);

	@Override
	public void createOverallPanel(boolean show) {

		setPageContext(PageContext.ALL_TRANSACTIONS);

		Border mainPanelBorder = BorderFactory.createTitledBorder("Alle Umsätze");
		setBorder(mainPanelBorder);
		log.info("main Panel Width / Height: {} / {}", getWidth(), getHeight());
		GridBagLayout mainlayout = new GridBagLayout();
		setLayout(mainlayout);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 2, 2, 2);

		transactionDetailPanel = new TransactionDetailPanel(this);

		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridx = 0;
		gbc.gridy = 0;
//		gbc.gridheight = 1;
		gbc.weightx = 1;
		gbc.weighty = 0.1;
		add(transactionDetailPanel, gbc);

		transactionListPanel = new TransactionListPanel(this);

		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.weighty = 0.9;

		add(transactionListPanel, gbc);

		setEnabled(show);
	}

}
