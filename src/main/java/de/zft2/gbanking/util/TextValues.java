package de.zft2.gbanking.util;

/**
 * Central helper for common string operations.
 *
 * <p>
 * The methods are used across all layers of the application so that null handling and trimming
 * behave consistently. The helper is stateless and therefore safe to use from multiple threads.
 * </p>
 */
public final class TextValues {

	private TextValues() {
	}

	/**
	 * Removes leading and trailing whitespace and returns {@code null} for blank input.
	 *
	 * @param value the text to trim, may be {@code null}
	 * @return the trimmed text or {@code null} if the input is {@code null} or blank
	 */
	public static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	/**
	 * Removes leading and trailing whitespace and returns an empty string for blank input.
	 *
	 * @param value the text to trim, may be {@code null}
	 * @return the trimmed text or an empty string if the input is {@code null} or blank
	 */
	public static String trimToBlank(String value) {
		String trimmedValue = trimToNull(value);
		return trimmedValue != null ? trimmedValue : "";
	}

	/**
	 * Returns the given text or an empty string if the input is {@code null}. The text is not
	 * trimmed.
	 *
	 * @param value the text to convert, may be {@code null}
	 * @return the text itself or an empty string
	 */
	public static String nullToEmpty(String value) {
		return value != null ? value : "";
	}

	/**
	 * Appends the trimmed text as a new paragraph, separated from existing content by a blank line.
	 * Blank text is ignored.
	 *
	 * @param builder the target builder
	 * @param text    the paragraph text, may be {@code null}
	 */
	public static void appendParagraph(StringBuilder builder, String text) {
		if (isBlank(text)) {
			return;
		}
		if (!builder.isEmpty()) {
			builder.append(System.lineSeparator()).append(System.lineSeparator());
		}
		builder.append(text.trim());
	}

	/**
	 * Checks whether the given text is {@code null} or contains only whitespace.
	 *
	 * @param value the text to check, may be {@code null}
	 * @return {@code true} if the text is {@code null} or blank
	 */
	public static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	/**
	 * Checks whether the given text contains at least one non-whitespace character.
	 *
	 * @param value the text to check, may be {@code null}
	 * @return {@code true} if the text is neither {@code null} nor blank
	 */
	public static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	/**
	 * Returns the first value that contains text, trimmed. If no value contains text, {@code null}
	 * is returned.
	 *
	 * @param values the candidates to check
	 * @return the first non-blank value, trimmed, or {@code null}
	 */
	public static String firstNonBlank(String... values) {
		for (String value : values) {
			String trimmedValue = trimToNull(value);
			if (trimmedValue != null) {
				return trimmedValue;
			}
		}
		return null;
	}

	/**
	 * Returns the first value that contains text without modifying it. If no value contains text,
	 * {@code null} is returned.
	 *
	 * @param values the candidates to check
	 * @return the first value that contains text or {@code null}
	 */
	public static String firstWithText(String... values) {
		for (String value : values) {
			if (hasText(value)) {
				return value;
			}
		}
		return null;
	}

	/**
	 * Returns the first value that contains text, trimmed. If no value contains text, an empty
	 * string is returned.
	 *
	 * @param values the candidates to check
	 * @return the first non-blank value, trimmed, or an empty string
	 */
	public static String firstNonBlankOrEmpty(String... values) {
		return nullToEmpty(firstNonBlank(values));
	}

	/**
	 * Returns the first value that contains text without modifying it. If no value contains text,
	 * the given default value is returned.
	 *
	 * @param defaultValue the fallback to use if no candidate contains text
	 * @param values       the candidates to check
	 * @return the first value that contains text or the default value
	 */
	public static String firstWithTextOrDefault(String defaultValue, String... values) {
		String value = firstWithText(values);
		return value != null ? value : defaultValue;
	}

	/**
	 * Compares two texts ignoring case. Two {@code null} values are considered equal, a
	 * {@code null} value is never equal to a non-{@code null} value.
	 *
	 * @param left  the first text, may be {@code null}
	 * @param right the second text, may be {@code null}
	 * @return {@code true} if both texts are equal ignoring case
	 */
	public static boolean equalsIgnoreCase(String left, String right) {
		if (left == null || right == null) {
			return left == right;
		}
		return left.equalsIgnoreCase(right);
	}

	/**
	 * Compares two texts ignoring case. Both texts have to be present, otherwise the result is
	 * {@code false}.
	 *
	 * @param left  the first text, may be {@code null}
	 * @param right the second text, may be {@code null}
	 * @return {@code true} if both texts are present and equal ignoring case
	 */
	public static boolean equalsIgnoreCaseNonNull(String left, String right) {
		return left != null && right != null && left.equalsIgnoreCase(right);
	}

	public static String removeLeadingBom(String value) {
		return value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
	}

	public static boolean isMoreReadable(String candidate, String currentValue) {
		return readabilityScore(candidate) > readabilityScore(currentValue);
	}

	public static int readabilityScore(String value) {
		boolean upperCaseLetter = false;
		boolean lowerCaseLetter = false;
		if (value != null) {
			for (int index = 0; index < value.length(); index++) {
				char character = value.charAt(index);
				upperCaseLetter |= Character.isUpperCase(character);
				lowerCaseLetter |= Character.isLowerCase(character);
			}
		}
		if (upperCaseLetter && lowerCaseLetter) {
			return 2;
		}
		return lowerCaseLetter ? 1 : 0;
	}
}
