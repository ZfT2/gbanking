package de.gbanking.gui.swing.panel.bankaccess;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.gui.swing.enu.ButtonContext;
import de.gbanking.gui.swing.panel.BasePanelHolder;
import de.gbanking.gui.swing.panel.overview.BankAccessOverviewPanel;
import de.gbanking.util.TypeConverter;

public class BankAccessDetailPanel extends BasePanelHolder {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7866791760636655244L;

	private JTextField blzText;
	private JTextField bankNameText;
	private JTextField urlText;
	private JTextField portText;
	private JTextField userNameText;
	private JTextField customerIdText;
	private JTextField systemIdText;
	private JTextField tanProcedureText;
	private JTextField hbciVersionText;
	private JTextField bpdVersionText;
	private JTextField updVersionText;
	private JTextField hbciFilterTypeText;
	private JTextField activeText;
	private JTextField updatedAtText;

	private JButton buttonBankAccessEdit;
	private JButton buttonBankAccessDelete;

	private JPanel parentPanel;

	public BankAccessDetailPanel(JPanel parentPanel) {
		this.parentPanel = parentPanel;
		createInnerBankAccessDetailPanel();
	}

	void createInnerBankAccessDetailPanel() {

		Border accessDetailsPanelBorder = BorderFactory.createTitledBorder("Bankzugang Details");
		setBorder(accessDetailsPanelBorder);

		JLabel blzLabel = new JLabel("BLZ");
		blzText = new JTextField();
		JLabel bankNameLabel = new JLabel("Bank");
		bankNameText = new JTextField();
		JLabel urlLabel = new JLabel("FinTS-URL");
		urlText = new JTextField();
		JLabel portLabel = new JLabel("FinTS-Port");
		portText = new JTextField();
		JLabel userNameLabel = new JLabel("Benutzer");
		userNameText = new JTextField();
		JLabel customerIdLabel = new JLabel("Customer-ID");
		customerIdText = new JTextField();
		JLabel systemIdLabel = new JLabel("System-ID");
		systemIdText = new JTextField();
		JLabel tanProcedureLabel = new JLabel("ausgew. TAN Verfahren"); // TODO m. Liste aus allowedTwostepMechanisms..
		tanProcedureText = new JTextField();
		JLabel hbciVersionLabel = new JLabel("HBCI-Version");
		hbciVersionText = new JTextField();
		JLabel hbciFilterTypeLabel = new JLabel("HBCI-Verschlüsselung");
		hbciFilterTypeText = new JTextField();
		JLabel bpdVersionLabel = new JLabel("BPD-Version");
		bpdVersionText = new JTextField();
		JLabel updVersionLabel = new JLabel("UPD-Version");
		updVersionText = new JTextField();
		JLabel activeLabel = new JLabel("aktiviert");
		activeText = new JTextField();
		JLabel updatedAtLabel = new JLabel("Stand");
		updatedAtText = new JTextField();

		GridBagLayout accessDetailsPanelLayout = new GridBagLayout();
		GridBagConstraints gbcDetails = new GridBagConstraints();
		setLayout(accessDetailsPanelLayout);
		gbcDetails.insets = new Insets(2, 2, 2, 2);

		gbcDetails.anchor = GridBagConstraints.NORTHWEST;
		gbcDetails.fill = GridBagConstraints.HORIZONTAL;
		gbcDetails.weightx = 1;

		gbcDetails.gridy = 0;

		gbcDetails.gridx = 0;
		add(blzLabel, gbcDetails);
		gbcDetails.gridx = 1;
		add(blzText, gbcDetails);
		gbcDetails.gridx = 2;
		add(bankNameLabel, gbcDetails);
		gbcDetails.gridx = 3;
		add(bankNameText, gbcDetails);

		gbcDetails.gridy = 1;

		gbcDetails.gridx = 0;
		add(urlLabel, gbcDetails);
		gbcDetails.gridx = 1;
		add(urlText, gbcDetails);
		gbcDetails.gridx = 2;
		add(portLabel, gbcDetails);
		gbcDetails.gridx = 3;
		add(portText, gbcDetails);

		gbcDetails.gridy = 2;

		gbcDetails.gridx = 0;
		add(userNameLabel, gbcDetails);
		gbcDetails.gridx = 1;
		add(userNameText, gbcDetails);
		gbcDetails.gridx = 2;
		add(customerIdLabel, gbcDetails);
		gbcDetails.gridx = 3;
		add(customerIdText, gbcDetails);

		gbcDetails.gridy = 3;

		gbcDetails.gridx = 0;
		add(systemIdLabel, gbcDetails);
		gbcDetails.gridx = 1;
		add(systemIdText, gbcDetails);
		gbcDetails.gridx = 2;
		add(tanProcedureLabel, gbcDetails);
		gbcDetails.gridx = 3;
		add(tanProcedureText, gbcDetails);

		gbcDetails.gridy = 4;

		gbcDetails.gridx = 0;
		add(hbciVersionLabel, gbcDetails);
		gbcDetails.gridx = 1;
		add(hbciVersionText, gbcDetails);
		gbcDetails.gridx = 2;
		add(hbciFilterTypeLabel, gbcDetails);
		gbcDetails.gridx = 3;
		add(hbciFilterTypeText, gbcDetails);

		gbcDetails.gridy = 5;

		gbcDetails.gridx = 0;
		add(bpdVersionLabel, gbcDetails);
		gbcDetails.gridx = 1;
		add(bpdVersionText, gbcDetails);
		gbcDetails.gridx = 2;
		add(updVersionLabel, gbcDetails);
		gbcDetails.gridx = 3;
		add(updVersionText, gbcDetails);

		gbcDetails.gridy = 6;

		add(activeLabel, gbcDetails);
		gbcDetails.gridx = 1;
		add(activeText, gbcDetails);
		gbcDetails.gridx = 2;
		add(updatedAtLabel, gbcDetails);
		gbcDetails.gridx = 3;
		add(updatedAtText, gbcDetails);

		gbcDetails.gridy = 7;
		gbcDetails.gridx = 0;

		JButton buttonBankAccessNew = new JButton(getText("BANKACCESS_BUTTON_NEW"));
		buttonBankAccessNew.addActionListener(e -> newBankAccessDialog(ButtonContext.BUTTON_NEW));
		add(buttonBankAccessNew, gbcDetails);
		gbcDetails.gridx = 1;
		buttonBankAccessEdit = new JButton(getText("BANKACCESS_BUTTON_EDIT"));
		buttonBankAccessEdit.addActionListener(e -> newBankAccessDialog(ButtonContext.BUTTON_EDIT));
		buttonBankAccessEdit.setEnabled(false);
		add(buttonBankAccessEdit, gbcDetails);
		gbcDetails.gridx = 2;
		buttonBankAccessDelete = new JButton(getText("BANKACCESS_BUTTON_DELETE"));
		buttonBankAccessDelete.addActionListener(e -> newBankAccessDialog(ButtonContext.BUTTON_DELETE));
		buttonBankAccessDelete.setEnabled(false);
		add(buttonBankAccessDelete, gbcDetails);
	}

	public void updatePanelFieldValues(BankAccess selectedAccess) {
		blzText.setText(selectedAccess.getBlz());
		bankNameText.setText(selectedAccess.getBankName());
		urlText.setText(selectedAccess.getHbciURL());
		portText.setText(String.valueOf(selectedAccess.getPort()));
		userNameText.setText(selectedAccess.getUserId());
		customerIdText.setText(selectedAccess.getCustomerId());
		systemIdText.setText(selectedAccess.getSysId());
		tanProcedureText.setText(selectedAccess.getTanProcedure().toString());
		hbciVersionText.setText(selectedAccess.getHbciURL());
		bpdVersionText.setText(selectedAccess.getBpdVersion());
		updVersionText.setText(selectedAccess.getUpdVersion());
		hbciFilterTypeText.setText(selectedAccess.getFilterType().toString());
		activeText.setText(String.valueOf(selectedAccess.isActive()));
		updatedAtText.setText(TypeConverter.toDateStringLong(selectedAccess.getUpdatedAt()));

		((BankAccessOverviewPanel) parentPanel).setCurrentBankAccess(selectedAccess);

		buttonBankAccessEdit.setEnabled(true);
		buttonBankAccessDelete.setEnabled(true);

	}

	private void newBankAccessDialog(ButtonContext buttonContext) {
		BankAccessOverviewPanel overviewPanel = (BankAccessOverviewPanel) parentPanel;
		BankAccessNewDialogHolder bankAccessNewDialogHolder = new BankAccessNewDialogHolder((JFrame) SwingUtilities.getWindowAncestor(this), buttonContext,
				overviewPanel);
		JDialog modelDialog = bankAccessNewDialogHolder.createNewBankAccessDialog();
		modelDialog.setVisible(true);
	}

}
