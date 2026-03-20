package de.gbanking.gui.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public final class DateFormatUtils {

	private static final SimpleDateFormat SHORT_DATE = new SimpleDateFormat("dd.MM.yy");
	private static final SimpleDateFormat LONG_DATE = new SimpleDateFormat("dd.MM.yyyy");

	private DateFormatUtils() {
	}

	public static String formatShort(Calendar calendar) {
		return calendar == null ? "" : SHORT_DATE.format(calendar.getTime());
	}

	public static String formatShort(Date date) {
		return date == null ? "" : SHORT_DATE.format(date);
	}

	public static String formatLong(Calendar calendar) {
		return calendar == null ? "" : LONG_DATE.format(calendar.getTime());
	}

	public static String formatBookingAndValue(Calendar bookingDate, Calendar valueDate) {
		if (bookingDate == null) {
			return "";
		}
		if (valueDate != null && !valueDate.equals(bookingDate)) {
			return formatShort(bookingDate) + "\n(" + formatShort(valueDate) + ")";
		}
		return formatShort(bookingDate);
	}
}