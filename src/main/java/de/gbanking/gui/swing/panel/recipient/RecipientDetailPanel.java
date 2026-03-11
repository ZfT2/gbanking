package de.gbanking.gui.swing.panel.recipient;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;

import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.gui.swing.panel.BasePanelHolder;
import de.gbanking.gui.swing.panel.overview.RecipientOverviewPanel;
import de.gbanking.util.TypeConverter;

public class RecipientDetailPanel extends BasePanelHolder {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3471974189165499754L;

	private JTextField nameText;
	private JTextField ibanText;
	private JTextField bicText;
	private JTextField accountNumberText;
	private JTextField blzText;
	private JTextField bankText;
	private JTextArea noteText;
	private JTextField updatedAtText;

	private JButton buttonRecipientNew;
	private JButton buttonRecipientSave;
	private JButton buttonRecipientDelete;

	private JPanel parentPanel;

	private Recipient selectedRecipient;

	public RecipientDetailPanel(JPanel parentPanel) {
		this.parentPanel = parentPanel;
		createInnerRecipientDetailPanel();
	}

	void createInnerRecipientDetailPanel() {

		Border accessDetailsPanelBorder = BorderFactory.createTitledBorder("Empfänger/Einzahler Details");
		setBorder(accessDetailsPanelBorder);

		nameText = new JTextField();
		ibanText = new JTextField();
		bicText = new JTextField();
		accountNumberText = new JTextField();
		blzText = new JTextField();
		bankText = new JTextField();
		noteText = new JTextArea();
		updatedAtText = new JTextField();
		updatedAtText.setEnabled(false);

		buttonRecipientNew = new JButton("neu");
		buttonRecipientNew.addActionListener(e -> resetTextFields());
		buttonRecipientSave = new JButton("speichern");
		buttonRecipientSave.addActionListener(e -> saveRecipient());
		buttonRecipientDelete = new JButton("löschen");
		buttonRecipientDelete.addActionListener(e -> deleteRecipient());

		setLayout(new GridBagLayout());
		GridBagConstraints gbcDetails = new GridBagConstraints();

		gbcDetails.insets = new Insets(5, 5, 5, 5);
		gbcDetails.fill = GridBagConstraints.HORIZONTAL;
		gbcDetails.weightx = 1.0;

		JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		buttonsPanel.add(buttonRecipientNew);
		buttonsPanel.add(buttonRecipientSave);
		buttonsPanel.add(buttonRecipientDelete);
		// row col colspan rownspan
		addLabelAndFieldAbove("Name", nameText, gbcDetails, 0, 0, 2, 1);
		addLabelAndFieldAbove("Bank", bankText, gbcDetails, 0, 2, 3, 1);
		addLabelAndFieldAbove("IBAN", ibanText, gbcDetails, 2, 0, 2, 1);
		addLabelAndFieldAbove("BIC", bicText, gbcDetails, 2, 2, 3, 1);
		addLabelAndFieldAbove("Konto-Nr.", accountNumberText, gbcDetails, 4, 0, 2, 1);
		addLabelAndFieldAbove("BLZ", blzText, gbcDetails, 4, 2, 3, 1);
		addLabelAndFieldAbove("Notiz", noteText, gbcDetails, 6, 0, 3, 2);
		addLabelAndFieldAbove("Stand", updatedAtText, gbcDetails, 6, 3, 1, 1);
		addLabelAndFieldAbove(null, buttonsPanel, gbcDetails, 8, 3, 1, 1);

	}

	private void saveRecipient() {

		if (nameText.getText().isEmpty() || ibanText.getText().isEmpty()) {
			JOptionPane.showMessageDialog(parentPanel, getText("ALERT_RECIPIENT_REQUIRED_FIELD_MISSING"));
		} else {
			Recipient recipient = new Recipient(nameText.getText(), ibanText.getText());
			if (!bicText.getText().isEmpty()) {
				recipient.setBic(bicText.getText());
			} else if (!accountNumberText.getText().isEmpty()) {
				recipient.setAccountNumber(accountNumberText.getText());
			} else if (!blzText.getText().isEmpty()) {
				recipient.setBlz(blzText.getText());
			} else if (!bankText.getText().isEmpty()) {
				recipient.setBank(bankText.getText());
			} else if (!noteText.getText().isEmpty()) {
				recipient.setNote(noteText.getText());
			}
			
			recipient.setSource(Source.MANUELL);

			bean.saveRecipientToDB(recipient);

			((RecipientOverviewPanel) parentPanel).getRecipientListPanel().revalidate();
			((RecipientOverviewPanel) parentPanel).getRecipientListPanel().repaint();
		}
	}

	private void deleteRecipient() {
		bean.deleteRecipientFromDB(selectedRecipient);
	}

	private void resetTextFields() {
		nameText.setText(null);
		ibanText.setText(null);
		bicText.setText(null);
		accountNumberText.setText(null);
		blzText.setText(null);
		bankText.setText(null);
		noteText.setText(null);
		updatedAtText.setText(null);
		
		enableInputFields(true);
	}
	
	private void enableInputFields(boolean enable) {
		nameText.setEnabled(enable);
		ibanText.setEnabled(enable);
		bicText .setEnabled(enable);
		accountNumberText.setEnabled(enable);
		blzText.setEnabled(enable);
		bankText.setEnabled(enable);
	}

	public void updatePanelFieldValues(Recipient selectedRecipient) {

		nameText.setText(selectedRecipient.getName());
		ibanText.setText(String.valueOf(selectedRecipient.getIban()));
		bicText.setText(selectedRecipient.getBic());
		accountNumberText.setText(selectedRecipient.getAccountNumber());

		blzText.setText(selectedRecipient.getBlz());
		bankText.setText(selectedRecipient.getBank());
		noteText.setText(selectedRecipient.getNote());

		updatedAtText.setText(TypeConverter.toDateStringLong(selectedRecipient.getUpdatedAt()));

		((RecipientOverviewPanel) parentPanel).setCurrentRecipient(selectedRecipient);
		
		boolean isRecipientEditable = bean.isRecipientEditable(selectedRecipient);

		buttonRecipientDelete.setEnabled(isRecipientEditable);
		
		enableInputFields(isRecipientEditable);

		this.selectedRecipient = selectedRecipient;

	}

}
