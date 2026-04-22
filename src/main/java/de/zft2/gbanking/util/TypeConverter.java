package de.zft2.gbanking.util;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
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

	public static final String ISO8601_PATTERN = SQLiteConfig.DEFAULT_DATE_STRING_FORMAT;

	private static final DateTimeFormatter DATE_FORMATTER_SHORT = DateTimeFormatter.ofPattern(DATE_PATTERN_SHORT);
	private static final DateTimeFormatter DATE_FORMATTER_LONG = DateTimeFormatter.ofPattern(DATE_PATTERN_LONG);

	public static String toDateStringShort(LocalDate date) {
		return date != null ? DATE_FORMATTER_SHORT.format(date) : null;
	}

	public static String toDateStringLong(LocalDate date) {
		return date != null ? DATE_FORMATTER_LONG.format(date) : null;
	}

	public static String toTimestampString(LocalDate date) {
		return toDateStringLong(date);
	}

	public static String toTimestampStringNow() {
		return toDateStringLong(LocalDate.now());
	}

	public static Date toSqlDateShort(LocalDate date) {
		return date != null ? Date.valueOf(date) : null;
	}

	public static Date toSqlDateLong(LocalDate date) {
		return date != null ? Date.valueOf(date) : null;
	}

	public static Date toSqlDateNow() {
		return Date.valueOf(LocalDate.now());
	}

	public static LocalDate toLocalDateFromDateStrShort(String date) {
		return toLocalDate(DATE_FORMATTER_SHORT, date);
	}

	public static LocalDate toLocalDateFromDateStr(String date) {
		return toLocalDate(DATE_FORMATTER_LONG, date);
	}

	public static LocalDate toLocalDateFromDateStrFlexible(String date) {
		return toLocalDate(date, DATE_FORMATTER_SHORT, DATE_FORMATTER_LONG);
	}

	public static LocalDate toLocalDateFromTimestampStr(String date) {
		return toLocalDateFromDateStr(date);
	}

	public static LocalDate toLocalDateFromDate(java.util.Date date) {
		return date == null ? null : Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
	}

	private static LocalDate toLocalDate(DateTimeFormatter formatter, String date) {
		return toLocalDate(date, formatter);
	}

	private static LocalDate toLocalDate(String date, DateTimeFormatter... formatters) {
		if (date == null) {
			return null;
		}

		for (DateTimeFormatter formatter : formatters) {
			try {
				return LocalDate.from(formatter.parse(date));
			} catch (Exception e) {
				// try next formatter
			}
		}

		log.error("Error parsing Date String to LocalDate: {}", date);
		return null;
	}

	public static LocalDate toLocalDateFromSqlDate(Date sqlDate) {
		return sqlDate == null ? null : sqlDate.toLocalDate();
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
