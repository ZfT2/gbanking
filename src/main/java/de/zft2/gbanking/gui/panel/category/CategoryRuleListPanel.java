package de.zft2.gbanking.gui.panel.category;

import java.util.List;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.zft2.gbanking.gui.panel.overview.CategoryOverviewPanel;
import de.zft2.gbanking.gui.util.TableColumnFactory;
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
		configureTableLayout("categoryRules");
		onSelection(parentPanel.getCategoryRuleInputPanel()::updatePanelFieldValues);
		reload();
	}

	private List<TableColumn<CategoryRule, ?>> createColumns() {
		TableColumn<CategoryRule, String> nameCol = TableColumnFactory.createTextColumn(getText("UI_LABEL_RULE_NAME"), CategoryRule::getName, 140, 180);
		TableColumn<CategoryRule, String> accountCol = TableColumnFactory.createTextColumn(getText("UI_LABEL_ACCOUNT"), this::getAccountScopeText, 160,
				220);
		TableColumn<CategoryRule, String> categoryCol = TableColumnFactory.createTextColumn(getText("UI_LABEL_CATEGORY"),
				rule -> rule.getCategory() != null ? rule.getCategory().getFullName() : "", 180, 240);
		TableColumn<CategoryRule, String> joinTypeCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_JOIN_TYPE"),
				rule -> rule.getJoinType() != null ? rule.getJoinType().getDescription() : "", 160, 220);
		TableColumn<CategoryRule, String> recipientCol = TableColumnFactory.createTextColumn(getText("UI_LABEL_RECIPIENT"),
				CategoryRule::getFilterRecipientName, 160, 220);
		TableColumn<CategoryRule, String> purposeCol = TableColumnFactory.createTextColumn(getText("UI_LABEL_PURPOSE"), CategoryRule::getFilterPurpose, 220,
				300);
		TableColumn<CategoryRule, java.time.LocalDate> updatedCol = TableColumnFactory.createUpdatedAtColumn(getText("UI_LABEL_UPDATED_AT"),
				CategoryRule::getUpdatedAt, 90);
		return List.of(nameCol, accountCol, categoryCol, joinTypeCol, recipientCol, purposeCol, updatedCol);
	}

	@Override
	protected boolean matchesFilter(CategoryRule categoryRule, String filter) {
		return matchesAny(filter, categoryRule.getName(), getAccountScopeText(categoryRule),
				categoryRule.getCategory() != null ? categoryRule.getCategory().getFullName() : null, categoryRule.getFilterRecipientName(),
				categoryRule.getFilterRecipientIban(), categoryRule.getFilterRecipientAccountNumber(), categoryRule.getFilterPurpose(),
				categoryRule.getJoinType() != null ? categoryRule.getJoinType().getDescription() : null);
	}

	private String getAccountScopeText(CategoryRule categoryRule) {
		List<BankAccount> accounts = categoryRule.getBankAccountList();
		if (accounts == null || accounts.isEmpty()) {
			return getText("UI_CATEGORY_RULE_SCOPE_ALL_ACCOUNTS");
		}
		if (accounts.size() == 1) {
			String accountName = accounts.get(0).getAccountName();
			return accountName != null && !accountName.isBlank() ? accountName : getText("UI_CATEGORY_RULE_SCOPE_CURRENT_ACCOUNT");
		}
		return getText("UI_CATEGORY_RULE_SCOPE_SELECTED_ACCOUNTS");
	}

	public void reload() {
		replaceItemsFrom(() -> dbController.getAllFull(CategoryRule.class));
	}
}
