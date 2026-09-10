package de.zft2.gbanking.gui.panel.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.component.GBankingTableView;
import de.zft2.gbanking.gui.util.FxTableUtils;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import javafx.scene.Scene;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

class AccountUpdatedAtColumnTest {

	@Test
	void shouldRenderRetrievalTimeLegacyDateAndClearReusedCells() {
		JavaFxTestSupport.runFx(() -> {
			LocalDate today = LocalDate.of(2026, 9, 10);
			Clock clock = Clock.fixed(today.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
			AccountUpdatedAtColumn column = new AccountUpdatedAtColumn("Stand", clock);
			BankAccount account = new BankAccount();
			account.setId(1);
			account.setUpdatedAt(today);
			account.setSessionRetrievalAt(today.atTime(9, 5));
			BankAccount older = new BankAccount();
			older.setId(2);
			older.setUpdatedAt(today.minusDays(2));
			older.setSessionRetrievalAt(today.minusDays(1).atTime(23, 59));
			BankAccount legacy = new BankAccount();
			legacy.setUpdatedAt(today);
			GBankingTableView<BankAccount> table = new GBankingTableView<>();
			table.getColumns().add(column);
			table.getItems().setAll(account, older, legacy);
			TableCell<BankAccount, BankAccount> cell = column.getCellFactory().call(column);
			cell.updateTableView(table);
			cell.updateTableColumn(column);
			cell.updateIndex(0);
			assertEquals("09:05", cell.getText());
			cell.updateIndex(1);
			assertEquals("08.09.2026", cell.getText());
			cell.updateIndex(2);
			assertEquals("10.09.2026", cell.getText());
			assertTrue(column.getComparator().compare(older, account) < 0);
			assertTrue(column.getComparator().compare(legacy, account) < 0);
			double dateWidth = column.getPrefWidth();
			older.setSessionRetrievalAt(today.atTime(10, 10));
			legacy.setSessionRetrievalAt(today.atTime(11, 15));
			cell.updateIndex(0);
			assertTrue(column.getPrefWidth() < dateWidth);
			cell.updateIndex(-1);
			assertNull(cell.getText());
		});
	}

	@Test
	void dateShouldFitLargerFontsAtCompactTableWidthsIncludingScrollbar() {
		JavaFxTestSupport.runFx(() -> {
			GBankingTableView<BankAccount> table = new GBankingTableView<>();
			TableColumn<BankAccount, Boolean> selected = FxTableUtils.createSelectionColumn("", account -> false, (account, value) -> { });
			FxTableUtils.setFixedWidth(selected, 32);
			TableColumn<BankAccount, String> name = TableColumnFactory.createTextColumn("Konto", account -> "Testkonto", 100, 220);
			AccountUpdatedAtColumn updated = new AccountUpdatedAtColumn("Stand");
			assertEquals(65, updated.getMinWidth());
			assertEquals(120, updated.getMaxWidth());
			table.getColumns().setAll(List.of(selected, name, updated));
			for (int i = 0; i < 30; i++) {
				BankAccount account = new BankAccount();
				account.setUpdatedAt(LocalDate.of(2026, 8, 28));
				table.getItems().add(account);
			}
			StackPane root = new StackPane(table);
			Scene scene = new Scene(root);
			scene.getStylesheets().add(getClass().getResource("/css/gbanking-table.css").toExternalForm());
			root.setStyle("-fx-font-size: 16px; -fx-font-family: 'DejaVu Sans';");
			root.resize(335, 180);
			root.applyCss();
			root.layout();
			for (double width : new double[] { 335, Math.max(280, updated.getMinWidth() + 156) }) {
				root.resize(width, 180);
				root.layout();
				assertTrue(selected.getWidth() + name.getWidth() + updated.getWidth() <= table.getWidth() - 14);
				Text date = new Text("28.08.2026");
				date.setFont(Font.font("DejaVu Sans", 16));
				assertTrue(updated.getWidth() >= date.getLayoutBounds().getWidth() + 16);
				assertTrue(updated.getWidth() <= 120);
			}
		});
	}
}
