package de.gbanking.gui.fx.panel;

import de.gbanking.GBankingBean;
import de.gbanking.db.DBController;
import de.gbanking.messages.Messages;
import javafx.scene.layout.VBox;

public abstract class BasePanelHolder extends VBox {

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

	protected String getText(String key, Object... values) {
		return messages.getFormattedMessage(key, values);
	}
}