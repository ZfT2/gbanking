package de.gbanking.gui.panel.category;

import java.util.List;

import de.gbanking.db.dao.Category;
import de.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.gbanking.gui.panel.overview.CategoryOverviewPanel;
import de.gbanking.gui.util.FxTableUtils;
import de.gbanking.gui.util.TableColumnFactory;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;

public class CategoryListPanel extends AbstractFilterableTablePanel<Category> {

	private final CategoryOverviewPanel parentPanel;

	public CategoryListPanel(CategoryOverviewPanel parentPanel) {
		super(FXCollections.observableArrayList());
		this.parentPanel = parentPanel;
		createInnerCategoryListPanel();
	}

	private void createInnerCategoryListPanel() {
		setPanelTitleByKey("UI_PANEL_CATEGORIES_LIST");
		setColumns(createColumns());
		onSelection(parentPanel.getCategoryInputPanel()::updatePanelFieldValues);
		reload();
	}

	private List<TableColumn<Category, ?>> createColumns() {
		TableColumn<Category, Boolean> selectedCol = FxTableUtils.createSelectionColumn(getText("UI_TABLE_SELECTED"), Category::isSelected,
				Category::setSelected);
		TableColumn<Category, String> nameCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_CATEGORY_NAME"), Category::getName, 180, 220);
		TableColumn<Category, String> fullNameCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_PARENT_CATEGORY"), Category::getFullName, 220, 280);
		TableColumn<Category, String> sourceCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_SOURCE"),
				category -> getText("UI_SOURCE_MANUAL"), 120);

		return List.of(selectedCol, nameCol, fullNameCol, sourceCol);
	}

	@Override
	protected boolean matchesFilter(Category category, String filter) {
		return filter.isBlank() || contains(category.getName(), filter) || contains(category.getFullName(), filter);
	}

	public void reload() {
		replaceItems(dbController.getAll(Category.class));
	}

	public void refresh() {
		reload();
	}
	public void selectById(int categoryId) {
		for (int i = 0; i < tableView.getItems().size(); i++) {
			Category category = tableView.getItems().get(i);
			if (category.getId() == categoryId) {
				tableView.getSelectionModel().select(i);
				tableView.scrollTo(i);
				return;
			}
		}
	}

}
