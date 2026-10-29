package de.zft2.gbanking.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Central helper for the conversion of decimal numbers in German notation.
 *
 * <p>
 * German amounts use a comma as decimal separator and a dot as thousand separator. For the
 * conversion to the Java notation the thousand separator has to be removed and the decimal
 * separator has to be replaced by a dot.
 * </p>
 */
public final class DecimalText {

	private static final Pattern GERMAN_GROUPED_AMOUNT = Pattern.compile("[-+]?\\d{1,3}(\\.\\d{3})+");

	private DecimalText() {
	}

	/**
	 * Parses an amount entered in German notation. Spaces are ignored. If the text contains a comma, it
	 * is used as decimal separator; without a comma, dots are treated as thousand separators only if
	 * they group exactly three digits (e.g. {@code 1.234}), otherwise as decimal separator.
	 *
	 * @param value the text to parse, may be {@code null}
	 * @return the parsed amount or {@code null} for blank input
	 * @throws NumberFormatException if the text is not a valid amount
	 */
	public static BigDecimal parseGermanAmount(String value) {
		String normalized = TextValues.trimToNull(value);
		if (normalized == null) {
			return null;
		}
		String compact = normalized.replace(" ", "").replace("\u00a0", "");
		if (compact.contains(",") || GERMAN_GROUPED_AMOUNT.matcher(compact).matches()) {
			return new BigDecimal(toDotDecimal(compact.replace(".", "")));
		}
		return new BigDecimal(compact);
	}

	/**
	 * Converts a decimal number in German notation into the Java notation. If the text contains
	 * both separators, the last occurring separator is treated as decimal separator.
	 *
	 * @param value the text to convert
	 * @return the text in Java notation
	 */
	public static String toGermanDecimal(String value) {
		if (value.contains(",") && value.contains(".")) {
			return value.lastIndexOf(',') > value.lastIndexOf('.')
					? value.replace(".", "").replace(',', '.')
					: value.replace(",", "");
		}
		return toDotDecimal(value);
	}

	/**
	 * Converts a decimal number with a German decimal separator into the Java notation.
	 *
	 * @param value the text to convert
	 * @return the text in Java notation
	 */
	public static String toDotDecimal(String value) {
		return value.replace(',', '.');
	}
}
