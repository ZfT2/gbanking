package de.zft2.gbanking.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IbanCalculatorTest {

	@Test
	void calculateIbanShouldCreateGermanIbanWithChecksumAndPaddedAccountNumber() {
		assertEquals("DE89370400440532013000", IbanCalculator.calculateIban("532013000", "37040044"));
	}

	@Test
	void calculateIbanShouldIgnoreWhitespaceInInput() {
		assertEquals("DE89370400440532013000", IbanCalculator.calculateIban(" 532 013 000 ", "3704 0044"));
	}

	@Test
	void calculateIbanShouldRejectInvalidInput() {
		assertNull(IbanCalculator.calculateIban(null, "37040044"));
		assertNull(IbanCalculator.calculateIban("532013000", null));
		assertNull(IbanCalculator.calculateIban("532013000", "3704004"));
		assertNull(IbanCalculator.calculateIban("532013000", "3704004A"));
		assertNull(IbanCalculator.calculateIban("12345678901", "37040044"));
		assertNull(IbanCalculator.calculateIban("12345A", "37040044"));
	}

	@Test
	void isValidIbanShouldAcceptInternationalIbansAndFormatting() {
		assertTrue(IbanCalculator.isValidIban("DE89 3704 0044 0532 0130 00"));
		assertTrue(IbanCalculator.isValidIban("gb29nwbk60161331926819"));
		assertTrue(IbanCalculator.isValidIban("FR14\u00A02004\u00A01010\u00A00505\u00A00001\u00A03M02\u00A0606"));
	}

	@Test
	void isValidIbanShouldRejectInvalidChecksumsAndFormats() {
		assertFalse(IbanCalculator.isValidIban("DE88 3704 0044 0532 0130 00"));
		assertFalse(IbanCalculator.isValidIban("DE8937040044053201300"));
		assertFalse(IbanCalculator.isValidIban("DE89-3704-0044-0532-0130-00"));
		assertFalse(IbanCalculator.isValidIban(null));
		assertFalse(IbanCalculator.isValidIban(" "));
	}
}
