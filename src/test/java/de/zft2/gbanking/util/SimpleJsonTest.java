package de.zft2.gbanking.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SimpleJsonTest {

	@Test
	void shouldRoundTripJsonValues() {
		Map<String, Object> source = Map.of("text", "Zeile\n\"Wert\"", "number", 42L, "values",
				List.of(true, false, Map.of("nested", "ä")));

		assertEquals(source, SimpleJson.parse(SimpleJson.write(source)));
	}

	@Test
	void shouldRejectTrailingContent() {
		assertThrows(IllegalArgumentException.class, () -> SimpleJson.parse("{} false"));
	}

	@Test
	void shouldReadNestedValuesAndEscapes() {
		Object parsed = SimpleJson.parse("""
				{"name":"Release\\nNotes","unicode":"A\\u20AC","assets":[{"size":42}],
				 "draft":null,"ratio":-1.25e2}
				""");

		Map<?, ?> root = assertInstanceOf(Map.class, parsed);
		assertEquals("Release\nNotes", root.get("name"));
		assertEquals("A\u20AC", root.get("unicode"));
		assertNull(root.get("draft"));
		assertEquals(-125.0d, assertInstanceOf(Number.class, root.get("ratio")).doubleValue());
		List<?> assets = assertInstanceOf(List.class, root.get("assets"));
		assertEquals(42L, assertInstanceOf(Number.class,
				assertInstanceOf(Map.class, assets.get(0)).get("size")).longValue());
	}

	@Test
	void shouldRejectInvalidInput() {
		IllegalArgumentException nullInput = assertThrows(IllegalArgumentException.class,
				() -> SimpleJson.parse(null));
		assertTrue(nullInput.getMessage().contains("must not be null"));
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> SimpleJson.parse("\"\\u12ZZ\"")).getMessage().contains("Invalid JSON unicode escape"));
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> SimpleJson.parse("-")).getMessage().contains("Invalid JSON number"));
	}
}
