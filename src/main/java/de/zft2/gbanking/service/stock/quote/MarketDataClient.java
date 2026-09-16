package de.zft2.gbanking.service.stock.quote;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.stock.quote.QuoteProviderSettings.Configuration;
import de.zft2.gbanking.util.SimpleJson;

final class MarketDataClient {

	private static final URI ALPHA_VANTAGE_ENDPOINT = URI.create("https://www.alphavantage.co/query");
	private static final URI EODHD_ENDPOINT = URI.create("https://eodhd.com/api/eod/");
	private static final URI ALPACA_ENDPOINT = URI.create("https://data.alpaca.markets/v2/stocks/");
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
	private static final int MAX_RESPONSE_CHARACTERS = 10_000_000;

	private final HttpClient httpClient;
	private final URI alphaVantageEndpoint;
	private final URI eodhdEndpoint;
	private final URI alpacaEndpoint;

	MarketDataClient() {
		this(HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT)
				.followRedirects(HttpClient.Redirect.NEVER).build(),
				ALPHA_VANTAGE_ENDPOINT, EODHD_ENDPOINT, ALPACA_ENDPOINT);
	}

	MarketDataClient(HttpClient httpClient, URI alphaVantageEndpoint, URI eodhdEndpoint, URI alpacaEndpoint) {
		this.httpClient = httpClient;
		this.alphaVantageEndpoint = alphaVantageEndpoint;
		this.eodhdEndpoint = eodhdEndpoint;
		this.alpacaEndpoint = alpacaEndpoint;
	}

	List<MarketQuote> fetch(QuoteSource source, QuoteRequest request, Configuration configuration) {
		return switch (source.provider()) {
		case ALPHA_VANTAGE -> fetchAlphaVantage(request.symbol(), configuration.alphaVantageApiKey());
		case EODHD -> fetchEodhd(request.symbol(), configuration.eodhdApiToken());
		case ALPACA -> fetchAlpaca(request.symbol(), configuration.alpacaKeyId(), configuration.alpacaSecretKey());
		case GENERIC -> fetchGeneric(request, source.genericFeed());
		};
	}

	private List<MarketQuote> fetchAlphaVantage(String symbol, String apiKey) {
		String query = "function=TIME_SERIES_DAILY&outputsize=compact&symbol=" + encode(symbol)
				+ "&apikey=" + encode(apiKey);
		return parseAlphaVantage(get(withQuery(alphaVantageEndpoint, query), Map.of()));
	}

	private List<MarketQuote> fetchEodhd(String symbol, String apiToken) {
		String query = "api_token=" + encode(apiToken) + "&fmt=json&order=a&from=" + LocalDate.now().minusYears(1);
		URI uri = URI.create(ensureTrailingSlash(eodhdEndpoint.toString()) + encode(symbol) + "?" + query);
		return parseEodhd(get(uri, Map.of()));
	}

	private List<MarketQuote> fetchAlpaca(String symbol, String keyId, String secretKey) {
		String query = "timeframe=1Day&feed=iex&adjustment=raw&limit=1000&start="
				+ LocalDate.now().minusMonths(6) + "&sort=asc";
		URI uri = URI.create(ensureTrailingSlash(alpacaEndpoint.toString()) + encode(symbol) + "/bars?" + query);
		Map<String, String> headers = Map.of("APCA-API-KEY-ID", keyId, "APCA-API-SECRET-KEY", secretKey);
		return parseAlpaca(get(uri, headers));
	}

	private List<MarketQuote> fetchGeneric(QuoteRequest request, GenericQuoteFeed feed) {
		URI uri = genericUri(expand(feed.urlTemplate(), request, feed.secret(), true));
		Map<String, String> headers = genericHeaders(request, feed);
		String content = get(uri, headers);
		return feed.format() == GenericQuoteFeedFormat.CSV
				? parseGenericCsv(content, feed) : parseGenericJson(content, feed);
	}

	private static URI genericUri(String value) {
		try {
			URI uri = URI.create(value);
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
				throw new GBankingException("Der generische Kursfeed muss eine vollständige HTTPS-Adresse verwenden");
			}
			return uri;
		} catch (IllegalArgumentException exception) {
			throw new GBankingException("Die URL-Vorlage des generischen Kursfeeds ist ungültig");
		}
	}

	private static Map<String, String> genericHeaders(QuoteRequest request, GenericQuoteFeed feed) {
		if (feed.headerName().isBlank()) {
			return Map.of();
		}
		if (feed.headerValue().isBlank()) {
			throw new GBankingException("Für den konfigurierten HTTP-Header fehlt ein Wert");
		}
		String value = expand(feed.headerValue(), request, feed.secret(), false);
		return Map.of(feed.headerName(), value);
	}

	private String get(URI uri, Map<String, String> headers) {
		try {
			HttpRequest.Builder builder = HttpRequest.newBuilder(uri).GET().timeout(REQUEST_TIMEOUT)
					.header("Accept", "application/json, text/csv;q=0.9, */*;q=0.1");
			headers.forEach(builder::header);
			HttpResponse<String> response = httpClient.send(builder.build(),
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new GBankingException("Der Kursanbieter antwortete mit HTTP-Status " + response.statusCode());
			}
			if (response.body().length() > MAX_RESPONSE_CHARACTERS) {
				throw new GBankingException("Die Antwort des Kursanbieters ist zu groß");
			}
			return response.body();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new GBankingException("Der Kursabruf wurde unterbrochen", exception);
		} catch (IOException | IllegalArgumentException exception) {
			throw new GBankingException("Der Kursanbieter konnte nicht erreicht werden");
		}
	}

	static List<MarketQuote> parseAlphaVantage(String content) {
		Map<String, Object> root = object(SimpleJson.parse(content), "Alpha-Vantage-Antwort");
		throwProviderError(root);
		Map<String, Object> series = object(root.get("Time Series (Daily)"), "Time Series (Daily)");
		List<MarketQuote> result = new ArrayList<>();
		for (Map.Entry<String, Object> entry : series.entrySet()) {
			Map<String, Object> values = object(entry.getValue(), "Tageskurs");
			result.add(quote(parseIsoDate(entry.getKey()), decimal(values.get("4. close"), "4. close")));
		}
		return sorted(result);
	}

	static List<MarketQuote> parseEodhd(String content) {
		Object parsed = SimpleJson.parse(content);
		if (parsed instanceof Map<?, ?>) {
			Map<String, Object> error = object(parsed, "EODHD-Antwort");
			throw new GBankingException(providerMessage(error));
		}
		List<MarketQuote> result = new ArrayList<>();
		for (Object item : array(parsed, "EODHD-Antwort")) {
			Map<String, Object> values = object(item, "EODHD-Tageskurs");
			result.add(quote(parseIsoDate(text(values.get("date"), "date")), decimal(values.get("close"), "close")));
		}
		return sorted(result);
	}

	static List<MarketQuote> parseAlpaca(String content) {
		Map<String, Object> root = object(SimpleJson.parse(content), "Alpaca-Antwort");
		if (root.get("bars") == null) {
			throw new GBankingException(providerMessage(root));
		}
		List<MarketQuote> result = new ArrayList<>();
		for (Object item : array(root.get("bars"), "bars")) {
			Map<String, Object> values = object(item, "Alpaca-Tageskurs");
			String timestamp = text(values.get("t"), "t");
			result.add(quote(parseTimestampDate(timestamp), decimal(values.get("c"), "c")));
		}
		return sorted(result);
	}

	static List<MarketQuote> parseGenericJson(String content, GenericQuoteFeed feed) {
		Object root = SimpleJson.parse(content);
		Object itemsValue = resolvePath(root, feed.itemsPath());
		if (feed.itemsPath().isBlank()) {
			List<MarketQuote> parallelQuotes = parseParallelJsonArrays(itemsValue, feed);
			if (parallelQuotes != null) {
				return parallelQuotes;
			}
		}
		List<?> items = itemsValue instanceof List<?> list ? list : List.of(itemsValue);
		List<MarketQuote> result = new ArrayList<>();
		for (Object item : items) {
			Object date = resolvePath(item, feed.datePath());
			Object price = resolvePath(item, feed.pricePath());
			result.add(quote(parseConfiguredDate(text(date, feed.datePath()), feed.dateFormat()),
					decimal(price, feed.pricePath())));
		}
		return sorted(result);
	}

	private static List<MarketQuote> parseParallelJsonArrays(Object root, GenericQuoteFeed feed) {
		if (root instanceof List<?>) {
			return null;
		}
		Object datesValue = resolvePath(root, feed.datePath());
		Object pricesValue = resolvePath(root, feed.pricePath());
		if (!(datesValue instanceof List<?> dates) || !(pricesValue instanceof List<?> prices)) {
			return null;
		}
		if (dates.size() != prices.size()) {
			throw new GBankingException("Die Datums- und Kurslisten des JSON-Kursfeeds sind unterschiedlich lang");
		}
		List<MarketQuote> result = new ArrayList<>();
		for (int index = 0; index < dates.size(); index++) {
			result.add(quote(parseConfiguredDate(text(dates.get(index), feed.datePath()), feed.dateFormat()),
					decimal(prices.get(index), feed.pricePath())));
		}
		return sorted(result);
	}

	static List<MarketQuote> parseGenericCsv(String content, GenericQuoteFeed feed) {
		CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(feed.csvDelimiter())
				.setHeader().setSkipHeaderRecord(true).setTrim(true).get();
		try (CSVParser parser = format.parse(new StringReader(content))) {
			List<MarketQuote> result = new ArrayList<>();
			for (CSVRecord record : parser) {
				String date = csvValue(record, feed.datePath());
				String price = csvValue(record, feed.pricePath());
				result.add(quote(parseConfiguredDate(date, feed.dateFormat()), decimal(price, feed.pricePath())));
			}
			return sorted(result);
		} catch (IOException | IllegalArgumentException exception) {
			throw new GBankingException("Der CSV-Kursfeed konnte nicht gelesen werden", exception);
		}
	}

	private static String csvValue(CSVRecord record, String column) {
		if (column.isBlank() || !record.isMapped(column)) {
			throw new GBankingException("Die CSV-Spalte " + column + " wurde nicht gefunden");
		}
		return record.get(column);
	}

	private static Object resolvePath(Object root, String path) {
		Object current = root;
		if (path == null || path.isBlank()) {
			return current;
		}
		for (String segment : path.split("\\.")) {
			if (current instanceof Map<?, ?> map) {
				current = map.get(segment);
			} else if (current instanceof List<?> list) {
				current = list.get(parseIndex(segment, list.size(), path));
			} else {
				throw new GBankingException("Der JSON-Pfad " + path + " wurde nicht gefunden");
			}
			if (current == null) {
				throw new GBankingException("Der JSON-Pfad " + path + " wurde nicht gefunden");
			}
		}
		return current;
	}

	private static int parseIndex(String segment, int size, String path) {
		try {
			int index = Integer.parseInt(segment);
			if (index >= 0 && index < size) {
				return index;
			}
		} catch (NumberFormatException ignored) {
			// Der gemeinsame Fehler enthält den vollständigen Pfad.
		}
		throw new GBankingException("Der JSON-Pfad " + path + " wurde nicht gefunden");
	}

	private static LocalDate parseConfiguredDate(String value, String format) {
		try {
			if ("UNIX_SECONDS".equalsIgnoreCase(format)) {
				return Instant.ofEpochSecond(Long.parseLong(value)).atZone(ZoneOffset.UTC).toLocalDate();
			}
			if ("UNIX_MILLISECONDS".equalsIgnoreCase(format)) {
				return Instant.ofEpochMilli(Long.parseLong(value)).atZone(ZoneOffset.UTC).toLocalDate();
			}
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format, Locale.ROOT)
					.withResolverStyle(ResolverStyle.STRICT);
			return LocalDate.parse(value, formatter);
		} catch (IllegalArgumentException | DateTimeParseException exception) {
			throw new GBankingException("Das Kursdatum " + value + " passt nicht zum Datumsformat " + format,
					exception);
		}
	}

	private static LocalDate parseIsoDate(String value) {
		try {
			return LocalDate.parse(value);
		} catch (DateTimeParseException exception) {
			throw new GBankingException("Ungültiges Kursdatum: " + value, exception);
		}
	}

	private static LocalDate parseTimestampDate(String value) {
		try {
			return OffsetDateTime.parse(value).toLocalDate();
		} catch (DateTimeParseException exception) {
			return parseIsoDate(value.length() >= 10 ? value.substring(0, 10) : value);
		}
	}

	private static MarketQuote quote(LocalDate date, BigDecimal price) {
		if (price.signum() <= 0) {
			throw new GBankingException("Der Kurs für " + date + " muss größer als null sein");
		}
		return new MarketQuote(date, price);
	}

	private static BigDecimal decimal(Object value, String field) {
		if (value instanceof Number number) {
			return new BigDecimal(number.toString());
		}
		String text = text(value, field).trim();
		if (text.indexOf(',') >= 0 && text.indexOf('.') < 0) {
			text = text.replace(',', '.');
		}
		try {
			return new BigDecimal(text);
		} catch (NumberFormatException exception) {
			throw new GBankingException("Das Kursfeld " + field + " enthält keine gültige Zahl", exception);
		}
	}

	private static String text(Object value, String field) {
		if (value == null) {
			throw new GBankingException("Das Kursfeld " + field + " fehlt");
		}
		return value.toString();
	}

	private static Map<String, Object> object(Object value, String field) {
		if (!(value instanceof Map<?, ?> map)) {
			throw new GBankingException(field + " ist kein JSON-Objekt");
		}
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (entry.getKey() instanceof String key) {
				result.put(key, entry.getValue());
			}
		}
		return result;
	}

	private static List<?> array(Object value, String field) {
		if (value instanceof List<?> list) {
			return list;
		}
		throw new GBankingException(field + " ist keine JSON-Liste");
	}

	private static void throwProviderError(Map<String, Object> values) {
		for (String key : List.of("Error Message", "Note", "Information")) {
			if (values.get(key) != null) {
				throw new GBankingException("Der Kursanbieter meldet: " + values.get(key));
			}
		}
	}

	private static String providerMessage(Map<String, Object> values) {
		for (String key : List.of("message", "error", "Information", "Error Message")) {
			if (values.get(key) != null) {
				return "Der Kursanbieter meldet: " + values.get(key);
			}
		}
		return "Der Kursanbieter lieferte keine Kursdaten";
	}

	private static List<MarketQuote> sorted(List<MarketQuote> values) {
		return values.stream().sorted(Comparator.comparing(MarketQuote::date)).toList();
	}

	private static URI withQuery(URI endpoint, String query) {
		return URI.create(endpoint.toString() + (endpoint.getQuery() == null ? "?" : "&") + query);
	}

	private static String ensureTrailingSlash(String value) {
		return value.endsWith("/") ? value : value + "/";
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String expand(String template, QuoteRequest request, String secret, boolean encodeValues) {
		Map<String, String> values = Map.of(
				"{symbol}", request.symbol(), "{isin}", request.isin(), "{wkn}", request.wkn(),
				"{mic}", request.marketIdentifierCode(), "{secret}", secret);
		String result = template;
		for (Map.Entry<String, String> entry : values.entrySet()) {
			String value = encodeValues ? encode(entry.getValue()) : entry.getValue();
			result = result.replace(entry.getKey(), value);
		}
		return result;
	}

	record QuoteRequest(String symbol, String isin, String wkn, String marketIdentifierCode) {
		QuoteRequest {
			isin = value(isin);
			wkn = value(wkn);
			marketIdentifierCode = value(marketIdentifierCode);
		}

		private static String value(String value) {
			return value != null ? value : "";
		}
	}

	record MarketQuote(LocalDate date, BigDecimal price) {
	}
}
