package de.zft2.gbanking.file.imp.csv;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.util.AppPaths;

public class CsvImportDefinitionRepository {

	public static final String FILE_NAME = "csv.properties";
	private static final String DEFAULT_RESOURCE = "/properties/import/" + FILE_NAME;

	private final Path definitionFile;

	public CsvImportDefinitionRepository() {
		this(AppPaths.getImportPropertiesDirectory().resolve(FILE_NAME));
	}

	public CsvImportDefinitionRepository(Path definitionFile) {
		this.definitionFile = definitionFile;
	}

	public Path getDefinitionFile() {
		return definitionFile;
	}

	public List<CsvImportDefinition> load() {
		try {
			Map<String, CsvImportDefinition> definitions = new LinkedHashMap<>();
			merge(definitions, parse(readBundledDefinitions(), DEFAULT_RESOURCE));
			if (Files.isRegularFile(definitionFile)) {
				merge(definitions, parse(Files.readAllLines(definitionFile, StandardCharsets.UTF_8), definitionFile.toString()));
			}
			if (definitions.isEmpty()) {
				throw new GBankingException("No booking definitions found in " + DEFAULT_RESOURCE);
			}
			return List.copyOf(definitions.values());
		} catch (IOException exception) {
			throw new GBankingException("CSV import definitions could not be read from " + definitionFile, exception);
		}
	}

	private List<String> readBundledDefinitions() throws IOException {
		try (InputStream input = CsvImportDefinitionRepository.class.getResourceAsStream(DEFAULT_RESOURCE)) {
			if (input == null) {
				throw new IOException("Missing bundled CSV import definitions " + DEFAULT_RESOURCE);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
		}
	}

	private void merge(Map<String, CsvImportDefinition> target, List<CsvImportDefinition> definitions) {
		for (CsvImportDefinition definition : definitions) {
			target.put(definition.getName(), definition);
		}
	}

	private List<CsvImportDefinition> parse(List<String> lines, String source) {
		List<CsvImportDefinition> definitions = new ArrayList<>();
		String currentName = null;
		Map<String, String> properties = new LinkedHashMap<>();

		for (int index = 0; index < lines.size(); index++) {
			String line = stripBom(lines.get(index)).trim();
			if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
				continue;
			}
			if (line.startsWith("[") && line.endsWith("]")) {
				addDefinition(definitions, currentName, properties, source);
				currentName = bookingSectionName(line);
				properties = new LinkedHashMap<>();
				continue;
			}
			if (currentName != null) {
				int separator = line.indexOf('=');
				if (separator <= 0) {
					throw invalidLine(source, index + 1);
				}
				properties.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
			}
		}
		addDefinition(definitions, currentName, properties, source);
		return List.copyOf(definitions);
	}

	private void addDefinition(List<CsvImportDefinition> definitions, String name, Map<String, String> properties, String source) {
		if (name == null) {
			return;
		}
		CsvImportDefinition definition = new CsvImportDefinition(name, properties);
		if (definition.getSourceFields(CsvImportTarget.AMOUNT).isEmpty()) {
			throw new GBankingException("CSV import definition '" + name + "' has no Betrag mapping in " + source);
		}
		definitions.add(definition);
	}

	private String bookingSectionName(String line) {
		String name = line.substring(1, line.length() - 1).trim();
		return name.regionMatches(true, 0, "Buchung:", 0, "Buchung:".length()) ? name : null;
	}

	private GBankingException invalidLine(String source, int lineNumber) {
		return new GBankingException("Invalid CSV import definition in " + source + " at line " + lineNumber);
	}

	private String stripBom(String value) {
		return value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
	}
}
