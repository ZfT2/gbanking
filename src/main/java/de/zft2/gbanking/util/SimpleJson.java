package de.zft2.gbanking.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimpleJson {

	private final String json;
	private int index;

	private SimpleJson(String json) {
		this.json = json;
	}

	public static Object parse(String json) {
		if (json == null) {
			throw new IllegalArgumentException("JSON input must not be null");
		}
		SimpleJson parser = new SimpleJson(json);
		Object value = parser.parseValue();
		parser.skipWhitespace();
		if (!parser.isEnd()) {
			throw parser.error("Unexpected trailing JSON content");
		}
		return value;
	}

	public static String write(Object value) {
		StringBuilder result = new StringBuilder();
		appendValue(result, value);
		return result.toString();
	}

	private static void appendValue(StringBuilder target, Object value) {
		if (value == null) {
			target.append("null");
		} else if (value instanceof String text) {
			appendString(target, text);
		} else if (value instanceof Number || value instanceof Boolean) {
			target.append(value);
		} else if (value instanceof Map<?, ?> map) {
			appendMap(target, map);
		} else if (value instanceof Iterable<?> iterable) {
			appendIterable(target, iterable);
		} else {
			throw new IllegalArgumentException("Unsupported JSON value type: " + value.getClass().getName());
		}
	}

	private static void appendMap(StringBuilder target, Map<?, ?> map) {
		target.append('{');
		boolean first = true;
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (!(entry.getKey() instanceof String key)) {
				throw new IllegalArgumentException("JSON object keys must be strings");
			}
			if (!first) {
				target.append(',');
			}
			appendString(target, key);
			target.append(':');
			appendValue(target, entry.getValue());
			first = false;
		}
		target.append('}');
	}

	private static void appendIterable(StringBuilder target, Iterable<?> iterable) {
		target.append('[');
		boolean first = true;
		for (Object item : iterable) {
			if (!first) {
				target.append(',');
			}
			appendValue(target, item);
			first = false;
		}
		target.append(']');
	}

	private static void appendString(StringBuilder target, String value) {
		target.append('"');
		for (int i = 0; i < value.length(); i++) {
			char current = value.charAt(i);
			switch (current) {
			case '"' -> target.append("\\\"");
			case '\\' -> target.append("\\\\");
			case '\b' -> target.append("\\b");
			case '\f' -> target.append("\\f");
			case '\n' -> target.append("\\n");
			case '\r' -> target.append("\\r");
			case '\t' -> target.append("\\t");
			default -> appendCharacter(target, current);
			}
		}
		target.append('"');
	}

	private static void appendCharacter(StringBuilder target, char value) {
		if (value < 0x20) {
			target.append(String.format("\\u%04x", (int) value));
		} else {
			target.append(value);
		}
	}

	private Object parseValue() {
		skipWhitespace();
		if (isEnd()) {
			throw error("Unexpected end of JSON");
		}
		return switch (peek()) {
		case '{' -> parseObject();
		case '[' -> parseArray();
		case '"' -> parseString();
		case 't' -> parseLiteral("true", Boolean.TRUE);
		case 'f' -> parseLiteral("false", Boolean.FALSE);
		case 'n' -> parseLiteral("null", null);
		default -> parseNumber();
		};
	}

	private Map<String, Object> parseObject() {
		expect('{');
		Map<String, Object> object = new LinkedHashMap<>();
		skipWhitespace();
		if (consumeIf('}')) {
			return object;
		}
		while (true) {
			skipWhitespace();
			String key = parseString();
			skipWhitespace();
			expect(':');
			object.put(key, parseValue());
			skipWhitespace();
			if (consumeIf('}')) {
				return object;
			}
			expect(',');
		}
	}

	private List<Object> parseArray() {
		expect('[');
		List<Object> array = new ArrayList<>();
		skipWhitespace();
		if (consumeIf(']')) {
			return array;
		}
		while (true) {
			array.add(parseValue());
			skipWhitespace();
			if (consumeIf(']')) {
				return array;
			}
			expect(',');
		}
	}

	private String parseString() {
		expect('"');
		StringBuilder value = new StringBuilder();
		while (!isEnd()) {
			char current = next();
			if (current == '"') {
				return value.toString();
			}
			if (current != '\\') {
				value.append(current);
				continue;
			}
			if (isEnd()) {
				throw error("Incomplete JSON escape sequence");
			}
			char escaped = next();
			switch (escaped) {
			case '"', '\\', '/' -> value.append(escaped);
			case 'b' -> value.append('\b');
			case 'f' -> value.append('\f');
			case 'n' -> value.append('\n');
			case 'r' -> value.append('\r');
			case 't' -> value.append('\t');
			case 'u' -> value.append(parseUnicodeEscape());
			default -> throw error("Unsupported JSON escape sequence: \\" + escaped);
			}
		}
		throw error("Unterminated JSON string");
	}

	private char parseUnicodeEscape() {
		if (index + 4 > json.length()) {
			throw error("Incomplete JSON unicode escape");
		}
		String hex = json.substring(index, index + 4);
		index += 4;
		try {
			return (char) Integer.parseInt(hex, 16);
		} catch (NumberFormatException exception) {
			throw error("Invalid JSON unicode escape: " + hex);
		}
	}

	private Number parseNumber() {
		int start = index;
		if (peek() == '-') {
			index++;
		}
		consumeDigits();
		boolean floatingPoint = false;
		if (!isEnd() && peek() == '.') {
			floatingPoint = true;
			index++;
			consumeDigits();
		}
		if (!isEnd() && (peek() == 'e' || peek() == 'E')) {
			floatingPoint = true;
			index++;
			if (!isEnd() && (peek() == '+' || peek() == '-')) {
				index++;
			}
			consumeDigits();
		}
		if (start == index) {
			throw error("Expected JSON value");
		}
		String number = json.substring(start, index);
		try {
			return floatingPoint ? (Number) Double.valueOf(number) : Long.valueOf(number);
		} catch (NumberFormatException exception) {
			throw error("Invalid JSON number: " + number);
		}
	}

	private void consumeDigits() {
		while (!isEnd() && Character.isDigit(peek())) {
			index++;
		}
	}

	private Object parseLiteral(String literal, Object value) {
		if (!json.startsWith(literal, index)) {
			throw error("Expected JSON literal " + literal);
		}
		index += literal.length();
		return value;
	}

	private void skipWhitespace() {
		while (!isEnd() && Character.isWhitespace(peek())) {
			index++;
		}
	}

	private void expect(char expected) {
		if (isEnd() || next() != expected) {
			throw error("Expected '" + expected + "'");
		}
	}

	private boolean consumeIf(char expected) {
		if (!isEnd() && peek() == expected) {
			index++;
			return true;
		}
		return false;
	}

	private char next() {
		return json.charAt(index++);
	}

	private char peek() {
		return json.charAt(index);
	}

	private boolean isEnd() {
		return index >= json.length();
	}

	private IllegalArgumentException error(String message) {
		return new IllegalArgumentException(message + " at JSON position " + index);
	}
}
