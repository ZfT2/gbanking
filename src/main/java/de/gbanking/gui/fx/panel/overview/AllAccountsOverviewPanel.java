package de.gbanking.gui.fx.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.account.AccountDetailPanel;
import de.gbanking.gui.fx.panel.account.AccountListPanel;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AllAccountsOverviewPanel extends OverviewBasePanel {

	private static final Logger log = LogManager.getLogger(AllAccountsOverviewPanel.class);

	private AccountListPanel accountListPanel;
	private AccountDetailPanel accountDetailPanel;

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.ALL_ACCOUNTS);

		log.info("Initializing AllAccountsOverviewPanel");

		accountDetailPanel = new AccountDetailPanel(true);
		accountDetailPanel.setPrefHeight(320);
		accountDetailPanel.setMinHeight(280);

		accountListPanel = new AccountListPanel(this);

		BorderPane content = new BorderPane();
		content.setTop(accountDetailPanel);
		content.setCenter(accountListPanel);

		Label title = new Label(getText("UI_PANEL_ALL_ACCOUNTS"));
		title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

		VBox root = new VBox(8, title, content);
		root.setPadding(new Insets(5));
		VBox.setVgrow(content, Priority.ALWAYS);

		getChildren().clear();
		getChildren().add(root);

		setDisable(!show);
	}

	public AccountDetailPanel getAccountDetailPanel() {
		return accountDetailPanel;
	}

	public AccountListPanel getAccountListPanel() {
		return accountListPanel;
	}
}