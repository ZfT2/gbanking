package de.zft2.gbanking.logging;

import java.util.Locale;

import org.apache.logging.log4j.Level;
import org.kapott.hbci.manager.HBCIUtils;

public enum LogLevelSetting {

	NONE(Level.OFF, HBCIUtils.LOG_NONE),
	ERROR(Level.ERROR, HBCIUtils.LOG_ERR),
	WARN(Level.WARN, HBCIUtils.LOG_WARN),
	INFO(Level.INFO, HBCIUtils.LOG_INFO),
	DEBUG(Level.DEBUG, HBCIUtils.LOG_DEBUG),
	TRACE(Level.TRACE, HBCIUtils.LOG_DEBUG2);

	private final Level log4jLevel;
	private final int hbciLogLevel;

	LogLevelSetting(Level log4jLevel, int hbciLogLevel) {
		this.log4jLevel = log4jLevel;
		this.hbciLogLevel = hbciLogLevel;
	}

	public Level toLog4jLevel() {
		return log4jLevel;
	}

	public int toHbciLogLevel() {
		return hbciLogLevel;
	}

	public static LogLevelSetting fromValue(String value, LogLevelSetting defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return LogLevelSetting.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return defaultValue;
		}
	}

	public static boolean isValid(String value) {
		return value != null && fromValue(value, null) != null;
	}
}
