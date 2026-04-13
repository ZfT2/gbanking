package de.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import de.gbanking.gui.JavaFxTestSupport;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.Cell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;

class FxTableUtilsTest {

	@Test
	void widthHelpersShouldApplyExpectedWidths() {
		TableColumn<String, String> fixed = new TableColumn<>("fixed");
		TableColumn<String, String> preferred = new TableColumn<>("preferred");

		JavaFxTestSupport.runFx(() -> {
			FxTableUtils.setFixedWidth(fixed, 90);
			FxTableUtils.setPreferredWidth(preferred, 60, 120);
		});

		assertEquals(90.0, fixed.getMinWidth());
		assertEquals(90.0, fixed.getMaxWidth());
		assertFalse(fixed.isResizable());
		assertEquals(60.0, preferred.getMinWidth());
		assertEquals(120.0, preferred.getPrefWidth());
	}

	@Test
	void createCheckBoxCellFactoryShouldCenterGraphicOnlyCells() {
		TableColumn<String, Boolean> column = new TableColumn<>("selected");
		TableCell<String, Boolean> cell = JavaFxTestSupport.callFx(() -> FxTableUtils.<String>createCheckBoxCellFactory().call(column));

		assertEquals(javafx.geometry.Pos.CENTER, cell.getAlignment());
		assertEquals(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY, cell.getContentDisplay());
	}

	@Test
	void bigDecimalAmountCellFactoryShouldFormatAndColorAmounts() {
		TableColumn<String, BigDecimal> column = new TableColumn<>("amount");
		TableCell<String, BigDecimal> cell = JavaFxTestSupport.callFx(() -> FxTableUtils.<String>createBigDecimalAmountCellFactory().call(column));

		JavaFxTestSupport.runFx(() -> invokeUpdateItem(cell, new BigDecimal("1234.56"), false));

		HBox graphic = (HBox) cell.getGraphic();
		Text text = (Text) graphic.getChildren().get(0);
		assertEquals("1.234,56", text.getText());
		assertTrue(cell.getStyleClass().contains("amount-positive"));
		assertTrue(text.getStyleClass().contains("amount-positive"));
	}

	@Test
	void stringAmountCellFactoryShouldFallbackForInvalidNumbers() {
		TableColumn<String, String> column = new TableColumn<>("amount");
		TableCell<String, String> cell = JavaFxTestSupport.callFx(() -> FxTableUtils.<String>createAmountCellFactory().call(column));

		JavaFxTestSupport.runFx(() -> invokeUpdateItem(cell, "abc", false));

		HBox graphic = (HBox) cell.getGraphic();
		Text text = (Text) graphic.getChildren().get(0);
		assertEquals("abc", text.getText());
		assertTrue(cell.getStyleClass().contains("amount-neutral"));
	}

	@Test
	void selectionColumnShouldReflectGetterAndWriteBackChanges() {
		SelectionRow row = new SelectionRow();
		TableColumn<SelectionRow, Boolean> column = JavaFxTestSupport
				.callFx(() -> FxTableUtils.createSelectionColumn("X", SelectionRow::selected, SelectionRow::selected));

		TableView<SelectionRow> tableView = new TableView<>();
		BooleanProperty property = (BooleanProperty) column.getCellValueFactory()
				.call(new TableColumn.CellDataFeatures<>(tableView, column, row));
		Boolean currentValue = property.getValue();
		property.setValue(Boolean.TRUE);

		assertFalse(currentValue);
		assertTrue(row.selected());
	}

	private static <T> void invokeUpdateItem(TableCell<?, T> cell, T item, boolean empty) {
		try {
			var method = Cell.class.getDeclaredMethod("updateItem", Object.class, boolean.class);
			method.setAccessible(true);
			method.invoke(cell, item, empty);
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static final class SelectionRow {
		private boolean selected;

		boolean selected() {
			return selected;
		}

		void selected(Boolean value) {
			this.selected = Boolean.TRUE.equals(value);
		}
	}
}
