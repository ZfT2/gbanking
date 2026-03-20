package de.gbanking.gui.util;

import java.util.Calendar;
import java.util.Date;
import java.util.function.Function;

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

	public static <S> TableColumn<S, String> createAmountColumn(String title, Function<S, String> valueProvider, double width) {
		TableColumn<S, String> column = createFixedTextColumn(title, valueProvider, width);
		column.setCellFactory(FxTableUtils.createAmountCellFactory());
		return column;
	}

	public static <S> TableColumn<S, String> createDateColumn(String title, Function<S, Date> valueProvider, double width) {
		TableColumn<S, String> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new SimpleStringProperty(DateFormatUtils.formatShort(valueProvider.apply(data.getValue()))));
		FxTableUtils.setFixedWidth(column, width);
		return column;
	}

	public static <S> TableColumn<S, String> createCalendarDateColumn(String title, Function<S, Calendar> valueProvider, double width) {
		TableColumn<S, String> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new SimpleStringProperty(DateFormatUtils.formatShort(valueProvider.apply(data.getValue()))));
		FxTableUtils.setFixedWidth(column, width);
		return column;
	}

	public static <S> TableColumn<S, String> createUpdatedAtColumn(String title, Function<S, Calendar> valueProvider, double width) {
		return createCalendarDateColumn(title, valueProvider, width);
	}

	public static <S> TableColumn<S, String> createSymbolColumn(String title, Function<S, String> valueProvider, double width) {
		return createFixedTextColumn(title, valueProvider, width);
	}

	public static <S> TableColumn<S, String> createBooleanAsTextColumn(String title, Function<S, Boolean> valueProvider, double width) {
		TableColumn<S, String> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new SimpleStringProperty(Boolean.TRUE.equals(valueProvider.apply(data.getValue())) ? "true" : "false"));
		FxTableUtils.setFixedWidth(column, width);
		return column;
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}