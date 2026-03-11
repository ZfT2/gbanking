package de.gbanking.gui.swing.panel.overview;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.border.Border;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.Recipient;
import de.gbanking.gui.swing.panel.recipient.RecipientDetailPanel;
import de.gbanking.gui.swing.panel.recipient.RecipientListPanel;

public class RecipientOverviewPanel extends OverviewBasePanel {


	/**
	 * 
	 */
	private static final long serialVersionUID = 4974596516784267072L;

	private static Logger log = LogManager.getLogger(RecipientOverviewPanel.class);

	private RecipientDetailPanel recipientDetailPanel;
	private RecipientListPanel recipientListPanel;
	
	private Recipient selectedRecipient;

	public RecipientOverviewPanel() {
		recipientDetailPanel = new RecipientDetailPanel(this);
		recipientListPanel = new RecipientListPanel(this);
	}

	@Override
	public void createOverallPanel(boolean show) {

		Border recipientPanelBorder = BorderFactory.createTitledBorder("Adressbuch");
		setBorder(recipientPanelBorder);

		GridBagLayout recipientLayout = new GridBagLayout();
		setLayout(recipientLayout);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 2, 2, 2);

		gbc.anchor = GridBagConstraints.NORTHEAST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weighty = 0.25;
		add(recipientDetailPanel, gbc);

		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 0;
		gbc.gridy = 1;

		gbc.weightx = 1;
		gbc.weighty = 0.75;

		add(recipientListPanel, gbc);

		setEnabled(show);

	}


	public void setCurrentRecipient(Recipient selectedRecipient) {
		this.selectedRecipient = selectedRecipient;
		
	}
	
	public RecipientListPanel getRecipientListPanel() {
		return recipientListPanel;
	}
	
	public RecipientDetailPanel getRecipientDetailPanel() {
		return recipientDetailPanel;
	}

}
