package de.zft2.gbanking.service.stock.quote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockDataSourceType;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.db.dao.stock.StockSecurityPrice;
import de.zft2.gbanking.db.dao.stock.StockSecurityPriceSource;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.quote.MarketDataClient.MarketQuote;
import de.zft2.gbanking.service.stock.quote.StockQuoteRetrievalService.RetrievalResult;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockQuoteRetrievalServiceTest {

	private DBController db;
	private Path tempDir;

	@BeforeAll
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_quote_");
		db = DBController.getInstance(tempDir.toString());
	}

	@BeforeEach
	void clearDatabase() {
		DBControllerTestUtil.clearAllTables(DBController.getConnection());
		QuoteProviderSettings.ensureSettingsExist();
		setSetting(QuoteProviderSettings.ALPHA_VANTAGE_API_KEY, "tenant-api-key");
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
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
	void marketDataPricesShouldRequireConfirmationForManualChanges() {
		StockSecurity security = createSecurity();
		MarketDataClient client = mock(MarketDataClient.class);
		QuoteSource source = QuoteSource.of(QuoteProvider.ALPHA_VANTAGE);
		when(client.fetch(eq(source), any(), any())).thenReturn(
				List.of(new MarketQuote(LocalDate.now().minusDays(1), new BigDecimal("10.00"))));
		new StockQuoteRetrievalService(client).retrieve(security.getId(), source, "TEST");
		int priceId = new StockPortfolioService().getPrices(security.getId()).get(0).priceId();

		assertThrows(GBankingException.class, () -> new StockPortfolioService().savePrice(security.getId(), priceId,
				LocalDate.now(), new BigDecimal("12.00"), Currency.EUR));
	}

	@Test
	void shouldKeepPricesWhenDeletingGenericFeedWithoutPriceDeletion() {
		GenericQuoteFeed feed = genericFeed("First feed");
		QuoteProviderSettings.saveGenericFeeds(List.of(feed, genericFeed("Second feed")));
		StockSecurity security = retrieveGenericPrice(feed);

		StockQuoteRetrievalService service = new StockQuoteRetrievalService(mock(MarketDataClient.class));
		assertEquals(0, service.deleteGenericFeed(feed.id(), false).deletedPrices());

		assertEquals(1, QuoteProviderSettings.getConfiguration().genericFeeds().size());
		assertEquals("Second feed", QuoteProviderSettings.getConfiguration().genericFeeds().get(0).name());
		assertEquals(1, new StockPortfolioService().getPrices(security.getId()).size());
	}

	@Test
	void shouldDeleteActivePricesWhenDeletingGenericFeedWithPriceDeletion() {
		GenericQuoteFeed feed = genericFeed("Disposable feed");
		QuoteProviderSettings.saveGenericFeeds(List.of(feed));
		StockSecurity security = retrieveGenericPrice(feed);

		StockQuoteRetrievalService service = new StockQuoteRetrievalService(mock(MarketDataClient.class));
		assertEquals(1, service.deleteGenericFeed(feed.id(), true).deletedPrices());

		assertEquals(List.of(), QuoteProviderSettings.getConfiguration().genericFeeds());
		assertEquals(List.of(), new StockPortfolioService().getPrices(security.getId()));
		assertEquals(2, db.getAll(StockSecurityPrice.class).size());
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
