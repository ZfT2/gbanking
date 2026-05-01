package de.zft2.gbanking.gui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import de.zft2.gbanking.util.AppPaths;

class RestoreHandler {

	private static final String DEFAULT_DIR = "defaultDir";
	private static final String LAST_TENANT_ID = "lastTenantId";
	private static final String LANGUAGE = "language";

	private RestoreHandler() {
	}

	public static final Path FILE_NAME = AppPaths.resolveInApplicationDirectory("properties", "gui.properties");

	public static void storeOptions(Map<String, String> optionsMap) throws IOException {
		Properties p = new Properties();

		putIfPresent(p, DEFAULT_DIR, optionsMap.get("lastPathSelected"));
		putIfPresent(p, LAST_TENANT_ID, optionsMap.get("lastTenantId"));
		putIfPresent(p, LANGUAGE, optionsMap.get("language"));

		Files.createDirectories(FILE_NAME.getParent());
		try (BufferedWriter br = Files.newBufferedWriter(FILE_NAME, StandardCharsets.UTF_8)) {
			p.store(br, "GUI Properties of the user app");
		}
	}

	public static void restoreOptions(Map<String, String> optionsMap) throws IOException {
		if (Files.exists(FILE_NAME)) {
			Properties p = new Properties();
			try (BufferedReader br = Files.newBufferedReader(FILE_NAME, StandardCharsets.UTF_8)) {
				p.load(br);
			}

			optionsMap.put("lastPathSelected", p.getProperty(DEFAULT_DIR));
			optionsMap.put("lastTenantId", p.getProperty(LAST_TENANT_ID));
			optionsMap.put("language", p.getProperty(LANGUAGE));
		}
	}

	private static void putIfPresent(Properties properties, String key, String value) {
		if (value != null && !value.isBlank()) {
			properties.setProperty(key, value);
		}
	}

}
