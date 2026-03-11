package de.gbanking.gui.fx.panel.overview;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.account.AccountListPanel;
import de.gbanking.gui.fx.panel.category.CategoryInputPanel;
import de.gbanking.gui.fx.panel.category.CategoryListPanel;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

		Label title = new Label(getText("UI_PANEL_CATEGORIES"));
		title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

		accountListPanel = new AccountListPanel(this);
		accountListPanel.setMinWidth(280);
		accountListPanel.setPrefWidth(335);

		SplitPane rightSplit = new SplitPane();
		categoryInputPanel.setMinWidth(360);
		categoryListPanel.setMinWidth(420);

		rightSplit.getItems().addAll(categoryInputPanel, categoryListPanel);
		rightSplit.setDividerPositions(0.40);

		SplitPane mainSplit = new SplitPane();
		mainSplit.getItems().addAll(accountListPanel, rightSplit);
		mainSplit.setDividerPositions(0.20);

		VBox root = new VBox(8, title, mainSplit);
		root.setPadding(new Insets(5));
		VBox.setVgrow(mainSplit, Priority.ALWAYS);

		getChildren().clear();
		getChildren().add(root);

		setDisable(!show);
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