package de.zft2.gbanking.service.stock.quote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.stock.quote.MarketDataClient.MarketQuote;
import de.zft2.gbanking.service.stock.quote.MarketDataClient.QuoteRequest;
import de.zft2.gbanking.service.stock.quote.QuoteProviderSettings.Configuration;

class MarketDataClientTest {

	private static final String FEED_ID = "5ab8aefe-d861-4b73-86f0-5f5d90dc14dc";
	private static final URI EODHD_ENDPOINT = URI.create("https://eodhd.test/api/eod");
	private static final URI ALPACA_ENDPOINT = URI.create("https://alpaca.test/v2/stocks/");
	private static final Configuration CONFIGURATION = new Configuration("av key", "eod token", "key-id",
			"secret-key", List.of());
	private static final QuoteRequest REQUEST = new QuoteRequest("IBM", "US4592001014", null, "XNYS");
	private static final String DAILY_LIST = "[{\"date\":\"2026-09-14\",\"close\":141.5}]";
	private static final List<MarketQuote> DAILY_QUOTES = List.of(quote("2026-09-14", "141.5"));

	private final HttpClient httpClient = mock(HttpClient.class);
	@SuppressWarnings("unchecked")
	private final HttpResponse<String> response = mock(HttpResponse.class);
	private final MarketDataClient client = new MarketDataClient(httpClient, URI.create("https://av.test/query"),
			EODHD_ENDPOINT, ALPACA_ENDPOINT);

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

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			https://av.test/query               | ?
			https://av.test/query?datatype=json | &
			""")
	void shouldFetchAlphaVantageQuotesWithEncodedQuery(String endpoint, String separator) throws Exception {
		stubResponse(200, "{\"Time Series (Daily)\":{\"2026-09-14\":{\"4. close\":\"141.5\"}}}");
		MarketDataClient alphaVantageClient = new MarketDataClient(httpClient, URI.create(endpoint),
				EODHD_ENDPOINT, ALPACA_ENDPOINT);

		assertEquals(DAILY_QUOTES,
				alphaVantageClient.fetch(QuoteSource.of(QuoteProvider.ALPHA_VANTAGE), REQUEST, CONFIGURATION));
		HttpRequest request = sentRequest();
		assertEquals(endpoint + separator + "function=TIME_SERIES_DAILY&outputsize=compact&symbol=IBM&apikey=av%20key",
				request.uri().toString());
		assertEquals(Optional.of("application/json, text/csv;q=0.9, */*;q=0.1"), request.headers().firstValue("Accept"));
	}

	@Test
	void shouldFetchEodhdQuotesForTheLastYear() throws Exception {
		stubResponse(200, DAILY_LIST);

		assertEquals(DAILY_QUOTES, fetch(QuoteSource.of(QuoteProvider.EODHD)));
		assertTrue(sentRequest().uri().toString()
				.startsWith("https://eodhd.test/api/eod/IBM?api_token=eod%20token&fmt=json&order=a&from="));
	}

	@Test
	void shouldFetchAlpacaBarsWithKeyHeaders() throws Exception {
		stubResponse(200, "{\"bars\":[{\"t\":\"2026-09-14T04:00:00Z\",\"c\":141.5}]}");

		assertEquals(DAILY_QUOTES, fetch(QuoteSource.of(QuoteProvider.ALPACA)));
		HttpRequest request = sentRequest();
		String uri = request.uri().toString();
		assertTrue(uri.startsWith(
				"https://alpaca.test/v2/stocks/IBM/bars?timeframe=1Day&feed=iex&adjustment=raw&limit=1000&start="));
		assertTrue(uri.endsWith("&sort=asc"));
		assertEquals(Optional.of("key-id"), request.headers().firstValue("APCA-API-KEY-ID"));
		assertEquals(Optional.of("secret-key"), request.headers().firstValue("APCA-API-SECRET-KEY"));
	}

	@Test
	void shouldFetchGenericJsonFeedWithExpandedTemplateAndHeader() throws Exception {
		stubResponse(200, DAILY_LIST);
		GenericQuoteFeed feed = new GenericQuoteFeed(FEED_ID, "Test feed",
				"https://feed.test/{symbol}/{isin}?wkn={wkn}&mic={mic}&key={secret}", GenericQuoteFeedFormat.JSON,
				"a b", "Authorization", "Bearer {secret}", "", "", "", "", ',');

		assertEquals(DAILY_QUOTES, fetch(QuoteSource.of(feed)));
		HttpRequest request = sentRequest();
		// Values are URL-encoded in the address but inserted verbatim into the header.
		assertEquals("https://feed.test/IBM/US4592001014?wkn=&mic=XNYS&key=a%20b", request.uri().toString());
		assertEquals(Optional.of("Bearer a b"), request.headers().firstValue("Authorization"));
	}

	@Test
	void shouldFetchGenericCsvFeedWithoutCustomHeader() throws Exception {
		stubResponse(200, "date,close\n2026-09-14,141.5\n");

		assertEquals(DAILY_QUOTES, fetch(QuoteSource.of(feed(GenericQuoteFeedFormat.CSV, ',', "", "", "", ""))));
		assertEquals(Optional.empty(), sentRequest().headers().firstValue("Authorization"));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			http://feed.test/{symbol}  |       | Der generische Kursfeed muss eine vollständige HTTPS-Adresse verwenden
			https:///{symbol}          |       | Der generische Kursfeed muss eine vollständige HTTPS-Adresse verwenden
			https://feed.test/a b      |       | Die URL-Vorlage des generischen Kursfeeds ist ungültig
			https://feed.test/{symbol} | X-Key | Für den konfigurierten HTTP-Header fehlt ein Wert
			""")
	void shouldRejectInvalidGenericFeedConfiguration(String urlTemplate, String headerName, String expectedMessage) {
		GenericQuoteFeed feed = new GenericQuoteFeed(FEED_ID, "Test feed", urlTemplate, GenericQuoteFeedFormat.JSON,
				"", headerName, "", "", "", "", "", ',');

		assertMessage(expectedMessage, () -> fetch(QuoteSource.of(feed)));
		verifyNoInteractions(httpClient);
	}

	@ParameterizedTest
	@ValueSource(ints = { 199, 300, 404 })
	void shouldRejectUnsuccessfulHttpStatus(int status) throws Exception {
		stubResponse(status, DAILY_LIST);

		assertMessage("Der Kursanbieter antwortete mit HTTP-Status " + status,
				() -> fetch(QuoteSource.of(QuoteProvider.EODHD)));
	}

	@Test
	void shouldRejectOversizedResponse() throws Exception {
		stubResponse(200, "x".repeat(10_000_001));

		assertMessage("Die Antwort des Kursanbieters ist zu groß", () -> fetch(QuoteSource.of(QuoteProvider.EODHD)));
	}

	@Test
	void shouldReportUnreachableProvider() throws Exception {
		when(httpClient.send(any(), any())).thenThrow(new IOException("offline"));

		assertMessage("Der Kursanbieter konnte nicht erreicht werden",
				() -> fetch(QuoteSource.of(QuoteProvider.EODHD)));
	}

	@Test
	void shouldRestoreInterruptFlagWhenRequestIsInterrupted() throws Exception {
		when(httpClient.send(any(), any())).thenThrow(new InterruptedException());

		assertMessage("Der Kursabruf wurde unterbrochen", () -> fetch(QuoteSource.of(QuoteProvider.EODHD)));
		// Thread.interrupted() also clears the flag again for subsequent tests.
		assertTrue(Thread.interrupted());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			alphaVantage | []                                               | Alpha-Vantage-Antwort ist kein JSON-Objekt
			alphaVantage | {}                                               | Time Series (Daily) ist kein JSON-Objekt
			alphaVantage | {"Time Series (Daily)":{"x":{"4. close":"1"}}}   | Ungültiges Kursdatum: x
			eodhd        | {"message":"Invalid token"}                      | Der Kursanbieter meldet: Invalid token
			eodhd        | 5                                                | EODHD-Antwort ist keine JSON-Liste
			eodhd        | [1]                                              | EODHD-Tageskurs ist kein JSON-Objekt
			eodhd        | [{"date":"2026-09-14"}]                          | Das Kursfeld close fehlt
			eodhd        | [{"date":"2026-09-14","close":"abc"}]            | Das Kursfeld close enthält keine gültige Zahl
			eodhd        | [{"date":"2026-09-14","close":"1.234,5"}]        | Das Kursfeld close enthält keine gültige Zahl
			eodhd        | [{"date":"2026-09-14","close":0}]                | Der Kurs für 2026-09-14 muss größer als null sein
			alpaca       | {"error":"forbidden"}                            | Der Kursanbieter meldet: forbidden
			alpaca       | {"Information":"limit"}                          | Der Kursanbieter meldet: limit
			alpaca       | {"Error Message":"bad request"}                  | Der Kursanbieter meldet: bad request
			alpaca       | {}                                               | Der Kursanbieter lieferte keine Kursdaten
			alpaca       | {"bars":{}}                                      | bars ist keine JSON-Liste
			alpaca       | {"bars":[{"t":"bad","c":1}]}                     | Ungültiges Kursdatum: bad
			nested       | {"data":[]}                                      | Der JSON-Pfad data.0.rows wurde nicht gefunden
			nested       | {"data":["x"]}                                   | Der JSON-Pfad data.0.rows wurde nicht gefunden
			nested       | {"data":[{"rows":[{"d":"14.09.2026"}]}]}         | Der JSON-Pfad p.v wurde nicht gefunden
			nested       | {"data":[{"rows":[{"d":"14.09.2026","p":5}]}]}   | Der JSON-Pfad p.v wurde nicht gefunden
			nested       | {"data":[{"rows":[{"d":"14.09.2026","p":[1]}]}]} | Der JSON-Pfad p.v wurde nicht gefunden
			flat         | {"d":["14.09.2026"],"p":{"v":[1,2]}}             | Die Datums- und Kurslisten des JSON-Kursfeeds sind unterschiedlich lang
			flat         | {"d":["14.09.2026"],"p":{"v":1}}                 | Das Kursdatum [14.09.2026] passt nicht zum Datumsformat dd.MM.uuuu
			""")
	void shouldRejectInvalidProviderPayloads(String parser, String content, String expectedMessage) {
		assertMessage(expectedMessage, () -> parse(parser, content));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			               | [{"date":"2026-09-14","close":1.5}]
			               | {"date":"2026-09-14","close":"1.5"}
			result.0.quote | {"result":[{"quote":{"date":"2026-09-14","close":1.5}}]}
			""")
	void shouldParseGenericJsonListsAndSingleObjects(String itemsPath, String content) {
		assertEquals(List.of(quote("2026-09-14", "1.5")), MarketDataClient.parseGenericJson(content,
				feed(GenericQuoteFeedFormat.JSON, ',', itemsPath, "", "", "")));
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-09-14T04:00:00", "2026-09-14" })
	void shouldParseAlpacaTimestampsWithoutOffset(String timestamp) {
		assertEquals(DAILY_QUOTES, MarketDataClient.parseAlpaca("{\"bars\":[{\"t\":\"" + timestamp + "\",\"c\":141.5}]}"));
	}

	@ParameterizedTest
	@CsvSource({ "UNIX_MILLISECONDS, 1789344000000", "unix_seconds, 1789344000", "dd.MM.uuuu, 14.09.2026" })
	void shouldParseConfiguredDateFormats(String dateFormat, String value) {
		assertEquals(List.of(quote("2026-09-14", "1")), parseCsvRow(dateFormat, value));
	}

	@ParameterizedTest
	@CsvSource({ "UNIX_SECONDS, abc", "uuuu-MM-dd, 2026-02-30", "uuuu{MM, 2026-09-14" })
	void shouldRejectDatesNotMatchingTheFormat(String dateFormat, String value) {
		assertMessage("Das Kursdatum " + value + " passt nicht zum Datumsformat " + dateFormat,
				() -> parseCsvRow(dateFormat, value));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			Datum;Kurs    | 14.09.2026;1 | Die CSV-Spalte Schluss wurde nicht gefunden
			Datum;Schluss | 14.09.2026   | Der CSV-Kursfeed konnte nicht gelesen werden
			""")
	void shouldRejectCsvFeedsWithMissingValues(String header, String row, String expectedMessage) {
		GenericQuoteFeed feed = feed(GenericQuoteFeedFormat.CSV, ';', "", "Datum", "Schluss", "dd.MM.uuuu");

		assertMessage(expectedMessage, () -> MarketDataClient.parseGenericCsv(header + "\n" + row + "\n", feed));
	}

	private void stubResponse(int status, String body) throws Exception {
		when(response.statusCode()).thenReturn(status);
		when(response.body()).thenReturn(body);
		when(httpClient.<String>send(any(), any())).thenReturn(response);
	}

	private HttpRequest sentRequest() throws Exception {
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		verify(httpClient).send(captor.capture(), any());
		return captor.getValue();
	}

	private List<MarketQuote> fetch(QuoteSource source) {
		return client.fetch(source, REQUEST, CONFIGURATION);
	}

	private static List<MarketQuote> parse(String parser, String content) {
		return switch (parser) {
		case "alphaVantage" -> MarketDataClient.parseAlphaVantage(content);
		case "eodhd" -> MarketDataClient.parseEodhd(content);
		case "alpaca" -> MarketDataClient.parseAlpaca(content);
		default -> MarketDataClient.parseGenericJson(content, feed(GenericQuoteFeedFormat.JSON, ',',
				"nested".equals(parser) ? "data.0.rows" : "", "d", "p.v", "dd.MM.uuuu"));
		};
	}

	private static List<MarketQuote> parseCsvRow(String dateFormat, String value) {
		return MarketDataClient.parseGenericCsv("date;close\n" + value + ";1\n",
				feed(GenericQuoteFeedFormat.CSV, ';', "", "", "", dateFormat));
	}

	private static void assertMessage(String expected, Executable executable) {
		// GBankingException.getMessage() appends ": " and the message of the original exception.
		String message = assertThrows(GBankingException.class, executable).getMessage();
		assertTrue(message.startsWith(expected + ": "), message);
	}

	private static GenericQuoteFeed feed(GenericQuoteFeedFormat format, char delimiter, String itemsPath,
			String datePath, String pricePath, String dateFormat) {
		return new GenericQuoteFeed(FEED_ID, "Test feed", "https://example.test/{symbol}", format, "", "", "",
				itemsPath, datePath, pricePath, dateFormat, delimiter);
	}

	private static MarketQuote quote(String date, String price) {
		return new MarketQuote(LocalDate.parse(date), new BigDecimal(price));
	}
}
