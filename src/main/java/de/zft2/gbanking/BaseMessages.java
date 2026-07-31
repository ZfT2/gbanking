package de.zft2.gbanking;

import de.zft2.gbanking.messages.Messages;

public interface BaseMessages {

	static Messages messages = Messages.getInstance();

	public static String getTextStatic(String key) {
		return messages.getMessage(key);
	}

	default String getText(String key) {
		return messages.getMessage(key);
	}

	default String getText(String key, int value) {
		return messages.getFormattedMessage(key, value);
	}

	default String getText(String key, String value1) {
		return messages.getFormattedMessage(key, value1);
	}

	default String getText(String key, String... values) {
		return messages.getFormattedMessage(key, values);
	}

	default String getText(String key, Object... values) {
		return messages.getFormattedMessage(key, values);
	}

}
