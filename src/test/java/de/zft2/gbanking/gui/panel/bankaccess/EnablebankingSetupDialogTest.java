package de.zft2.gbanking.gui.panel.bankaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

class EnablebankingSetupDialogTest {

	@Test
	void shouldShowTheAccountTypeInADropdownWithTheDerivedTypePreselected() {
		JavaFxTestSupport.runFx(() -> {
			BankAccount account = account(AccountType.CURRENT_ACCOUNT);
			TableView<BankAccount> table = createTable(account);

			TableColumn<BankAccount, AccountType> accountTypeColumn = accountTypeColumn(table);
			assertEquals(BaseMessages.getTextStatic("UI_TABLE_ACCOUNT_TYPE"), accountTypeColumn.getText());

			ComboBox<AccountType> comboBox = comboBox(accountTypeColumn, table, 0);
			assertNotNull(comboBox);
			assertEquals(AccountType.CURRENT_ACCOUNT, comboBox.getValue());
			assertEquals(List.of(AccountType.values()), comboBox.getItems());
		});
	}

	@Test
	void shouldCorrectTheAccountTypeWhenTheDropdownSelectionChanges() {
		JavaFxTestSupport.runFx(() -> {
			BankAccount account = account(AccountType.CURRENT_ACCOUNT);
			TableView<BankAccount> table = createTable(account);

			ComboBox<AccountType> comboBox = comboBox(accountTypeColumn(table), table, 0);
			assertNotNull(comboBox);

			comboBox.setValue(AccountType.SAVINGS_ACCOUNT);
			comboBox.fireEvent(new ActionEvent());

			assertEquals(AccountType.SAVINGS_ACCOUNT, account.getAccountType());
			// The account name contains the localized account type label, so it is derived from the enum
			// to keep the assertion independent of the locale of the build.
			assertEquals(AccountType.SAVINGS_ACCOUNT + " - 4711 - EUR - (PSD2)", account.getAccountName());
		});
	}

	@Test
	void shouldNotShowADropdownForRowsWithoutAnAccount() {
		JavaFxTestSupport.runFx(() -> {
			TableView<BankAccount> table = createTable();

			assertNull(comboBox(accountTypeColumn(table), table, 0));
		});
	}

	private TableView<BankAccount> createTable(BankAccount... accounts) {
		return EnablebankingSetupDialog.createAccountTable(FXCollections.observableArrayList(List.of(accounts)));
	}

	private TableColumn<BankAccount, AccountType> accountTypeColumn(TableView<BankAccount> table) {
		return table.getColumns().stream()
				.filter(column -> BaseMessages.getTextStatic("UI_TABLE_ACCOUNT_TYPE").equals(column.getText()))
				.map(column -> (TableColumn<BankAccount, AccountType>) column)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No account type column found"));
	}

	@SuppressWarnings("unchecked")
	private ComboBox<AccountType> comboBox(TableColumn<BankAccount, AccountType> column, TableView<BankAccount> table,
			int index) {
		TableCell<BankAccount, AccountType> cell = column.getCellFactory().call(column);
		cell.updateTableView(table);
		cell.updateTableColumn(column);
		cell.updateIndex(index);
		return (ComboBox<AccountType>) cell.getGraphic();
	}

	private BankAccount account(AccountType accountType) {
		BankAccount account = new BankAccount();
		account.setIban("DE44500105175407324931");
		account.setNumber("4711");
		account.setCurrency("EUR");
		account.setAccountType(accountType);
		account.setAccountName(accountType + " - 4711 - EUR - (PSD2)");
		return account;
	}
}
