package de.zft2.gbanking.gui.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Function;
import java.util.function.Predicate;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;

public final class TableColumnFactory {

	private TableColumnFactory() {
	}

	public static <S> TableColumn<S, String> createTextColumn(String title, Function<S, String> valueProvider, double minWidth, double prefWidth) {
		TableColumn<S, String> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new SimpleStringProperty(safe(valueProvider.apply(data.getValue()))));
		FxTableUtils.setPreferredWidth(column, minWidth, prefWidth);
		return column;
	}

	public static <S> TableColumn<S, String> createFixedTextColumn(String title, Function<S, String> valueProvider, double width) {
		TableColumn<S, String> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new SimpleStringProperty(safe(valueProvider.apply(data.getValue()))));
		FxTableUtils.setFixedWidth(column, width);
		return column;
	}

	public static <S> TableColumn<S, String> createWrappedTextColumn(String title, Function<S, String> valueProvider, double minWidth, double prefWidth) {
		TableColumn<S, String> column = createTextColumn(title, valueProvider, minWidth, prefWidth);
		column.setCellFactory(FxTableUtils.createWrappedTextCellFactory());
		return column;
	}

	public static <S> TableColumn<S, BigDecimal> createAmountColumn(String title, Function<S, BigDecimal> valueProvider) {
		return createAmountColumn(title, valueProvider, item -> false);
	}

	public static <S> TableColumn<S, BigDecimal> createAmountColumn(String title, Function<S, BigDecimal> valueProvider, Predicate<S> mutedProvider) {
		TableColumn<S, BigDecimal> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(valueProvider.apply(data.getValue())));
		column.setCellFactory(FxTableUtils.createBigDecimalAmountCellFactory(mutedProvider));
		return column;
	}

	public static <S> TableColumn<S, LocalDate> createCalendarDateColumn(String title, Function<S, LocalDate> valueProvider, double width) {
		TableColumn<S, LocalDate> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(valueProvider.apply(data.getValue())));
		column.setCellFactory(FxTableUtils.createLocalDateCellFactory());
		FxTableUtils.setFixedWidth(column, width);
		return column;
	}

	public static <S> TableColumn<S, LocalDate> createUpdatedAtColumn(String title, Function<S, LocalDate> valueProvider, double width) {
		TableColumn<S, LocalDate> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(valueProvider.apply(data.getValue())));
		column.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
			@Override
			protected void updateItem(LocalDate item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : DateFormatUtils.formatLong(item));
			}
		});
		FxTableUtils.setFixedWidth(column, width);
		return column;
	}

	public static <S> TableColumn<S, String> createSymbolColumn(String title, Function<S, String> valueProvider, double width) {
		return createFixedTextColumn(title, valueProvider, width);
	}

	public static <S> TableColumn<S, String> createBooleanAsTextColumn(String title, Predicate<S> valueProvider, double width) {
		TableColumn<S, String> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new SimpleStringProperty(Boolean.TRUE.equals(valueProvider.test(data.getValue())) ? "true" : "false"));
		FxTableUtils.setFixedWidth(column, width);
		return column;
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
