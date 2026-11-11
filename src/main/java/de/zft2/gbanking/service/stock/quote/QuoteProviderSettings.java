package de.zft2.gbanking.service.stock.quote;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.enu.DataType;
import de.zft2.gbanking.db.dao.enu.StockDataSourceType;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockQuoteFeed;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.settings.SettingDefinition;
import de.zft2.gbanking.service.settings.SettingsStore;
import de.zft2.gbanking.service.stock.StockSources;

public final class QuoteProviderSettings {

	public static final String ALPHA_VANTAGE_API_KEY = "stock.quote.alpha-vantage.api-key";
	public static final String EODHD_API_TOKEN = "stock.quote.eodhd.api-token";
	public static final String ALPACA_KEY_ID = "stock.quote.alpaca.key-id";
	public static final String ALPACA_SECRET_KEY = "stock.quote.alpaca.secret-key";
	static final int MARKET_DATA_PRIORITY = 50;

	private static final List<SettingDefinition> DEFINITIONS = List.of(
			definition(ALPHA_VANTAGE_API_KEY, ""), definition(EODHD_API_TOKEN, ""),
			definition(ALPACA_KEY_ID, ""), definition(ALPACA_SECRET_KEY, ""));

	private QuoteProviderSettings() {
	}

	public static void ensureSettingsExist() {
		SettingsStore settings = SettingsStore.current();
		settings.ensure(DEFINITIONS.toArray(SettingDefinition[]::new));
	}

	public static Configuration getConfiguration() {
		ensureSettingsExist();
		SettingsStore settings = SettingsStore.current();
		return new Configuration(value(settings, ALPHA_VANTAGE_API_KEY), value(settings, EODHD_API_TOKEN),
				value(settings, ALPACA_KEY_ID), value(settings, ALPACA_SECRET_KEY), readGenericFeeds());
	}

	public static void saveGenericFeeds(List<GenericQuoteFeed> feeds) {
		List<GenericQuoteFeed> requested = List.copyOf(feeds);
		DBController db = DBController.getInstance(".");
		db.executeInTransaction(() -> {
			Map<String, StockQuoteFeed> stored = db.getAll(StockQuoteFeed.class).stream()
					.collect(Collectors.toMap(feed -> feed.getUuid(), feed -> feed));
			Set<String> savedIds = new HashSet<>();
			for (int index = 0; index < requested.size(); index++) {
				GenericQuoteFeed feed = requested.get(index);
				if (!savedIds.add(feed.id())) {
					throw new GBankingException("Ein Kursfeed darf nur einmal gespeichert werden.");
				}
				saveGenericFeed(db, stored.remove(feed.id()), feed, index);
			}
			for (StockQuoteFeed removed : stored.values()) {
				db.delete(removed, null);
			}
		});
	}

	static StockQuoteFeed requireGenericFeed(String uuid) {
		return DBController.getInstance(".").getAll(StockQuoteFeed.class).stream()
				.filter(feed -> feed.getUuid().equals(uuid)).findFirst()
				.orElseThrow(() -> new GBankingException("Der generische Kursfeed wurde nicht gefunden"));
	}

	private static void saveGenericFeed(DBController db, StockQuoteFeed stored, GenericQuoteFeed feed, int sortOrder) {
		StockDataSource source;
		if (stored == null) {
			source = StockSources.createDataSource(db, feed.sourceCode(), feed.name(),
					StockDataSourceType.MARKET_DATA, MARKET_DATA_PRIORITY);
			stored = new StockQuoteFeed();
			stored.setSourceId(source.getId());
			stored.setUuid(feed.id());
		} else {
			source = db.getById(StockDataSource.class, stored.getSourceId());
			if (!feed.name().equals(source.getSourceName())) {
				source.setSourceName(feed.name());
				db.insertOrUpdate(source);
			}
		}
		stored.setSortOrder(sortOrder);
		stored.setUrlTemplate(feed.urlTemplate());
		stored.setFormat(feed.format().name());
		stored.setSecret(feed.secret());
		stored.setHeaderName(feed.headerName());
		stored.setHeaderValue(feed.headerValue());
		stored.setItemsPath(feed.itemsPath());
		stored.setDatePath(feed.datePath());
		stored.setPricePath(feed.pricePath());
		stored.setDateFormat(feed.dateFormat());
		stored.setCsvDelimiter(Character.toString(feed.csvDelimiter()));
		db.insertOrUpdate(stored);
	}

	private static SettingDefinition definition(String attribute, String defaultValue) {
		return new SettingDefinition(attribute, defaultValue, DataType.STRING, true, false, "");
	}

	private static String value(SettingsStore settings, String attribute) {
		String value = settings.getString(attribute, "");
		return value != null ? value.trim() : "";
	}

	private static List<GenericQuoteFeed> readGenericFeeds() {
		DBController db = DBController.getInstance(".");
		return db.executeInTransaction(() -> {
			Map<Integer, String> sourceNames = db.getAll(StockDataSource.class).stream()
					.collect(Collectors.toMap(source -> source.getId(), source -> source.getSourceName()));
			return db.getAll(StockQuoteFeed.class).stream()
					.sorted(Comparator.comparingInt(feed -> feed.getSortOrder()))
					.map(feed -> toGenericFeed(feed, Objects.requireNonNull(sourceNames.get(feed.getSourceId()),
							"Missing quote feed data source")))
					.toList();
		});
	}

	private static GenericQuoteFeed toGenericFeed(StockQuoteFeed feed, String name) {
		return new GenericQuoteFeed(feed.getUuid(), name, feed.getUrlTemplate(),
				GenericQuoteFeedFormat.valueOf(feed.getFormat()), feed.getSecret(), feed.getHeaderName(),
				feed.getHeaderValue(), feed.getItemsPath(), feed.getDatePath(), feed.getPricePath(),
				feed.getDateFormat(), feed.getCsvDelimiter().charAt(0));
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
