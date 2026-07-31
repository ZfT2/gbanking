package de.zft2.gbanking.update;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SimpleJsonParser {

	private final String json;
	private int index;

	private SimpleJsonParser(String json) {
		this.json = json;
	}

	static Object parse(String json) throws UpdateException {
		if (json == null) {
			throw new UpdateException("JSON input must not be null");
		}

		SimpleJsonParser parser = new SimpleJsonParser(json);
		Object value = parser.parseValue();
		parser.skipWhitespace();
		if (!parser.isEnd()) {
			throw parser.error("Unexpected trailing JSON content");
		}
		return value;
	}

	private Object parseValue() throws UpdateException {
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

	private Map<String, Object> parseObject() throws UpdateException {
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

	private List<Object> parseArray() throws UpdateException {
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

	private String parseString() throws UpdateException {
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

	private char parseUnicodeEscape() throws UpdateException {
		if (index + 4 > json.length()) {
			throw error("Incomplete JSON unicode escape");
		}
		String hex = json.substring(index, index + 4);
		index += 4;
		try {
			return (char) Integer.parseInt(hex, 16);
		} catch (NumberFormatException e) {
			throw error("Invalid JSON unicode escape: " + hex);
		}
	}

	private Object parseNumber() throws UpdateException {
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
		} catch (NumberFormatException e) {
			throw error("Invalid JSON number: " + number);
		}
	}

	private void consumeDigits() {
		while (!isEnd() && Character.isDigit(peek())) {
			index++;
		}
	}

	private Object parseLiteral(String literal, Object value) throws UpdateException {
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

	private void expect(char expected) throws UpdateException {
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

	private UpdateException error(String message) {
		return new UpdateException(message + " at JSON position " + index);
	}
}
