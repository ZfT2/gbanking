package de.gbanking.gui.swing.panel.moneytransfer;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JPanel;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.gui.swing.panel.overview.MoneyTransferOverviewPanel;
import de.gbanking.gui.swing.panel.recipient.RecipientListPanel;

public class MoneyTransferDetailListTabPanel extends JPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3551748913416361594L;
	
	private JPanel parentPanel;

	private MoneyTransferInputBasePanel moneyTransferInputPanel;
	private MoneyTransferListPanel moneyTransferListPanel;
	
	private OrderType orderType;

	public MoneyTransferDetailListTabPanel(OrderType orderType, JPanel parentPanel) {
		this.parentPanel = parentPanel;
		this.orderType = orderType;
		getTabbedPanel();
	}

	public MoneyTransferInputBasePanel getMoneyTransferInputPanel() {
		return moneyTransferInputPanel;
	}

	public void setMoneyTransferInputPanel(MoneyTransferInputBasePanel moneyTransferInputPanel) {
		this.moneyTransferInputPanel = moneyTransferInputPanel;
	}

	public MoneyTransferListPanel getMoneyTransferListPanel() {
		return moneyTransferListPanel;
	}

	public void setMoneyTransferListPanel(MoneyTransferListPanel moneyTransferListPanel) {
		this.moneyTransferListPanel = moneyTransferListPanel;
	}

	private void getTabbedPanel() {

		setLayout(new GridBagLayout());
		GridBagConstraints gbcDetailList = new GridBagConstraints();
		gbcDetailList.insets = new Insets(0, 0, 0, 0);

		switch (orderType) {
		case TRANSFER:
			moneyTransferInputPanel = new MoneyTransferInputPanel(this);
			break;
		case SCHEDULED_TRANSFER:
			moneyTransferInputPanel = new MoneyTransferInputSheduledPanel(this);
			break;
		case STANDING_ORDER:
			moneyTransferInputPanel = new MoneyTransferInputStandingOrderPanel(this);
			break;
		default:
			break;
		}

		gbcDetailList.anchor = GridBagConstraints.NORTHWEST;
		gbcDetailList.fill = GridBagConstraints.BOTH;
		gbcDetailList.gridx = 0;
		gbcDetailList.gridy = 0;
		gbcDetailList.weighty = 0.15;
		gbcDetailList.weightx = 0.5;
		add(moneyTransferInputPanel, gbcDetailList);

		RecipientListPanel recipientListPanel = new RecipientListPanel(this);
		gbcDetailList.gridx = 1;
		gbcDetailList.weightx = 0.5;
		add(recipientListPanel, gbcDetailList);

		moneyTransferListPanel = new MoneyTransferListPanel(orderType, this);

		gbcDetailList.gridx = 0;
		gbcDetailList.gridy = 1;
		gbcDetailList.gridwidth = 2;
		gbcDetailList.weighty = 0.85;
		gbcDetailList.weightx = 1.0;
		add(moneyTransferListPanel, gbcDetailList);
	}

	public BankAccount getSelectedAccount() {
		return ((MoneyTransferOverviewPanel) parentPanel).getSelectedAccount();
	}

	public OrderType getOrderType() {
		return orderType;
	}

}
