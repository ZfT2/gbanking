package de.zft2.gbanking.gui.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.zft2.gbanking.BaseMessagesBean;
import de.zft2.gbanking.gui.KeyboardShortcutDispatcher;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public final class TableSearchBar<T> extends HBox implements BaseMessagesBean {

	private final ObservableList<T> rows = FXCollections.observableArrayList();
	private final FilteredList<T> filtered;
	private final TextField search = new TextField();
	private final PauseTransition delay;
	private final Function<T, String> searchableText;

	public TableSearchBar(TableView<T> table, Function<T, String> searchableText) {
		super(10);
		this.searchableText = searchableText;
		filtered = FxTableUtils.createFilteredTableItems(table, rows, null).filtered();
		delay = FxTableUtils.createFilterDelay(search, () -> applyFilter());
		Button reset = new Button(getText("UI_BUTTON_RESET_FILTER"));
		reset.setOnAction(event -> clear());
		getChildren().addAll(new Label(getText("UI_LABEL_SEARCH")), search, reset);
		setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(search, Priority.ALWAYS);
		search.setMaxWidth(Double.MAX_VALUE);
		KeyboardShortcutDispatcher.registerFilter(this, search, table);
		search.setOnAction(event -> applyFilter());
	}

	public void setRows(Collection<T> values) {
		rows.setAll(values);
	}

	public void clear() {
		search.clear();
		applyFilter();
	}

	public void setActive(boolean active) {
		setVisible(active);
		setManaged(active);
		applyFilter();
	}

	private void applyFilter() {
		delay.stop();
		String query = search.getText().strip().toLowerCase(Locale.ROOT);
		List<String> terms = query.isEmpty() ? List.of() : List.of(query.split("\\s+"));
		filtered.setPredicate(!isVisible() || terms.isEmpty() ? null : row -> {
			String text = searchableText.apply(row).toLowerCase(Locale.ROOT);
			return terms.stream().anyMatch(term -> text.contains(term));
		});
	}

	public static String text(Object... values) {
		return Stream.of(values).filter(value -> value != null).map(value -> {
			if (value instanceof LocalDate date) {
				return date + " " + DateFormatUtils.formatShort(date) + " " + DateFormatUtils.formatLong(date);
			}
			if (value instanceof BigDecimal amount) {
				String plain = amount.toPlainString();
				String formatted = FxTableUtils.formatGermanAmount(amount);
				// Preserve DecimalFormat's sign for negative amounts rounded to zero.
				if (amount.signum() < 0 && "0,00".equals(formatted)) {
					formatted = "-" + formatted;
				}
				return plain + " " + plain.replace('.', ',') + " " + formatted;
			}
			return value.toString();
		}).collect(Collectors.joining(" "));
	}
}
