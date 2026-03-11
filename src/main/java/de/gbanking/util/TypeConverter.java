package de.gbanking.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sqlite.SQLiteConfig;

public class TypeConverter {

	private static Logger log = LogManager.getLogger(TypeConverter.class);

	private TypeConverter() {
	}

	public static final String DATE_PATTERN_SHORT = "dd.MM.yy";
	public static final String DATE_PATTERN_LONG = "dd.MM.yyyy";
	public static final String TIMESTAMP_PATTERN = "dd.MM.yy HH:mm:ss.SSS";

	public static final String ISO8601_PATTERN = SQLiteConfig.DEFAULT_DATE_STRING_FORMAT; // "yyyy-MM-dd HH:mm:ss.SSS";

	public static String toDateStringShort(Calendar date) {
		return date != null ? new SimpleDateFormat(DATE_PATTERN_SHORT).format(date.getTime()) : null;
	}

	public static String toDateStringLong(Calendar date) {
		return date != null ? new SimpleDateFormat(DATE_PATTERN_LONG).format(date.getTime()) : null;
	}

	public static String toTimestampString(Calendar date) {
		return date != null ? new SimpleDateFormat(TIMESTAMP_PATTERN).format(date.getTime()) : null;
	}

	public static String toTimestampStringNow() {
		return new SimpleDateFormat(TIMESTAMP_PATTERN).format(Calendar.getInstance().getTime());
	}

//	public static String toISO8601DateStringShort(Calendar cal) {
//		if (cal == null) {
//			return null;
//		}
//		cal.set(Calendar.HOUR_OF_DAY, 0);
//		cal.set(Calendar.MINUTE, 0);
//		cal.set(Calendar.SECOND, 0);
//		cal.set(Calendar.MILLISECOND, 0);
//		return new SimpleDateFormat(ISO8601_PATTERN).format(cal.getTime());
//	}
//	
//	public static String toISO8601DateStringLong(Calendar cal) {
//		return cal != null ? new SimpleDateFormat(ISO8601_PATTERN).format(cal.getTime()) : null;
//	}
//	
//	public static String toISO8601TimestampStringNow() {
//		return new SimpleDateFormat(ISO8601_PATTERN).format(Calendar.getInstance().getTime());
//	}

	public static java.sql.Date toSqlDateShort(Calendar cal) {
		if (cal == null) {
			return null;
		}
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return new java.sql.Date(cal.getTime().getTime());
	}

	public static java.sql.Date toSqlDateLong(Calendar cal) {
		return cal != null ? new java.sql.Date(cal.getTime().getTime()) : null;
	}

	public static java.sql.Date toSqlDateNow() {
		return new java.sql.Date(System.currentTimeMillis());
	}

	public static Calendar toCalendarFromDateStrShort(String date) {
		return toCalendar(DATE_PATTERN_SHORT, date);
	}

	public static Calendar toCalendarFromDateStr(String date) {
		return toCalendar(DATE_PATTERN_LONG, date);
	}

	public static Calendar toCalendarFromTimestampStr(String date) {
		return toCalendar(TIMESTAMP_PATTERN, date);
	}

	public static Calendar toCalendarFromDate(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		return cal;
	}

	private static Calendar toCalendar(String pattern, String date) {
		if (date == null) {
			return null;
		}
		Calendar cal = Calendar.getInstance();
		try {
			cal.setTime(new SimpleDateFormat(pattern).parse(date));
		} catch (ParseException e) {
			log.error("Error parsing Date String to Calendar: {}", date);
			return null;
		}

		return cal;
	}

	public static Calendar toCalendarFromSqlDate(java.sql.Date sqlDate) {
		if (sqlDate == null) {
			return null;
		}
		Calendar cal = Calendar.getInstance();
		cal.setTime(sqlDate);
		return cal;
	}

	public static List<String> toList(String commaSeparatedString) {
		if (commaSeparatedString != null) {
			return Arrays.asList(commaSeparatedString.split(","));
		}
		return Collections.emptyList();
	}

	public static String toCommaSeparatedString(List<String> list) {
		if (list != null) {
			return String.join(",", list);
		}
		return null;
	}

	public static Boolean toBoolean(String boolValue) {
		if ("yes".equalsIgnoreCase(boolValue) || "ja".equalsIgnoreCase(boolValue) || "true".equalsIgnoreCase(boolValue) || "1".equalsIgnoreCase(boolValue)) {
			return true;
		}
		if ("no".equalsIgnoreCase(boolValue) || "nein".equalsIgnoreCase(boolValue) || "false".equalsIgnoreCase(boolValue) || "0".equalsIgnoreCase(boolValue)) {
			return false;
		}
		return null;
	}

}
