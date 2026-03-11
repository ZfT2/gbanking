package de.gbanking.gui.fx.panel;

import de.gbanking.GBankingBean;
import de.gbanking.db.DBController;
import de.gbanking.messages.Messages;
import javafx.scene.layout.BorderPane;

public abstract class BaseBorderPanePanel extends BorderPane {

	protected static final DBController dbController = DBController.getInstance(".");
	protected static final Messages messages = Messages.getInstance();

	protected final GBankingBean bean;

	protected BaseBorderPanePanel() {
		this.bean = new GBankingBean();
	}

	protected String getText(String key) {
		return messages.getMessage(key);
	}

	protected String getText(String key, int value) {
		return messages.getFormattedMessage(key, value);
	}

	protected String getText(String key, Object... values) {
		return messages.getFormattedMessage(key, values);
	}
}