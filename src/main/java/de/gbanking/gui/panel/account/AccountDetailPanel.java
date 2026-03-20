package de.gbanking.gui.panel.account;

import java.text.SimpleDateFormat;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.enu.AccountState;
import de.gbanking.gui.panel.AbstractReadonlyDetailPanel;
import de.gbanking.gui.util.FormFields;
import de.gbanking.gui.util.FormStyleUtils;
import javafx.collections.FXCollections;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;

public class AccountDetailPanel extends AbstractReadonlyDetailPanel {

	private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy");
	private final boolean fullDetails;

	private final TextField accountIbanText = FormFields.textM();

	private final TextField bankNameText = FormFields.textM();
	private final TextField accountTypText = FormFields.textS();

	private final TextField numberText = FormFields.textS();
	private final TextField subnumberText = FormFields.textXs();

	private final TextField bankAccessText = FormFields.textS();
	private final TextField currencyText = FormFields.textXs();

	private final TextField bicText = FormFields.textS();
	private final TextField blzText = FormFields.textS();

	private final TextField ownerNameText = FormFields.textM();
	private final TextField ownerName2Text = FormFields.textM();

	private final CheckBox isSEPAAccount = FormFields.checkBox();

	private final TextField updatedAtText = FormFields.textS();

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
		// Spalte 1 - Bezeichnung

		// addFieldInline("UI_LABEL_ACCOUNT", accountNameText, 0, 0);
		addFieldInline("UI_LABEL_OWNER", ownerNameText, 0, 0);
		addFieldInline("UI_LABEL_IBAN", accountIbanText, 1, 0);
		addFieldInline("UI_LABEL_BIC", bicText, 2, 0);

		addFieldInline("UI_LABEL_OWNER_2", ownerName2Text, 0, 1);
		addFieldInline("UI_LABEL_ACCOUNT_NUMBER", numberText, 1, 1);
		addFieldInline("UI_LABEL_BLZ", blzText, 2, 1);

		addFieldInline("UI_LABEL_ACCOUNT_TYPE", accountTypText, 0, 2);
		addFieldInline("UI_LABEL_SUBNUMBER", subnumberText, 1, 2);
		addFieldInline("UI_LABEL_BANK", bankNameText, 2, 2);

		addFieldInline("UI_LABEL_CURRENCY", currencyText, 0, 3);
		addFieldInline("UI_LABEL_SEPA_ACCOUNT", isSEPAAccount, 1, 3);
		addFieldInline("UI_LABEL_BANK_ACCESS", bankAccessText, 2, 3);

		addFieldInline("UI_LABEL_UPDATED_AT", updatedAtText, 2, 4);

		// Spalte 2 - Kontodaten

		// Spalte 3 - Technik / Status

		if (fullDetails) {
			isOfflineAccount = FormFields.checkBox();
			accountStateCombo = FormFields.comboM(FXCollections.observableArrayList(AccountState.values()));

			addFieldInline("UI_LABEL_OFFLINE_ACCOUNT", isOfflineAccount, 2, 6);
			addFieldInline("UI_LABEL_ACCOUNT_STATE", accountStateCombo, 2, 7);
		}

		makeReadOnly(accountIbanText, bankNameText, accountTypText, bankAccessText, currencyText, bicText, blzText, numberText, subnumberText,
				ownerNameText, ownerName2Text, updatedAtText);

		FormStyleUtils.setReadOnlyStyle(true, accountIbanText, bankNameText, accountTypText, bankAccessText, currencyText, bicText, blzText,
				numberText, subnumberText, ownerNameText, ownerName2Text, updatedAtText);

		disable(isSEPAAccount);

		if (isOfflineAccount != null) {
			disable(isOfflineAccount);
		}
		if (accountStateCombo != null) {
			disable(accountStateCombo);
			FormStyleUtils.setReadOnlyStyle(true, accountStateCombo);
		}
	}

	public void updatePanelFieldValues(BankAccount bankAccount) {

		updateTitle(bankAccount.getAccountName());

		accountIbanText.setText(bankAccount.getIban());
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