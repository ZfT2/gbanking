package de.zft2.gbanking.service.stock.quote;

import static de.zft2.gbanking.testdata.TestDataFactory.createSecurityIdentifier;
import static de.zft2.gbanking.testdata.TestDataFactory.createSecurityPrice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.TestDatabaseExtension;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockDataSourceType;
import de.zft2.gbanking.db.dao.enu.StockIdentifierType;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockQuoteFeed;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.db.dao.stock.StockSecurityPrice;
import de.zft2.gbanking.db.dao.stock.StockSecurityPriceSource;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.quote.MarketDataClient.MarketQuote;
import de.zft2.gbanking.service.stock.quote.MarketDataClient.QuoteRequest;
import de.zft2.gbanking.service.stock.quote.StockQuoteRetrievalService.RetrievalResult;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockQuoteRetrievalServiceTest {

	@RegisterExtension
	static final TestDatabaseExtension database = new TestDatabaseExtension();

	private final DBController db = database.controller();

	@BeforeEach
	void initQuoteSettings() {
		QuoteProviderSettings.ensureSettingsExist();
		setSetting(QuoteProviderSettings.ALPHA_VANTAGE_API_KEY, "tenant-api-key");
	}

	@ParameterizedTest
	@CsvSource({ "1,", "-2,-1", "-2,1" })
	void identifierSelectionShouldKeepTheFirstStartedOpenEndedTickerAndItsMarket(int fromDays, Integer toDays) {
		int securityId = createSecurity().getId();
		LocalDate today = LocalDate.now(ZoneId.systemDefault());
		db.insertOrUpdate(createSecurityIdentifier(securityId, StockIdentifierType.TICKER, "Excluded",
				today.plusDays(fromDays), toDays != null ? today.plusDays(toDays) : null));
		var first = createSecurityIdentifier(securityId, StockIdentifierType.TICKER, "First", today, null);
		first.setMarketIdentifierCode("XETR");
		db.insertOrUpdate(first);
		var last = createSecurityIdentifier(securityId, StockIdentifierType.TICKER, "Last", null, null);
		last.setMarketIdentifierCode("XNYS");
		db.insertOrUpdate(last);
		MarketDataClient client = mock(MarketDataClient.class);
		when(client.fetch(any(), any(), any())).thenReturn(List.of(new MarketQuote(today.minusDays(1), BigDecimal.TEN)));
		StockQuoteRetrievalService service = new StockQuoteRetrievalService(client);
		QuoteSource source = QuoteSource.of(QuoteProvider.ALPHA_VANTAGE);

		assertEquals("First", service.getSuggestedSymbol(securityId, source));
		service.retrieve(securityId, source, "Requested");

		ArgumentCaptor<QuoteRequest> request = ArgumentCaptor.forClass(QuoteRequest.class);
		verify(client).fetch(eq(source), request.capture(), any());
		assertEquals(new QuoteRequest("Requested", "", "", "XETR"), request.getValue());
	}

	@Test
	void shouldInsertSkipAndCorrectProviderPrices() {
		StockSecurity security = createSecurity();
		MarketDataClient client = mock(MarketDataClient.class);
		QuoteSource source = QuoteSource.of(QuoteProvider.ALPHA_VANTAGE);
		LocalDate firstDate = LocalDate.now().minusDays(2);
		LocalDate secondDate = LocalDate.now().minusDays(1);
		when(client.fetch(eq(source), any(), any()))
				.thenReturn(List.of(new MarketQuote(firstDate, new BigDecimal("10.00")),
						new MarketQuote(secondDate, new BigDecimal("11.00"))))
				.thenReturn(List.of(new MarketQuote(firstDate, new BigDecimal("10.00")),
						new MarketQuote(secondDate, new BigDecimal("11.50"))));
		StockQuoteRetrievalService service = new StockQuoteRetrievalService(client);

		RetrievalResult first = service.retrieve(security.getId(), source, "TEST.DE");
		RetrievalResult second = service.retrieve(security.getId(), source, "TEST.DE");

		assertEquals(new RetrievalResult(2, 2, 0, 0), first);
		assertEquals(new RetrievalResult(2, 0, 1, 1), second);
		assertEquals(3, db.getAll(StockSecurityPrice.class).size());
		assertEquals(new BigDecimal("11.5"), new StockPortfolioService().getPrices(security.getId()).get(0).price());
		StockDataSource dataSource = db.getAll(StockDataSource.class).get(0);
		assertEquals(StockDataSourceType.MARKET_DATA, dataSource.getSourceType());
		assertEquals("TEST.DE", db.getAll(StockSecurityPriceSource.class).get(0).getProviderSymbol());
	}

	@Test
	void currentPricesShouldChooseTheHighestIdWithinTheProviderDay() {
		StockSecurity security = createSecurity();
		MarketDataClient client = mock(MarketDataClient.class);
		LocalDate date = LocalDate.of(2024, Month.JANUARY, 15);
		when(client.fetch(any(), any(), any()))
				.thenReturn(List.of(new MarketQuote(date, BigDecimal.TEN)))
				.thenReturn(List.of(new MarketQuote(date, new BigDecimal("12"))));
		StockQuoteRetrievalService service = new StockQuoteRetrievalService(client);
		QuoteSource source = QuoteSource.of(QuoteProvider.ALPHA_VANTAGE);
		service.retrieve(security.getId(), source, "TEST");
		int priceSourceId = db.getAll(StockSecurityPriceSource.class).get(0).getId();
		db.insertOrUpdate(createSecurityPrice(priceSourceId, date.atTime(18, 0), 1_100_000_000L));
		db.insertOrUpdate(createSecurityPrice(priceSourceId, date.atTime(8, 0), 1_200_000_000L));
		new StockPortfolioService().savePrice(security.getId(), null, date, new BigDecimal("13"), Currency.EUR);

		assertEquals(new RetrievalResult(1, 0, 0, 1), service.retrieve(security.getId(), source, "TEST"));
	}

	@Test
	void marketDataPricesShouldRequireConfirmationForManualChanges() {
		StockSecurity security = createSecurity();
		MarketDataClient client = mock(MarketDataClient.class);
		QuoteSource source = QuoteSource.of(QuoteProvider.ALPHA_VANTAGE);
		when(client.fetch(eq(source), any(), any())).thenReturn(
				List.of(new MarketQuote(LocalDate.now().minusDays(1), new BigDecimal("10.00"))));
		new StockQuoteRetrievalService(client).retrieve(security.getId(), source, "TEST");
		int priceId = new StockPortfolioService().getPrices(security.getId()).get(0).priceId();

		StockPortfolioService portfolios = new StockPortfolioService();
		int securityId = security.getId();
		LocalDate today = LocalDate.now();
		BigDecimal replacement = new BigDecimal("12.00");
		assertThrows(GBankingException.class, () -> portfolios.savePrice(securityId, priceId,
				today, replacement, Currency.EUR));
	}

	@Test
	void shouldKeepPricesWhenDeletingGenericFeedWithoutPriceDeletion() {
		GenericQuoteFeed feed = genericFeed("First feed");
		QuoteProviderSettings.saveGenericFeeds(List.of(feed, genericFeed("Second feed")));
		StockSecurity security = retrieveGenericPrice(feed);
		int sourceId = QuoteProviderSettings.requireGenericFeed(feed.id()).getSourceId();

		StockQuoteRetrievalService service = new StockQuoteRetrievalService(mock(MarketDataClient.class));
		assertEquals(0, service.deleteGenericFeed(feed.id(), false).deletedPrices());

		assertEquals(1, QuoteProviderSettings.getConfiguration().genericFeeds().size());
		assertEquals("Second feed", QuoteProviderSettings.getConfiguration().genericFeeds().get(0).name());
		assertEquals(1, new StockPortfolioService().getPrices(security.getId()).size());
		assertNotNull(db.getById(StockDataSource.class, sourceId));
		assertEquals(1, db.getAll(StockQuoteFeed.class).size());
	}

	@Test
	void shouldDeleteActivePricesWhenDeletingGenericFeedWithPriceDeletion() {
		GenericQuoteFeed feed = genericFeed("Disposable feed");
		QuoteProviderSettings.saveGenericFeeds(List.of(feed));
		StockSecurity security = retrieveGenericPrice(feed);
		int sourceId = QuoteProviderSettings.requireGenericFeed(feed.id()).getSourceId();
		StockDataSource dataSource = db.getById(StockDataSource.class, sourceId);
		dataSource.setSourceCode("RENAMED_SOURCE_CODE");
		db.insertOrUpdate(dataSource);

		StockQuoteRetrievalService service = new StockQuoteRetrievalService(mock(MarketDataClient.class));
		assertEquals(1, service.deleteGenericFeed(feed.id(), true).deletedPrices());

		assertEquals(List.of(), QuoteProviderSettings.getConfiguration().genericFeeds());
		assertEquals(List.of(), new StockPortfolioService().getPrices(security.getId()));
		assertEquals(2, db.getAll(StockSecurityPrice.class).size());
		assertNotNull(db.getById(StockDataSource.class, sourceId));
		assertTrue(db.getAll(StockQuoteFeed.class).isEmpty());
	}

	@Test
	void shouldUseTheLinkedSourceAndItsNameInsteadOfDerivingTheSourceFromTheFeedId() {
		GenericQuoteFeed feed = genericFeed("Original name");
		QuoteProviderSettings.saveGenericFeeds(List.of(feed));
		StockSecurity security = retrieveGenericPrice(feed);
		int sourceId = QuoteProviderSettings.requireGenericFeed(feed.id()).getSourceId();
		StockDataSource dataSource = db.getById(StockDataSource.class, sourceId);
		dataSource.setSourceCode("INDEPENDENT_SOURCE_CODE");
		dataSource.setSourceName("Canonical name");
		db.insertOrUpdate(dataSource);
		GenericQuoteFeed loaded = QuoteProviderSettings.getConfiguration().genericFeeds().get(0);
		QuoteSource source = QuoteSource.of(loaded);
		MarketDataClient client = mock(MarketDataClient.class);
		when(client.fetch(eq(source), any(), any())).thenReturn(
				List.of(new MarketQuote(LocalDate.now().minusDays(1), new BigDecimal("11.00"))));
		StockQuoteRetrievalService service = new StockQuoteRetrievalService(client);

		assertEquals("Canonical name", loaded.name());
		assertEquals("TEST", service.getSuggestedSymbol(security.getId(), source));
		assertEquals(new RetrievalResult(1, 0, 1, 0), service.retrieve(security.getId(), source, "TEST"));
		assertEquals(1, db.getAll(StockDataSource.class).size());
		assertEquals(sourceId, db.getAll(StockSecurityPriceSource.class).get(0).getSourceId());
		assertEquals("Canonical name", new StockPortfolioService().getPrices(security.getId()).get(0).sourceName());
	}

	@Test
	void shouldNotRecreateAFeedDeletedWhilePricesAreBeingFetched() {
		GenericQuoteFeed feed = genericFeed("Removed during retrieval");
		QuoteProviderSettings.saveGenericFeeds(List.of(feed));
		StockSecurity security = createSecurity();
		QuoteSource source = QuoteSource.of(feed);
		MarketDataClient client = mock(MarketDataClient.class);
		StockQuoteRetrievalService service = new StockQuoteRetrievalService(client);
		when(client.fetch(eq(source), any(), any())).thenAnswer(invocation -> {
			service.deleteGenericFeed(feed.id(), false);
			return List.of(new MarketQuote(LocalDate.now().minusDays(1), BigDecimal.TEN));
		});

		int securityId = security.getId();
		assertThrows(GBankingException.class, () -> service.retrieve(securityId, source, "TEST"));

		assertTrue(db.getAll(StockQuoteFeed.class).isEmpty());
		assertTrue(db.getAll(StockSecurityPrice.class).isEmpty());
		assertTrue(db.getAll(StockSecurityPriceSource.class).isEmpty());
		assertEquals(1, db.getAll(StockDataSource.class).size());
	}

	private StockSecurity retrieveGenericPrice(GenericQuoteFeed feed) {
		StockSecurity security = createSecurity();
		MarketDataClient client = mock(MarketDataClient.class);
		QuoteSource source = QuoteSource.of(feed);
		when(client.fetch(eq(source), any(), any())).thenReturn(
				List.of(new MarketQuote(LocalDate.now().minusDays(1), new BigDecimal("10.00"))));
		new StockQuoteRetrievalService(client).retrieve(security.getId(), source, "TEST");
		return security;
	}

	private static GenericQuoteFeed genericFeed(String name) {
		GenericQuoteFeed emptyFeed = GenericQuoteFeed.create(name);
		return new GenericQuoteFeed(emptyFeed.id(), name, "https://example.test/{symbol}",
				GenericQuoteFeedFormat.JSON, "", "", "", "", "date", "close", "uuuu-MM-dd", ',');
	}

	private StockSecurity createSecurity() {
		StockSecurity security = new StockSecurity();
		security.setSecurityType(StockSecurityType.STOCK);
		security.setName("Test AG");
		security.setDefaultQuantityType(StockQuantityType.UNITS);
		security.setDefaultQuoteCurrency(Currency.EUR);
		security.setDefaultQuotationType(StockQuotationType.ABSOLUTE);
		return db.insertOrUpdate(security);
	}

	private void setSetting(String attribute, String value) {
		Setting setting = db.getAll(Setting.class).stream()
				.filter(candidate -> attribute.equals(candidate.getAttribute())).findFirst().orElseThrow();
		setting.setValue(value);
		db.insertOrUpdate(setting);
	}
}
