package de.gbanking.gui.fx.util;

import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.Region;
import javafx.util.Callback;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class FxTableUtils {

	private FxTableUtils() {
	}

	public static void setFixedWidth(TableColumn<?, ?> column, double width) {
		column.setMinWidth(width);
		column.setPrefWidth(width);
		column.setMaxWidth(width);
		column.setResizable(false);
	}

	public static void setPreferredWidth(TableColumn<?, ?> column, double minWidth, double prefWidth) {
		column.setMinWidth(minWidth);
		column.setPrefWidth(prefWidth);
	}

	public static <S> Callback<TableColumn<S, Boolean>, TableCell<S, Boolean>> createCheckBoxCellFactory() {
		return column -> new TableCell<>() {
			private final CheckBox checkBox = new CheckBox();

			{
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
				setAlignment(Pos.CENTER);
				checkBox.setFocusTraversable(false);
			}

			@Override
			protected void updateItem(Boolean item, boolean empty) {
				super.updateItem(item, empty);

				if (empty) {
					setGraphic(null);
					return;
				}

				checkBox.setSelected(Boolean.TRUE.equals(item));
				setGraphic(checkBox);
			}
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

	public static <S> TableColumn<S, Boolean> createSelectionColumn(String title, Function<S, Boolean> getter, BiConsumer<S, Boolean> setter) {

		TableColumn<S, Boolean> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> {
			S rowItem = data.getValue();
			return new javafx.beans.property.SimpleBooleanProperty(Boolean.TRUE.equals(getter.apply(rowItem))) {
				@Override
				public void set(boolean newValue) {
					setter.accept(rowItem, newValue);
					super.set(newValue);
				}
			};
		});
		column.setCellFactory(createCheckBoxCellFactory());
		setFixedWidth(column, 25);
		return column;
	}
}