package de.gbanking.gui.swing.panel.account;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.SimpleDateFormat;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.enu.AccountState;
import de.gbanking.gui.swing.panel.BasePanelHolder;

public class AccountDetailPanel extends BasePanelHolder {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6337300416769691751L;

	private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy");

	private boolean fullDetails;

	private DefaultComboBoxModel<AccountState> accountStateItemList;

	private JTextField accountNameText;
	private JTextField accountIbanText;
	private JTextField bankNameText;
	private JTextField accountTypText;
	private JTextField bankAccessText;
	private JTextField currencyText;
	private JTextField bicText;
	private JTextField blzText;
	private JTextField numberText;
	private JTextField subnumberText;
	private JTextField ownerNameText;
	private JTextField ownerName2Text;
	private JCheckBox isSEPAAccountText;

	private JCheckBox isOfflineAccount;
	private JComboBox<AccountState> accountStateCombo;

	private JTextField updatedAtText;

	public JTextField getAccountNameText() {
		return accountNameText;
	}

	public JTextField getAccountIbanText() {
		return accountIbanText;
	}

	public JTextField getBankNameText() {
		return bankNameText;
	}

	public JTextField getAccountTypText() {
		return accountTypText;
	}

	public JTextField getBankAccessText() {
		return bankAccessText;
	}

	public JTextField getCurrencyText() {
		return currencyText;
	}

	public JTextField getBicText() {
		return bicText;
	}

	public JTextField getBlzText() {
		return blzText;
	}

	public JTextField getNumberText() {
		return numberText;
	}

	public JTextField getSubnumberText() {
		return subnumberText;
	}

	public JTextField getOwnerNameText() {
		return ownerNameText;
	}

	public JTextField getOwnerName2Text() {
		return ownerName2Text;
	}

	public JCheckBox getIsSEPAAccountText() {
		return isSEPAAccountText;
	}

	public JTextField getUpdatedAtText() {
		return updatedAtText;
	}

	public AccountDetailPanel(boolean fullDetails) {
		this.fullDetails = fullDetails;
		createInnerAccountDetailPanel();
	}

	private void createInnerAccountDetailPanel() {
		Border accountPanelBorder = BorderFactory.createTitledBorder("Konto Details");
		setBorder(accountPanelBorder);

		JLabel accountNameLabel = new JLabel("Konto");
		accountNameLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		accountNameText = new JTextField();
		accountNameText.setHorizontalAlignment(SwingConstants.LEFT);
		accountIbanText = new JTextField();
		bankNameText = new JTextField();
		accountTypText = new JTextField();
		numberText = new JTextField();
		subnumberText = new JTextField();
		bankAccessText = new JTextField();
		currencyText = new JTextField();
		bicText = new JTextField();
		blzText = new JTextField();
		ownerNameText = new JTextField();
		ownerName2Text = new JTextField();
		isSEPAAccountText = new JCheckBox();
		updatedAtText = new JTextField();

		GridBagLayout detailsLayout = new GridBagLayout();
		setLayout(detailsLayout);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 2, 2, 2);

		double weightxLabel;

		if (fullDetails) {
			weightxLabel = 0.033;
		} else {
			weightxLabel = 0.1;
		}

		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;

		addLabelAndFieldAboveWithWeight("Konto", accountNameText, gbc, 0, 0, 1, 1, weightxLabel);
		addLabelAndFieldAboveWithWeight("IBAN", accountIbanText, gbc, 0, 1, 1, 1, weightxLabel);
		addLabelAndFieldAboveWithWeight("Bank", bankNameText, gbc, 2, 0, 1, 1, weightxLabel);
		addLabelAndFieldAboveWithWeight("Konto-Typ", accountTypText, gbc, 2, 1, 1, 1, weightxLabel);
		addLabelAndFieldAboveWithWeight("Kontonummer", numberText, gbc, 4, 0, 1, 1, weightxLabel);
		addLabelAndFieldAboveWithWeight("Unterkonto-Nr.", subnumberText, gbc, 4, 1, 1, 1, weightxLabel);
		addLabelAndFieldAboveWithWeight("Bankzugang", bankAccessText, gbc, 6, 0, 1, 1, weightxLabel);
		addLabelAndFieldAboveWithWeight("Währung", currencyText, gbc, 6, 1, 1, 1, weightxLabel);
		addLabelAndFieldAboveWithWeight("BIC", bicText, gbc, 8, 0, 1, 1, weightxLabel);
		addLabelAndFieldAboveWithWeight("Bankleitzahl", blzText, gbc, 8, 1, 1, 1, weightxLabel);
		addLabelAndFieldAboveWithWeight("Inhaber", ownerNameText, gbc, 10, 0, 1, 1, weightxLabel);
		addLabelAndFieldAboveWithWeight("Inhaber 2", ownerName2Text, gbc, 10, 1, 1, 1, weightxLabel);
		addLabelAndFieldAboveWithWeight("SEPA Konto", isSEPAAccountText, gbc, 12, 0, 1, 1, weightxLabel);
		addLabelAndFieldAboveWithWeight("Stand", updatedAtText, gbc, 12, 1, 1, 1, weightxLabel);

		if (fullDetails) {
			isOfflineAccount = new JCheckBox();
			accountStateItemList = new DefaultComboBoxModel<>();
			setAccountStateItems();
			accountStateCombo = new JComboBox<>(AccountState.values());

			addLabelAndFieldAboveWithWeight("Offline-Konto?", isOfflineAccount, gbc, 0, 2, 1, 1, weightxLabel);
			addLabelAndFieldAboveWithWeight("Konto-Status", accountStateCombo, gbc, 2, 2, 1, 1, weightxLabel);
		}
	}

	private void setAccountStateItems() {
		if (accountStateItemList == null) {
			accountStateItemList = new DefaultComboBoxModel<>();
		}
		for (AccountState accountState : AccountState.values()) {
			accountStateItemList.addElement(accountState);
		}
	}

	public void updatePanelFieldValues(BankAccount bankAccount) {

		getAccountIbanText().setText(bankAccount.getIban());
		getAccountNameText().setText(bankAccount.getAccountName());
		getAccountTypText().setText(bankAccount.getAccountType() != null ? bankAccount.getAccountType().toString() : null);
		getBankNameText().setText(bankAccount.getBankName());
		getBankAccessText().setText(String.valueOf(bankAccount.getBankAccessId()));
		getCurrencyText().setText(bankAccount.getCurrency());
		getBicText().setText(bankAccount.getBic());
		getBlzText().setText(bankAccount.getBlz());
		getNumberText().setText(bankAccount.getNumber());
		getSubnumberText().setText(bankAccount.getSubnumber());
		getOwnerNameText().setText(bankAccount.getOwnerName());
		getOwnerName2Text().setText(bankAccount.getOwnerName2());
		getIsSEPAAccountText().setEnabled(bankAccount.isSEPAAccount());

		if (fullDetails) {
			isOfflineAccount.setEnabled(bankAccount.isOfflineAccount());
			accountStateCombo.setSelectedItem(bankAccount.getAccountState() != null ? bankAccount.getAccountState() : null);
		}

		getUpdatedAtText().setText(dateFormat.format(bankAccount.getUpdatedAt().getTime()));
	}

}
