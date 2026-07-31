package de.zft2.gbanking.gui;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;

import de.zft2.core.config.ImportProperties;
import de.zft2.gbanking.util.AppPaths;

public final class EnvironmentOptions {

	public static final String DATA_DIRECTORY = "dataDirectory";
	public static final String DEFAULT_DIR_IMPORT = "defaultDirImport";
	public static final String DEFAULT_DIR_EXPORT = "defaultDirExport";
	public static final String IMPORT_PROPERTIES_DIRECTORY = "importPropertiesDirectory";
	public static final String DEFAULT_DATA_DIRECTORY = "./data";
	public static final String DEFAULT_IMPORT_PROPERTIES_DIRECTORY = "./properties/import";
	private static final String WORK_DIRECTORY = "work";

	private EnvironmentOptions() {
	}

	public static String getDataDirectory(Map<String, String> optionsMap) {
		if (optionsMap == null) {
			return DEFAULT_DATA_DIRECTORY;
		}
		return normalizeDataDirectory(optionsMap.get(DATA_DIRECTORY));
	}

	public static String normalizeDataDirectory(String dataDirectory) {
		if (dataDirectory == null || dataDirectory.isBlank()) {
			return DEFAULT_DATA_DIRECTORY;
		}
		return dataDirectory.trim();
	}

	public static Path resolveDataDirectory(Map<String, String> optionsMap) {
		return AppPaths.resolveInApplicationDirectory(getDataDirectory(optionsMap));
	}

	public static String getImportPropertiesDirectory(Map<String, String> optionsMap) {
		if (optionsMap == null) {
			return DEFAULT_IMPORT_PROPERTIES_DIRECTORY;
		}
		return normalizeImportPropertiesDirectory(optionsMap.get(IMPORT_PROPERTIES_DIRECTORY));
	}

	public static String normalizeImportPropertiesDirectory(String directory) {
		return normalizeDirectory(directory, DEFAULT_IMPORT_PROPERTIES_DIRECTORY);
	}

	public static Path resolveImportPropertiesDirectory(Map<String, String> optionsMap) {
		return AppPaths.resolveInApplicationDirectory(getImportPropertiesDirectory(optionsMap));
	}

	public static Path resolveWorkDirectory() {
		return AppPaths.resolveInApplicationDirectory(WORK_DIRECTORY);
	}

	public static boolean usesExternalDataDirectory(Map<String, String> optionsMap) {
		Path defaultDirectory = AppPaths.resolveInApplicationDirectory(DEFAULT_DATA_DIRECTORY);
		return !resolveDataDirectory(optionsMap).equals(defaultDirectory);
	}

	public static void applyRuntimeOptions(Map<String, String> optionsMap) {
		System.setProperty(ImportProperties.IMPORT_PROPERTIES_DIRECTORY_PROPERTY,
				resolveImportPropertiesDirectory(optionsMap).toString());
	}

	public static String normalizeOptionalDirectory(String directory) {
		return directory == null ? "" : directory.trim();
	}

	public static boolean isValidPath(String path) {
		try {
			if (path != null && !path.isBlank()) {
				Path.of(path.trim());
			}
			return true;
		} catch (InvalidPathException e) {
			return false;
		}
	}

	private static String normalizeDirectory(String directory, String defaultDirectory) {
		return directory == null || directory.isBlank() ? defaultDirectory : directory.trim();
	}
}
