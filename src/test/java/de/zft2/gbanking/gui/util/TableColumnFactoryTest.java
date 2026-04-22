package de.zft2.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

class TableColumnFactoryTest {

	@Test
	void textColumnsShouldHandleNullValuesAndWidths() {
		TableColumn<Row, String> column = JavaFxTestSupport.callFx(
				() -> TableColumnFactory.createTextColumn("Name", Row::name, 80, 120));

		assertEquals("", readValue(column, new Row(null, null, null, null)).getValue());
		assertEquals(80.0, column.getMinWidth());
		assertEquals(120.0, column.getPrefWidth());
	}

	@Test
	void wrappedTextColumnShouldHaveCellFactory() {
		TableColumn<Row, String> column = JavaFxTestSupport.callFx(
				() -> TableColumnFactory.createWrappedTextColumn("Usage", Row::name, 120, 220));

		assertNotNull(column.getCellFactory());
	}

	@Test
	void amountAndDateColumnsShouldExposeValues() {
		Row row = new Row("Test", new BigDecimal("42.50"), new Date(0), LocalDate.of(2025, 7, 14));

		TableColumn<Row, BigDecimal> amountColumn = JavaFxTestSupport.callFx(
				() -> TableColumnFactory.createAmountColumn("Amount", Row::amount, 100));
		TableColumn<Row, Date> dateColumn = JavaFxTestSupport.callFx(
				() -> TableColumnFactory.createDateColumn("Date", Row::date, 90));
		TableColumn<Row, LocalDate> updatedColumn = JavaFxTestSupport.callFx(
				() -> TableColumnFactory.createUpdatedAtColumn("Updated", Row::updated, 90));

		assertEquals(new BigDecimal("42.50"), readValue(amountColumn, row).getValue());
		assertEquals(new Date(0), readValue(dateColumn, row).getValue());
		assertEquals(LocalDate.of(2025, 7, 14), readValue(updatedColumn, row).getValue());
		assertEquals(90.0, updatedColumn.getPrefWidth());
	}

	@Test
	void booleanAsTextColumnShouldMapTrueAndFalse() {
		TableColumn<Row, String> column = JavaFxTestSupport.callFx(
				() -> TableColumnFactory.createBooleanAsTextColumn("Active", Row::active, 70));

		assertEquals("true", readValue(column, new Row("A", null, null, null, true)).getValue());
		assertEquals("false", readValue(column, new Row("B", null, null, null, false)).getValue());
	}

	private static <T> ObservableValue<T> readValue(TableColumn<Row, T> column, Row row) {
		TableView<Row> tableView = new TableView<>();
		return column.getCellValueFactory().call(new TableColumn.CellDataFeatures<>(tableView, column, row));
	}

	private record Row(String name, BigDecimal amount, Date date, LocalDate updated, Boolean active) {
		private Row(String name, BigDecimal amount, Date date, LocalDate updated) {
			this(name, amount, date, updated, Boolean.FALSE);
		}
	}
}
