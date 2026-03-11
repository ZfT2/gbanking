package de.gbanking.gui.fx.panel.account;

import java.text.SimpleDateFormat;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.enu.AccountState;
import de.gbanking.gui.fx.panel.AbstractReadonlyDetailPanel;
import javafx.collections.FXCollections;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;

public class AccountDetailPanel extends AbstractReadonlyDetailPanel {

	private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy");
	private final boolean fullDetails;

	private final TextField accountNameText = new TextField();
	private final TextField accountIbanText = new TextField();
	private final TextField bankNameText = new TextField();
	private final TextField accountTypText = new TextField();
	private final TextField bankAccessText = new TextField();
	private final TextField currencyText = new TextField();
	private final TextField bicText = new TextField();
	private final TextField blzText = new TextField();
	private final TextField numberText = new TextField();
	private final TextField subnumberText = new TextField();
	private final TextField ownerNameText = new TextField();
	private final TextField ownerName2Text = new TextField();
	private final CheckBox isSEPAAccount = new CheckBox();
	private final TextField updatedAtText = new TextField();

	private CheckBox isOfflineAccount;
	private ComboBox<AccountState> accountStateCombo;

	public AccountDetailPanel(boolean fullDetails) {
		super("UI_PANEL_ACCOUNT_DETAILS");
		this.fullDetails = fullDetails;
		configureGrid();
		createPanel();
	}

	private void configureGrid() {
		formGrid.getColumnConstraints().clear();
		formGrid.getColumnConstraints().addAll(createGrowColumn(), createGrowColumn(), createGrowColumn());
	}

	private ColumnConstraints createGrowColumn() {
		ColumnConstraints constraints = new ColumnConstraints();
		constraints.setHgrow(Priority.ALWAYS);
		constraints.setFillWidth(true);
		return constraints;
	}

	private void createPanel() {
		addFieldAbove("UI_LABEL_ACCOUNT", accountNameText, 0, 0);
		addFieldAbove("UI_LABEL_IBAN", accountIbanText, 1, 0);

		addFieldAbove("UI_LABEL_BANK", bankNameText, 0, 1);
		addFieldAbove("UI_LABEL_ACCOUNT_TYPE", accountTypText, 1, 1);

		addFieldAbove("UI_LABEL_ACCOUNT_NUMBER", numberText, 0, 2);
		addFieldAbove("UI_LABEL_SUBNUMBER", subnumberText, 1, 2);

		addFieldAbove("UI_LABEL_BANK_ACCESS", bankAccessText, 0, 3);
		addFieldAbove("UI_LABEL_CURRENCY", currencyText, 1, 3);

		addFieldAbove("UI_LABEL_BIC", bicText, 0, 4);
		addFieldAbove("UI_LABEL_BLZ", blzText, 1, 4);

		addFieldAbove("UI_LABEL_OWNER", ownerNameText, 0, 5);
		addFieldAbove("UI_LABEL_OWNER_2", ownerName2Text, 1, 5);

		addFieldAbove("UI_LABEL_SEPA_ACCOUNT", isSEPAAccount, 0, 6);
		addFieldAbove("UI_LABEL_UPDATED_AT", updatedAtText, 1, 6);

		if (fullDetails) {
			isOfflineAccount = new CheckBox();
			accountStateCombo = new ComboBox<>(FXCollections.observableArrayList(AccountState.values()));
			addFieldAbove("UI_LABEL_OFFLINE_ACCOUNT", isOfflineAccount, 2, 0);
			addFieldAbove("UI_LABEL_ACCOUNT_STATE", accountStateCombo, 2, 1);
		}

		makeReadOnly(accountNameText, accountIbanText, bankNameText, accountTypText, bankAccessText, currencyText, bicText, blzText, numberText, subnumberText,
				ownerNameText, ownerName2Text, updatedAtText);

		disable(isSEPAAccount);

		if (isOfflineAccount != null) {
			disable(isOfflineAccount);
		}
		if (accountStateCombo != null) {
			disable(accountStateCombo);
		}
	}

	public void updatePanelFieldValues(BankAccount bankAccount) {
		accountIbanText.setText(bankAccount.getIban());
		accountNameText.setText(bankAccount.getAccountName());
		accountTypText.setText(bankAccount.getAccountType() != null ? bankAccount.getAccountType().toString() : "");
		bankNameText.setText(bankAccount.getBankName());
		bankAccessText.setText(String.valueOf(bankAccount.getBankAccessId()));
		currencyText.setText(bankAccount.getCurrency());
		bicText.setText(bankAccount.getBic());
		blzText.setText(bankAccount.getBlz());
		numberText.setText(bankAccount.getNumber());
		subnumberText.setText(bankAccount.getSubnumber());
		ownerNameText.setText(bankAccount.getOwnerName());
		ownerName2Text.setText(bankAccount.getOwnerName2());
		isSEPAAccount.setSelected(bankAccount.isSEPAAccount());

		if (fullDetails) {
			if (isOfflineAccount != null) {
				isOfflineAccount.setSelected(bankAccount.isOfflineAccount());
			}
			if (accountStateCombo != null) {
				accountStateCombo.setValue(bankAccount.getAccountState());
			}
		}

		updatedAtText.setText(bankAccount.getUpdatedAt() != null ? dateFormat.format(bankAccount.getUpdatedAt().getTime()) : "");
	}
}