package de.zft2.gbanking.service.stock.quote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.enu.DataType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.settings.SettingDefinition;
import de.zft2.gbanking.service.settings.SettingsStore;
import de.zft2.gbanking.util.SimpleJson;

public final class QuoteProviderSettings {

	public static final String ALPHA_VANTAGE_API_KEY = "stock.quote.alpha-vantage.api-key";
	public static final String EODHD_API_TOKEN = "stock.quote.eodhd.api-token";
	public static final String ALPACA_KEY_ID = "stock.quote.alpaca.key-id";
	public static final String ALPACA_SECRET_KEY = "stock.quote.alpaca.secret-key";
	public static final String GENERIC_FEEDS = "stock.quote.generic.feeds";

	private static final String LEGACY_URL_TEMPLATE = "stock.quote.generic.url-template";
	private static final String LEGACY_FORMAT = "stock.quote.generic.format";
	private static final String LEGACY_SECRET = "stock.quote.generic.secret";
	private static final String LEGACY_HEADER_NAME = "stock.quote.generic.header-name";
	private static final String LEGACY_HEADER_VALUE = "stock.quote.generic.header-value";
	private static final String LEGACY_ITEMS_PATH = "stock.quote.generic.items-path";
	private static final String LEGACY_DATE_PATH = "stock.quote.generic.date-path";
	private static final String LEGACY_PRICE_PATH = "stock.quote.generic.price-path";
	private static final String LEGACY_DATE_FORMAT = "stock.quote.generic.date-format";
	private static final String LEGACY_CSV_DELIMITER = "stock.quote.generic.csv-delimiter";

	private static final SettingDefinition GENERIC_FEEDS_DEFINITION = definition(GENERIC_FEEDS, "[]");
	private static final List<SettingDefinition> DEFINITIONS = List.of(
			definition(ALPHA_VANTAGE_API_KEY, ""), definition(EODHD_API_TOKEN, ""),
			definition(ALPACA_KEY_ID, ""), definition(ALPACA_SECRET_KEY, ""), GENERIC_FEEDS_DEFINITION);
	private static final List<SettingDefinition> LEGACY_DEFINITIONS = List.of(
			definition(LEGACY_URL_TEMPLATE, ""), definition(LEGACY_FORMAT, GenericQuoteFeedFormat.JSON.name()),
			definition(LEGACY_SECRET, ""), definition(LEGACY_HEADER_NAME, ""),
			definition(LEGACY_HEADER_VALUE, ""), definition(LEGACY_ITEMS_PATH, ""),
			definition(LEGACY_DATE_PATH, "date"), definition(LEGACY_PRICE_PATH, "close"),
			definition(LEGACY_DATE_FORMAT, "uuuu-MM-dd"),
			new SettingDefinition(LEGACY_CSV_DELIMITER, ",", DataType.CHAR, true, false, ""));

	private QuoteProviderSettings() {
	}

	public static void ensureSettingsExist() {
		SettingsStore settings = SettingsStore.current();
		settings.ensure(DEFINITIONS.toArray(SettingDefinition[]::new));
		settings.ensure(LEGACY_DEFINITIONS.toArray(SettingDefinition[]::new));
		migrateLegacyFeed(settings);
	}

	public static Configuration getConfiguration() {
		ensureSettingsExist();
		SettingsStore settings = SettingsStore.current();
		return new Configuration(value(settings, ALPHA_VANTAGE_API_KEY), value(settings, EODHD_API_TOKEN),
				value(settings, ALPACA_KEY_ID), value(settings, ALPACA_SECRET_KEY), readGenericFeeds(settings));
	}

	public static void saveGenericFeeds(List<GenericQuoteFeed> feeds) {
		saveGenericFeeds(new SettingsStore(DBController.getInstance(".")), feeds);
	}

	static void saveGenericFeeds(SettingsStore settings, List<GenericQuoteFeed> feeds) {
		settings.save(GENERIC_FEEDS_DEFINITION, serializeGenericFeeds(feeds));
	}

	public static String serializeGenericFeeds(List<GenericQuoteFeed> feeds) {
		return SimpleJson.write(feeds.stream().map(QuoteProviderSettings::serializeFeed).toList());
	}

	static List<GenericQuoteFeed> deserializeGenericFeeds(String json) {
		try {
			Object parsed = SimpleJson.parse(json != null && !json.isBlank() ? json : "[]");
			if (!(parsed instanceof List<?> values)) {
				throw new IllegalArgumentException("feed list expected");
			}
			List<GenericQuoteFeed> result = new ArrayList<>();
			for (Object value : values) {
				result.add(deserializeFeed(value));
			}
			return List.copyOf(result);
		} catch (IllegalArgumentException exception) {
			throw new GBankingException("Die Konfiguration der generischen Kursfeeds ist ungültig", exception);
		}
	}

	private static SettingDefinition definition(String attribute, String defaultValue) {
		return new SettingDefinition(attribute, defaultValue, DataType.STRING, true, false, "");
	}

	private static String value(SettingsStore settings, String attribute) {
		String value = settings.getString(attribute, "");
		return value != null ? value.trim() : "";
	}

	private static List<GenericQuoteFeed> readGenericFeeds(SettingsStore settings) {
		return deserializeGenericFeeds(settings.getString(GENERIC_FEEDS, "[]"));
	}

	private static void migrateLegacyFeed(SettingsStore settings) {
		String url = value(settings, LEGACY_URL_TEMPLATE);
		if (url.isBlank()) {
			return;
		}
		List<GenericQuoteFeed> feeds = new ArrayList<>(readGenericFeeds(settings));
		feeds.add(new GenericQuoteFeed(UUID.randomUUID().toString(), "Generischer Kursfeed", url,
				enumValue(settings, LEGACY_FORMAT), value(settings, LEGACY_SECRET), value(settings, LEGACY_HEADER_NAME),
				value(settings, LEGACY_HEADER_VALUE), value(settings, LEGACY_ITEMS_PATH),
				value(settings, LEGACY_DATE_PATH), value(settings, LEGACY_PRICE_PATH),
				value(settings, LEGACY_DATE_FORMAT), delimiter(settings, LEGACY_CSV_DELIMITER)));
		saveGenericFeeds(settings, feeds);
		settings.save(LEGACY_DEFINITIONS.get(0), "");
	}

	private static GenericQuoteFeedFormat enumValue(SettingsStore settings, String attribute) {
		try {
			return GenericQuoteFeedFormat.valueOf(value(settings, attribute).toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return GenericQuoteFeedFormat.JSON;
		}
	}

	private static char delimiter(SettingsStore settings, String attribute) {
		String value = value(settings, attribute);
		return value.length() == 1 ? value.charAt(0) : ',';
	}

	private static Map<String, Object> serializeFeed(GenericQuoteFeed feed) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("id", feed.id());
		value.put("name", feed.name());
		value.put("urlTemplate", feed.urlTemplate());
		value.put("format", feed.format().name());
		value.put("secret", feed.secret());
		value.put("headerName", feed.headerName());
		value.put("headerValue", feed.headerValue());
		value.put("itemsPath", feed.itemsPath());
		value.put("datePath", feed.datePath());
		value.put("pricePath", feed.pricePath());
		value.put("dateFormat", feed.dateFormat());
		value.put("csvDelimiter", Character.toString(feed.csvDelimiter()));
		return value;
	}

	private static GenericQuoteFeed deserializeFeed(Object value) {
		if (!(value instanceof Map<?, ?> map)) {
			throw new IllegalArgumentException("feed object expected");
		}
		String csvDelimiter = string(map, "csvDelimiter", ",");
		return new GenericQuoteFeed(string(map, "id", ""), string(map, "name", ""),
				string(map, "urlTemplate", ""), format(map), string(map, "secret", ""),
				string(map, "headerName", ""), string(map, "headerValue", ""),
				string(map, "itemsPath", ""), string(map, "datePath", "date"),
				string(map, "pricePath", "close"), string(map, "dateFormat", "uuuu-MM-dd"),
				csvDelimiter.length() == 1 ? csvDelimiter.charAt(0) : ',');
	}

	private static GenericQuoteFeedFormat format(Map<?, ?> map) {
		try {
			return GenericQuoteFeedFormat.valueOf(string(map, "format", "JSON").toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return GenericQuoteFeedFormat.JSON;
		}
	}

	private static String string(Map<?, ?> map, String key, String defaultValue) {
		Object value = map.get(key);
		return value != null ? value.toString() : defaultValue;
	}

	public record Configuration(String alphaVantageApiKey, String eodhdApiToken,
			String alpacaKeyId, String alpacaSecretKey, List<GenericQuoteFeed> genericFeeds) {

		public Configuration {
			genericFeeds = List.copyOf(genericFeeds);
		}

		public boolean isConfigured(QuoteProvider provider) {
			return switch (provider) {
			case ALPHA_VANTAGE -> !alphaVantageApiKey.isBlank();
			case EODHD -> !eodhdApiToken.isBlank();
			case ALPACA -> !alpacaKeyId.isBlank() && !alpacaSecretKey.isBlank();
			case GENERIC -> genericFeeds.stream().anyMatch(GenericQuoteFeed::isConfigured);
			};
		}

		@Override
		public String toString() {
			return "Configuration[credentials redacted, genericFeeds=" + genericFeeds.size() + "]";
		}
	}
}
