package de.gbanking.gui.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.gui.enu.PageContext;
import de.gbanking.gui.panel.account.AccountListPanel;
import de.gbanking.gui.panel.category.CategoryInputPanel;
import de.gbanking.gui.panel.category.CategoryListPanel;
import de.gbanking.gui.panel.category.CategoryRuleInputPanel;
import de.gbanking.gui.panel.category.CategoryRuleListPanel;
import de.gbanking.gui.panel.category.CategoryTabContentPanel;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class CategoryOverviewPanel extends OverviewBasePanel {

	private static final Logger log = LogManager.getLogger(CategoryOverviewPanel.class);

	private static final double ACCOUNT_DIVIDER = 0.20;

	private AccountListPanel accountListPanel;
	private CategoryRuleInputPanel categoryRuleInputPanel;
	private CategoryRuleListPanel categoryRuleListPanel;
	private CategoryInputPanel categoryInputPanel;
	private CategoryListPanel categoryListPanel;
	private BankAccount selectedAccount;
	private TabPane tabPane;

	public CategoryOverviewPanel() {
		categoryRuleInputPanel = new CategoryRuleInputPanel(this);
		categoryRuleListPanel = new CategoryRuleListPanel(this);
		categoryInputPanel = new CategoryInputPanel(this);
		categoryListPanel = new CategoryListPanel(this);
	}

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.CATEGORIES);

		accountListPanel = new AccountListPanel(this);
		tabPane = new TabPane();
		tabPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		tabPane.getTabs().setAll(createTab("UI_TAB_CATEGORY_RULES", new CategoryTabContentPanel(categoryRuleInputPanel, categoryRuleListPanel)),
				createTab("UI_TAB_CATEGORIES", new CategoryTabContentPanel(categoryInputPanel, categoryListPanel)));

		SplitPane mainSplit = createSplitPane(ACCOUNT_DIVIDER, accountListPanel, tabPane);
		setOverviewContent("UI_PANEL_CATEGORIES", mainSplit, show);

		log.info("CategoryOverviewPanel initialized");
	}

	private Tab createTab(String titleKey, CategoryTabContentPanel contentPanel) {
		Tab tab = new Tab(getText(titleKey), contentPanel);
		tab.setClosable(false);
		return tab;
	}

	public void handleAccountSelection(BankAccount bankAccount) {
		selectedAccount = bankAccount;
		categoryRuleInputPanel.updatePanelFieldValues(bankAccount);
		categoryRuleListPanel.reload();
		categoryInputPanel.updatePanelFieldValues(bankAccount);
		categoryListPanel.reload();
	}

	public BankAccount getSelectedAccount() {
		return selectedAccount;
	}

	public void setSelectedAccount(BankAccount selectedAccount) {
		this.selectedAccount = selectedAccount;
	}

	public CategoryRuleInputPanel getCategoryRuleInputPanel() {
		return categoryRuleInputPanel;
	}

	public CategoryRuleListPanel getCategoryRuleListPanel() {
		return categoryRuleListPanel;
	}

	public CategoryInputPanel getCategoryInputPanel() {
		return categoryInputPanel;
	}

	public CategoryListPanel getCategoryListPanel() {
		return categoryListPanel;
	}

	public AccountListPanel getAccountListPanel() {
		return accountListPanel;
	}
}
