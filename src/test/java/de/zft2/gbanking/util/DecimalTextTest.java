package de.zft2.gbanking.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class DecimalTextTest {

	@Test
	void toGermanDecimal_shouldReplaceGermanDecimalSeparator() {
		assertEquals("12.34", DecimalText.toGermanDecimal("12,34"));
		assertEquals("12.34", DecimalText.toGermanDecimal("12.34"));
		assertEquals("12", DecimalText.toGermanDecimal("12"));
	}

	@Test
	void toGermanDecimal_shouldTreatLastSeparatorAsDecimalSeparator() {
		assertEquals("1234.56", DecimalText.toGermanDecimal("1.234,56"));
		assertEquals("1234.56", DecimalText.toGermanDecimal("1,234.56"));
	}

	@Test
	void toDotDecimal_shouldOnlyReplaceTheComma() {
		assertEquals("1.234.56", DecimalText.toDotDecimal("1.234,56"));
	}

	@Test
	void parseGermanAmount_shouldSupportGermanAndPlainNotation() {
		assertEquals(new BigDecimal("1234.56"), DecimalText.parseGermanAmount("1.234,56"));
		assertEquals(new BigDecimal("1234.56"), DecimalText.parseGermanAmount("1234,56"));
		assertEquals(new BigDecimal("1234.56"), DecimalText.parseGermanAmount("1234.56"));
		assertEquals(new BigDecimal("1234"), DecimalText.parseGermanAmount("1.234"));
		assertEquals(new BigDecimal("1234"), DecimalText.parseGermanAmount("+1.234"));
		assertEquals(new BigDecimal("-1234.5"), DecimalText.parseGermanAmount("- 1\u00a0234,5"));
		assertNull(DecimalText.parseGermanAmount(" "));
		assertThrows(NumberFormatException.class, () -> DecimalText.parseGermanAmount("abc"));
	}
}
