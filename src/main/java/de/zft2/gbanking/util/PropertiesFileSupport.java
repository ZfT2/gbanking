package de.zft2.gbanking.util;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

public final class PropertiesFileSupport {

	private PropertiesFileSupport() {
	}

	public static String updateContent(Path source, Properties properties, String defaultComment) throws IOException {
		Map<String, String> values = new TreeMap<>();
		for (String key : properties.stringPropertyNames()) {
			values.put(key, properties.getProperty(key));
		}
		return updateContent(source, values, defaultComment);
	}

	public static String updateContent(Path source, Map<String, String> values, String defaultComment) throws IOException {
		String previousContent = Files.isRegularFile(source) ? Files.readString(source, StandardCharsets.UTF_8) : "";
		String lineSeparator = detectLineSeparator(previousContent);
		List<String> outputLines = mergeExistingLines(previousContent, values);
		if (previousContent.isBlank() && defaultComment != null && !defaultComment.isBlank()) {
			outputLines.add(0, "# " + defaultComment);
		}
		return String.join(lineSeparator, outputLines) + lineSeparator;
	}

	private static List<String> mergeExistingLines(String content, Map<String, String> values) throws IOException {
		List<String> inputLines = new ArrayList<>(List.of(content.split("\\R", -1)));
		if (!inputLines.isEmpty() && inputLines.get(inputLines.size() - 1).isEmpty()) {
			inputLines.remove(inputLines.size() - 1);
		}

		List<String> outputLines = new ArrayList<>();
		Set<String> writtenKeys = new HashSet<>();
		for (int index = 0; index < inputLines.size();) {
			String line = inputLines.get(index);
			if (line.isBlank() || isComment(line)) {
				outputLines.add(line);
				index++;
				continue;
			}

			int entryEnd = findEntryEnd(inputLines, index);
			String key = parseKey(line);
			if (values.containsKey(key) && writtenKeys.add(key)) {
				outputLines.add(formatEntry(key, values.get(key)));
			}
			index = entryEnd;
		}

		for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
			if (writtenKeys.add(entry.getKey())) {
				outputLines.add(formatEntry(entry.getKey(), entry.getValue()));
			}
		}
		return outputLines;
	}

	private static boolean isComment(String line) {
		String trimmed = line.stripLeading();
		return trimmed.startsWith("#") || trimmed.startsWith("!");
	}

	private static int findEntryEnd(List<String> lines, int start) {
		int end = start + 1;
		while (end < lines.size() && hasContinuation(lines.get(end - 1))) {
			end++;
		}
		return end;
	}

	private static boolean hasContinuation(String line) {
		int backslashes = 0;
		for (int index = line.length() - 1; index >= 0 && line.charAt(index) == '\\'; index--) {
			backslashes++;
		}
		return backslashes % 2 != 0;
	}

	private static String parseKey(String line) throws IOException {
		int start = 0;
		while (start < line.length() && Character.isWhitespace(line.charAt(start))) {
			start++;
		}
		int separator = findSeparator(line, start);
		String rawKey = line.substring(start, separator).stripTrailing();
		Properties parsed = new Properties();
		try (StringReader reader = new StringReader(escapeUnescapedSpaces(rawKey) + "=value")) {
			parsed.load(reader);
		}
		return parsed.stringPropertyNames().stream().findFirst().orElseThrow();
	}

	private static int findSeparator(String line, int start) {
		int whitespaceSeparator = line.length();
		for (int index = start; index < line.length(); index++) {
			char character = line.charAt(index);
			if ((character == '=' || character == ':') && !isEscaped(line, index)) {
				return index;
			}
			if (whitespaceSeparator == line.length() && Character.isWhitespace(character) && !isEscaped(line, index)) {
				whitespaceSeparator = index;
			}
		}
		return whitespaceSeparator;
	}

	private static String escapeUnescapedSpaces(String value) {
		StringBuilder escaped = new StringBuilder(value.length());
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (Character.isWhitespace(character) && !isEscaped(value, index)) {
				escaped.append('\\');
			}
			escaped.append(character);
		}
		return escaped.toString();
	}

	private static boolean isEscaped(String text, int index) {
		int backslashes = 0;
		for (int position = index - 1; position >= 0 && text.charAt(position) == '\\'; position--) {
			backslashes++;
		}
		return backslashes % 2 != 0;
	}

	private static String formatEntry(String key, String value) throws IOException {
		Properties property = new Properties();
		property.setProperty(key, value != null ? value : "");
		StringWriter writer = new StringWriter();
		property.store(writer, null);
		return writer.toString().lines().filter(line -> !line.startsWith("#")).findFirst().orElseThrow();
	}

	private static String detectLineSeparator(String content) {
		int lineFeed = content.indexOf('\n');
		if (lineFeed >= 0) {
			return lineFeed > 0 && content.charAt(lineFeed - 1) == '\r' ? "\r\n" : "\n";
		}
		return content.indexOf('\r') >= 0 ? "\r" : System.lineSeparator();
	}
}
