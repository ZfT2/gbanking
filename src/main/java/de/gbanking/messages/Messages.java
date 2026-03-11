package de.gbanking.messages;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public class Messages {
	
	static Messages messages;

	private static ResourceBundle messageBundle;
	
	public static Messages getInstance() {
		if (messages == null) {
			messageBundle =  ResourceBundle.getBundle("messages", Locale.GERMAN);
			messages = new Messages();
		} 
		return messages;
	}

	public String getMessage(String key) {
		try {
			return messageBundle.getString(key);
		} catch (Exception e) {
			return key;
		}
	}
	
	public String getFormattedMessage(String key, String param) {
		return MessageFormat.format(messageBundle.getString(key), param);
	}
	
	public String getFormattedMessage(String key, int param) {
		return MessageFormat.format(messageBundle.getString(key), param);
	}
	
	public String getFormattedMessage(String key, Object[] params) {
		return MessageFormat.format(messageBundle.getString(key), params);
	}
	
}
