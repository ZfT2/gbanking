package de.gbanking.gui.fx;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

class RestoreHandler {

	private RestoreHandler() {
	}

	public static final String FILE_NAME = "properties/gui.properties";

	public static void storeOptions(Map<String, String> optionsMap) throws IOException {
		File file = new File(FILE_NAME);
		Properties p = new Properties();

		p.setProperty("defaultDir", optionsMap.get("lastPathSelected"));

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

			optionsMap.put("lastPathSelected", p.getProperty("defaultDir"));
		}
	}

}
