package de.zft2.gbanking.enablebanking;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class EnablebankingJson {

	private EnablebankingJson() {
	}

	static BigDecimal decimal(Object value) {
		try {
			return value != null ? new BigDecimal(value.toString()) : null;
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	static String firstText(Map<String, Object> values, String... keys) {
		for (String key : keys) {
			String value = string(values.get(key));
			if (hasText(value) && !"{}".equals(value)) {
				return value;
			}
		}
		return null;
	}

	static String firstText(String... values) {
		for (String value : values) {
			if (hasText(value)) {
				return value;
			}
		}
		return null;
	}

	static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	static List<?> list(Object value) {
		return value instanceof List<?> result ? result : List.of();
	}

	static Number number(Object value) {
		return value instanceof Number result ? result : null;
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> object(Object value) {
		return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
	}

	static List<Map<String, Object>> objectList(Object value) {
		return list(value).stream().filter(item -> item instanceof Map<?, ?>).map(item -> object(item)).toList();
	}

	static Map<String, Object> requireObject(Object value) {
		if (value instanceof Map<?, ?>) {
			return object(value);
		}
		throw new EnablebankingException("Enablebanking hat eine unerwartete Antwort geliefert.");
	}

	static String string(Object value) {
		return value instanceof String text ? text : value != null && !(value instanceof Map<?, ?>) ? value.toString() : null;
	}

	static String trimToNull(String value) {
		return hasText(value) ? value.trim() : null;
	}

	static String upper(String value) {
		return value != null ? value.toUpperCase(Locale.ROOT) : "";
	}
}
