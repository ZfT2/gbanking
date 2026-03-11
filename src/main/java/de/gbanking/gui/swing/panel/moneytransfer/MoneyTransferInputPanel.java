package de.gbanking.gui.swing.panel.moneytransfer;

import javax.swing.JComboBox;
import javax.swing.JPanel;

import de.gbanking.db.dao.enu.OrderType;

public class MoneyTransferInputPanel extends MoneyTransferInputBasePanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3271505927300868995L;

	private JComboBox<OrderType> orderTypeCombo = new JComboBox<>(
			new OrderType[] { OrderType.TRANSFER, OrderType.REALTIME_TRANSFER });

	public MoneyTransferInputPanel(JPanel parent) {
		super(parent);
		createInnerMoneyTransferInputPanel();
	}

	private void createInnerMoneyTransferInputPanel() {

		addLabelAndFieldAbove("Auftraggeberkonto", tfAccountSender, gbc, 12, 0, 2, 1);
		addLabelAndFieldAbove("Art", orderTypeCombo, gbc, 12, 2, 1, 1);

		buttonSubmit.setText("Überweisung speichern");

	}

}
