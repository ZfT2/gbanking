package de.zft2.gbanking.gui.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DateFormatUtils {

	private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM.yy");
	private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

	private DateFormatUtils() {
	}

	public static String formatShort(LocalDate date) {
		return date == null ? "" : SHORT_DATE.format(date);
	}

	public static String formatLong(LocalDate date) {
		return date == null ? "" : LONG_DATE.format(date);
	}

	public static String formatDateTime(LocalDateTime dateTime) {
		return dateTime == null ? "" : DATE_TIME.format(dateTime);
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
