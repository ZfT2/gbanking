package de.zft2.gbanking.logging;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.manager.HBCIUtils;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.enu.DataType;
import de.zft2.gbanking.hbci.HbciProperties;

public final class LoggingSettings {

	public static final String SETTING_HBCI_LOG_LEVEL = "hbci.log.level";
	public static final String SETTING_GBANKING_LOG_LEVEL = "gbanking.log.level";
	public static final String HBCI4JAVA_LOGGER_NAME = "de.zft2.gbanking.hbci4java";

	private static final Logger log = LogManager.getLogger(LoggingSettings.class);
	private static final String GBANKING_LOGGER_NAME = "de.zft2.gbanking";
	private static final LogLevelSetting DEFAULT_HBCI_LOG_LEVEL = LogLevelSetting.WARN;
	private static final LogLevelSetting DEFAULT_GBANKING_LOG_LEVEL = LogLevelSetting.INFO;

	private LoggingSettings() {
	}

	public static void ensureSettingsExist() {
		DBController dbController = DBController.getInstance(".");
		List<Setting> settings = dbController.getAll(Setting.class);
		ensureSetting(dbController, settings, SETTING_HBCI_LOG_LEVEL, DEFAULT_HBCI_LOG_LEVEL, "HBCI4Java-Loglevel");
		ensureSetting(dbController, settings, SETTING_GBANKING_LOG_LEVEL, DEFAULT_GBANKING_LOG_LEVEL, "GBanking-Loglevel");
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
		applyGbankingLogLevel();
		applyHbciLogLevel();
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

	private static void ensureSetting(DBController dbController, List<Setting> settings, String attribute, LogLevelSetting defaultValue, String comment) {
		boolean exists = settings != null && settings.stream().anyMatch(setting -> attribute.equals(setting.getAttribute()));
		if (exists) {
			return;
		}

		Setting setting = new Setting();
		setting.setAttribute(attribute);
		setting.setValue(defaultValue.name());
		setting.setDataType(DataType.ENUM);
		setting.setEditable(true);
		setting.setVisible(true);
		setting.setComment(comment);
		setting.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		dbController.insertOrUpdate(setting);
	}

	private static LogLevelSetting getLogLevel(String attribute, LogLevelSetting defaultValue) {
		ensureSettingsExist();
		return DBController.getInstance(".").getAll(Setting.class).stream()
				.filter(setting -> attribute.equals(setting.getAttribute()))
				.map(setting -> LogLevelSetting.fromValue(setting.getValue(), defaultValue))
				.findFirst()
				.orElse(defaultValue);
	}
}
