package de.gbanking.gui.fx.panel.category;

import de.gbanking.db.dao.Category;
import de.gbanking.gui.fx.panel.AbstractFilterableTablePanel;
import de.gbanking.gui.fx.panel.overview.CategoryOverviewPanel;
import de.gbanking.gui.fx.util.DateFormatUtils;
import de.gbanking.gui.fx.util.FxTableUtils;
import de.gbanking.gui.fx.util.TableColumnFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;

import java.util.List;

public class CategoryListPanel extends AbstractFilterableTablePanel<Category> {

	private final CategoryOverviewPanel parentPanel;
	private final ObservableList<Category> masterData;

	public CategoryListPanel(CategoryOverviewPanel parentPanel) {
		this(FXCollections.observableArrayList(), parentPanel);
	}

	private CategoryListPanel(ObservableList<Category> data, CategoryOverviewPanel parentPanel) {
		super(data);
		this.masterData = data;
		this.parentPanel = parentPanel;
		createInnerCategoryListPanel();
	}

	private void createInnerCategoryListPanel() {
		setPanelTitle(getText("UI_PANEL_CATEGORIES_LIST"));

		TableColumn<Category, Boolean> selectedCol = FxTableUtils.createSelectionColumn(getText("UI_TABLE_SELECTED"), Category::isSelected,
				Category::setSelected);

		TableColumn<Category, String> nameCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_CATEGORY_NAME"), Category::getName, 180, 220);

		TableColumn<Category, String> fullNameCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_PARENT_CATEGORY"), Category::getFullName, 220, 280);

		TableColumn<Category, String> updatedCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_UPDATED_AT"),
				category -> DateFormatUtils.formatShort(category.getUpdatedAt()), 90);

		tableView.getColumns().setAll(List.of(selectedCol, nameCol, fullNameCol, updatedCol));

		tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selectedCategory) -> {
			if (selectedCategory != null) {
				parentPanel.getCategoryInputPanel().updatePanelFieldValues(selectedCategory);
			}
		});

		reload();
	}

	@Override
	protected boolean matchesFilter(Category category, String filter) {
		return filter.isBlank() || contains(category.getName(), filter) || contains(category.getFullName(), filter);
	}

	public void reload() {
		masterData.setAll(dbController.getAllFull(Category.class));
	}

	public void refresh() {
		reload();
	}
}