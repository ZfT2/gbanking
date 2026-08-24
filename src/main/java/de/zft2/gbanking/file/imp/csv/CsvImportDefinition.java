package de.zft2.gbanking.file.imp.csv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CsvImportDefinition {

	private static final String EXTRA_HEADER = "extra.header";

	private final String name;
	private final Map<CsvImportTarget, List<String>> mappings;
	private final Map<String, String> options;
	private final Set<String> definedHeaders;
	private final List<String> extraHeaders;

	CsvImportDefinition(String name, Map<String, String> properties) {
		this.name = name;
		Map<CsvImportTarget, List<String>> targetMappings = new EnumMap<>(CsvImportTarget.class);
		Map<String, String> definitionOptions = new LinkedHashMap<>();
		Set<String> headers = new LinkedHashSet<>();

		for (Map.Entry<String, String> property : properties.entrySet()) {
			CsvImportTarget target = CsvImportTarget.forPropertyName(property.getKey());
			if (target != null) {
				List<String> sourceFields = splitFields(property.getValue(), false);
				targetMappings.put(target, sourceFields);
				headers.addAll(sourceFields);
			} else if (isOption(property.getKey())) {
				definitionOptions.put(normalizeKey(property.getKey()), property.getValue());
			} else {
				headers.addAll(splitFields(property.getValue(), false));
			}
		}

		this.extraHeaders = splitFields(definitionOptions.get(EXTRA_HEADER), true);
		this.extraHeaders.stream().filter(header -> !header.isBlank()).forEach(header -> {
			headers.add(header);
			CsvImportTarget target = CsvImportTarget.forPropertyName(header);
			if (target != null) {
				targetMappings.putIfAbsent(target, List.of(header));
			}
		});
		this.mappings = Collections.unmodifiableMap(targetMappings);
		this.options = Collections.unmodifiableMap(definitionOptions);
		this.definedHeaders = Collections.unmodifiableSet(headers);
	}

	public String getName() {
		return name;
	}

	public List<String> getSourceFields(CsvImportTarget target) {
		return mappings.getOrDefault(target, List.of());
	}

	public Set<String> getDefinedHeaders() {
		return definedHeaders;
	}

	public Set<String> getRequiredHeaders() {
		return Set.copyOf(getSourceFields(CsvImportTarget.AMOUNT));
	}

	public List<String> getExtraHeaders() {
		return List.copyOf(extraHeaders);
	}

	public boolean hasConfiguredHeader() {
		return !extraHeaders.isEmpty();
	}

	public char getSeparator() {
		String value = option("Extra.Separator");
		if (value == null || value.isEmpty()) {
			return ';';
		}
		return "\\t".equals(value) ? '\t' : value.charAt(0);
	}

	public String getDecimalSeparator() {
		return optionOrDefault("Format.DecimalSeparator", ",");
	}

	public String getThousandSeparator() {
		return optionOrDefault("Format.ThousandSeparator", ".");
	}

	public String getDateOrder() {
		return option("Format.DateOrder");
	}

	public String getValueDateOrder() {
		return optionOrDefault("Format.DateOrderValuta", getDateOrder());
	}

	public int getLeadingRows() {
		return nonNegativeIntOption("Ignore.LeadingRows");
	}

	public int getTrailingRows() {
		return nonNegativeIntOption("Ignore.TrailingRows");
	}

	public String getDefaultCurrency() {
		return optionOrDefault("Default.Waehrung", "EUR");
	}

	public boolean isSwapAmount() {
		return options.containsKey(normalizeKey("Extra.SwapAmount"));
	}

	public boolean isPurposeWithoutColumnNames() {
		return options.containsKey(normalizeKey("Extra.NoZweckColumnNames"));
	}

	public boolean hasAccountIdentifier(Set<String> actualHeaders) {
		for (CsvImportTarget target : CsvImportTarget.values()) {
			if (target.isAccountIdentifier() && getSourceFields(target).stream().anyMatch(actualHeaders::contains)) {
				return true;
			}
		}
		return false;
	}

	private String option(String key) {
		return options.get(normalizeKey(key));
	}

	private String optionOrDefault(String key, String defaultValue) {
		String value = option(key);
		return value == null || value.isBlank() ? defaultValue : value.trim();
	}

	private int nonNegativeIntOption(String key) {
		String value = option(key);
		if (value == null || value.isBlank()) {
			return 0;
		}
		try {
			return Math.max(0, Integer.parseInt(value.trim()));
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Invalid numeric option " + key + " in CSV import definition " + name, exception);
		}
	}

	private static boolean isOption(String key) {
		String normalized = normalizeKey(key);
		return normalized.startsWith("dialog.") || normalized.startsWith("format.") || normalized.startsWith("ignore.")
				|| normalized.startsWith("extra.") || normalized.startsWith("default.");
	}

	private static String normalizeKey(String key) {
		return key.trim().toLowerCase(Locale.ROOT);
	}

	private static List<String> splitFields(String value, boolean preserveEmptyFields) {
		if (value == null) {
			return List.of();
		}
		String[] fields = value.split(";", -1);
		List<String> result = new ArrayList<>(fields.length);
		for (String field : fields) {
			String normalized = field.trim();
			if (preserveEmptyFields || !normalized.isEmpty()) {
				result.add(normalized);
			}
		}
		return List.copyOf(result);
	}

	@Override
	public String toString() {
		return name;
	}
}
