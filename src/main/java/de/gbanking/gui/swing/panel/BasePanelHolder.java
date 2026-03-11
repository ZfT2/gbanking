package de.gbanking.gui.swing.panel;

import java.awt.GridBagConstraints;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import de.gbanking.GBankingBean;
import de.gbanking.db.DBController;
import de.gbanking.messages.Messages;

public abstract class BasePanelHolder extends JPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8334641605278505901L;

	protected static DBController dbController;
	
	protected GBankingBean bean;

	protected static Messages messages;
	
	static {
		dbController = DBController.getInstance(".");
		messages = Messages.getInstance();
	}

	protected BasePanelHolder() {
		bean = new GBankingBean();
	}
	
	protected String getText(String key) {
		return messages.getMessage(key);
	}
	
	protected String getText(String key, int value) {
		return messages.getFormattedMessage(key, value);
	}
	
	protected void addLabelAndFieldAbove(String label, JComponent field, GridBagConstraints gbc, int row, int col, int colspan, int rowspan) {
		addLabel(label, gbc, row, col, colspan);
		addField(field, gbc, rowspan);
	}
	
	protected void addLabelAndFieldAboveWithWeight(String label, JComponent field, GridBagConstraints gbc, int row, int col, int colspan, int rowspan, double weightx) {
		gbc.weightx = weightx;
		addLabel(label, gbc, row, col, colspan);
		addField(field, gbc, rowspan);
	}
	
	protected void addLabelAndFieldSide(String label, JComponent field, GridBagConstraints gbc, int row, int col, int colspan, int rowspan) {
		addLabel(label, gbc, row, col, colspan);
		addField(field, gbc, rowspan);
	}
	
	protected void addLabelAndFieldSideWithWeight(String label, JComponent field, GridBagConstraints gbc, int row, int col, int colspan, int rowspan, double weightxLabel, double weightxField) {
		gbc.weightx = weightxLabel;
		addLabel(label, gbc, row, col, colspan);
		gbc.weightx = weightxField;
		addField(field, gbc, rowspan);
	}
	
	protected void addLabel(String label, GridBagConstraints gbc, int row, int col, int colspan) {
		gbc.gridx = col;
		gbc.gridy = row;
		gbc.gridwidth = colspan;
		gbc.gridheight = 1;
		gbc.weighty = 0;
		if (label != null) {
			add(new JLabel(label), gbc);
			gbc.gridy = row + 1;
		}
	}
	
	protected void addField(JComponent field, GridBagConstraints gbc, int rowspan) {
		gbc.gridheight = rowspan;
		if (rowspan > 1) {
			gbc.weighty = 1.0;
			gbc.fill = GridBagConstraints.BOTH;
		}
		add(field, gbc);
		gbc.fill = GridBagConstraints.HORIZONTAL;
	}
}
