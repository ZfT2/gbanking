package de.gbanking.gui.fx.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.account.AccountListPanel;
import de.gbanking.gui.fx.panel.category.CategoryInputPanel;
import de.gbanking.gui.fx.panel.category.CategoryListPanel;
import javafx.scene.control.SplitPane;

public class CategoryOverviewPanel extends OverviewBasePanel {

	private static final Logger log = LogManager.getLogger(CategoryOverviewPanel.class);

	private AccountListPanel accountListPanel;
	private CategoryInputPanel categoryInputPanel;
	private CategoryListPanel categoryListPanel;
	private BankAccount selectedAccount;

	public CategoryOverviewPanel() {
		categoryInputPanel = new CategoryInputPanel(this);
		categoryListPanel = new CategoryListPanel(this);
	}

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.CATEGORIES);

		accountListPanel = new AccountListPanel(this);
		accountListPanel.setMinWidth(280);
		accountListPanel.setPrefWidth(335);

		categoryInputPanel.setMinWidth(360);
		categoryInputPanel.setMaxWidth(Double.MAX_VALUE);

		categoryListPanel.setMinWidth(420);
		categoryListPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		SplitPane rightSplit = new SplitPane(categoryInputPanel, categoryListPanel);
		rightSplit.setDividerPositions(0.40);
		rightSplit.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		SplitPane mainSplit = new SplitPane(accountListPanel, rightSplit);
		mainSplit.setDividerPositions(0.20);
		mainSplit.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		setOverviewContent("UI_PANEL_CATEGORIES", mainSplit, show);
		log.info("CategoryOverviewPanel initialized");
	}

	public BankAccount getSelectedAccount() {
		return selectedAccount;
	}

	public void setSelectedAccount(BankAccount selectedAccount) {
		this.selectedAccount = selectedAccount;
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