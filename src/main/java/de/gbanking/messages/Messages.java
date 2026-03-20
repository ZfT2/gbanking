package de.gbanking.messages;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Messages {
	
	private static Logger log = LogManager.getLogger(Messages.class);

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
		return getFormatted(key, param);
	}
	
	public String getFormattedMessage(String key, int param) {
		return getFormatted(key, param);
	}
	
	public String getFormattedMessage(String key, Object[] params) {
		return getFormatted(key, params);
	}
	
	private String getFormatted(String key, Object... params) {
		try {
			return MessageFormat.format(messageBundle.getString(key), params);
		} catch (Exception e) {
			log.error("could not create message for key: {} : {}", key, params);
		}
		return "<unknown: " + key + ">";
	}

}
