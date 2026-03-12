package de.gbanking.gui.fx.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.Region;
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

	public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> createAmountCellFactory() {
		return column -> new TableCell<>() {

			private final DecimalFormat format = createGermanDecimalFormat();

			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				getStyleClass().removeAll("amount-positive", "amount-negative", "amount-neutral");

				if (empty || item == null || item.isBlank()) {
					setText(null);
					setGraphic(null);
					setAlignment(Pos.CENTER_RIGHT);
					return;
				}

				setAlignment(Pos.CENTER_RIGHT);

				try {
					BigDecimal value = new BigDecimal(item.replace(",", "."));
					setText(format.format(value));

					if (value.signum() > 0) {
						getStyleClass().add("amount-positive");
					} else if (value.signum() < 0) {
						getStyleClass().add("amount-negative");
					} else {
						getStyleClass().add("amount-neutral");
					}
				} catch (NumberFormatException ex) {
					setText(item);
					getStyleClass().add("amount-neutral");
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
				label.maxWidthProperty().bind(getTableColumn().widthProperty().subtract(16));
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

		column.setCellValueFactory(cellData -> {
			S rowItem = cellData.getValue();
			SimpleBooleanProperty property = new SimpleBooleanProperty(Boolean.TRUE.equals(getter.apply(rowItem)));
			property.addListener((obs, oldValue, newValue) -> setter.accept(rowItem, Boolean.TRUE.equals(newValue)));
			return property;
		});

		column.setCellFactory(col -> {
			CheckBoxTableCell<S, Boolean> cell = new CheckBoxTableCell<>();
			cell.setAlignment(Pos.CENTER);
			cell.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			cell.setSelectedStateCallback(index -> {
				if (index < 0 || index >= col.getTableView().getItems().size()) {
					return new SimpleBooleanProperty(false);
				}

				S rowItem = col.getTableView().getItems().get(index);
				SimpleBooleanProperty property = new SimpleBooleanProperty(Boolean.TRUE.equals(getter.apply(rowItem)));
				property.addListener((obs, oldValue, newValue) -> setter.accept(rowItem, Boolean.TRUE.equals(newValue)));
				return property;
			});
			return cell;
		});

		column.setEditable(true);
		setFixedWidth(column, 25);
		return column;
	}
}