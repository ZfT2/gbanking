package de.zft2.gbanking.util;

public final class TextValues {

	private TextValues() {
	}

	public static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	public static String firstNonBlank(String... values) {
		for (String value : values) {
			String trimmedValue = trimToNull(value);
			if (trimmedValue != null) {
				return trimmedValue;
			}
		}
		return null;
	}

	public static boolean hasText(String value) {
		return value != null && !value.isBlank();
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
