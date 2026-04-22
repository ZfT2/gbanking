package de.zft2.gbanking.gui.panel;

import de.zft2.gbanking.GBankingBean;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.messages.Messages;
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