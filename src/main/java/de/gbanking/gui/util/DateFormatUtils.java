package de.gbanking.gui.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public final class DateFormatUtils {

	private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM.yy");
	private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	private DateFormatUtils() {
	}

	public static String formatShort(LocalDate date) {
		return date == null ? "" : SHORT_DATE.format(date);
	}

	public static String formatShort(Date date) {
		return date == null ? "" : SHORT_DATE.format(Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate());
	}

	public static String formatLong(LocalDate date) {
		return date == null ? "" : LONG_DATE.format(date);
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