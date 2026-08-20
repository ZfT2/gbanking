package de.zft2.gbanking.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestoreHandlerEnvironmentTest {

	@TempDir
	private Path tempDirectory;

	@Test
	void shouldStoreGuiAndEnvironmentOptionsSeparately() throws Exception {
		Path guiFile = tempDirectory.resolve("gui.properties");
		Path environmentFile = tempDirectory.resolve("env.properties");
		Files.writeString(guiFile, "# Keep GUI comment\nonlyOnlineAccounts=false\n", StandardCharsets.UTF_8);
		Files.writeString(environmentFile, "! Keep environment comment\nlanguage=de\n", StandardCharsets.UTF_8);
		Map<String, String> options = new HashMap<>();
		options.put(EnvironmentOptions.DATA_DIRECTORY, "X:/shared-data");
		options.put(EnvironmentOptions.DEFAULT_DIR_IMPORT, "X:/imports");
		options.put(EnvironmentOptions.DEFAULT_DIR_EXPORT, "X:/exports");
		options.put(EnvironmentOptions.IMPORT_PROPERTIES_DIRECTORY, "X:/import-properties");
		options.put("lastTenantId", "tenant-id");
		options.put("language", "en");
		options.put(RestoreHandler.ONLY_ONLINE_ACCOUNTS, "true");
		options.put(GuiLayoutState.OPTION_PREFIX + "window.width", "1200");

		RestoreHandler.storeOptions(options, guiFile, environmentFile);

		Properties guiProperties = load(guiFile);
		Properties environmentProperties = load(environmentFile);
		assertEquals("true", guiProperties.getProperty(RestoreHandler.ONLY_ONLINE_ACCOUNTS));
		assertEquals("1200", guiProperties.getProperty(GuiLayoutState.OPTION_PREFIX + "window.width"));
		assertFalse(guiProperties.containsKey(EnvironmentOptions.DATA_DIRECTORY));
		assertEquals("X:/shared-data", environmentProperties.getProperty(EnvironmentOptions.DATA_DIRECTORY));
		assertEquals("X:/imports", environmentProperties.getProperty(EnvironmentOptions.DEFAULT_DIR_IMPORT));
		assertEquals("X:/exports", environmentProperties.getProperty(EnvironmentOptions.DEFAULT_DIR_EXPORT));
		assertEquals("X:/import-properties", environmentProperties.getProperty(EnvironmentOptions.IMPORT_PROPERTIES_DIRECTORY));
		assertEquals("tenant-id", environmentProperties.getProperty("lastTenantId"));
		assertEquals("en", environmentProperties.getProperty("language"));
		assertTrue(Files.readString(guiFile, StandardCharsets.UTF_8).contains("# Keep GUI comment"));
		assertTrue(Files.readString(environmentFile, StandardCharsets.UTF_8).contains("! Keep environment comment"));
	}

	@Test
	void shouldPreferEnvironmentFileAndKeepOptionalDirectoriesEmpty() throws Exception {
		Path environmentFile = tempDirectory.resolve("env.properties");
		Properties environmentProperties = new Properties();
		environmentProperties.setProperty(EnvironmentOptions.DATA_DIRECTORY, "X:/environment-data");
		environmentProperties.setProperty("language", "en");
		store(environmentFile, environmentProperties);
		Map<String, String> restored = new HashMap<>();

		RestoreHandler.restoreOptions(restored, tempDirectory.resolve("missing-gui.properties"), environmentFile);

		assertEquals("X:/environment-data", restored.get(EnvironmentOptions.DATA_DIRECTORY));
		assertEquals(EnvironmentOptions.DEFAULT_IMPORT_PROPERTIES_DIRECTORY,
				restored.get(EnvironmentOptions.IMPORT_PROPERTIES_DIRECTORY));
		assertEquals("en", restored.get("language"));
		assertNull(restored.get(EnvironmentOptions.DEFAULT_DIR_IMPORT));
		assertNull(restored.get(EnvironmentOptions.DEFAULT_DIR_EXPORT));
	}

	private Properties load(Path file) throws IOException {
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}

	private void store(Path file, Properties properties) throws IOException {
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			properties.store(writer, "test");
		}
	}
}
