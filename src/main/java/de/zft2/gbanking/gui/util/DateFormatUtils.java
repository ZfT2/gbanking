package de.zft2.gbanking.gui.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

import de.zft2.gbanking.util.TextValues;

public final class DateFormatUtils {

	private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM.yy");
	private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private DateFormatUtils() {
	}

	public static String formatShort(LocalDate date) {
		return date == null ? "" : SHORT_DATE.format(date);
	}

	public static String formatLong(LocalDate date) {
		return date == null ? "" : LONG_DATE.format(date);
	}

	/**
	 * Parses a date in the format dd.MM.yyyy.
	 *
	 * @param text the text to parse
	 * @return the parsed date or {@code null} for a blank text
	 * @throws java.time.format.DateTimeParseException if the text is not a valid date
	 */
	public static LocalDate parseLong(String text) {
		String trimmedText = TextValues.trimToNull(text);
		return trimmedText == null ? null : LocalDate.parse(trimmedText, LONG_DATE);
	}

	public static String formatDateTime(LocalDateTime dateTime) {
		return dateTime == null ? "" : DATE_TIME.format(dateTime);
	}

	public static String formatRetrievalAt(LocalDateTime retrievedAt, LocalDate updatedAt, LocalDate today) {
		return retrievedAt != null && retrievedAt.toLocalDate().equals(today) ? TIME.format(retrievedAt) : formatLong(updatedAt);
	}

	public static String formatBookingAndValue(LocalDate bookingDate, LocalDate valueDate) {
		if (bookingDate == null) {
			return "";
		}
		if (valueDate != null && !valueDate.equals(bookingDate)) {
			return formatShort(bookingDate) + "\n(" + formatShort(valueDate) + ")";
		}
		return formatShort(bookingDate);
	}
}
