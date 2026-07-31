package de.zft2.gbanking.gui.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import de.zft2.gbanking.gui.panel.category.CategoryInputPanel;
import de.zft2.gbanking.gui.panel.category.CategoryListPanel;
import de.zft2.gbanking.gui.panel.category.CategoryRuleInputPanel;
import de.zft2.gbanking.gui.panel.category.CategoryRuleListPanel;
import de.zft2.gbanking.gui.panel.layout.DetailListPane;
import de.zft2.gbanking.gui.panel.layout.MasterContentPane;
import javafx.geometry.Insets;
import javafx.scene.Node;
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
		TabPane tabPane = new TabPane();
		tabPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		tabPane.getTabs().setAll(createTab("UI_TAB_CATEGORY_RULES", createTabContent(categoryRuleInputPanel, categoryRuleListPanel)),
				createTab("UI_TAB_CATEGORIES", createTabContent(categoryInputPanel, categoryListPanel)));
		GuiLayoutState.configureTabPane(tabPane, "categories.main");

		MasterContentPane mainPane = new MasterContentPane(accountListPanel, tabPane, "categories.main", ACCOUNT_DIVIDER);
		setOverviewContent("UI_PANEL_CATEGORIES", mainPane, show);

		log.info("CategoryOverviewPanel initialized");
	}

	private DetailListPane createTabContent(Node detailNode, Node listNode) {
		DetailListPane contentPane = new DetailListPane(detailNode, listNode);
		contentPane.setPadding(new Insets(5));
		return contentPane;
	}

	private Tab createTab(String titleKey, Node contentPanel) {
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

	@Override
	public void refreshOnShow() {
		if (accountListPanel != null) {
			accountListPanel.refreshModelAccount();
			if (accountListPanel.getSelectedAccount() == null && selectedAccount != null) {
				handleAccountSelection(null);
			}
		}
		categoryRuleListPanel.reload();
		categoryListPanel.reload();
	}
}
