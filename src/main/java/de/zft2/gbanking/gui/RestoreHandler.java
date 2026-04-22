package de.zft2.gbanking.gui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

class RestoreHandler {

	private static final String DEFAULT_DIR = "defaultDir";
	private static final String LAST_TENANT_ID = "lastTenantId";
	private static final String LANGUAGE = "language";

	private RestoreHandler() {
	}

	public static final String FILE_NAME = "properties/gui.properties";

	public static void storeOptions(Map<String, String> optionsMap) throws IOException {
		File file = new File(FILE_NAME);
		Properties p = new Properties();

		putIfPresent(p, DEFAULT_DIR, optionsMap.get("lastPathSelected"));
		putIfPresent(p, LAST_TENANT_ID, optionsMap.get("lastTenantId"));
		putIfPresent(p, LANGUAGE, optionsMap.get("language"));

		try (BufferedWriter br = new BufferedWriter(new FileWriter(file))) {
			p.store(br, "GUI Properties of the user app");
		}
	}

	public static void restoreOptions(Map<String, String> optionsMap) throws IOException {
		File optionsFile = new File(FILE_NAME);
		if (optionsFile.exists()) {
			Properties p = new Properties();
			try (BufferedReader br = new BufferedReader(new FileReader(optionsFile))) {
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
