package de.zft2.gbanking.db;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class BuildInfo {

	private static final String UNKNOWN_VERSION = "0.0.0";

	private BuildInfo() {
	}

	public static String getJavaVersion() {
		return System.getProperty("java.version");
	}

	public static String getJavaFxVersion() {
		return System.getProperty("javafx.version");
	}

	public static String getProgramVersion() {
		Package pkg = BuildInfo.class.getPackage();
		if (pkg != null && pkg.getImplementationVersion() != null && !pkg.getImplementationVersion().isBlank()) {
			return pkg.getImplementationVersion();
		}

		Properties properties = new Properties();
		try (InputStream in = BuildInfo.class.getClassLoader().getResourceAsStream("build-info.properties")) {
			if (in != null) {
				properties.load(in);
				String value = properties.getProperty("app.version");
				if (isValidProperty(value)) {
					return value.trim();
				}
			}
		} catch (IOException e) {
			// ignore and use fallback below
		}
		return UNKNOWN_VERSION;
	}

	private static boolean isValidProperty(String value) {
		return value != null && !value.isBlank() && !value.contains("@project.version@") && !value.contains("${project.version}");
	}
}
