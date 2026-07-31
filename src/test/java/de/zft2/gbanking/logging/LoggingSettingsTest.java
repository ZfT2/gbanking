package de.zft2.gbanking.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kapott.hbci.callback.HBCICallbackConsole;
import org.kapott.hbci.manager.HBCIUtils;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.enu.DataType;
import de.zft2.gbanking.hbci.HbciProperties;

class LoggingSettingsTest {

	private DBController dbController;
	private Path tempDir;
	private LoggerContext loggerContext;

	@BeforeEach
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		dbController = DBController.getInstance(tempDir.toString());
		loggerContext = (LoggerContext) LogManager.getContext(false);
	}

	@AfterEach
	void cleanupDatabase() throws Exception {
		if (HBCIUtils.getParams() != null) {
			HBCIUtils.done();
		}
		removeLogger(LoggingSettings.HBCI4JAVA_LOGGER_NAME);
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void ensureSettingsExistShouldCreateVisibleEnumDefaults() {
		LoggingSettings.ensureSettingsExist();

		Map<String, Setting> settingsByAttribute = dbController.getAll(Setting.class).stream()
				.collect(Collectors.toMap(Setting::getAttribute, setting -> setting));

		assertLogSetting(settingsByAttribute.get(LoggingSettings.SETTING_HBCI_LOG_LEVEL), "WARN");
		assertLogSetting(settingsByAttribute.get(LoggingSettings.SETTING_GBANKING_LOG_LEVEL), "INFO");
	}

	@Test
	void applyHbciLogLevelShouldConfigureHbciParameterAndDedicatedLogger() {
		HBCIUtils.init(new Properties(), new HBCICallbackConsole());

		LoggingSettings.applyHbciLogLevel(LogLevelSetting.DEBUG);

		assertEquals("4", HBCIUtils.getParam(HbciProperties.LOG_LEVEL_PARAM));
		assertEquals(Level.DEBUG, getConfiguredLevel(LoggingSettings.HBCI4JAVA_LOGGER_NAME));
	}

	@Test
	void applyHbciLogLevelShouldConfigureDedicatedLoggerBeforeHbciInit() {
		LoggingSettings.applyHbciLogLevel(LogLevelSetting.TRACE);

		assertEquals(Level.TRACE, getConfiguredLevel(LoggingSettings.HBCI4JAVA_LOGGER_NAME));
	}

	private void assertLogSetting(Setting setting, String expectedValue) {
		assertNotNull(setting);
		assertEquals(expectedValue, setting.getValue());
		assertEquals(DataType.ENUM, setting.getDataType());
		assertTrue(setting.isEditable());
		assertTrue(setting.isVisible());
	}

	private Level getConfiguredLevel(String loggerName) {
		return loggerContext.getConfiguration().getLoggerConfig(loggerName).getLevel();
	}

	private void removeLogger(String loggerName) {
		Configuration configuration = loggerContext.getConfiguration();
		configuration.removeLogger(loggerName);
		loggerContext.updateLoggers();
	}
}
