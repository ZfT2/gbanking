package de.zft2.gbanking.service.stock;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import de.zft2.gbanking.exception.GBankingException;

final class PortfolioPerformanceCsvSupport {

	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
	private static final DecimalFormatSymbols GERMAN_SYMBOLS = DecimalFormatSymbols.getInstance(Locale.GERMANY);

	private PortfolioPerformanceCsvSupport() {
	}

	static CSVParser parse(Path file) throws IOException {
		Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
		CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true)
				.setTrim(true).get();
		return format.parse(reader);
	}

	static CSVPrinter printer(Path file, String... headers) throws IOException {
		Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
		CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader(headers)
				.setRecordSeparator("\r\n").get();
		return new CSVPrinter(writer, format);
	}

	static String value(CSVRecord record, String header) {
		if (!record.isMapped(header)) {
			return null;
		}
		String value = record.get(header);
		return value == null || value.isBlank() ? null : value.trim();
	}

	static BigDecimal decimal(CSVRecord record, String header) {
		return decimal(value(record, header), header, record.getRecordNumber());
	}

	static BigDecimal decimal(String value, String field, long row) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return new BigDecimal(value.trim().replace(".", "").replace(',', '.'));
		} catch (NumberFormatException exception) {
			throw new GBankingException("Ungültige Zahl in Feld '" + field + "', Zeile " + row + ": " + value,
					exception);
		}
	}

	static LocalDateTime dateTime(CSVRecord record) {
		String value = required(record, "Datum");
		try {
			return LocalDateTime.parse(value);
		} catch (DateTimeParseException ignored) {
			try {
				return LocalDate.parse(value).atStartOfDay();
			} catch (DateTimeParseException exception) {
				throw new GBankingException("Ungültiges Datum in Zeile " + record.getRecordNumber() + ": " + value,
						exception);
			}
		}
	}

	static LocalDate date(CSVRecord record) {
		return dateTime(record).toLocalDate();
	}

	static String required(CSVRecord record, String header) {
		String result = value(record, header);
		if (result == null) {
			throw new GBankingException("Pflichtfeld '" + header + "' fehlt in Zeile " + record.getRecordNumber());
		}
		return result;
	}

	static String formatDateTime(LocalDateTime value) {
		return value != null ? DATE_TIME_FORMAT.format(value) : "";
	}

	static String formatDate(LocalDate value) {
		return value != null ? value.toString() : "";
	}

	static String formatDecimal(BigDecimal value) {
		if (value == null) {
			return "";
		}
		DecimalFormat format = new DecimalFormat("#,##0.##########", GERMAN_SYMBOLS);
		format.setParseBigDecimal(true);
		return format.format(value.stripTrailingZeros());
	}

	static String formatMoney(BigDecimal value) {
		if (value == null) {
			return "";
		}
		DecimalFormat format = new DecimalFormat("#,##0.00########", GERMAN_SYMBOLS);
		return format.format(value);
	}

	static void requireHeaders(CSVParser parser, List<String> headers) {
		List<String> missing = headers.stream().filter(header -> !parser.getHeaderMap().containsKey(header)).toList();
		if (!missing.isEmpty()) {
			throw new GBankingException("Die Portfolio-Performance-Datei enthält nicht alle Pflichtspalten: "
					+ String.join(", ", missing));
		}
	}
}
