package de.zft2.gbanking.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class IbanStructureTest {

	@Test
	void extractAccountNumber_shouldReturnGermanAccountNumber() {
		assertEquals("5407324931", IbanStructure.extractAccountNumber("DE44 5001 0517 5407 3249 31"));
		assertEquals("5407324931", IbanStructure.extractAccountNumber("DE44500105175407324931"));
	}

	@Test
	void extractAccountNumber_shouldReturnNorwegianAccountNumberIncludingCheckDigit() {
		assertEquals("1117947", IbanStructure.extractAccountNumber("NO9386011117947"));
		assertEquals("1117947", IbanStructure.extractAccountNumber("NO93 8601 1117 947"));
	}

	@Test
	void extractAccountNumber_shouldReturnAccountNumberOfOtherCountries() {
		assertEquals("31926819", IbanStructure.extractAccountNumber("GB29NWBK60161331926819"));
		assertEquals("00234573201", IbanStructure.extractAccountNumber("AT611904300234573201"));
		assertEquals("0417164300", IbanStructure.extractAccountNumber("NL91ABNA0417164300"));
	}

	@Test
	void extractAccountNumber_shouldRejectInvalidOrUnknownIbans() {
		assertNull(IbanStructure.extractAccountNumber(null));
		assertNull(IbanStructure.extractAccountNumber(" "));
		assertNull(IbanStructure.extractAccountNumber("4711"));
		assertNull(IbanStructure.extractAccountNumber("DE88 3704 0044 0532 0130 00"));
		assertNull(IbanStructure.extractAccountNumber("XX9386011117947"));
	}

	@Test
	void normalizeIban_shouldReturnElectronicFormatOrNull() {
		assertEquals("DE44500105175407324931", IbanStructure.normalizeIban("DE44 5001 0517 5407 3249 31"));
		assertEquals("DE44500105175407324931", IbanStructure.normalizeIban("de44500105175407324931"));
		assertNull(IbanStructure.normalizeIban("4711"));
		assertNull(IbanStructure.normalizeIban(null));
	}

	@Test
	void extractAccountNumber_shouldReturnTheAccountNumberFieldOnly() {
		// The Icelandic national identification number does not belong to the account number.
		assertEquals("007654", IbanStructure.extractAccountNumber("IS140159260076545510730339"));
		// The French RIB key belongs to the account number, the branch code does not.
		assertEquals("0500013M02606", IbanStructure.extractAccountNumber("FR1420041010050500013M02606"));
	}
}
