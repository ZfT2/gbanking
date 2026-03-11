package de.gbanking.gui.swing.panel.moneytransfer;

import java.util.stream.IntStream;

import javax.swing.JComboBox;
import javax.swing.JPanel;

import de.gbanking.db.dao.enu.StandingorderMode;

public class MoneyTransferInputStandingOrderPanel extends MoneyTransferInputBasePanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3271505927300868995L;

	private JComboBox<String> dayCombo = new JComboBox<>(
			IntStream.range(1, 31).sorted().mapToObj(String::valueOf).toArray(String[]::new));

	private JComboBox<StandingorderMode> cycleCombo = new JComboBox<>(StandingorderMode.values());

	public MoneyTransferInputStandingOrderPanel(JPanel parent) {
		super(parent);
		createInnerMoneyTransferInputPanel();
	}

	private void createInnerMoneyTransferInputPanel() {

		dayCombo.addItem("ultimo");

		addLabelAndFieldAbove("Auftraggeberkonto", tfAccountSender, gbc, 12, 0, 1, 1);
		addLabelAndFieldAbove("Tag", dayCombo, gbc, 12, 1, 1, 1);
		addLabelAndFieldAbove("Intervall", cycleCombo, gbc, 12, 2, 1, 1);

		buttonSubmit.setText("Dauerauftrag speichern");
	}

}
