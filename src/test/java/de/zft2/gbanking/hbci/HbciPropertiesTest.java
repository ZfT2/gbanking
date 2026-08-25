package de.zft2.gbanking.hbci;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.BuildInfo;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.logging.LoggingSettings;

class HbciPropertiesTest {

	private DBController dbController;
	private Path tempDir;

	@BeforeEach
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		dbController = DBController.getInstance(tempDir.toString());
	}

	@AfterEach
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void createBasePropertiesShouldUseConfiguredLevel() {
		Setting hbciLogLevel = dbController.getAll(Setting.class).stream()
				.filter(setting -> LoggingSettings.SETTING_HBCI_LOG_LEVEL.equals(setting.getAttribute()))
				.findFirst()
				.orElseThrow();
		hbciLogLevel.setValue("TRACE");
		dbController.insertOrUpdate(hbciLogLevel);

		Properties properties = HbciProperties.createBaseProperties();

		assertEquals("1", properties.getProperty(HbciProperties.PINTAN_INIT_PARAM));
		assertEquals("5", properties.getProperty(HbciProperties.LOG_LEVEL_PARAM));
		assertEquals(HbciProperties.toFinTsProductVersion(BuildInfo.getProgramVersion()),
				properties.getProperty(HbciProperties.PRODUCT_VERSION_PARAM));
	}

	@Test
	void shouldConvertApplicationVersionToFinTsProductVersion() {
		assertEquals("0.3.0", HbciProperties.toFinTsProductVersion("0.3.0"));
		assertEquals("0.3.1", HbciProperties.toFinTsProductVersion("0.3.1-SNAPSHOT"));
		assertEquals("0.10", HbciProperties.toFinTsProductVersion("0.10.0"));
		assertEquals("10.0", HbciProperties.toFinTsProductVersion("10.0.0"));
	}
}
