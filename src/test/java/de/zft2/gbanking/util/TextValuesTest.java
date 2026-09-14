package de.zft2.gbanking.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	@Test
	void shouldHandleCommonTextChecksAndNormalizations() {
		assertTrue(TextValues.hasText(" Wert "));
		assertFalse(TextValues.hasText(" \t"));
		assertEquals("Inhalt", TextValues.removeLeadingBom("\uFEFFInhalt"));
		assertEquals("Inhalt", TextValues.removeLeadingBom("Inhalt"));
		assertNull(TextValues.removeLeadingBom(null));
	}

	@Test
	void shouldPreferMixedCaseTextForReadability() {
		assertEquals(0, TextValues.readabilityScore("BANK"));
		assertEquals(1, TextValues.readabilityScore("bank"));
		assertEquals(2, TextValues.readabilityScore("Bank"));
		assertTrue(TextValues.isMoreReadable("Bank", "BANK"));
		assertFalse(TextValues.isMoreReadable("BANK", "Bank"));
	}
}
