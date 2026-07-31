package de.zft2.gbanking.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
