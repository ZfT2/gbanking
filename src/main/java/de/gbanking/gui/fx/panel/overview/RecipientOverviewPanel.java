package de.gbanking.gui.fx.panel.overview;

import de.gbanking.db.dao.Recipient;
import de.gbanking.gui.fx.panel.recipient.RecipientDetailPanel;
import de.gbanking.gui.fx.panel.recipient.RecipientListPanel;
import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.messages.Messages;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RecipientOverviewPanel extends OverviewBasePanel {

	private static final Logger log = LogManager.getLogger(RecipientOverviewPanel.class);

	private RecipientDetailPanel recipientDetailPanel;
	private RecipientListPanel recipientListPanel;

	private Recipient selectedRecipient;

	public RecipientOverviewPanel() {
		recipientDetailPanel = new RecipientDetailPanel(this);
		recipientListPanel = new RecipientListPanel(this);
	}

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.RECIPIENTS);

		Label title = new Label(getText("UI_PANEL_RECIPIENTS"));
		title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

		recipientDetailPanel.setPrefHeight(260);
		recipientDetailPanel.setMinHeight(220);

		BorderPane content = new BorderPane();
		content.setTop(recipientDetailPanel);
		content.setCenter(recipientListPanel);

		VBox root = new VBox(8, title, content);
		root.setPadding(new Insets(5));
		VBox.setVgrow(content, Priority.ALWAYS);

		getChildren().clear();
		getChildren().add(root);

		setDisable(!show);
		log.info("RecipientOverviewPanel initialized");
	}

	public void setCurrentRecipient(Recipient selectedRecipient) {
		this.selectedRecipient = selectedRecipient;
	}

	public Recipient getCurrentRecipient() {
		return selectedRecipient;
	}

	public RecipientListPanel getRecipientListPanel() {
		return recipientListPanel;
	}

	public RecipientDetailPanel getRecipientDetailPanel() {
		return recipientDetailPanel;
	}
}