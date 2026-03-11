package de.gbanking.gui.swing.panel.overview;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.border.Border;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.gui.swing.enu.PageContext;
import de.gbanking.gui.swing.panel.account.AccountListPanel;
import de.gbanking.gui.swing.panel.moneytransfer.MoneyTransferDetailListTabPanel;
import de.gbanking.gui.swing.panel.moneytransfer.MoneyTransferInputBasePanel;
import de.gbanking.gui.swing.panel.moneytransfer.MoneyTransferListPanel;

public class MoneyTransferOverviewPanel extends OverviewBasePanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2726707342857213948L;

	private static Logger log = LogManager.getLogger(MoneyTransferOverviewPanel.class);

	private AccountListPanel accountListPanel;
	private MoneyTransferInputBasePanel moneyTransferInputPanel;
	private MoneyTransferListPanel moneyTransferListPanel;

	private BankAccount selectedAccount;

	@Override
	public void createOverallPanel(boolean show) {

		setPageContext(PageContext.ACCOUNTS_MONEYTRANSFERS);

		Border accountsMoneyTransfersPanelBorder = BorderFactory.createTitledBorder("Aufträge");
		setBorder(accountsMoneyTransfersPanelBorder);
		log.info("main Panel Width / Height: {} / {}", getWidth(), getHeight());
		setLayout(new GridBagLayout());

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 2, 2, 2);

		accountListPanel = new AccountListPanel(this);

		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0.2;
		gbc.weighty = 1.0;

		add(accountListPanel, gbc);

		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

		JPanel moneyTransferDetailListPanel = new MoneyTransferDetailListTabPanel(OrderType.TRANSFER, this);
		tabbedPane.addTab(OrderType.TRANSFER.getPlural(), moneyTransferDetailListPanel);

		JPanel scheduledMoneyTransferDetailListPanel = new MoneyTransferDetailListTabPanel(OrderType.SCHEDULED_TRANSFER, this);
		tabbedPane.addTab(OrderType.SCHEDULED_TRANSFER.getPlural(), scheduledMoneyTransferDetailListPanel);

		JPanel standingOrderTransferDetailListPanel = new MoneyTransferDetailListTabPanel(OrderType.STANDING_ORDER, this);
		tabbedPane.addTab(OrderType.STANDING_ORDER.getPlural(), standingOrderTransferDetailListPanel);

		tabbedPane.addChangeListener(e -> switchTab(tabbedPane));

		tabbedPane.setSelectedIndex(0);
		setActivePanels((MoneyTransferDetailListTabPanel) moneyTransferDetailListPanel);

		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 1;
		gbc.weightx = 0.8;

		add(tabbedPane, gbc);

		setEnabled(show);

	}

	private void switchTab(JTabbedPane tabbedPane) {
		log.info("selected Tab: {} ", tabbedPane.getSelectedIndex());
		MoneyTransferDetailListTabPanel selectedTab = (MoneyTransferDetailListTabPanel) tabbedPane
				.getComponentAt(tabbedPane.getSelectedIndex());
		setActivePanels(selectedTab);
	}

	private void setActivePanels(MoneyTransferDetailListTabPanel selectedTab) {
		setMoneyTransferInputPanel(selectedTab.getMoneyTransferInputPanel());
		setMoneyTransferListPanel(selectedTab.getMoneyTransferListPanel());
		selectedTab.getMoneyTransferListPanel().updatePanelBorder(
				selectedTab.getOrderType().getPlural() + " " + (selectedAccount != null ? selectedAccount.getAccountName() : ""));
	}

	private void setMoneyTransferInputPanel(MoneyTransferInputBasePanel moneyTransferInputPanel) {
		this.moneyTransferInputPanel = moneyTransferInputPanel;
	}

	private void setMoneyTransferListPanel(MoneyTransferListPanel moneyTransferListPanel) {
		this.moneyTransferListPanel = moneyTransferListPanel;
	}

	public AccountListPanel getAccountListPanel() {
		return accountListPanel;
	}

	public MoneyTransferInputBasePanel getMoneyTransferInputPanel() {
		return moneyTransferInputPanel;
	}

	public MoneyTransferListPanel getMoneyTransferListPanel() {
		return moneyTransferListPanel;
	}

	public BankAccount getSelectedAccount() {
		return selectedAccount;
	}

	public void setSelectedAccount(BankAccount selectedAccount) {
		this.selectedAccount = selectedAccount;
	}

}
