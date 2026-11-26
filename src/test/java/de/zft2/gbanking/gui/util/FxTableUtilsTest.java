package de.zft2.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.JavaFxTestSupport;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Cell;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;

class FxTableUtilsTest {

	@Test
	void filteredTableItemsShouldKeepFilteringAndSortingOnSourceChanges() {
		JavaFxTestSupport.runFx(() -> {
			ObservableList<String> rows = FXCollections.observableArrayList("alpha", "hidden", "gamma");
			TableView<String> table = new TableView<>();
			TableColumn<String, String> column = new TableColumn<>("name");
			column.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue()));
			table.getColumns().add(column);
			FxTableUtils.FilteredTableItems<String> items = FxTableUtils.createFilteredTableItems(table, rows, row -> !"hidden".equals(row));
			column.setSortType(TableColumn.SortType.DESCENDING);
			table.getSortOrder().add(column);
			assertEquals(java.util.List.of("gamma", "alpha"), table.getItems());
			rows.add("beta");
			assertEquals(java.util.List.of("gamma", "beta", "alpha"), table.getItems());
			items.filtered().setPredicate(row -> row.startsWith("b"));
			assertEquals(java.util.List.of("beta"), table.getItems());
		});
	}

	@Test
	void germanAmountFormattingShouldUseGermanSeparators() {
		assertEquals("1.234,56", FxTableUtils.formatGermanAmount(new BigDecimal("1234.56")));
		assertEquals("-1.234.567,89", FxTableUtils.formatGermanAmount(new BigDecimal("-1234567.891")));
		assertEquals("-123,40", FxTableUtils.formatGermanAmount(new BigDecimal("-123.4")));
		assertEquals("1.000,00", FxTableUtils.formatGermanAmount(new BigDecimal("999.995")));
		assertEquals("0,12", FxTableUtils.formatGermanAmount(new BigDecimal("0.125")));
		assertEquals("0,00", FxTableUtils.formatGermanAmount(new BigDecimal("-0.001")));
		assertEquals("", FxTableUtils.formatGermanAmount(null));
	}

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
		TableCell<String, BigDecimal> cell = JavaFxTestSupport
				.callFx(() -> FxTableUtils.<String>createAutoFitBigDecimalAmountCellFactory(item -> false).call(column));

		JavaFxTestSupport.runFx(() -> invokeUpdateItem(cell, new BigDecimal("1234.56"), false));

		HBox graphic = (HBox) cell.getGraphic();
		Text text = (Text) graphic.getChildren().get(0);
		assertEquals("1.234,56", text.getText());
		assertTrue(column.getMinWidth() > 10);
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
	void formattedCellFactoryShouldFormatValuesAndClearEmptyCells() {
		TableColumn<String, Integer> column = new TableColumn<>("number");
		TableCell<String, Integer> cell = JavaFxTestSupport
				.callFx(() -> FxTableUtils.<String, Integer>createFormattedCellFactory(value -> "#" + value).call(column));

		JavaFxTestSupport.runFx(() -> invokeUpdateItem(cell, 7, false));
		assertEquals("#7", cell.getText());

		JavaFxTestSupport.runFx(() -> invokeUpdateItem(cell, null, false));
		assertNull(cell.getText());

		JavaFxTestSupport.runFx(() -> invokeUpdateItem(cell, 7, true));
		assertNull(cell.getText());
	}

	@Test
	void amountStyleClassShouldReflectAmountSignAndMutedState() {
		assertEquals("amount-positive", FxTableUtils.amountStyleClass(BigDecimal.ONE, false));
		assertEquals("amount-negative", FxTableUtils.amountStyleClass(BigDecimal.ONE.negate(), false));
		assertEquals("amount-neutral", FxTableUtils.amountStyleClass(BigDecimal.ZERO, false));
		assertEquals("amount-neutral", FxTableUtils.amountStyleClass(null, false));
		assertEquals("amount-muted", FxTableUtils.amountStyleClass(BigDecimal.ONE, true));
	}

	@Test
	void selectionColumnShouldReflectGetterAndWriteBackChanges() {
		SelectionRow row = new SelectionRow();
		TableColumn<SelectionRow, Boolean> column = JavaFxTestSupport
				.callFx(() -> FxTableUtils.createSelectionColumn("X", item -> item.selected(), (item, selected) -> item.selected(selected)));

		TableView<SelectionRow> tableView = new TableView<>();
		BooleanProperty property = (BooleanProperty) column.getCellValueFactory()
				.call(new TableColumn.CellDataFeatures<>(tableView, column, row));
		Boolean currentValue = property.getValue();
		property.setValue(Boolean.TRUE);

		assertFalse(currentValue);
		assertTrue(row.selected());
	}

	@Test
	void selectAllSelectionColumnShouldToggleAllRowsAndShowPartialSelection() {
		JavaFxTestSupport.runFx(() -> {
			SelectionRow first = new SelectionRow();
			SelectionRow second = new SelectionRow();
			ObservableList<SelectionRow> rows = FXCollections.observableArrayList(first, second);
			TableColumn<SelectionRow, Boolean> column = FxTableUtils.createSelectAllSelectionColumn(
					"Select all", rows, item -> item.selected(), (item, selected) -> item.selected(selected));
			TableView<SelectionRow> tableView = new TableView<>(rows);
			tableView.getColumns().add(column);
			CheckBox selectAll = (CheckBox) column.getGraphic();
			assertEquals("", column.getText());
			assertFalse(column.isSortable());

			selectAll.fire();
			assertTrue(first.selected());
			assertTrue(second.selected());
			assertTrue(selectAll.isSelected());

			BooleanProperty firstSelection = (BooleanProperty) column.getCellValueFactory()
					.call(new TableColumn.CellDataFeatures<>(tableView, column, first));
			firstSelection.set(false);
			assertTrue(selectAll.isIndeterminate());

			selectAll.fire();
			assertTrue(first.selected());
			assertTrue(second.selected());
			selectAll.fire();
			assertFalse(first.selected());
			assertFalse(second.selected());
		});
	}

	@Test
	void selectAllSelectionColumnShouldAffectVisibleRowsOnly() {
		JavaFxTestSupport.runFx(() -> {
			SelectionRow visible = new SelectionRow();
			SelectionRow hidden = new SelectionRow();
			ObservableList<SelectionRow> allRows = FXCollections.observableArrayList(visible, hidden);
			FilteredList<SelectionRow> visibleRows = new FilteredList<>(allRows, row -> row == visible);
			TableColumn<SelectionRow, Boolean> column = FxTableUtils.createSelectAllSelectionColumn(
					"Select all", visibleRows, row -> row.selected(), (row, selected) -> row.selected(selected));
			CheckBox selectAll = (CheckBox) column.getGraphic();

			selectAll.fire();

			assertTrue(visible.selected());
			assertFalse(hidden.selected());
			assertTrue(selectAll.isSelected());

			visibleRows.setPredicate(row -> true);
			assertTrue(selectAll.isIndeterminate());
		});
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
