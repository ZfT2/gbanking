package de.zft2.gbanking.file.imp.csv;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import de.zft2.gbanking.file.BookingCsvFormat;
import de.zft2.gbanking.util.TypeConverter;

public final class CsvImportValueParser {

	private CsvImportValueParser() {
	}

	public static BigDecimal parseDecimal(String value, CsvImportDefinition definition) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim().replace("\u00A0", "").replace(" ", "");
		if ("auto".equalsIgnoreCase(definition.getDecimalSeparator())) {
			return new BigDecimal(normalizeDecimal(normalized));
		}
		String decimalSeparator = definition.getDecimalSeparator();
		String thousandSeparator = definition.getThousandSeparator();
		if (!thousandSeparator.isEmpty() && !thousandSeparator.equals(decimalSeparator)) {
			normalized = normalized.replace(thousandSeparator, "");
		}
		return new BigDecimal(".".equals(decimalSeparator) ? normalized : normalized.replace(decimalSeparator, "."));
	}

	public static LocalDateTime parseDateTime(String value, CsvImportDefinition definition) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim();
		try {
			return LocalDateTime.parse(normalized);
		} catch (DateTimeParseException exception) {
			return parseDate(normalized, definition).atStartOfDay();
		}
	}

	public static LocalDate parseDate(String value, CsvImportDefinition definition) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String dateOrder = definition.getDateOrder();
		if (dateOrder == null || dateOrder.isBlank()) {
			LocalDate date = BookingCsvFormat.parseDate(value);
			if (date != null) {
				return date;
			}
			throw new DateTimeException("Unsupported date format");
		}
		return parseOrderedDate(value, dateOrder);
	}

	private static String normalizeDecimal(String value) {
		if (value.contains(",") && value.contains(".")) {
			return value.lastIndexOf(',') > value.lastIndexOf('.')
					? value.replace(".", "").replace(',', '.') : value.replace(",", "");
		}
		return value.replace(',', '.');
	}

	private static LocalDate parseOrderedDate(String value, String dateOrder) {
		String order = dateOrder.trim().toUpperCase(Locale.ROOT);
		String[] parts = value.trim().split("\\D+");
		if (parts.length != 3 || order.length() != 3 || !characters(order).equals(Set.of('T', 'M', 'J'))) {
			throw new DateTimeException("Unsupported date format");
		}
		int day = Integer.parseInt(parts[order.indexOf('T')]);
		int month = Integer.parseInt(parts[order.indexOf('M')]);
		int year = TypeConverter.expandTwoDigitYear(Integer.parseInt(parts[order.indexOf('J')]));
		return LocalDate.of(year, month, day);
	}

	private static Set<Character> characters(String value) {
		Set<Character> result = new HashSet<>();
		for (int index = 0; index < value.length(); index++) {
			result.add(value.charAt(index));
		}
		return result;
	}
}
