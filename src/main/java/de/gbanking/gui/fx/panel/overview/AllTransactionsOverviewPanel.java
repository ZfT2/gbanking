package de.gbanking.gui.fx.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.transaction.TransactionDetailPanel;
import de.gbanking.gui.fx.panel.transaction.TransactionListPanel;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AllTransactionsOverviewPanel extends TransactionsOverviewBasePanel {

	private static final Logger log = LogManager.getLogger(AllTransactionsOverviewPanel.class);

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.ALL_TRANSACTIONS);

		log.info("Initializing AllTransactionsOverviewPanel");

		transactionDetailPanel = new TransactionDetailPanel();
		transactionDetailPanel.setPrefHeight(420);
		transactionDetailPanel.setMinHeight(320);

		transactionListPanel = new TransactionListPanel(this);

		BorderPane content = new BorderPane();
		content.setTop(transactionDetailPanel);
		content.setCenter(transactionListPanel);

		Label title = new Label(getText("UI_PANEL_ALL_TRANSACTIONS"));
		title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

		VBox root = new VBox(8, title, content);
		root.setPadding(new Insets(5));
		VBox.setVgrow(content, Priority.ALWAYS);

		getChildren().clear();
		getChildren().add(root);

		setDisable(!show);
	}
}