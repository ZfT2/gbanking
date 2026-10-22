package de.zft2.gbanking.util;

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

	private DecimalText() {
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
