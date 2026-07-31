package de.zft2.gbanking.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.zft2.core.config.ImportProperties;
import de.zft2.gbanking.util.AppPaths;

class EnvironmentOptionsTest {

	@AfterEach
	void clearRuntimeOptions() {
		System.clearProperty(ImportProperties.IMPORT_PROPERTIES_DIRECTORY_PROPERTY);
	}

	@Test
	void getDataDirectory_shouldReturnDefaultForBlankValue() {
		assertEquals("./data", EnvironmentOptions.getDataDirectory(new HashMap<>()));

		Map<String, String> options = new HashMap<>();
		options.put(EnvironmentOptions.DATA_DIRECTORY, " ");

		assertEquals("./data", EnvironmentOptions.getDataDirectory(options));
	}

	@Test
	void resolveDataDirectory_shouldResolveRelativePathAgainstApplicationDirectory() {
		Map<String, String> options = new HashMap<>();
		options.put(EnvironmentOptions.DATA_DIRECTORY, "custom-data");

		assertEquals(AppPaths.resolveInApplicationDirectory("custom-data"), EnvironmentOptions.resolveDataDirectory(options));
	}

	@Test
	void resolveDataDirectory_shouldKeepAbsolutePath() {
		Path absolutePath = AppPaths.getApplicationBaseDirectory().resolve("external-data").toAbsolutePath().normalize();
		Map<String, String> options = new HashMap<>();
		options.put(EnvironmentOptions.DATA_DIRECTORY, absolutePath.toString());

		assertEquals(absolutePath, EnvironmentOptions.resolveDataDirectory(options));
	}

	@Test
	void importPropertiesDirectory_shouldResolveDefaultAndConfiguredPaths() {
		assertEquals(AppPaths.resolveInApplicationDirectory(EnvironmentOptions.DEFAULT_IMPORT_PROPERTIES_DIRECTORY),
				EnvironmentOptions.resolveImportPropertiesDirectory(new HashMap<>()));

		Map<String, String> options = new HashMap<>();
		options.put(EnvironmentOptions.IMPORT_PROPERTIES_DIRECTORY, "custom-import-properties");

		assertEquals(AppPaths.resolveInApplicationDirectory("custom-import-properties"),
				EnvironmentOptions.resolveImportPropertiesDirectory(options));
	}

	@Test
	void applyRuntimeOptions_shouldConfigureBookingCoreDirectory() {
		Map<String, String> options = new HashMap<>();
		options.put(EnvironmentOptions.IMPORT_PROPERTIES_DIRECTORY, "custom-import-properties");

		EnvironmentOptions.applyRuntimeOptions(options);

		assertEquals(AppPaths.resolveInApplicationDirectory("custom-import-properties").toString(),
				System.getProperty(ImportProperties.IMPORT_PROPERTIES_DIRECTORY_PROPERTY));
		assertEquals(AppPaths.resolveInApplicationDirectory("custom-import-properties"), AppPaths.getImportPropertiesDirectory());
	}

	@Test
	void usesExternalDataDirectory_shouldCompareResolvedDefaultDirectory() {
		assertFalse(EnvironmentOptions.usesExternalDataDirectory(new HashMap<>()));

		Map<String, String> options = new HashMap<>();
		options.put(EnvironmentOptions.DATA_DIRECTORY, "shared-data");

		assertTrue(EnvironmentOptions.usesExternalDataDirectory(options));
	}

	@Test
	void isValidPath_shouldRejectInvalidPath() {
		assertTrue(EnvironmentOptions.isValidPath("./data"));
		assertFalse(EnvironmentOptions.isValidPath("data\u0000broken"));
	}
}
