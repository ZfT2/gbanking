package de.zft2.gbanking.gui.panel.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.testdata.TestDataFactory;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

class AccountDetailPanelTest {

	@Test
	void shouldSaveChangeAndClearClosureDatesOnlyInEditableAccountDetails(@TempDir Path directory) {
		DBController db = DBController.getInstance(directory.toString());
		BankAccount account = db.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		JavaFxTestSupport.runFx(() -> {
			AccountDetailPanel panel = new AccountDetailPanel(true);
			panel.updatePanelFieldValues(account);
			GridPane grid = getFormGrid(panel);
			TextField date = (TextField) fieldBox(grid, "UI_LABEL_ACCOUNT_CLOSED_AT").getChildren().get(1);
			assertFalse(date.isEditable());
			click(panel, "UI_BUTTON_EDIT");
			assertTrue(date.isEditable());
			date.setText("15.06.2026");
			click(panel, "UI_BUTTON_SAVE");
			assertEquals(LocalDate.of(2026, 6, 15), db.getById(BankAccount.class, account.getId()).getClosedAt());
			click(panel, "UI_BUTTON_EDIT");
			date.setText("16.06.2026");
			click(panel, "UI_BUTTON_SAVE");
			assertEquals(LocalDate.of(2026, 6, 16), db.getById(BankAccount.class, account.getId()).getClosedAt());
			click(panel, "UI_BUTTON_EDIT");
			date.clear();
			click(panel, "UI_BUTTON_CANCEL");
			assertEquals("16.06.2026", date.getText());
			click(panel, "UI_BUTTON_EDIT");
			date.clear();
			click(panel, "UI_BUTTON_SAVE");
			assertNull(db.getById(BankAccount.class, account.getId()).getClosedAt());
		});
	}

	private void click(AccountDetailPanel panel, String labelKey) {
		TitledPane pane = (TitledPane) panel.getChildren().get(0);
		pane.getContent().lookupAll(".button").stream().filter(node -> node instanceof Button button
				&& button.getText().equals(BaseMessages.getTextStatic(labelKey)))
				.map(node -> (Button) node).findFirst().orElseThrow().fire();
	}

	@AfterEach
	void closeDatabase() {
		DBControllerTestUtil.closeAndNullifyConnection();
	}

	@Test
	void shouldArrangeReadonlyAccountDetailsInFourColumns() {
		JavaFxTestSupport.runFx(() -> assertLayout(new AccountDetailPanel(false), 0));
	}

	@Test
	void shouldArrangeFullAccountDetailsInFourColumns() {
		JavaFxTestSupport.runFx(() -> {
			GridPane grid = getFormGrid(new AccountDetailPanel(true));
			assertEquals(4, grid.getColumnConstraints().size());
			assertFieldPosition(grid, "UI_LABEL_ACCOUNT_NAME", 0, 0);
			assertFieldPosition(grid, "UI_LABEL_ACCOUNT_STATE", 1, 0);
			assertFieldPosition(grid, "UI_LABEL_OFFLINE_ACCOUNT", 2, 0);
			assertFieldPosition(grid, "UI_LABEL_CREATED_AT", 3, 0);
			assertLayout(grid, 1);
		});
	}

	private static void assertLayout(AccountDetailPanel panel, int firstRow) {
		GridPane grid = getFormGrid(panel);
		assertEquals(4, grid.getColumnConstraints().size());
		assertLayout(grid, firstRow);
	}

	private static void assertLayout(GridPane grid, int firstRow) {
		assertFieldPosition(grid, "UI_LABEL_OWNER", 0, firstRow);
		assertFieldPosition(grid, "UI_LABEL_IBAN", 1, firstRow);
		assertFieldPosition(grid, "UI_LABEL_BIC", 2, firstRow);
		assertFieldPosition(grid, "UI_LABEL_BANK", 3, firstRow);
		assertFieldPosition(grid, "UI_LABEL_OWNER_2", 0, firstRow + 1);
		assertFieldPosition(grid, "UI_LABEL_ACCOUNT_NUMBER", 1, firstRow + 1);
		assertFieldPosition(grid, "UI_LABEL_BLZ", 2, firstRow + 1);
		assertFieldPosition(grid, "UI_LABEL_BANK_ACCESS", 3, firstRow + 1);
		assertFieldPosition(grid, "UI_LABEL_ACCOUNT_TYPE", 0, firstRow + 2);
		assertFieldPosition(grid, "UI_LABEL_SUBNUMBER", 1, firstRow + 2);
		assertFieldPosition(grid, "UI_LABEL_CURRENCY", 2, firstRow + 2);
		assertFieldPosition(grid, "UI_LABEL_SEPA_ACCOUNT", 3, firstRow + 2);
		assertFieldPosition(grid, "UI_LABEL_ACCOUNT_CLOSED_AT", 0, firstRow + 3);
		assertFieldPosition(grid, "UI_LABEL_BANK_BALANCE", 0, firstRow + 4);
		assertFieldPosition(grid, "UI_LABEL_ACCOUNT_RETRIEVAL_AT", 1, firstRow + 4);
		assertFieldPosition(grid, "UI_LABEL_ACCOUNT_RETRIEVAL_RESULT", 2, firstRow + 4);
		assertFieldPosition(grid, "UI_LABEL_ACCOUNT_RETRIEVAL_COUNTS", 3, firstRow + 4);
	}

	private static GridPane getFormGrid(AccountDetailPanel panel) {
		TitledPane titledPane = assertInstanceOf(TitledPane.class, panel.getChildren().get(0));
		HBox title = assertInstanceOf(HBox.class, titledPane.getGraphic());
		HBox updatedAt = assertInstanceOf(HBox.class, title.getChildren().get(1));
		Label updatedAtLabel = assertInstanceOf(Label.class, updatedAt.getChildren().get(0));
		assertEquals(BaseMessages.getTextStatic("UI_LABEL_UPDATED_AT"), updatedAtLabel.getText());
		assertInstanceOf(Label.class, updatedAt.getChildren().get(1));
		VBox content = assertInstanceOf(VBox.class, titledPane.getContent());
		return assertInstanceOf(GridPane.class, content.getChildren().get(0));
	}

	private static void assertFieldPosition(GridPane grid, String labelKey, int column, int row) {
		Node field = fieldBox(grid, labelKey);
		assertEquals(column, GridPane.getColumnIndex(field));
		assertEquals(row, GridPane.getRowIndex(field));
	}

	private static HBox fieldBox(GridPane grid, String labelKey) {
		String labelText = BaseMessages.getTextStatic(labelKey);
		return (HBox) grid.getChildren().stream()
				.filter(node -> node instanceof HBox box && box.getChildren().get(0) instanceof Label label
						&& labelText.equals(label.getText()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing field: " + labelKey));
	}
}
