package de.zft2.gbanking.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SimpleJsonParserTest {

	@Test
	void parseShouldReadNestedObjectsArraysEscapesAndNumbers() throws Exception {
		Object parsed = SimpleJsonParser.parse("""
				{
				  "name": "Release\\nNotes",
				  "unicode": "A\\u20AC",
				  "assets": [
				    {
				      "name": "gbanking.zip",
				      "size": 42,
				      "prerelease": false
				    }
				  ],
				  "draft": null,
				  "ratio": -1.25e2
				}
				""");

		Map<?, ?> root = assertInstanceOf(Map.class, parsed);
		assertEquals("Release\nNotes", root.get("name"));
		assertEquals("A\u20AC", root.get("unicode"));
		assertNull(root.get("draft"));
		assertEquals(-125.0d, assertInstanceOf(Number.class, root.get("ratio")).doubleValue());

		List<?> assets = assertInstanceOf(List.class, root.get("assets"));
		Map<?, ?> asset = assertInstanceOf(Map.class, assets.get(0));
		assertEquals("gbanking.zip", asset.get("name"));
		assertEquals(42L, assertInstanceOf(Number.class, asset.get("size")).longValue());
		assertEquals(Boolean.FALSE, asset.get("prerelease"));
	}

	@Test
	void parseShouldRejectNullInputAndTrailingContent() {
		UpdateException nullInput = assertThrows(UpdateException.class, () -> SimpleJsonParser.parse(null));
		assertTrue(nullInput.getMessage().contains("must not be null"));

		UpdateException trailingContent = assertThrows(UpdateException.class, () -> SimpleJsonParser.parse("{\"ok\": true} false"));
		assertTrue(trailingContent.getMessage().contains("Unexpected trailing JSON content"));
	}

	@Test
	void parseShouldRejectMalformedEscapesAndNumbers() {
		UpdateException malformedEscape = assertThrows(UpdateException.class, () -> SimpleJsonParser.parse("\"\\u12ZZ\""));
		assertTrue(malformedEscape.getMessage().contains("Invalid JSON unicode escape"));

		UpdateException malformedNumber = assertThrows(UpdateException.class, () -> SimpleJsonParser.parse("-"));
		assertTrue(malformedNumber.getMessage().contains("Invalid JSON number"));
	}
}
