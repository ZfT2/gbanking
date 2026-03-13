package de.gbanking;

import de.gbanking.db.DBController;
import de.gbanking.messages.Messages;

public class BaseBean {

	protected Messages messages;
	protected DBController dbController;
	
	protected BaseBean() {
		messages = Messages.getInstance();
		dbController = DBController.getInstance(".");
	}

	protected String getText(String key) {
		return messages.getMessage(key);
	}
	
	protected String getText(String key, int value) {
		return messages.getFormattedMessage(key, value);
	}
	
	protected String getText(String key, String value1) {
		return messages.getFormattedMessage(key, value1);
	}
	
	protected String getText(String key, String... values) {
		return messages.getFormattedMessage(key, values);
	}

}
