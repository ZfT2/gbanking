package de.gbanking.gui.fx.panel;

import de.gbanking.gui.fx.components.GBankingTableView;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public abstract class AbstractFilterableTablePanel<T> extends BaseBorderPanePanel {

	protected final GBankingTableView<T> tableView = new GBankingTableView<>();
	protected final TextField filterText = new TextField();
	protected final Label titleLabel = new Label();

	protected final FilteredList<T> filteredData;

	protected AbstractFilterableTablePanel(ObservableList<T> masterData) {
		this.filteredData = new FilteredList<>(masterData, item -> true);

		SortedList<T> sorted = new SortedList<>(filteredData);
		sorted.comparatorProperty().bind(tableView.comparatorProperty());
		tableView.setItems(sorted);

		filterText.textProperty().addListener((obs, oldVal, newVal) -> filteredData.setPredicate(item -> matchesFilter(item, normalize(newVal))));

		HBox filterBox = new HBox(10, new Label(getText("UI_LABEL_SEARCH")), filterText);
		filterBox.setAlignment(Pos.CENTER_LEFT);

		setTop(titleLabel);
		setCenter(tableView);
		setBottom(filterBox);
	}

	protected void setPanelTitle(String title) {
		titleLabel.setText(title);
	}

	protected String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase();
	}

	protected boolean contains(String value, String filter) {
		return value != null && value.toLowerCase().contains(filter);
	}

	protected abstract boolean matchesFilter(T item, String filter);
}