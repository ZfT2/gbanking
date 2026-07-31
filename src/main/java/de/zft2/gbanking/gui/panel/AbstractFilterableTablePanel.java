package de.zft2.gbanking.gui.panel;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import de.zft2.gbanking.BaseMessagesBean;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.KeyboardShortcutDispatcher;
import de.zft2.gbanking.gui.component.GBankingTableView;
import de.zft2.gbanking.gui.util.FxTableUtils;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

public abstract class AbstractFilterableTablePanel<T> extends BorderPane implements BaseMessagesBean {

	protected final GBankingTableView<T> tableView = new GBankingTableView<>();
	protected final TextField filterText = new TextField();
	protected final Label titleLabel = new Label();
	protected final ObservableList<T> masterData;
	protected final FilteredList<T> filteredData;

	protected AbstractFilterableTablePanel(ObservableList<T> masterData) {
		this.masterData = masterData;
		this.filteredData = new FilteredList<>(masterData, item -> true);
		KeyboardShortcutDispatcher.registerFilter(this, filterText, tableView);

		SortedList<T> sorted = new SortedList<>(filteredData);
		sorted.comparatorProperty().bind(tableView.comparatorProperty());
		tableView.setItems(sorted);

		filterText.textProperty().addListener((obs, oldVal, newVal) -> filteredData.setPredicate(item -> matchesFilter(item, normalize(newVal))));

		filterText.setMaxWidth(Double.MAX_VALUE);
		HBox filterBox = new HBox(10, new Label(getText("UI_LABEL_SEARCH")), filterText);
		filterBox.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(filterText, Priority.ALWAYS);

		titleLabel.setMaxWidth(Double.MAX_VALUE);
		tableView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		setTop(titleLabel);
		setCenter(tableView);
		setBottom(filterBox);

		BorderPane.setMargin(titleLabel, new Insets(0, 0, 6, 0));
		BorderPane.setMargin(filterBox, new Insets(6, 0, 0, 0));
	}

	protected void setPanelTitle(String title) {
		titleLabel.setText(title);
	}

	protected void setPanelTitleByKey(String key) {
		setPanelTitle(getText(key));
	}

	protected void setColumns(List<TableColumn<T, ?>> columns) {
		tableView.getColumns().setAll(columns);
	}

	protected TableColumn<T, Boolean> createSelectAllSelectionColumn(Predicate<T> getter, BiConsumer<T, Boolean> setter) {
		return FxTableUtils.createSelectAllSelectionColumn(getText("UI_TABLE_SELECT_ALL"), tableView.getItems(), getter, setter);
	}

	protected void configureTableLayout(String layoutKey) {
		GuiLayoutState.configureTable(tableView, layoutKey);
	}

	protected void onSelection(Consumer<T> handler) {
		tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
			if (selected != null) {
				handler.accept(selected);
			}
		});
	}

	protected void replaceItems(Collection<T> items) {
		masterData.setAll(items);
	}

	protected void replaceItemsFrom(Supplier<? extends Collection<T>> itemSupplier) {
		replaceItems(itemSupplier.get());
	}

	public T getSelectedItem() {
		return tableView.getSelectionModel().getSelectedItem();
	}

	public Window getTableWindow() {
		return tableView.getScene() != null ? tableView.getScene().getWindow() : null;
	}

	protected void installRowContextMenu(ContextMenu contextMenu) {
		installRowContextMenu(contextMenu, null);
	}

	protected void installRowContextMenu(ContextMenu contextMenu, Consumer<T> secondaryClickHandler) {
		tableView.setRowFactory(tv -> {
			TableRow<T> row = tableView.createDefaultRow();
			row.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> updateRow(row, contextMenu));
			row.itemProperty().addListener((obs, oldItem, newItem) -> updateRow(row, contextMenu));
			row.setOnMousePressed(event -> {
				if (event.isSecondaryButtonDown() && !row.isEmpty()) {
					tableView.getSelectionModel().select(row.getIndex());
					if (secondaryClickHandler != null) {
						secondaryClickHandler.accept(row.getItem());
					}
				}
			});
			updateRow(row, contextMenu);
			return row;
		});
	}

	private void updateRow(TableRow<T> row, ContextMenu contextMenu) {
		row.setContextMenu(row.isEmpty() ? null : contextMenu);
		updateRowStyle(row, row.isEmpty() ? null : row.getItem());
	}

	protected void updateRowStyle(TableRow<T> row, T item) {
	}

	protected String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase();
	}

	protected boolean contains(String value, String filter) {
		return value != null && value.toLowerCase().contains(filter);
	}

	protected boolean matchesAny(String filter, String... values) {
		if (filter == null || filter.isBlank()) {
			return true;
		}

		for (String value : values) {
			if (contains(value, filter)) {
				return true;
			}
		}
		return false;
	}

	protected abstract boolean matchesFilter(T item, String filter);
}
