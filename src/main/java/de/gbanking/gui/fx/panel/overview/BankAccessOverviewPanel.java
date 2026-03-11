package de.gbanking.gui.fx.panel.overview;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.bankaccess.BankAccessDetailPanel;
import de.gbanking.gui.fx.panel.bankaccess.BankAccessListPanel;

public class BankAccessOverviewPanel extends OverviewBasePanel {

	private BankAccessDetailPanel bankAccessDetailPanel;
	private BankAccessListPanel bankAccessListPanel;
	private BankAccess currentBankAccess;

	public BankAccessOverviewPanel() {
		bankAccessDetailPanel = new BankAccessDetailPanel(this);
		bankAccessListPanel = new BankAccessListPanel(this);
	}

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.BANKACCESS);
		prepareDetailPanel(bankAccessDetailPanel);

		setOverviewContent("UI_PANEL_BANK_ACCESS", createTopCenterLayout(bankAccessDetailPanel, bankAccessListPanel), show);
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