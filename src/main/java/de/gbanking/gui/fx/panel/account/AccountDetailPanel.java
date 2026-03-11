package de.gbanking.gui.fx.panel.account;

import java.text.SimpleDateFormat;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.enu.AccountState;
import de.gbanking.gui.fx.panel.BasePanelHolder;
import de.gbanking.gui.fx.util.FormGridHelper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AccountDetailPanel extends BasePanelHolder {

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
		this.fullDetails = fullDetails;
		createPanel();
	}

	private void createPanel() {
		GridPane grid = FormGridHelper.createDefaultGrid();
		grid.setPadding(new Insets(8));
		grid.getColumnConstraints().addAll(createGrowColumn(), createGrowColumn(), createGrowColumn());

		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_ACCOUNT"), accountNameText, 0, 0);
		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_IBAN"), accountIbanText, 1, 0);

		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_BANK"), bankNameText, 0, 1);
		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_ACCOUNT_TYPE"), accountTypText, 1, 1);

		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_ACCOUNT_NUMBER"), numberText, 0, 2);
		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_SUBNUMBER"), subnumberText, 1, 2);

		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_BANK_ACCESS"), bankAccessText, 0, 3);
		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_CURRENCY"), currencyText, 1, 3);

		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_BIC"), bicText, 0, 4);
		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_BLZ"), blzText, 1, 4);

		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_OWNER"), ownerNameText, 0, 5);
		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_OWNER_2"), ownerName2Text, 1, 5);

		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_SEPA_ACCOUNT"), isSEPAAccount, 0, 6);
		FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_UPDATED_AT"), updatedAtText, 1, 6);

		if (fullDetails) {
			isOfflineAccount = new CheckBox();
			accountStateCombo = new ComboBox<>(FXCollections.observableArrayList(AccountState.values()));

			FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_OFFLINE_ACCOUNT"), isOfflineAccount, 2, 0);
			FormGridHelper.addFieldAbove(grid, getText("UI_LABEL_ACCOUNT_STATE"), accountStateCombo, 2, 1);
			accountStateCombo.setMaxWidth(Double.MAX_VALUE);
		}

		setReadOnly();

		VBox content = new VBox(grid);
		content.setPadding(new Insets(6, 8, 8, 8));
		content.setFillWidth(true);
		content.setMaxWidth(Double.MAX_VALUE);

		TitledPane titledPane = new TitledPane(getText("UI_PANEL_ACCOUNT_DETAILS"), content);
		titledPane.setCollapsible(false);
		titledPane.setMaxWidth(Double.MAX_VALUE);

		getChildren().setAll(titledPane);
		setFillWidth(true);
		setMaxWidth(Double.MAX_VALUE);
	}

	private ColumnConstraints createGrowColumn() {
		ColumnConstraints cc = new ColumnConstraints();
		cc.setHgrow(Priority.ALWAYS);
		cc.setFillWidth(true);
		return cc;
	}

	private void setReadOnly() {
		accountNameText.setEditable(false);
		accountIbanText.setEditable(false);
		bankNameText.setEditable(false);
		accountTypText.setEditable(false);
		bankAccessText.setEditable(false);
		currencyText.setEditable(false);
		bicText.setEditable(false);
		blzText.setEditable(false);
		numberText.setEditable(false);
		subnumberText.setEditable(false);
		ownerNameText.setEditable(false);
		ownerName2Text.setEditable(false);
		updatedAtText.setEditable(false);

		isSEPAAccount.setDisable(true);

		if (isOfflineAccount != null) {
			isOfflineAccount.setDisable(true);
		}
		if (accountStateCombo != null) {
			accountStateCombo.setDisable(true);
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

	public TextField getAccountNameText() {
		return accountNameText;
	}

	public TextField getAccountIbanText() {
		return accountIbanText;
	}

	public TextField getBankNameText() {
		return bankNameText;
	}

	public TextField getAccountTypText() {
		return accountTypText;
	}

	public TextField getBankAccessText() {
		return bankAccessText;
	}

	public TextField getCurrencyText() {
		return currencyText;
	}

	public TextField getBicText() {
		return bicText;
	}

	public TextField getBlzText() {
		return blzText;
	}

	public TextField getNumberText() {
		return numberText;
	}

	public TextField getSubnumberText() {
		return subnumberText;
	}

	public TextField getOwnerNameText() {
		return ownerNameText;
	}

	public TextField getOwnerName2Text() {
		return ownerName2Text;
	}

	public CheckBox getIsSEPAAccount() {
		return isSEPAAccount;
	}

	public TextField getUpdatedAtText() {
		return updatedAtText;
	}
}