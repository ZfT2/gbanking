package de.gbanking.gui.fx.panel.category;

import java.util.List;

import de.gbanking.db.dao.Category;
import de.gbanking.gui.fx.panel.AbstractFilterableTablePanel;
import de.gbanking.gui.fx.panel.overview.CategoryOverviewPanel;
import de.gbanking.gui.fx.util.DateFormatUtils;
import de.gbanking.gui.fx.util.FxTableUtils;
import de.gbanking.gui.fx.util.TableColumnFactory;
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
		TableColumn<Category, String> updatedCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_UPDATED_AT"),
				category -> DateFormatUtils.formatShort(category.getUpdatedAt()), 90);

		return List.of(selectedCol, nameCol, fullNameCol, updatedCol);
	}

	@Override
	protected boolean matchesFilter(Category category, String filter) {
		return filter.isBlank() || contains(category.getName(), filter) || contains(category.getFullName(), filter);
	}

	public void reload() {
		replaceItems(dbController.getAllFull(Category.class));
	}

	public void refresh() {
		reload();
	}
}