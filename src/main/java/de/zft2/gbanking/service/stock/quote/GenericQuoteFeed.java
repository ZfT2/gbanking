package de.zft2.gbanking.service.stock.quote;

import java.util.Objects;
import java.util.UUID;

public record GenericQuoteFeed(String id, String name, String urlTemplate, GenericQuoteFeedFormat format,
		String secret, String headerName, String headerValue, String itemsPath, String datePath,
		String pricePath, String dateFormat, char csvDelimiter) {

	private static final String SOURCE_CODE_PREFIX = "GENERIC_QUOTE_FEED:";

	public GenericQuoteFeed {
		id = requireId(id);
		name = normalized(name);
		urlTemplate = normalized(urlTemplate);
		format = format != null ? format : GenericQuoteFeedFormat.JSON;
		secret = normalized(secret);
		headerName = normalized(headerName);
		headerValue = normalized(headerValue);
		itemsPath = normalized(itemsPath);
		datePath = defaultValue(datePath, "date");
		pricePath = defaultValue(pricePath, "close");
		dateFormat = defaultValue(dateFormat, "uuuu-MM-dd");
		csvDelimiter = csvDelimiter != 0 ? csvDelimiter : ',';
	}

	public static GenericQuoteFeed create(String name) {
		return new GenericQuoteFeed(UUID.randomUUID().toString(), name, "", GenericQuoteFeedFormat.JSON,
				"", "", "", "", "date", "close", "uuuu-MM-dd", ',');
	}

	public boolean isConfigured() {
		return !name.isBlank() && !urlTemplate.isBlank();
	}

	public String sourceCode() {
		return SOURCE_CODE_PREFIX + id;
	}

	@Override
	public String toString() {
		return name;
	}

	private static String requireId(String value) {
		String id = Objects.requireNonNull(value, "id").trim();
		UUID.fromString(id);
		return id;
	}

	private static String normalized(String value) {
		return value != null ? value.trim() : "";
	}

	private static String defaultValue(String value, String fallback) {
		String normalized = normalized(value);
		return normalized.isEmpty() ? fallback : normalized;
	}
}
