package de.gbanking.gui.swing.panel.moneytransfer;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.Recipient;
import de.gbanking.gui.swing.model.dto.MoneyTransferForm;
import de.gbanking.gui.swing.panel.BasePanelHolder;

public abstract class MoneyTransferInputBasePanel extends BasePanelHolder {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8310067884682054946L;

	private JPanel parentPanel;
	
	protected GridBagConstraints gbc;

	protected JTextField tfRecipientName = new JTextField(20);
	protected JTextField tfIBAN = new JTextField(20);
	protected JTextField tfBIC = new JTextField(20);
	protected JTextField tfBank = new JTextField(100);
	protected JTextField tfAmount = new JTextField(10);
	protected JTextArea tfPurpose = new JTextArea();
	protected JTextField tfAccountSender = new JTextField(30);

	protected JButton buttonSubmit = new JButton("");
	
	private MoneyTransfer currentMoneytransfer;

	protected MoneyTransferInputBasePanel(JPanel parent) {
		this.parentPanel = parent;
		createInnerMoneyTransferInputBasePanel();
	}

	private void createInnerMoneyTransferInputBasePanel() {

		setLayout(new GridBagLayout());
		gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;

		addLabelAndFieldAbove("Empfänger: Name, Vorname / Firma", tfRecipientName, gbc, 0, 0, 3, 1);
		addLabelAndFieldAbove("IBAN des Empfängers", tfIBAN, gbc, 2, 0, 3, 1);
		addLabelAndFieldAbove("BIC", tfBIC, gbc, 4, 0, 1, 1);
		addLabelAndFieldAbove("Kreditinstitut", tfBank, gbc, 4, 1, 2, 1);
		addLabelAndFieldAbove("Währung", new JLabel("EUR"), gbc, 6, 1, 1, 1);
		addLabelAndFieldAbove("Betrag", tfAmount, gbc, 6, 2, 2, 1);
		addLabelAndFieldAbove("Verwendungszweck", tfPurpose, gbc, 8, 0, 3, 3);
		
		tfAccountSender.setEnabled(false);
		
		JButton buttonNew = new JButton("Neu...");
		buttonNew.addActionListener(e -> resetTextFields());

		buttonSubmit.addActionListener(e -> saveTransfer());
		
		JButton buttonDelete = new JButton("Löschen");
		buttonDelete.addActionListener(e -> deleteTransfer());

		JButton buttonCancel = new JButton("Abbrechen");
		buttonCancel.addActionListener(e -> resetTextFields());
		
		JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		buttonsPanel.add(buttonNew);		
		buttonsPanel.add(buttonSubmit);
		buttonsPanel.add(buttonDelete);
		buttonsPanel.add(buttonCancel);
		
		gbc.gridx = 0;
		gbc.gridwidth = 3;
		gbc.gridy = 14;
		add(buttonsPanel, gbc);
	}
	
	protected void saveTransfer() {
		BankAccount account = ((MoneyTransferDetailListTabPanel) parentPanel).getSelectedAccount();

		if (tfRecipientName.getText().isEmpty() || tfIBAN.getText().isEmpty() || tfBank.getText().isEmpty()
				|| tfAmount.getText().isEmpty() || tfPurpose.getText().isEmpty()) {
			JOptionPane.showMessageDialog(parentPanel, getText("ALERT_MONEYTRANSFER_REQUIRED_FIELD_MISSING"));
		} else {
			MoneyTransferForm moneyTransfer = new MoneyTransferForm(account, tfRecipientName.getText(),
					tfIBAN.getText(), tfBIC.getText(), tfBank.getText(), new BigDecimal(tfAmount.getText()),
					tfPurpose.getText());

			bean.saveMoneyTransferToDB(moneyTransfer);
			
			((MoneyTransferDetailListTabPanel) parentPanel).getMoneyTransferListPanel().revalidate();
			((MoneyTransferDetailListTabPanel) parentPanel).getMoneyTransferListPanel().repaint();
		}
	}
	
	protected void deleteTransfer() {
		bean.deleteMoneyTransferFromDB(currentMoneytransfer);
	}
	
	protected void resetTextFields() {
		tfRecipientName.setText(null);
		tfIBAN.setText(null);
		tfBIC.setText(null);
		tfBank.setText(null);
		tfAmount.setText(null);
		tfPurpose.setText(null);
	}

	void updatePanelFieldValues(MoneyTransfer selectedMoneytransfer) {
		currentMoneytransfer =  selectedMoneytransfer;
		tfAmount.setText(selectedMoneytransfer.getAmount().toString());
		tfPurpose.setText(selectedMoneytransfer.getPurpose());
		updatePanelFieldValues(selectedMoneytransfer.getRecipient());
	}
	
	public void updatePanelFieldValues(Recipient selectedRecipient) {
		tfRecipientName.setText(selectedRecipient.getName());
		tfIBAN.setText(selectedRecipient.getIban());
		tfBIC.setText(selectedRecipient.getBic());
		tfBank.setText(selectedRecipient.getBank());
	}

	public void updatePanelFieldValues(BankAccount selectedAccount) {
		tfAccountSender.setText(selectedAccount.getAccountName());
	}
}
