package de.zft2.gbanking.service.stock;

import java.io.IOException;
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
import java.util.Locale;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

final class PortfolioPerformanceCsvSupport {

	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
	private static final DecimalFormatSymbols GERMAN_SYMBOLS = DecimalFormatSymbols.getInstance(Locale.GERMANY);

	private PortfolioPerformanceCsvSupport() {
	}

	static CSVPrinter printer(Path file, String... headers) throws IOException {
		Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
		CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader(headers)
				.setRecordSeparator("\r\n").get();
		return new CSVPrinter(writer, format);
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

}
