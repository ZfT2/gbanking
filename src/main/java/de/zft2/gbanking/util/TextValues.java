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
}
