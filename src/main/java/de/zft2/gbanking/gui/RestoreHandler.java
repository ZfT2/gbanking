package de.zft2.gbanking.gui;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.util.AppPaths;
import de.zft2.gbanking.util.PropertiesFileSupport;

class RestoreHandler {

	private static final Logger log = LogManager.getLogger(RestoreHandler.class);

	private static final String LAST_TENANT_ID = "lastTenantId";
	private static final String LANGUAGE = "language";
	static final String ONLY_ONLINE_ACCOUNTS = "onlyOnlineAccounts";

	private RestoreHandler() {
	}

	public static final Path FILE_NAME = AppPaths.resolveInApplicationDirectory("properties", "gui.properties");
	public static final Path ENV_FILE_NAME = AppPaths.resolveInApplicationDirectory("properties", "env.properties");

	public static void storeOptions(Map<String, String> optionsMap) throws IOException {
		storeOptions(optionsMap, FILE_NAME, ENV_FILE_NAME);
	}

	static void storeOptions(Map<String, String> optionsMap, Path guiFile, Path environmentFile) throws IOException {
		Properties guiProperties = new Properties();
		putIfPresent(guiProperties, ONLY_ONLINE_ACCOUNTS, optionsMap.get(ONLY_ONLINE_ACCOUNTS));
		storeLayoutOptions(guiProperties, optionsMap);
		storeProperties(guiFile, guiProperties, "GUI Properties of the user app");

		Properties environmentProperties = new Properties();
		putIfPresent(environmentProperties, EnvironmentOptions.DATA_DIRECTORY, EnvironmentOptions.getDataDirectory(optionsMap));
		putIfPresent(environmentProperties, EnvironmentOptions.DEFAULT_DIR_IMPORT, optionsMap.get(EnvironmentOptions.DEFAULT_DIR_IMPORT));
		putIfPresent(environmentProperties, EnvironmentOptions.DEFAULT_DIR_EXPORT, optionsMap.get(EnvironmentOptions.DEFAULT_DIR_EXPORT));
		putIfPresent(environmentProperties, EnvironmentOptions.IMPORT_PROPERTIES_DIRECTORY,
				EnvironmentOptions.getImportPropertiesDirectory(optionsMap));
		putIfPresent(environmentProperties, LAST_TENANT_ID, optionsMap.get(LAST_TENANT_ID));
		putIfPresent(environmentProperties, LANGUAGE, optionsMap.get(LANGUAGE));
		storeProperties(environmentFile, environmentProperties, "Environment Properties of the user app");
	}

	public static void restoreOptions(Map<String, String> optionsMap) throws IOException {
		restoreOptions(optionsMap, FILE_NAME, ENV_FILE_NAME);
	}

	static void restoreOptions(Map<String, String> optionsMap, Path guiFile, Path environmentFile) throws IOException {
		Properties guiProperties = loadProperties(guiFile);
		putIfPresent(optionsMap, ONLY_ONLINE_ACCOUNTS, guiProperties.getProperty(ONLY_ONLINE_ACCOUNTS));
		restoreLayoutOptions(guiProperties, optionsMap);

		Properties environmentProperties = loadProperties(environmentFile);
		for (String key : List.of(EnvironmentOptions.DATA_DIRECTORY, EnvironmentOptions.DEFAULT_DIR_IMPORT,
				EnvironmentOptions.DEFAULT_DIR_EXPORT, EnvironmentOptions.IMPORT_PROPERTIES_DIRECTORY, LAST_TENANT_ID, LANGUAGE)) {
			putIfPresent(optionsMap, key, environmentProperties.getProperty(key));
		}

		optionsMap.put(EnvironmentOptions.DATA_DIRECTORY, EnvironmentOptions.getDataDirectory(optionsMap));
		optionsMap.put(EnvironmentOptions.IMPORT_PROPERTIES_DIRECTORY, EnvironmentOptions.getImportPropertiesDirectory(optionsMap));
	}

	private static Properties loadProperties(Path file) throws IOException {
		Properties properties = new Properties();
		if (Files.exists(file)) {
			try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				properties.load(reader);
			}
		}
		return properties;
	}

	private static void storeProperties(Path file, Properties properties, String comment) throws IOException {
		Path parent = file.getParent();
		if (parent == null) {
			log.error("Properties path has no parent directory: {}", file);
			return;
		}
		Files.createDirectories(parent);
		Files.writeString(file, PropertiesFileSupport.updateContent(file, properties, comment), StandardCharsets.UTF_8);
	}

	private static void putIfPresent(Properties properties, String key, String value) {
		if (value != null && !value.isBlank()) {
			properties.setProperty(key, value);
		}
	}

	private static void putIfPresent(Map<String, String> optionsMap, String key, String value) {
		if (value != null && !value.isBlank()) {
			optionsMap.put(key, value);
		}
	}

	static void storeLayoutOptions(Properties properties, Map<String, String> optionsMap) {
		optionsMap.forEach((key, value) -> {
			if (key.startsWith(GuiLayoutState.OPTION_PREFIX) && value != null) {
				properties.setProperty(key, value);
			}
		});
	}

	static void restoreLayoutOptions(Properties properties, Map<String, String> optionsMap) {
		properties.stringPropertyNames().stream()
				.filter(key -> key.startsWith(GuiLayoutState.OPTION_PREFIX))
				.forEach(key -> optionsMap.put(key, properties.getProperty(key)));
	}

}
