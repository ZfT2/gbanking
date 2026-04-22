package de.zft2.gbanking.gui.panel;

import de.zft2.gbanking.GBankingBean;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.messages.Messages;
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

	protected String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	protected boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	protected Integer parseAndValidatePostiveInt(String value) {
		if (isBlank(value)) {
			return null;
		}

		try {
			int port = Integer.parseInt(value.trim());
			return port > 0 ? port : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}