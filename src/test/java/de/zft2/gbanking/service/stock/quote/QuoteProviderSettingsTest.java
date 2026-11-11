package de.zft2.gbanking.service.stock.quote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.TestDatabaseExtension;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockQuoteFeed;
import de.zft2.gbanking.exception.GBankingException;

class QuoteProviderSettingsTest {

	@RegisterExtension
	static final TestDatabaseExtension database = new TestDatabaseExtension();

	private final DBController db = database.controller();

	@Test
	void shouldRoundTripIndividualFeedsIncludingIncompleteDrafts() {
		List<GenericQuoteFeed> feeds = List.of(feed("JSON feed", GenericQuoteFeedFormat.JSON),
				feed("CSV feed", GenericQuoteFeedFormat.CSV), GenericQuoteFeed.create("Draft"));

		QuoteProviderSettings.saveGenericFeeds(feeds);

		assertEquals(feeds, QuoteProviderSettings.getConfiguration().genericFeeds());
		assertEquals(3, db.getAll(StockQuoteFeed.class).size());
		assertFalse(QuoteProviderSettings.getConfiguration().genericFeeds().get(2).isConfigured());
		assertTrue(db.getAll(Setting.class).stream()
				.noneMatch(setting -> setting.getAttribute().startsWith("stock.quote.generic.")));
	}

	@Test
	void shouldRenameAndReorderFeedsWithoutReplacingTheirSources() {
		GenericQuoteFeed first = feed("First", GenericQuoteFeedFormat.JSON);
		GenericQuoteFeed second = feed("Second", GenericQuoteFeedFormat.CSV);
		QuoteProviderSettings.saveGenericFeeds(List.of(first, second));
		int firstSourceId = QuoteProviderSettings.requireGenericFeed(first.id()).getSourceId();
		int secondSourceId = QuoteProviderSettings.requireGenericFeed(second.id()).getSourceId();
		GenericQuoteFeed renamed = withName(first, "Renamed");

		QuoteProviderSettings.saveGenericFeeds(List.of(second, renamed));

		assertEquals(List.of(second, renamed), QuoteProviderSettings.getConfiguration().genericFeeds());
		assertEquals(firstSourceId, QuoteProviderSettings.requireGenericFeed(first.id()).getSourceId());
		assertEquals(secondSourceId, QuoteProviderSettings.requireGenericFeed(second.id()).getSourceId());
		assertEquals("Renamed", db.getById(StockDataSource.class, firstSourceId).getSourceName());
		assertEquals(2, db.getAll(StockDataSource.class).size());
	}

	@Test
	void shouldRollBackSourceAndFeedChangesWhenTheListContainsDuplicateIds() {
		GenericQuoteFeed original = feed("Original", GenericQuoteFeedFormat.JSON);
		GenericQuoteFeed added = feed("Added", GenericQuoteFeedFormat.CSV);
		QuoteProviderSettings.saveGenericFeeds(List.of(original));

		assertThrows(GBankingException.class, () -> QuoteProviderSettings.saveGenericFeeds(
				List.of(withName(original, "Changed"), added, added)));

		assertEquals(List.of(original), QuoteProviderSettings.getConfiguration().genericFeeds());
		assertEquals(1, db.getAll(StockDataSource.class).size());
		assertEquals(1, db.getAll(StockQuoteFeed.class).size());
	}

	@Test
	void shouldRemoveOnlyFeedConfigurationWhenSavingAnEmptyList() {
		GenericQuoteFeed feed = feed("Retained source", GenericQuoteFeedFormat.JSON);
		QuoteProviderSettings.saveGenericFeeds(List.of(feed));
		int sourceId = QuoteProviderSettings.requireGenericFeed(feed.id()).getSourceId();

		QuoteProviderSettings.saveGenericFeeds(List.of());

		assertTrue(QuoteProviderSettings.getConfiguration().genericFeeds().isEmpty());
		assertEquals(feed.name(), db.getById(StockDataSource.class, sourceId).getSourceName());
	}

	@Test
	void shouldEnforceFeedIdentityAndSourceReferencesInTheDatabase() throws Exception {
		QuoteProviderSettings.saveGenericFeeds(List.of(
				feed("First", GenericQuoteFeedFormat.JSON), feed("Second", GenericQuoteFeedFormat.CSV)));

		try (var statement = DBController.getConnection().createStatement()) {
			assertThrows(SQLException.class, () -> statement.executeUpdate(
					"UPDATE stockQuoteFeed SET source_id = 999999 WHERE sortOrder = 0"));
			assertThrows(SQLException.class, () -> statement.executeUpdate("""
					UPDATE stockQuoteFeed
					SET uuid = (SELECT uuid FROM stockQuoteFeed WHERE sortOrder = 0)
					WHERE sortOrder = 1
					"""));
			assertThrows(SQLException.class, () -> statement.executeUpdate(
					"DELETE FROM stockDataSource WHERE id IN (SELECT source_id FROM stockQuoteFeed)"));
		}
		assertEquals(2, QuoteProviderSettings.getConfiguration().genericFeeds().size());
	}

	private static GenericQuoteFeed feed(String name, GenericQuoteFeedFormat format) {
		return new GenericQuoteFeed(UUID.randomUUID().toString(), name, "https://example.test/{symbol}?key={secret}",
				format, "test-secret", "Authorization", "test-header", "prices", "timestamp",
				"closing", "dd.MM.uuuu", ';');
	}

	private static GenericQuoteFeed withName(GenericQuoteFeed feed, String name) {
		return new GenericQuoteFeed(feed.id(), name, feed.urlTemplate(), feed.format(), feed.secret(), feed.headerName(),
				feed.headerValue(), feed.itemsPath(), feed.datePath(), feed.pricePath(), feed.dateFormat(), feed.csvDelimiter());
	}
}
