package de.gbanking.gui.fx.panel.account;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.enu.AccountState;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.text.SimpleDateFormat;

public class AccountDetailPanel extends TitledPane {

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

		setText("Konto Details");
		setCollapsible(false);
		setContent(createForm());
	}

	private GridPane createForm() {
		GridPane grid = new GridPane();
		grid.setPadding(new Insets(10));
		grid.setHgap(12);
		grid.setVgap(8);

		grid.getColumnConstraints().addAll(createGrowColumn(), createGrowColumn(), createGrowColumn());

		// row group 0
		addFieldAbove(grid, "Konto", accountNameText, 0, 0);
		addFieldAbove(grid, "IBAN", accountIbanText, 1, 0);

		// row group 1
		addFieldAbove(grid, "Bank", bankNameText, 0, 1);
		addFieldAbove(grid, "Konto-Typ", accountTypText, 1, 1);

		// row group 2
		addFieldAbove(grid, "Kontonummer", numberText, 0, 2);
		addFieldAbove(grid, "Unterkonto-Nr.", subnumberText, 1, 2);

		// row group 3
		addFieldAbove(grid, "Bankzugang", bankAccessText, 0, 3);
		addFieldAbove(grid, "Währung", currencyText, 1, 3);

		// row group 4
		addFieldAbove(grid, "BIC", bicText, 0, 4);
		addFieldAbove(grid, "Bankleitzahl", blzText, 1, 4);

		// row group 5
		addFieldAbove(grid, "Inhaber", ownerNameText, 0, 5);
		addFieldAbove(grid, "Inhaber 2", ownerName2Text, 1, 5);

		// row group 6
		addFieldAbove(grid, "SEPA Konto", isSEPAAccount, 0, 6);
		addFieldAbove(grid, "Stand", updatedAtText, 1, 6);

		if (fullDetails) {
			isOfflineAccount = new CheckBox();
			accountStateCombo = new ComboBox<>(FXCollections.observableArrayList(AccountState.values()));

			// wie in Swing: dritte Spalte, nur oben belegt
			addFieldAbove(grid, "Offline-Konto?", isOfflineAccount, 2, 0);
			addFieldAbove(grid, "Konto-Status", accountStateCombo, 2, 1);
		}

		setReadOnly();

		return grid;
	}

	private javafx.scene.layout.ColumnConstraints createGrowColumn() {
		javafx.scene.layout.ColumnConstraints cc = new javafx.scene.layout.ColumnConstraints();
		cc.setHgrow(Priority.ALWAYS);
		cc.setFillWidth(true);
		return cc;
	}

	private void addFieldAbove(GridPane grid, String labelText, Control field, int col, int rowGroup) {
		int labelRow = rowGroup * 2;
		int fieldRow = labelRow + 1;

		Label label = new Label(labelText);
		VBox box = new VBox(2, label, field);
		VBox.setVgrow(field, Priority.NEVER);

		if (field instanceof TextField textField) {
			textField.setMaxWidth(Double.MAX_VALUE);
		}
		if (field instanceof ComboBox<?> comboBox) {
			comboBox.setMaxWidth(Double.MAX_VALUE);
		}

		grid.add(box, col, labelRow, 1, 2);
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
		accountTypText.setText(bankAccount.getAccountType() != null ? bankAccount.getAccountType().toString() : null);
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