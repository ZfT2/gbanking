package de.gbanking.gui.swing.panel.moneytransfer;

import java.text.SimpleDateFormat;

import javax.swing.JFormattedTextField;
import javax.swing.JPanel;

import de.gbanking.util.TypeConverter;

public class MoneyTransferInputSheduledPanel extends MoneyTransferInputBasePanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3271505927300868995L;

	protected JFormattedTextField tfExecutionDate = new JFormattedTextField(
			new SimpleDateFormat(TypeConverter.DATE_PATTERN_SHORT));

	public MoneyTransferInputSheduledPanel(JPanel parent) {
		super(parent);
		createInnerMoneyTransferInputPanel();
	}

	private void createInnerMoneyTransferInputPanel() {

		addLabelAndFieldAbove("Auftraggeberkonto", tfAccountSender, gbc, 12, 0, 2, 1);
		addLabelAndFieldAbove("Ausführungs-Datum", tfExecutionDate, gbc, 12, 2, 1, 1);

		buttonSubmit.setText("Terminüberweisung speichern");

	}

}
