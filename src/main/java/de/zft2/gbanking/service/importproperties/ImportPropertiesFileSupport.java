package de.zft2.gbanking.service.importproperties;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import de.zft2.gbanking.util.PropertiesFileSupport;

public final class ImportPropertiesFileSupport {

	private ImportPropertiesFileSupport() {
	}

	public static Map<String, String> read(Path path) throws IOException {
		if (!Files.isRegularFile(path)) {
			return Map.of();
		}
		Properties properties = new Properties();
		String content = Files.readString(path, StandardCharsets.UTF_8);
		try (StringReader reader = new StringReader(escapeUnescapedKeySpaces(content))) {
			properties.load(reader);
		}
		Map<String, String> values = new LinkedHashMap<>();
		for (String key : new java.util.TreeSet<>(properties.stringPropertyNames())) {
			values.put(key, properties.getProperty(key));
		}
		return values;
	}

	private static String escapeUnescapedKeySpaces(String content) {
		return String.join("\n", content.lines().map(line -> escapeUnescapedKeySpacesInLine(line)).toList());
	}

	private static String escapeUnescapedKeySpacesInLine(String line) {
		int separator = line.indexOf('=');
		if (separator <= 0 || line.startsWith("#") || line.startsWith("!")) {
			return line;
		}
		String key = line.substring(0, separator).replaceAll("(?<!\\\\) ", "\\\\ ");
		return key + line.substring(separator);
	}

	static void write(Path path, Map<String, String> values) throws IOException {
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path temporaryPath = path.resolveSibling(path.getFileName() + ".tmp");
		Files.writeString(temporaryPath, PropertiesFileSupport.updateContent(path, values, null), StandardCharsets.UTF_8);
		try {
			Files.move(temporaryPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
