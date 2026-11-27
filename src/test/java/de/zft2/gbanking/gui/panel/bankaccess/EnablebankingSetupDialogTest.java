package de.zft2.gbanking.gui.panel.bankaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

class EnablebankingSetupDialogTest {

	@Test
	void missingAccountHintShouldRemainFullyReadableWhenTheDialogNarrows() {
		JavaFxTestSupport.runFx(() -> {
			MissingBankAccountsPane missing = new MissingBankAccountsPane(List.of(account(AccountType.CURRENT_ACCOUNT)));
			Label hint = (Label) missing.getChildren().get(1);
			TableView<BankAccount> returned = createTable(account(AccountType.CURRENT_ACCOUNT));
			VBox root = DialogWindowSupport.createDialogRoot(
					new Label(BaseMessages.getTextStatic("UI_BANK_ACCESS_DIALOG_ACCOUNTS_LABEL")), returned, missing,
					DialogWindowSupport.createButtonBar(new Button("OK")));
			VBox.setVgrow(returned, Priority.ALWAYS);
			Stage dialog = DialogWindowSupport.createModalStage(null, "UI_BANK_ACCESS_DIALOG_ACCOUNTS_TITLE");
			dialog.setScene(DialogWindowSupport.createScene(root, 900, 600));
			root.applyCss();
			double previousHeight = 0;
			for (int width : new int[] { 900, 680 }) {
				root.resize(width, 420);
				root.layout();
				Text expected = new Text(hint.getText());
				expected.setFont(hint.getFont());
				expected.setWrappingWidth(hint.getWidth() - hint.getInsets().getLeft() - hint.getInsets().getRight());
				assertTrue(hint.getHeight() >= expected.getLayoutBounds().getHeight());
				assertTrue(hint.getHeight() > previousHeight);
				assertEquals(hint.getText(), ((Text) hint.lookup(".text")).getText());
				assertTrue(missing.getBoundsInParent().getMaxY() <= root.getHeight());
				previousHeight = hint.getHeight();
			}
		});
	}

	@Test
	void missingAccountDatesShouldBeEditableClearableAndIsolatedUntilConfirmation() {
		JavaFxTestSupport.runFx(() -> {
			BankAccount open = account(AccountType.CURRENT_ACCOUNT);
			open.setId(1);
			BankAccount closed = account(AccountType.SAVINGS_ACCOUNT);
			closed.setId(2);
			closed.setClosedAt(LocalDate.of(2025, Month.JUNE, 15));
			MissingBankAccountsPane pane = new MissingBankAccountsPane(List.of(open, closed));
			assertEquals(LocalDate.now(ZoneId.systemDefault()), pane.getClosureDates().get(open.getId()));
			assertEquals(closed.getClosedAt(), pane.getClosureDates().get(closed.getId()));
			TableView<?> table = (TableView<?>) pane.getChildren().get(2);
			TextField editor = closureEditor(table, 0);
			editor.setText("31.02.2026");
			assertThrows(GBankingException.class, pane::getClosureDates);
			editor.setText("14.06.2026");
			assertEquals(LocalDate.of(2026, Month.JUNE, 14), pane.getClosureDates().get(open.getId()));
			editor.clear();
			closureEditor(table, 1).clear();
			assertNull(pane.getClosureDates().get(open.getId()));
			assertNull(pane.getClosureDates().get(closed.getId()));
			assertNull(open.getClosedAt());
			assertEquals(LocalDate.of(2025, Month.JUNE, 15), closed.getClosedAt());
		});
	}

	private <S> TextField closureEditor(TableView<S> table, int index) {
		return closureEditor(table, table.getColumns().get(3), index);
	}

	private <S, T> TextField closureEditor(TableView<S> table, TableColumn<S, T> column, int index) {
		TableCell<S, T> cell = column.getCellFactory().call(column);
		cell.updateTableView(table);
		cell.updateTableColumn(column);
		cell.updateIndex(index);
		return (TextField) cell.getGraphic();
	}

	@Test
	void shouldShowTheAccountTypeInADropdownWithTheDerivedTypePreselected() {
		JavaFxTestSupport.runFx(() -> {
			BankAccount account = account(AccountType.CURRENT_ACCOUNT);
			TableView<BankAccount> table = createTable(account);

			TableColumn<BankAccount, ?> accountTypeColumn = accountTypeColumn(table);
			assertEquals(BaseMessages.getTextStatic("UI_TABLE_ACCOUNT_TYPE"), accountTypeColumn.getText());

			ComboBox<?> comboBox = comboBox(accountTypeColumn, table, 0);
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

			ComboBox<?> comboBox = comboBox(accountTypeColumn(table), table, 0);
			assertNotNull(comboBox);

			comboBox.getSelectionModel().select(comboBox.getItems().indexOf(AccountType.SAVINGS_ACCOUNT));
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

	private TableColumn<BankAccount, ?> accountTypeColumn(TableView<BankAccount> table) {
		return table.getColumns().stream()
				.filter(column -> BaseMessages.getTextStatic("UI_TABLE_ACCOUNT_TYPE").equals(column.getText()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No account type column found"));
	}

	private <T> ComboBox<?> comboBox(TableColumn<BankAccount, T> column, TableView<BankAccount> table,
			int index) {
		TableCell<BankAccount, T> cell = column.getCellFactory().call(column);
		cell.updateTableView(table);
		cell.updateTableColumn(column);
		cell.updateIndex(index);
		return (ComboBox<?>) cell.getGraphic();
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
