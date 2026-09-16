package de.zft2.gbanking.service.stock.quote;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.service.stock.quote.MarketDataClient.MarketQuote;
import de.zft2.gbanking.service.stock.quote.MarketDataClient.QuoteRequest;
import de.zft2.gbanking.service.stock.quote.QuoteProviderSettings.Configuration;

class GenericQuoteFeedLiveTest {

	private static final String INFINEON_ISIN = "DE0006231004";
	private static final String APPLE_ISIN = "US0378331005";

	@Test
	@Disabled("Live-Beispiel: Onvista untersagt automatisierte Abrufe ohne vorherige schriftliche Erlaubnis")
	void shouldFetchInfineonPricesFromOnvista() {
		String startDate = LocalDate.now().minusMonths(1).toString();
		GenericQuoteFeed feed = new GenericQuoteFeed("32b78a40-c14e-414c-a971-8d842f29f740",
				"Onvista – Infineon", "https://api.onvista.de/api/v1/instruments/STOCK/82561/"
						+ "eod_history?idNotation=154990&range=M1&startDate=" + startDate,
				GenericQuoteFeedFormat.JSON, "", "", "", "", "datetimeLast", "last", "UNIX_SECONDS", ',');

		List<MarketQuote> quotes = fetch(feed, new QuoteRequest("IFX", INFINEON_ISIN, "623100", "XETR"));

		assertPlausibleQuotes(quotes);
	}

	@Test
	@Disabled("Live-Beispiel: ARIVA benötigt ein Konto sowie eine ausdrücklich erlaubte Abrufmöglichkeit")
	void shouldFetchApplePricesFromAriva() {
		String securityId = requiredProperty("gbanking.test.ariva.security-id");
		String cookie = requiredProperty("gbanking.test.ariva.cookie");
		DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu");
		String url = "https://www.ariva.de/quote/historic/historic.csv?secu={symbol}&boerse_id=40"
				+ "&clean_split=1&clean_payout=0&clean_bezug=1&min_time="
				+ LocalDate.now().minusMonths(1).format(dateFormat) + "&max_time="
				+ LocalDate.now().format(dateFormat) + "&trenner=%3B&go=Download";
		GenericQuoteFeed feed = new GenericQuoteFeed("bd4aba12-7436-4732-9461-a12e229927a4",
				"ARIVA – Apple", url, GenericQuoteFeedFormat.CSV, "", "Cookie", cookie, "", "Datum",
				"Schluss", "dd.MM.uuuu", ';');

		List<MarketQuote> quotes = fetch(feed, new QuoteRequest(securityId, APPLE_ISIN, "865985", "XNAS"));

		assertPlausibleQuotes(quotes);
	}

	private static List<MarketQuote> fetch(GenericQuoteFeed feed, QuoteRequest request) {
		Configuration configuration = new Configuration("", "", "", "", List.of(feed));
		return new MarketDataClient().fetch(QuoteSource.of(feed), request, configuration);
	}

	private static String requiredProperty(String name) {
		String value = System.getProperty(name, "").trim();
		Assumptions.assumeTrue(!value.isEmpty(), "System property " + name + " is required");
		return value;
	}

	private static void assertPlausibleQuotes(List<MarketQuote> quotes) {
		assertFalse(quotes.isEmpty());
		assertTrue(quotes.stream().allMatch(quote -> quote.price().signum() > 0));
		assertTrue(quotes.stream().allMatch(quote -> !quote.date().isAfter(LocalDate.now())));
	}
}
