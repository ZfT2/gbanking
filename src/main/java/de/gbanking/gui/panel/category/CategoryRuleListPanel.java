package de.gbanking.gui.panel.category;

import java.util.List;

import de.gbanking.db.dao.CategoryRule;
import de.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.gbanking.gui.panel.overview.CategoryOverviewPanel;
import de.gbanking.gui.util.DateFormatUtils;
import de.gbanking.gui.util.TableColumnFactory;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;

public class CategoryRuleListPanel extends AbstractFilterableTablePanel<CategoryRule> {

	private final CategoryOverviewPanel parentPanel;

	public CategoryRuleListPanel(CategoryOverviewPanel parentPanel) {
		super(FXCollections.observableArrayList());
		this.parentPanel = parentPanel;
		createInnerCategoryRuleListPanel();
	}

	private void createInnerCategoryRuleListPanel() {
		setPanelTitleByKey("UI_PANEL_CATEGORY_RULES_LIST");
		setColumns(createColumns());
		onSelection(parentPanel.getCategoryRuleInputPanel()::updatePanelFieldValues);
		reload();
	}

	private List<TableColumn<CategoryRule, ?>> createColumns() {
		TableColumn<CategoryRule, String> categoryCol = TableColumnFactory.createTextColumn(getText("UI_LABEL_CATEGORY"),
				rule -> rule.getCategory() != null ? rule.getCategory().getFullName() : "", 180, 240);
		TableColumn<CategoryRule, String> joinTypeCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_JOIN_TYPE"),
				rule -> rule.getJoinType() != null ? rule.getJoinType().name() : "", 90, 110);
		TableColumn<CategoryRule, String> recipientCol = TableColumnFactory.createTextColumn(getText("UI_LABEL_RECIPIENT"), CategoryRule::getFilterRecipient,
				160, 220);
		TableColumn<CategoryRule, String> purposeCol = TableColumnFactory.createTextColumn(getText("UI_LABEL_PURPOSE"), CategoryRule::getFilterPurpose, 220,
				300);
		TableColumn<CategoryRule, String> updatedCol = TableColumnFactory.createFixedTextColumn(getText("UI_LABEL_UPDATED_AT"),
				rule -> DateFormatUtils.formatShort(rule.getUpdatedAt()), 90);
		return List.of(categoryCol, joinTypeCol, recipientCol, purposeCol, updatedCol);
	}

	@Override
	protected boolean matchesFilter(CategoryRule categoryRule, String filter) {
		return filter.isBlank() || contains(categoryRule.getCategory() != null ? categoryRule.getCategory().getFullName() : null, filter)
				|| contains(categoryRule.getFilterRecipient(), filter) || contains(categoryRule.getFilterPurpose(), filter)
				|| contains(categoryRule.getJoinType() != null ? categoryRule.getJoinType().name() : null, filter);
	}

	public void reload() {
		replaceItems(dbController.getAll(CategoryRule.class));
	}
}
