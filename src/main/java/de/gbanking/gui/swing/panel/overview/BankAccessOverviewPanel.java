package de.gbanking.gui.swing.panel.overview;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.border.Border;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.gui.swing.panel.bankaccess.BankAccessDetailPanel;
import de.gbanking.gui.swing.panel.bankaccess.BankAccessListPanel;

public class BankAccessOverviewPanel extends OverviewBasePanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1958287185575354451L;

	private BankAccessDetailPanel bankAccessDetailPanel;
	private BankAccessListPanel bankAccessListPanel;

	private BankAccess currentBankAccess;

	public BankAccessOverviewPanel(/*ActionListener actionEvent*/) {
		bankAccessDetailPanel = new BankAccessDetailPanel(this/*, actionEvent*/);
		bankAccessListPanel = new BankAccessListPanel(this);
	}

	@Override
	public void createOverallPanel(boolean show) {

		Border mainPanelBorder = BorderFactory.createTitledBorder("Bankzugänge");
		setBorder(mainPanelBorder);

		GridBagLayout bankAccesslayout = new GridBagLayout();
		setLayout(bankAccesslayout);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 2, 2, 2);

		gbc.anchor = GridBagConstraints.NORTHEAST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weighty = 0.25;
		add(bankAccessDetailPanel, gbc);

		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 0;
		gbc.gridy = 1;

		gbc.weightx = 1;
		gbc.weighty = 0.75;

		add(bankAccessListPanel, gbc);

		setEnabled(show);
	}

	public BankAccessDetailPanel getBankAccessDetailPanel() {
		return bankAccessDetailPanel;
	}

	public BankAccessListPanel getBankAccessListPanel() {
		return bankAccessListPanel;
	}

	public BankAccess getCurrentBankAccess() {
		return currentBankAccess;
	}

	public void setCurrentBankAccess(BankAccess currentBankAccess) {
		this.currentBankAccess = currentBankAccess;
	}

}
