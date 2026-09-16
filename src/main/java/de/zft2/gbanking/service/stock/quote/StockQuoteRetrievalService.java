package de.zft2.gbanking.service.stock.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockDataSourceType;
import de.zft2.gbanking.db.dao.enu.StockIdentifierType;
import de.zft2.gbanking.db.dao.enu.StockPriceBasis;
import de.zft2.gbanking.db.dao.enu.StockPriceType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.db.dao.stock.StockSecurityIdentifier;
import de.zft2.gbanking.db.dao.stock.StockSecurityPrice;
import de.zft2.gbanking.db.dao.stock.StockSecurityPriceSource;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.stock.quote.MarketDataClient.MarketQuote;
import de.zft2.gbanking.service.stock.quote.MarketDataClient.QuoteRequest;
import de.zft2.gbanking.service.stock.quote.QuoteProviderSettings.Configuration;

public class StockQuoteRetrievalService extends AbstractDbService {

	private static final int PRICE_SCALE = 8;
	private static final int MARKET_DATA_PRIORITY = 50;

	private final MarketDataClient client;

	public StockQuoteRetrievalService() {
		this(new MarketDataClient());
	}

	StockQuoteRetrievalService(MarketDataClient client) {
		this.client = Objects.requireNonNull(client, "client");
	}

	public List<QuoteSource> getConfiguredSources() {
		Configuration configuration = QuoteProviderSettings.getConfiguration();
		List<QuoteSource> sources = new java.util.ArrayList<>(Arrays.stream(QuoteProvider.values())
				.filter(provider -> provider != QuoteProvider.GENERIC).filter(configuration::isConfigured)
				.map(QuoteSource::of).toList());
		configuration.genericFeeds().stream().filter(GenericQuoteFeed::isConfigured).map(QuoteSource::of)
				.forEach(sources::add);
		return List.copyOf(sources);
	}

	public String getSuggestedSymbol(int securityId, QuoteSource quoteSource) {
		StockDataSource source = findSource(quoteSource);
		if (source != null) {
			String configuredSymbol = dbController.getAllByParent(StockSecurityPriceSource.class, securityId).stream()
					.filter(priceSource -> priceSource.getSourceId() == source.getId() && priceSource.isEnabled())
					.sorted(Comparator.comparingInt(StockSecurityPriceSource::getId).reversed())
					.map(StockSecurityPriceSource::getProviderSymbol).filter(Objects::nonNull)
					.filter(symbol -> !symbol.isBlank()).findFirst().orElse(null);
			if (configuredSymbol != null) {
				return configuredSymbol;
			}
		}
		return identifiers(securityId).ticker();
	}

	public RetrievalResult retrieve(int securityId, QuoteSource quoteSource, String requestedSymbol) {
		if (quoteSource == null) {
			throw new GBankingException("Bitte einen Kursanbieter auswählen");
		}
		String symbol = requestedSymbol != null ? requestedSymbol.trim() : "";
		if (symbol.isBlank()) {
			throw new GBankingException("Bitte das beim Kursanbieter verwendete Symbol angeben");
		}

		StockSecurity security = dbController.getById(StockSecurity.class, securityId);
		if (security == null) {
			throw new GBankingException("Das Wertpapier wurde nicht gefunden");
		}
		Currency currency = security.getDefaultQuoteCurrency();
		if (currency == null) {
			throw new GBankingException("Für das Wertpapier ist keine Kurswährung hinterlegt");
		}

		Configuration configuration = QuoteProviderSettings.getConfiguration();
		validateConfiguration(quoteSource, configuration);
		Identifiers identifiers = identifiers(securityId);
		QuoteRequest request = new QuoteRequest(symbol, identifiers.isin(), identifiers.wkn(),
				identifiers.marketIdentifierCode());
		List<MarketQuote> quotes = client.fetch(quoteSource, request, configuration);
		if (quotes.isEmpty()) {
			throw new GBankingException("Der Kursanbieter lieferte für " + symbol + " keine Kursdaten");
		}

		return dbController.executeInTransaction(() -> saveQuotes(security, quoteSource, symbol, currency, quotes));
	}

	public FeedDeletionResult deleteGenericFeed(String feedId, boolean deletePrices) {
		Configuration configuration = QuoteProviderSettings.getConfiguration();
		GenericQuoteFeed feed = configuration.genericFeeds().stream().filter(candidate -> candidate.id().equals(feedId))
				.findFirst().orElseThrow(() -> new GBankingException("Der generische Kursfeed wurde nicht gefunden"));
		List<GenericQuoteFeed> remainingFeeds = configuration.genericFeeds().stream()
				.filter(candidate -> !candidate.id().equals(feedId)).toList();
		return dbController.executeInTransaction(() -> {
			int deletedPrices = deletePrices ? deleteActivePrices(feed) : 0;
			QuoteProviderSettings.saveGenericFeeds(remainingFeeds);
			return new FeedDeletionResult(deletedPrices);
		});
	}

	private int deleteActivePrices(GenericQuoteFeed feed) {
		StockDataSource source = findSource(QuoteSource.of(feed));
		if (source == null) {
			return 0;
		}
		int deleted = 0;
		for (StockSecurityPriceSource priceSource : dbController.getAll(StockSecurityPriceSource.class)) {
			if (priceSource.getSourceId() == source.getId()) {
				deleted += deleteActivePrices(priceSource);
			}
		}
		return deleted;
	}

	private int deleteActivePrices(StockSecurityPriceSource priceSource) {
		List<StockSecurityPrice> prices = dbController.getAllByParent(StockSecurityPrice.class, priceSource.getId());
		Set<Integer> supersededIds = prices.stream().map(StockSecurityPrice::getSupersedesPriceId)
				.filter(Objects::nonNull).collect(Collectors.toSet());
		List<StockSecurityPrice> activePrices = prices.stream()
				.filter(price -> !price.isDeleted() && !supersededIds.contains(price.getId())).toList();
		for (StockSecurityPrice price : activePrices) {
			dbController.insertOrUpdate(deletion(price));
		}
		return activePrices.size();
	}

	private static StockSecurityPrice deletion(StockSecurityPrice price) {
		StockSecurityPrice deletion = new StockSecurityPrice();
		deletion.setPriceSourceId(price.getPriceSourceId());
		deletion.setQuotedAt(price.getQuotedAt());
		deletion.setPriceE8(price.getPriceE8());
		deletion.setQuoteCurrency(price.getQuoteCurrency());
		deletion.setQuotationType(price.getQuotationType());
		deletion.setPriceBasis(price.getPriceBasis());
		deletion.setPriceType(price.getPriceType());
		deletion.setVolumeE9(price.getVolumeE9());
		deletion.setExternalReference(price.getExternalReference());
		deletion.setDeleted(true);
		deletion.setSupersedesPriceId(price.getId());
		return deletion;
	}

	private RetrievalResult saveQuotes(StockSecurity security, QuoteSource quoteSource, String symbol,
			Currency currency, List<MarketQuote> quotes) {
		StockDataSource source = getOrCreateSource(quoteSource);
		StockSecurityPriceSource priceSource = getOrCreatePriceSource(security.getId(), source, symbol);
		Map<LocalDate, StockSecurityPrice> currentPrices = currentPrices(priceSource.getId());
		Map<LocalDate, MarketQuote> quotesByDate = quotes.stream().collect(Collectors.toMap(MarketQuote::date,
				Function.identity(), (previous, replacement) -> replacement, LinkedHashMap::new));

		int inserted = 0;
		int corrected = 0;
		int unchanged = 0;
		for (MarketQuote quote : quotesByDate.values()) {
			if (quote.date().isAfter(LocalDate.now())) {
				throw new GBankingException("Der Kursanbieter lieferte ein Datum in der Zukunft: " + quote.date());
			}
			long priceE8 = toPriceE8(quote.price());
			StockSecurityPrice current = currentPrices.get(quote.date());
			if (samePrice(current, priceE8, currency)) {
				unchanged++;
				continue;
			}
			StockSecurityPrice price = createPrice(security, priceSource, quoteSource, symbol, quote, priceE8,
					currency, current);
			dbController.insertOrUpdate(price);
			if (current == null) {
				inserted++;
			} else {
				corrected++;
			}
		}
		return new RetrievalResult(quotesByDate.size(), inserted, corrected, unchanged);
	}

	private StockSecurityPrice createPrice(StockSecurity security, StockSecurityPriceSource priceSource,
			QuoteSource quoteSource, String symbol, MarketQuote quote, long priceE8, Currency currency,
			StockSecurityPrice current) {
		StockQuotationType quotationType = security.getDefaultQuotationType() != null
				? security.getDefaultQuotationType() : StockQuotationType.ABSOLUTE;
		StockSecurityPrice price = new StockSecurityPrice();
		price.setPriceSourceId(priceSource.getId());
		price.setQuotedAt(quote.date().atStartOfDay());
		price.setPriceE8(priceE8);
		price.setQuoteCurrency(currency);
		price.setQuotationType(quotationType);
		price.setPriceBasis(quotationType == StockQuotationType.PERCENT_OF_NOMINAL
				? defaultPriceBasis(security) : null);
		price.setPriceType(StockPriceType.CLOSE);
		price.setExternalReference(quoteSource.sourceCode() + ":" + symbol + ":" + quote.date());
		price.setSupersedesPriceId(current != null ? current.getId() : null);
		return price;
	}

	private static StockPriceBasis defaultPriceBasis(StockSecurity security) {
		return security.getDefaultPriceBasis() != null ? security.getDefaultPriceBasis() : StockPriceBasis.CLEAN;
	}

	private Map<LocalDate, StockSecurityPrice> currentPrices(int priceSourceId) {
		List<StockSecurityPrice> prices = dbController.getAllByParent(StockSecurityPrice.class, priceSourceId);
		Set<Integer> supersededIds = prices.stream().map(StockSecurityPrice::getSupersedesPriceId)
				.filter(Objects::nonNull).collect(Collectors.toSet());
		return prices.stream().filter(price -> !price.isDeleted() && !supersededIds.contains(price.getId()))
				.collect(Collectors.toMap(price -> price.getQuotedAt().toLocalDate(), Function.identity(),
						(first, second) -> first.getId() > second.getId() ? first : second));
	}

	private StockDataSource getOrCreateSource(QuoteSource quoteSource) {
		StockDataSource source = findSource(quoteSource);
		if (source != null) {
			if (!source.getSourceName().equals(quoteSource.toString())) {
				source.setSourceName(quoteSource.toString());
				dbController.insertOrUpdate(source);
			}
			return source;
		}
		StockDataSource created = new StockDataSource();
		created.setSourceCode(quoteSource.sourceCode());
		created.setSourceName(quoteSource.toString());
		created.setSourceType(StockDataSourceType.MARKET_DATA);
		created.setDefaultPriority(MARKET_DATA_PRIORITY);
		return dbController.insertOrUpdate(created);
	}

	private StockDataSource findSource(QuoteSource quoteSource) {
		if (quoteSource == null) {
			return null;
		}
		return dbController.getAll(StockDataSource.class).stream()
				.filter(source -> quoteSource.sourceCode().equalsIgnoreCase(source.getSourceCode()))
				.findFirst().orElse(null);
	}

	private StockSecurityPriceSource getOrCreatePriceSource(int securityId, StockDataSource source, String symbol) {
		List<StockSecurityPriceSource> providerSources = dbController
				.getAllByParent(StockSecurityPriceSource.class, securityId).stream()
				.filter(priceSource -> priceSource.getSourceId() == source.getId()).toList();
		StockSecurityPriceSource selected = providerSources.stream()
				.filter(priceSource -> symbol.equalsIgnoreCase(priceSource.getProviderSymbol()))
				.findFirst().orElse(null);
		for (StockSecurityPriceSource priceSource : providerSources) {
			boolean enabled = priceSource == selected;
			if (priceSource.isEnabled() != enabled) {
				priceSource.setEnabled(enabled);
				dbController.insertOrUpdate(priceSource);
			}
		}
		if (selected != null) {
			return selected;
		}
		StockSecurityPriceSource created = new StockSecurityPriceSource();
		created.setSecurityId(securityId);
		created.setSourceId(source.getId());
		created.setProviderSymbol(symbol);
		created.setPriority(MARKET_DATA_PRIORITY);
		return dbController.insertOrUpdate(created);
	}

	private Identifiers identifiers(int securityId) {
		Map<StockIdentifierType, StockSecurityIdentifier> identifiers = dbController
				.getAllByParent(StockSecurityIdentifier.class, securityId).stream()
				.filter(identifier -> identifier.getValidTo() == null)
				.filter(identifier -> identifier.getValidFrom() == null
						|| !identifier.getValidFrom().isAfter(LocalDate.now()))
				.collect(Collectors.toMap(StockSecurityIdentifier::getIdentifierType, Function.identity(),
						(first, ignored) -> first));
		StockSecurityIdentifier ticker = identifiers.get(StockIdentifierType.TICKER);
		return new Identifiers(identifierValue(identifiers, StockIdentifierType.ISIN),
				identifierValue(identifiers, StockIdentifierType.WKN),
				ticker != null ? value(ticker.getIdentifierValue()) : "",
				ticker != null ? value(ticker.getMarketIdentifierCode()) : "");
	}

	private static String identifierValue(Map<StockIdentifierType, StockSecurityIdentifier> identifiers,
			StockIdentifierType type) {
		StockSecurityIdentifier identifier = identifiers.get(type);
		return identifier != null ? value(identifier.getIdentifierValue()) : "";
	}

	private static String value(String value) {
		return value != null ? value : "";
	}

	private static void validateConfiguration(QuoteSource source, Configuration configuration) {
		if (source.provider() == QuoteProvider.GENERIC && source.genericFeed().isConfigured()
				&& configuration.genericFeeds().stream().anyMatch(source.genericFeed()::equals)) {
			return;
		}
		if (source.provider() != QuoteProvider.GENERIC && configuration.isConfigured(source.provider())) {
			return;
		}
		String detail = source.provider() == QuoteProvider.ALPACA ? "Key ID und Secret Key" : "API-Schlüssel bzw. URL";
		throw new GBankingException("Für " + source + " sind " + detail
				+ " noch nicht vollständig in den Einstellungen hinterlegt");
	}

	private static boolean samePrice(StockSecurityPrice price, long priceE8, Currency currency) {
		return price != null && price.getPriceE8() == priceE8 && price.getQuoteCurrency() == currency
				&& price.getPriceType() == StockPriceType.CLOSE;
	}

	private static long toPriceE8(BigDecimal value) {
		try {
			return value.setScale(PRICE_SCALE, RoundingMode.HALF_UP).movePointRight(PRICE_SCALE).longValueExact();
		} catch (ArithmeticException exception) {
			throw new GBankingException("Der abgerufene Kurs ist zu groß", exception);
		}
	}

	public record RetrievalResult(int received, int inserted, int corrected, int unchanged) {
	}

	public record FeedDeletionResult(int deletedPrices) {
	}

	private record Identifiers(String isin, String wkn, String ticker, String marketIdentifierCode) {
	}
}
