package de.zft2.gbanking.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TextValuesTest {

	@Test
	void trimToNull_shouldNormalizeTextAndBlankValues() {
		assertNull(TextValues.trimToNull(null));
		assertNull(TextValues.trimToNull(" \t "));
		assertNull(TextValues.trimToNull("\u2003"));
		assertEquals("Text", TextValues.trimToNull("  Text  "));
	}

	@Test
	void firstNonBlank_shouldReturnFirstNormalizedText() {
		assertEquals("Erster Wert", TextValues.firstNonBlank(null, " ", "  Erster Wert  ", "Zweiter Wert"));
		assertNull(TextValues.firstNonBlank(null, "\t"));
	}
}
