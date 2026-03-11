package de.gbanking.gui.fx.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.Recipient;
import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.recipient.RecipientDetailPanel;
import de.gbanking.gui.fx.panel.recipient.RecipientListPanel;
import javafx.scene.layout.BorderPane;

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

		recipientDetailPanel.setPrefHeight(300);
		recipientDetailPanel.setMinHeight(250);
		recipientDetailPanel.setMaxWidth(Double.MAX_VALUE);

		recipientListPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		BorderPane content = new BorderPane();
		content.setTop(recipientDetailPanel);
		content.setCenter(recipientListPanel);
		content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		BorderPane.setMargin(recipientListPanel, new javafx.geometry.Insets(8, 0, 0, 0));

		setOverviewContent("UI_PANEL_RECIPIENTS", content, show);
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