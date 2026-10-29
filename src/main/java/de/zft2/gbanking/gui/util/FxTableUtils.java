package de.zft2.gbanking.gui.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.util.Callback;

public final class FxTableUtils {

	private static final String AMOUNT_POSITIVE = "amount-positive";
	private static final String AMOUNT_NEGATIVE = "amount-negative";
	private static final String AMOUNT_NEUTRAL = "amount-neutral";
	private static final String AMOUNT_MUTED = "amount-muted";
	private static final List<String> AMOUNT_STYLE_CLASSES = List.of(AMOUNT_POSITIVE, AMOUNT_NEGATIVE,
			AMOUNT_NEUTRAL, AMOUNT_MUTED);
	private static final double CELL_HORIZONTAL_INSETS = 20;

	private FxTableUtils() {
	}

	public static <S, T> void setFixedWidth(TableColumn<S, T> column, double width) {
		column.setMinWidth(width);
		column.setPrefWidth(width);
		column.setMaxWidth(width);
		column.setResizable(false);
	}

	public static <S, T> void setPreferredWidth(TableColumn<S, T> column, double minWidth, double prefWidth) {
		column.setMinWidth(minWidth);
		column.setPrefWidth(prefWidth);
	}

	public static void ensureColumnFitsText(TableColumn<?, ?> column, Text text) {
		column.setMinWidth(Math.max(column.getMinWidth(),
				Math.ceil(text.getLayoutBounds().getWidth()) + CELL_HORIZONTAL_INSETS));
	}

	public static <S> Callback<TableColumn<S, Boolean>, TableCell<S, Boolean>> createCheckBoxCellFactory() {
		return column -> {
			CheckBoxTableCell<S, Boolean> cell = new CheckBoxTableCell<>();
			cell.setAlignment(Pos.CENTER);
			cell.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			return cell;
		};
	}

	public static <S> Callback<TableColumn<S, BigDecimal>, TableCell<S, BigDecimal>> createBigDecimalAmountCellFactory() {
		return createBigDecimalAmountCellFactory(item -> false);
	}

	public static <S> Callback<TableColumn<S, BigDecimal>, TableCell<S, BigDecimal>> createBigDecimalAmountCellFactory(Predicate<S> mutedProvider) {
		return createBigDecimalAmountCellFactory(mutedProvider, false);
	}

	public static <S> Callback<TableColumn<S, BigDecimal>, TableCell<S, BigDecimal>> createAutoFitBigDecimalAmountCellFactory(
			Predicate<S> mutedProvider) {
		return createBigDecimalAmountCellFactory(mutedProvider, true);
	}

	private static <S> Callback<TableColumn<S, BigDecimal>, TableCell<S, BigDecimal>> createBigDecimalAmountCellFactory(
			Predicate<S> mutedProvider, boolean autoFit) {
		return column -> new BigDecimalAmountTableCell<>(column, mutedProvider, autoFit);
	}

	public static <S> Callback<TableColumn<S, LocalDate>, TableCell<S, LocalDate>> createLocalDateCellFactory() {
		return createFormattedCellFactory(date -> DateFormatUtils.formatShort(date));
	}

	/**
	 * Creates a cell factory that renders the non-null values of a column with the given formatter.
	 *
	 * @param <S>       the row type
	 * @param <T>       the value type of the column
	 * @param formatter supplies the text of a non-null value
	 * @return the cell factory
	 */
	public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> createFormattedCellFactory(Function<? super T, String> formatter) {
		return column -> new TableCell<>() {
			@Override
			protected void updateItem(T item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : formatter.apply(item));
			}
		};
	}

	/**
	 * Creates a cell factory that renders a boolean state as a green check mark or a red cross.
	 *
	 * @param <S> the row type
	 * @return the cell factory for boolean symbol columns
	 */
	public static <S> Callback<TableColumn<S, Boolean>, TableCell<S, Boolean>> createBooleanSymbolCellFactory() {
		return createBooleanSymbolCellFactory(StatusSymbols.STYLE_CLASS, StatusSymbols.STYLE_CLASS_VALID,
				StatusSymbols.STYLE_CLASS_INVALID);
	}

	/**
	 * Creates a cell factory that renders a boolean state as a green check mark or a red cross with
	 * the given style classes.
	 *
	 * @param <S>             the row type
	 * @param styleClass      the style class of the symbol
	 * @param validStyleClass the style class of the check mark
	 * @param invalidStyleClass the style class of the cross
	 * @return the cell factory for boolean symbol columns
	 */
	public static <S> Callback<TableColumn<S, Boolean>, TableCell<S, Boolean>> createBooleanSymbolCellFactory(String styleClass,
			String validStyleClass, String invalidStyleClass) {
		return column -> new TableCell<>() {

			private final Label indicator = new Label();

			{
				setAlignment(Pos.CENTER);
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			}

			@Override
			protected void updateItem(Boolean item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setGraphic(null);
					return;
				}
				indicator.setText(StatusSymbols.text(item));
				indicator.getStyleClass().setAll(styleClass, item ? validStyleClass : invalidStyleClass);
				setGraphic(indicator);
			}
		};
	}

	public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> createAmountCellFactory() {
		return column -> new TableCell<>() {

			private final Text textNode = createAmountText();
			private final HBox graphic = createAmountGraphic(textNode);

			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				clearAmountStyleClasses(this, textNode);
				setAlignment(Pos.CENTER_RIGHT);

				if (empty || item == null || item.isBlank()) {
					setText(null);
					setGraphic(null);
					setStyle(null);
					return;
				}

				try {
					formatAmount(this, textNode, graphic, item);
				} catch (NumberFormatException ex) {
					textNode.setText(item);
					applyAmountTextStyle(textNode, null);
					setText(null);
					setGraphic(graphic);
					setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
					addAmountStyleClass(null, false, this, textNode);
				}
			}

			@Override
			public void updateSelected(boolean selected) {
				super.updateSelected(selected);
				if (getItem() == null || getItem().isBlank()) {
					return;
				}
				try {
					applyAmountTextStyle(textNode, new BigDecimal(getItem().replace(",", ".")));
				} catch (NumberFormatException ex) {
					applyAmountTextStyle(textNode, null);
				}
			}
		};
	}

	public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> createWrappedTextCellFactory() {
		return createWrappedTextCellFactory(Pos.CENTER_LEFT);
	}

	/**
	 * Creates a cell factory for a text that is wrapped into the cell.
	 *
	 * @param <S>       the row type
	 * @param alignment the alignment of the text within the cell
	 * @return the cell factory for wrapped text columns
	 */
	public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> createWrappedTextCellFactory(Pos alignment) {
		return column -> new TableCell<>() {

			private final Label label = new Label();

			{
				label.setWrapText(true);
				label.setMaxWidth(Double.MAX_VALUE);
				label.setPrefHeight(Region.USE_COMPUTED_SIZE);
				label.maxWidthProperty().bind(column.widthProperty().subtract(16));
				setGraphic(label);
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
				setAlignment(alignment);
			}

			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);

				if (empty || item == null) {
					label.setText(null);
					setGraphic(null);
					return;
				}

				label.setText(item);
				setGraphic(label);
			}
		};
	}

	public static DecimalFormat createGermanDecimalFormat() {
		DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMAN);
		symbols.setGroupingSeparator('.');
		return new DecimalFormat("#,##0.00;#,##0.00", symbols);
	}

	public static <S> TableColumn<S, Boolean> createSelectionColumn(
			String title,
			Predicate<S> getter,
			BiConsumer<S, Boolean> setter) {

		TableColumn<S, Boolean> column = new TableColumn<>(title);

		column.setCellValueFactory(cellData -> createSelectionProperty(cellData.getValue(), getter, setter));
		column.setCellFactory(col -> {
			CheckBoxTableCell<S, Boolean> cell = new CheckBoxTableCell<>();
			cell.setAlignment(Pos.CENTER);
			cell.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			cell.setSelectedStateCallback(index -> {
				if (index < 0 || index >= col.getTableView().getItems().size()) {
					return new SimpleBooleanProperty(false);
				}

				S rowItem = col.getTableView().getItems().get(index);
				return createSelectionProperty(rowItem, getter, setter);
			});
			return cell;
		});

		column.setEditable(true);
		setFixedWidth(column, 25);
		return column;
	}

	public static <S> TableColumn<S, Boolean> createSelectAllSelectionColumn(
			String accessibleTitle,
			ObservableList<S> rows,
			Predicate<S> getter,
			BiConsumer<S, Boolean> setter) {

		CheckBox selectAll = new CheckBox();
		selectAll.setAccessibleText(accessibleTitle);
		selectAll.setTooltip(new Tooltip(accessibleTitle));
		selectAll.setAllowIndeterminate(true);

		BiConsumer<S, Boolean> updatingSetter = (row, selected) -> {
			setter.accept(row, selected);
			updateSelectAllState(selectAll, rows, getter);
		};
		TableColumn<S, Boolean> column = createSelectionColumn("", getter, updatingSetter);
		column.setGraphic(selectAll);
		column.setSortable(false);
		setFixedWidth(column, 32);

		selectAll.setOnAction(event -> {
			boolean selectRows = rows.stream().anyMatch(row -> !getter.test(row));
			rows.forEach(row -> setter.accept(row, selectRows));
			updateSelectAllState(selectAll, rows, getter);
			if (column.getTableView() != null) {
				column.getTableView().refresh();
			}
		});
		rows.addListener((ListChangeListener<S>) change -> updateSelectAllState(selectAll, rows, getter));
		updateSelectAllState(selectAll, rows, getter);
		return column;
	}

	private static <S> void updateSelectAllState(CheckBox selectAll, List<S> rows, Predicate<S> getter) {
		long selectedCount = rows.stream().filter(getter).count();
		selectAll.setDisable(rows.isEmpty());
		selectAll.setIndeterminate(selectedCount > 0 && selectedCount < rows.size());
		selectAll.setSelected(!rows.isEmpty() && selectedCount == rows.size());
	}

	private static <S> BooleanProperty createSelectionProperty(S rowItem, Predicate<S> getter, BiConsumer<S, Boolean> setter) {

		SimpleBooleanProperty property = new SimpleBooleanProperty(Boolean.TRUE.equals(getter.test(rowItem)));
		property.addListener((obs, oldValue, newValue) -> setter.accept(rowItem, Boolean.TRUE.equals(newValue)));
		return property;
	}

	public static Text createAmountText() {
		Text text = new Text();
		text.getStyleClass().addAll("amount", "amount-text");
		text.setStyle("-fx-font-weight: bold;");
		return text;
	}

	private static Color resolveAmountColor(BigDecimal value) {
		if (value == null || value.signum() == 0) {
			return Color.BLACK;
		}
		return value.signum() > 0 ? Color.rgb(0, 100, 0) : Color.RED;
	}

	/**
	 * Returns the CSS color of the given amount: green for positive, red for negative and black for zero.
	 */
	public static String amountCssColor(BigDecimal value) {
		return toCssColor(resolveAmountColor(value));
	}

	public static void applyAmountTextStyle(Text text, BigDecimal value) {
		applyAmountTextStyle(text, value, false);
	}

	private static void applyAmountTextStyle(Text text, BigDecimal value, boolean muted) {
		Color color = muted ? Color.rgb(112, 112, 112) : resolveAmountColor(value);
		text.setFill(color);
		text.setStyle("-fx-font-weight: bold; -fx-fill: " + toCssColor(color) + ";");
	}

	public static HBox createAmountGraphic(Text text) {
		HBox box = new HBox(text);
		box.setAlignment(Pos.CENTER_RIGHT);
		box.setMaxWidth(Double.MAX_VALUE);
		return box;
	}

	/**
	 * Removes the amount style classes (positive, negative, neutral, muted) from the given nodes.
	 */
	public static void clearAmountStyleClasses(Node... nodes) {
		for (Node node : nodes) {
			node.getStyleClass().removeAll(AMOUNT_STYLE_CLASSES);
		}
	}

	/**
	 * Returns the amount style class matching the sign of the given value. A {@code null} value is treated
	 * as zero, a muted amount always receives the muted style class.
	 */
	public static String amountStyleClass(BigDecimal value, boolean muted) {
		if (muted) {
			return AMOUNT_MUTED;
		}
		if (value == null || value.signum() == 0) {
			return AMOUNT_NEUTRAL;
		}
		return value.signum() > 0 ? AMOUNT_POSITIVE : AMOUNT_NEGATIVE;
	}

	/**
	 * Adds the amount style class matching the given value to the given nodes.
	 *
	 * @see #amountStyleClass(BigDecimal, boolean)
	 */
	public static void addAmountStyleClass(BigDecimal value, boolean muted, Node... nodes) {
		String styleClass = amountStyleClass(value, muted);
		for (Node node : nodes) {
			node.getStyleClass().add(styleClass);
		}
	}

	private static String toCssColor(Color color) {
		return String.format(Locale.ROOT, "rgb(%d, %d, %d)",
				(int) Math.round(color.getRed() * 255),
				(int) Math.round(color.getGreen() * 255),
				(int) Math.round(color.getBlue() * 255));
	}

	private static final class BigDecimalAmountTableCell<S> extends TableCell<S, BigDecimal> {

		private final TableColumn<S, BigDecimal> column;
		private final Predicate<S> mutedProvider;
		private final boolean autoFit;
		private final DecimalFormat format = createGermanDecimalFormat();
		private final Text textNode = createAmountText();
		private final HBox graphic = createAmountGraphic(textNode);

		private BigDecimalAmountTableCell(TableColumn<S, BigDecimal> column, Predicate<S> mutedProvider, boolean autoFit) {
			this.column = column;
			this.mutedProvider = mutedProvider;
			this.autoFit = autoFit;
		}

		@Override
		protected void updateItem(BigDecimal item, boolean empty) {
			super.updateItem(item, empty);
			clearAmountStyleClasses(this, textNode);
			setAlignment(Pos.CENTER_RIGHT);
			if (empty || item == null) {
				setText(null);
				setGraphic(null);
				setStyle(null);
				return;
			}

			boolean muted = isMuted();
			textNode.setText(format.format(item));
			applyAmountTextStyle(textNode, item, muted);
			if (autoFit) {
				ensureColumnFitsText(column, textNode);
			}
			setText(null);
			setGraphic(graphic);
			setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			addAmountStyleClass(item, muted, this, textNode);
		}

		@Override
		public void updateSelected(boolean selected) {
			super.updateSelected(selected);
			if (getItem() != null) {
				applyAmountTextStyle(textNode, getItem(), isMuted());
			}
		}

		private boolean isMuted() {
			S rowItem = getTableRow() != null ? getTableRow().getItem() : null;
			if (rowItem == null && column.getTableView() != null && getIndex() >= 0
					&& getIndex() < column.getTableView().getItems().size()) {
				rowItem = column.getTableView().getItems().get(getIndex());
			}
			return rowItem != null && mutedProvider != null && mutedProvider.test(rowItem);
		}
	}

	private static <S> void formatAmount(TableCell<S, String> tableCell, Text textNode, HBox graphic, String item) {
		BigDecimal value = new BigDecimal(item.replace(",", "."));
		final DecimalFormat format = createGermanDecimalFormat();
		textNode.setText(format.format(value));
		applyAmountTextStyle(textNode, value);
		tableCell.setText(null);
		tableCell.setGraphic(graphic);
		tableCell.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		addAmountStyleClass(value, false, tableCell, textNode);
	}
}
