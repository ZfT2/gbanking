package de.zft2.gbanking.logging;

import java.lang.reflect.Method;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.LogFilter;

import de.zft2.gbanking.db.dao.enu.DataType;
import de.zft2.gbanking.hbci.HbciProperties;
import de.zft2.gbanking.service.settings.SettingDefinition;
import de.zft2.gbanking.service.settings.SettingsStore;

public final class LoggingSettings {

	public static final String SETTING_HBCI_LOG_LEVEL = "hbci.log.level";
	public static final String SETTING_GBANKING_LOG_LEVEL = "gbanking.log.level";
	public static final String SETTING_MASK_SENSITIVE_DATA = "log.mask.sensitiveData";
	public static final String HBCI4JAVA_LOGGER_NAME = "de.zft2.gbanking.hbci4java";

	private static final Logger log = LogManager.getLogger(LoggingSettings.class);
	private static final String GBANKING_LOGGER_NAME = "de.zft2.gbanking";
	private static final LogLevelSetting DEFAULT_HBCI_LOG_LEVEL = LogLevelSetting.WARN;
	private static final LogLevelSetting DEFAULT_GBANKING_LOG_LEVEL = LogLevelSetting.INFO;
	private static final boolean DEFAULT_MASK_SENSITIVE_DATA = true;
	private static final SettingDefinition HBCI_LOG_LEVEL = new SettingDefinition(SETTING_HBCI_LOG_LEVEL,
			DEFAULT_HBCI_LOG_LEVEL.name(), DataType.ENUM, true, true, "HBCI4Java-Loglevel");
	private static final SettingDefinition GBANKING_LOG_LEVEL = new SettingDefinition(SETTING_GBANKING_LOG_LEVEL,
			DEFAULT_GBANKING_LOG_LEVEL.name(), DataType.ENUM, true, true, "GBanking-Loglevel");
	private static final SettingDefinition MASK_SENSITIVE_DATA = new SettingDefinition(SETTING_MASK_SENSITIVE_DATA,
			Boolean.toString(DEFAULT_MASK_SENSITIVE_DATA), DataType.BOOLEAN, true, true, "Vertrauliche Daten in Log-Ausgaben maskieren");
	private static volatile boolean sensitiveDataMaskingEnabled = DEFAULT_MASK_SENSITIVE_DATA;

	private LoggingSettings() {
	}

	public static void ensureSettingsExist() {
		SettingsStore.current().ensure(HBCI_LOG_LEVEL, GBANKING_LOG_LEVEL, MASK_SENSITIVE_DATA);
	}

	public static LogLevelSetting getHbciLogLevel() {
		return getLogLevel(SETTING_HBCI_LOG_LEVEL, DEFAULT_HBCI_LOG_LEVEL);
	}

	public static LogLevelSetting getGbankingLogLevel() {
		return getLogLevel(SETTING_GBANKING_LOG_LEVEL, DEFAULT_GBANKING_LOG_LEVEL);
	}

	public static LogLevelSetting getDefaultLogLevel(String attribute) {
		return SETTING_HBCI_LOG_LEVEL.equals(attribute) ? DEFAULT_HBCI_LOG_LEVEL : DEFAULT_GBANKING_LOG_LEVEL;
	}

	public static LogLevelSetting resolveLogLevel(String attribute, String value) {
		return LogLevelSetting.fromValue(value, getDefaultLogLevel(attribute));
	}

	public static boolean isLogLevelSetting(String attribute) {
		return SETTING_HBCI_LOG_LEVEL.equals(attribute) || SETTING_GBANKING_LOG_LEVEL.equals(attribute);
	}

	public static boolean isSensitiveDataMaskingEnabled() {
		return sensitiveDataMaskingEnabled;
	}

	public static int getHbciLogFilterLevel() {
		return LogFilter.FILTER_SECRETS;
	}

	public static void applyGbankingLogLevel() {
		applyLoggerLevel(GBANKING_LOGGER_NAME, getGbankingLogLevel(), DEFAULT_GBANKING_LOG_LEVEL, "GBanking");
	}

	public static void applyGbankingLogLevel(LogLevelSetting level) {
		applyLoggerLevel(GBANKING_LOGGER_NAME, level, DEFAULT_GBANKING_LOG_LEVEL, "GBanking");
	}

	public static void applyHbciLogLevel() {
		applyHbciLogLevel(getHbciLogLevel());
	}

	public static void applyHbciLogLevel(LogLevelSetting level) {
		LogLevelSetting resolvedLevel = level != null ? level : DEFAULT_HBCI_LOG_LEVEL;
		if (HBCIUtils.getParams() != null) {
			HBCIUtils.setParam(HbciProperties.LOG_LEVEL_PARAM, Integer.toString(resolvedLevel.toHbciLogLevel()));
		}
		applyLoggerLevel(HBCI4JAVA_LOGGER_NAME, resolvedLevel, DEFAULT_HBCI_LOG_LEVEL, "HBCI4Java");
	}

	public static void applyLogLevels() {
		applySensitiveDataMasking();
		applyGbankingLogLevel();
		applyHbciLogLevel();
	}

	public static void applySensitiveDataMasking() {
		applySensitiveDataMasking(getBooleanSetting(SETTING_MASK_SENSITIVE_DATA, DEFAULT_MASK_SENSITIVE_DATA));
	}

	static void applySensitiveDataMasking(boolean enabled) {
		sensitiveDataMaskingEnabled = enabled;
		if (HBCIUtils.getParams() != null) {
			HBCIUtils.setParam(HbciProperties.LOG_FILTER_PARAM, Integer.toString(getHbciLogFilterLevel()));
		}
		log.info("Applied sensitive data masking: {}", enabled);
	}

	private static void applyLoggerLevel(String loggerName, LogLevelSetting level, LogLevelSetting defaultValue, String label) {
		LogLevelSetting resolvedLevel = level != null ? level : defaultValue;
		try {
			Class<?> configuratorClass = Class.forName("org.apache.logging.log4j.core.config.Configurator");
			Method setLevel = configuratorClass.getMethod("setLevel", String.class, org.apache.logging.log4j.Level.class);
			setLevel.invoke(null, loggerName, resolvedLevel.toLog4jLevel());
			log.info("Applied {} log level {}", label, resolvedLevel);
		} catch (ReflectiveOperationException e) {
			log.warn("Could not apply {} log level {}", label, resolvedLevel, e);
		}
	}

	private static LogLevelSetting getLogLevel(String attribute, LogLevelSetting defaultValue) {
		ensureSettingsExist();
		return LogLevelSetting.fromValue(SettingsStore.current().getString(attribute, null), defaultValue);
	}

	private static boolean getBooleanSetting(String attribute, boolean defaultValue) {
		ensureSettingsExist();
		return SettingsStore.current().getBoolean(attribute, defaultValue);
	}
}
