package de.zft2.gbanking.gui.panel.overview;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.bankaccess.BankAccessDetailPanel;
import de.zft2.gbanking.gui.panel.bankaccess.BankAccessListPanel;

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