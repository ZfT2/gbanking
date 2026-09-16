package de.zft2.gbanking.service.stock.quote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.stock.quote.MarketDataClient.MarketQuote;
class MarketDataClientTest {

	@Test
	void shouldParseAlphaVantageDailySeries() {
		String content = """
				{"Meta Data":{"2. Symbol":"IBM"},"Time Series (Daily)":{
				 "2026-09-15":{"4. close":"143.25"},
				 "2026-09-14":{"4. close":"141.50"}}}
				""";

		assertEquals(List.of(quote("2026-09-14", "141.50"), quote("2026-09-15", "143.25")),
				MarketDataClient.parseAlphaVantage(content));
	}

	@Test
	void shouldExposeAlphaVantageErrors() {
		assertThrows(GBankingException.class,
				() -> MarketDataClient.parseAlphaVantage("{\"Information\":\"API limit reached\"}"));
	}

	@Test
	void shouldParseEodhdPrices() {
		String content = """
				[{"date":"2026-09-14","close":141.5},{"date":"2026-09-15","close":143.25}]
				""";

		assertEquals(List.of(quote("2026-09-14", "141.5"), quote("2026-09-15", "143.25")),
				MarketDataClient.parseEodhd(content));
	}

	@Test
	void shouldParseAlpacaDailyBars() {
		String content = """
				{"bars":[{"t":"2026-09-14T04:00:00Z","c":141.5},
				 {"t":"2026-09-15T04:00:00Z","c":143.25}],"next_page_token":null}
				""";

		assertEquals(List.of(quote("2026-09-14", "141.5"), quote("2026-09-15", "143.25")),
				MarketDataClient.parseAlpaca(content));
	}

	@Test
	void shouldParseConfiguredJsonFeed() {
		GenericQuoteFeed feed = feed(GenericQuoteFeedFormat.JSON, ';', "payload.prices",
				"day", "value", "dd.MM.uuuu");
		String content = """
				{"payload":{"prices":[{"day":"14.09.2026","value":"141,50"},
				 {"day":"15.09.2026","value":143.25}]}}
				""";

		assertEquals(List.of(quote("2026-09-14", "141.50"), quote("2026-09-15", "143.25")),
				MarketDataClient.parseGenericJson(content, feed));
	}

	@Test
	void shouldParseConfiguredCsvFeed() {
		GenericQuoteFeed feed = feed(GenericQuoteFeedFormat.CSV, ';', "",
				"Datum", "Schluss", "dd.MM.uuuu");
		String content = "Datum;Schluss\r\n14.09.2026;141,50\r\n15.09.2026;143,25\r\n";

		assertEquals(List.of(quote("2026-09-14", "141.50"), quote("2026-09-15", "143.25")),
				MarketDataClient.parseGenericCsv(content, feed));
	}

	@Test
	void shouldParseParallelJsonArraysWithUnixSeconds() {
		GenericQuoteFeed feed = feed(GenericQuoteFeedFormat.JSON, ',', "",
				"datetimeLast", "last", "UNIX_SECONDS");
		String content = """
				{"datetimeLast":[1789344000,1789430400],"last":[141.5,143.25]}
				""";

		assertEquals(List.of(quote("2026-09-14", "141.5"), quote("2026-09-15", "143.25")),
				MarketDataClient.parseGenericJson(content, feed));
	}

	private static GenericQuoteFeed feed(GenericQuoteFeedFormat format, char delimiter, String itemsPath,
			String datePath, String pricePath, String dateFormat) {
		return new GenericQuoteFeed("5ab8aefe-d861-4b73-86f0-5f5d90dc14dc", "Test feed",
				"https://example.test/{symbol}", format, "", "", "", itemsPath, datePath, pricePath,
				dateFormat, delimiter);
	}

	private static MarketQuote quote(String date, String price) {
		return new MarketQuote(LocalDate.parse(date), new BigDecimal(price));
	}
}
