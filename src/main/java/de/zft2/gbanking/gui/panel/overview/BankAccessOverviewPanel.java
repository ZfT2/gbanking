package de.zft2.gbanking.gui.panel.overview;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.bankaccess.BankAccessDetailPanel;
import de.zft2.gbanking.gui.panel.bankaccess.BankAccessListPanel;
import de.zft2.gbanking.gui.panel.bankaccess.BankMessagePanel;
import de.zft2.gbanking.gui.panel.layout.DetailListPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class BankAccessOverviewPanel extends OverviewBasePanel {

	private BankAccessDetailPanel bankAccessDetailPanel;
	private BankAccessListPanel bankAccessListPanel;
	private BankMessagePanel bankMessagePanel;
	private TabPane tabPane;
	private Tab bankMessagesTab;
	private BankAccess currentBankAccess;

	public BankAccessOverviewPanel() {
		bankAccessDetailPanel = new BankAccessDetailPanel(this);
		bankAccessListPanel = new BankAccessListPanel(this);
		bankMessagePanel = new BankMessagePanel();
	}

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.BANKACCESS);
		setOverviewContent("UI_PANEL_BANK_ACCESS", createTabPane(), show);
	}

	private TabPane createTabPane() {
		Tab bankAccessTab = new Tab(getText("UI_TAB_BANK_ACCESSES"), new DetailListPane(bankAccessDetailPanel, bankAccessListPanel));
		bankMessagesTab = new Tab(getText("UI_TAB_BANK_MESSAGES"), bankMessagePanel);
		bankAccessTab.setClosable(false);
		bankMessagesTab.setClosable(false);

		tabPane = new TabPane(bankAccessTab, bankMessagesTab);
		tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		tabPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		updateBankMessagesTabState();
		return tabPane;
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
		updateBankMessagesTabState();
		if (bankMessagePanel != null) {
			bankMessagePanel.updateBankAccess(currentBankAccess);
		}
	}

	@Override
	public void refreshOnShow() {
		bankAccessListPanel.refreshModelBankAccess();
		updateBankMessagesTabState();
		if (bankMessagePanel != null) {
			bankMessagePanel.updateBankAccess(currentBankAccess);
		}
	}

	private void updateBankMessagesTabState() {
		if (bankMessagesTab == null) {
			return;
		}

		boolean supported = currentBankAccess != null && bean.supportsBankMessages(currentBankAccess);
		bankMessagesTab.setDisable(!supported);
		if (!supported && tabPane != null && tabPane.getSelectionModel().getSelectedItem() == bankMessagesTab) {
			tabPane.getSelectionModel().selectFirst();
		}
	}
}
