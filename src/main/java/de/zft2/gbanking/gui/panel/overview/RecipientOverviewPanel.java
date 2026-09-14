package de.zft2.gbanking.gui.panel.overview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.layout.DetailListPane;
import de.zft2.gbanking.gui.panel.recipient.RecipientDetailPanel;
import de.zft2.gbanking.gui.panel.recipient.RecipientListPanel;
import de.zft2.gbanking.gui.panel.recipient.RecipientListPanel.ViewMode;

public class RecipientOverviewPanel extends OverviewBasePanel {

	private static final Logger log = LogManager.getLogger(RecipientOverviewPanel.class);

	private RecipientDetailPanel recipientDetailPanel;
	private RecipientListPanel recipientListPanel;
	private Recipient selectedRecipient;

	public RecipientOverviewPanel() {
		recipientDetailPanel = new RecipientDetailPanel(this);
		recipientListPanel = new RecipientListPanel(ViewMode.RECIPIENTS,
				recipient -> recipientDetailPanel.updatePanelFieldValues(recipient));
		initializePanel();
	}

	private void initializePanel() {
		setPageContext(PageContext.RECIPIENTS);
		setOverviewContent("UI_PANEL_RECIPIENTS", new DetailListPane(recipientDetailPanel, recipientListPanel));

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

	@Override
	public void refreshOnShow() {
		recipientListPanel.reload();
	}
}
