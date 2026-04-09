package de.gbanking.gui.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.util.Callback;

public final class FxTableUtils {

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

	public static <S> Callback<TableColumn<S, Boolean>, TableCell<S, Boolean>> createCheckBoxCellFactory() {
		return column -> {
			CheckBoxTableCell<S, Boolean> cell = new CheckBoxTableCell<>();
			cell.setAlignment(Pos.CENTER);
			cell.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			return cell;
		};
	}

	public static <S> Callback<TableColumn<S, BigDecimal>, TableCell<S, BigDecimal>> createBigDecimalAmountCellFactory() {
		return column -> new TableCell<>() {

			private final DecimalFormat format = createGermanDecimalFormat();
			private final Text textNode = createAmountText();
			private final HBox graphic = createAmountGraphic(textNode);

			@Override
			protected void updateItem(BigDecimal item, boolean empty) {
				super.updateItem(item, empty);
				getStyleClass().removeAll("amount-positive", "amount-negative", "amount-neutral");
				textNode.getStyleClass().removeAll("amount-positive", "amount-negative", "amount-neutral");
				setAlignment(Pos.CENTER_RIGHT);

				if (empty || item == null) {
					setText(null);
					setGraphic(null);
					setStyle(null);
					return;
				}

				textNode.setText(format.format(item));
				applyAmountTextStyle(textNode, item);
				setText(null);
				setGraphic(graphic);
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

				if (item.signum() > 0) {
					getStyleClass().add("amount-positive");
					textNode.getStyleClass().add("amount-positive");
				} else if (item.signum() < 0) {
					getStyleClass().add("amount-negative");
					textNode.getStyleClass().add("amount-negative");
				} else {
					getStyleClass().add("amount-neutral");
					textNode.getStyleClass().add("amount-neutral");
				}
			}

			@Override
			public void updateSelected(boolean selected) {
				super.updateSelected(selected);
				if (getItem() != null) {
					applyAmountTextStyle(textNode, getItem());
				}
			}
		};
	}

	public static <S> Callback<TableColumn<S, LocalDate>, TableCell<S, LocalDate>> createLocalDateCellFactory() {
		return column -> new TableCell<>() {
			@Override
			protected void updateItem(LocalDate item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : DateFormatUtils.formatShort(item));
			}
		};
	}

	public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> createAmountCellFactory() {
		return column -> new TableCell<>() {

			private final DecimalFormat format = createGermanDecimalFormat();
			private final Text textNode = createAmountText();
			private final HBox graphic = createAmountGraphic(textNode);

			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				getStyleClass().removeAll("amount-positive", "amount-negative", "amount-neutral");
				textNode.getStyleClass().removeAll("amount-positive", "amount-negative", "amount-neutral");
				setAlignment(Pos.CENTER_RIGHT);

				if (empty || item == null || item.isBlank()) {
					setText(null);
					setGraphic(null);
					setStyle(null);
					return;
				}

				try {
					BigDecimal value = new BigDecimal(item.replace(",", "."));
					textNode.setText(format.format(value));
					applyAmountTextStyle(textNode, value);
					setText(null);
					setGraphic(graphic);
					setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

					if (value.signum() > 0) {
						getStyleClass().add("amount-positive");
						textNode.getStyleClass().add("amount-positive");
					} else if (value.signum() < 0) {
						getStyleClass().add("amount-negative");
						textNode.getStyleClass().add("amount-negative");
					} else {
						getStyleClass().add("amount-neutral");
						textNode.getStyleClass().add("amount-neutral");
					}
				} catch (NumberFormatException ex) {
					textNode.setText(item);
					applyAmountTextStyle(textNode, null);
					setText(null);
					setGraphic(graphic);
					setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
					getStyleClass().add("amount-neutral");
					textNode.getStyleClass().add("amount-neutral");
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
		return column -> new TableCell<>() {

			private final Label label = new Label();

			{
				label.setWrapText(true);
				label.setMaxWidth(Double.MAX_VALUE);
				label.setPrefHeight(Region.USE_COMPUTED_SIZE);
				label.maxWidthProperty().bind(column.widthProperty().subtract(16));
				setGraphic(label);
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
				setAlignment(Pos.CENTER_LEFT);
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
			Function<S, Boolean> getter,
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

	private static <S> BooleanProperty createSelectionProperty(S rowItem, Function<S, Boolean> getter, BiConsumer<S, Boolean> setter) {

		SimpleBooleanProperty property = new SimpleBooleanProperty(Boolean.TRUE.equals(getter.apply(rowItem)));
		property.addListener((obs, oldValue, newValue) -> setter.accept(rowItem, Boolean.TRUE.equals(newValue)));
		return property;
	}

	private static Text createAmountText() {
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

	private static void applyAmountTextStyle(Text text, BigDecimal value) {
		Color color = resolveAmountColor(value);
		text.setFill(color);
		text.setStyle("-fx-font-weight: bold; -fx-fill: " + toCssColor(color) + ";");
	}

	private static HBox createAmountGraphic(Text text) {
		HBox box = new HBox(text);
		box.setAlignment(Pos.CENTER_RIGHT);
		box.setMaxWidth(Double.MAX_VALUE);
		return box;
	}

	private static String toCssColor(Color color) {
		return String.format("rgb(%d, %d, %d)",
				(int) Math.round(color.getRed() * 255),
				(int) Math.round(color.getGreen() * 255),
				(int) Math.round(color.getBlue() * 255));
	}
}
