package de.zft2.gbanking.messages;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Messages {

	private static final Logger log = LogManager.getLogger(Messages.class);
	private static final Locale GERMAN_LOCALE = Locale.GERMAN;
	private static final Locale ENGLISH_LOCALE = Locale.ENGLISH;
	private static final String BUNDLE_NAME = "messages";

	static Messages messages;

	private static ResourceBundle messageBundle;
	private static Locale currentLocale;

	public static Messages getInstance() {
		if (messages == null) {
			setLocale(resolveSupportedLocale(Locale.getDefault()));
			messages = new Messages();
		}
		return messages;
	}

	public static synchronized void setLocale(Locale locale) {
		currentLocale = resolveSupportedLocale(locale);
		messageBundle = ResourceBundle.getBundle(BUNDLE_NAME, currentLocale);
	}

	public static synchronized Locale getLocale() {
		if (currentLocale == null) {
			setLocale(Locale.getDefault());
		}
		return currentLocale;
	}

	public static Locale localeFromCode(String localeCode) {
		if (localeCode == null || localeCode.isBlank()) {
			return resolveSupportedLocale(Locale.getDefault());
		}
		return resolveSupportedLocale(Locale.forLanguageTag(localeCode));
	}

	public static String toLanguageCode(Locale locale) {
		return resolveSupportedLocale(locale).getLanguage();
	}

	private static Locale resolveSupportedLocale(Locale locale) {
		if (locale != null && GERMAN_LOCALE.getLanguage().equalsIgnoreCase(locale.getLanguage())) {
			return GERMAN_LOCALE;
		}
		return ENGLISH_LOCALE;
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
