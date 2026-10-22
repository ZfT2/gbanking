package de.zft2.gbanking.gui.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.function.Predicate;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;

public final class TableColumnFactory {

	private TableColumnFactory() {
	}

	public static <S> TableColumn<S, String> createTextColumn(String title, Function<S, String> valueProvider, double minWidth, double prefWidth) {
		TableColumn<S, String> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new SimpleStringProperty(safe(valueProvider.apply(data.getValue()))));
		FxTableUtils.setPreferredWidth(column, minWidth, prefWidth);
		return column;
	}

	/**
	 * Creates a text column with the given preferred width. The minimum width of the column stays
	 * unchanged, therefore the user can still resize the column.
	 *
	 * @param <S>           the row type
	 * @param title         the column title
	 * @param valueProvider supplies the text of a row
	 * @param prefWidth     the preferred column width
	 * @return the configured column
	 */
	public static <S> TableColumn<S, String> createTextColumn(String title, Function<S, String> valueProvider, double prefWidth) {
		TableColumn<S, String> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new SimpleStringProperty(safe(valueProvider.apply(data.getValue()))));
		column.setPrefWidth(prefWidth);
		return column;
	}

	/**
	 * Creates a column for an arbitrary value with the given preferred width.
	 *
	 * @param <S>           the row type
	 * @param <T>           the value type of the column
	 * @param title         the column title
	 * @param valueProvider supplies the value of a row
	 * @param prefWidth     the preferred column width
	 * @return the configured column
	 */
	public static <S, T> TableColumn<S, T> createValueColumn(String title, Function<S, T> valueProvider, double prefWidth) {
		TableColumn<S, T> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(valueProvider.apply(data.getValue())));
		column.setPrefWidth(prefWidth);
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

	/**
	 * Creates a wrapped text column with the given preferred width.
	 *
	 * @param <S>           the row type
	 * @param title         the column title
	 * @param valueProvider supplies the text of a row
	 * @param prefWidth     the preferred column width
	 * @return the configured column
	 */
	public static <S> TableColumn<S, String> createWrappedTextColumn(String title, Function<S, String> valueProvider, double prefWidth) {
		TableColumn<S, String> column = createTextColumn(title, valueProvider, prefWidth);
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

	/**
	 * Creates an amount column with the given preferred width.
	 *
	 * @param <S>           the row type
	 * @param title         the column title
	 * @param valueProvider supplies the amount of a row
	 * @param prefWidth     the preferred column width
	 * @return the configured column
	 */
	public static <S> TableColumn<S, BigDecimal> createAmountColumn(String title, Function<S, BigDecimal> valueProvider, double prefWidth) {
		TableColumn<S, BigDecimal> column = createAmountColumn(title, valueProvider);
		column.setPrefWidth(prefWidth);
		return column;
	}

	public static <S> TableColumn<S, LocalDate> createCalendarDateColumn(String title, Function<S, LocalDate> valueProvider, double width) {
		TableColumn<S, LocalDate> column = createDateColumn(title, valueProvider, width);
		FxTableUtils.setFixedWidth(column, width);
		return column;
	}

	/**
	 * Creates a date column with the given preferred width.
	 *
	 * @param <S>           the row type
	 * @param title         the column title
	 * @param valueProvider supplies the date of a row
	 * @param prefWidth     the preferred column width
	 * @return the configured column
	 */
	public static <S> TableColumn<S, LocalDate> createDateColumn(String title, Function<S, LocalDate> valueProvider, double prefWidth) {
		TableColumn<S, LocalDate> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(valueProvider.apply(data.getValue())));
		column.setCellFactory(FxTableUtils.createLocalDateCellFactory());
		column.setPrefWidth(prefWidth);
		return column;
	}

	public static <S> TableColumn<S, LocalDateTime> createDateTimeColumn(String title, Function<S, LocalDateTime> valueProvider, double width) {
		TableColumn<S, LocalDateTime> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(valueProvider.apply(data.getValue())));
		column.setCellFactory(ignored -> new TableCell<>() {
			@Override
			protected void updateItem(LocalDateTime item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : DateFormatUtils.formatDateTime(item));
			}
		});
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

	/**
	 * Creates a column that renders a boolean state as a green check mark or a red cross.
	 *
	 * @param <S> the row type
	 * @param title the column title
	 * @param valueProvider supplies the boolean state of a row
	 * @param width the fixed column width
	 * @return the configured column
	 */
	public static <S> TableColumn<S, Boolean> createBooleanSymbolColumn(String title, Predicate<S> valueProvider, double width) {
		TableColumn<S, Boolean> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(valueProvider.test(data.getValue())));
		column.setCellFactory(FxTableUtils.createBooleanSymbolCellFactory());
		FxTableUtils.setFixedWidth(column, width);
		return column;
	}

	/**
	 * Creates a column that renders a boolean state as a symbol with the given style classes and
	 * the given preferred width.
	 *
	 * @param <S>              the row type
	 * @param title            the column title
	 * @param valueProvider    supplies the boolean state of a row
	 * @param prefWidth        the preferred column width
	 * @param styleClass       the style class of the symbol
	 * @param validStyleClass  the style class of the check mark
	 * @param invalidStyleClass the style class of the cross
	 * @return the configured column
	 */
	public static <S> TableColumn<S, Boolean> createBooleanSymbolColumn(String title, Predicate<S> valueProvider, double prefWidth,
			String styleClass, String validStyleClass, String invalidStyleClass) {
		TableColumn<S, Boolean> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(valueProvider.test(data.getValue())));
		column.setCellFactory(FxTableUtils.createBooleanSymbolCellFactory(styleClass, validStyleClass, invalidStyleClass));
		column.setPrefWidth(prefWidth);
		return column;
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
