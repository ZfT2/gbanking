package de.gbanking.gui.fx.panel.overview;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.bankaccess.BankAccessDetailPanel;
import de.gbanking.gui.fx.panel.bankaccess.BankAccessListPanel;
import javafx.scene.layout.BorderPane;

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

		bankAccessDetailPanel.setPrefHeight(340);
		bankAccessDetailPanel.setMinHeight(290);
		bankAccessDetailPanel.setMaxWidth(Double.MAX_VALUE);

		bankAccessListPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		BorderPane content = new BorderPane();
		content.setTop(bankAccessDetailPanel);
		content.setCenter(bankAccessListPanel);
		content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		BorderPane.setMargin(bankAccessListPanel, new javafx.geometry.Insets(8, 0, 0, 0));

		setOverviewContent("UI_PANEL_BANK_ACCESS", content, show);
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