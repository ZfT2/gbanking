package de.gbanking.gui.fx.panel;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import de.gbanking.gui.fx.components.GBankingTableView;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public abstract class AbstractFilterableTablePanel<T> extends BaseBorderPanePanel {

	protected final GBankingTableView<T> tableView = new GBankingTableView<>();
	protected final TextField filterText = new TextField();
	protected final Label titleLabel = new Label();
	protected final ObservableList<T> masterData;
	protected final FilteredList<T> filteredData;

	protected AbstractFilterableTablePanel(ObservableList<T> masterData) {
		this.masterData = masterData;
		this.filteredData = new FilteredList<>(masterData, item -> true);

		SortedList<T> sorted = new SortedList<>(filteredData);
		sorted.comparatorProperty().bind(tableView.comparatorProperty());
		tableView.setItems(sorted);

		filterText.textProperty().addListener((obs, oldVal, newVal) ->
				filteredData.setPredicate(item -> matchesFilter(item, normalize(newVal))));

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

	protected String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase();
	}

	protected boolean contains(String value, String filter) {
		return value != null && value.toLowerCase().contains(filter);
	}

	protected abstract boolean matchesFilter(T item, String filter);
}