package de.zft2.gbanking.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {

	@Test
	void shouldMaskIbanAndIdentifiersAtTail() {
		assertEquals("******************4931", SensitiveDataMasker.maskIban("DE44500105175407324931"));
		assertEquals("******789", SensitiveDataMasker.maskAccountNumber("123456789"));
		assertEquals("****56", SensitiveDataMasker.maskIdentifier("123456"));
	}

	@Test
	void shouldNormalizeWhitespaceAndHandleShortValues() {
		assertEquals("******************4931", SensitiveDataMasker.maskIban("DE44 5001 0517 5407 3249 31"));
		assertEquals("***", SensitiveDataMasker.maskAccountNumber("123"));
		assertEquals("**", SensitiveDataMasker.maskIdentifier("12"));
		assertEquals("", SensitiveDataMasker.maskIdentifier(""));
		assertNull(SensitiveDataMasker.maskIban(null));
	}

	@Test
	void shouldDescribePresence() {
		assertEquals("none", SensitiveDataMasker.describePresence(null));
		assertEquals("present", SensitiveDataMasker.describePresence("value"));
	}

	@Test
	void shouldDescribeTextWithoutExposingValue() {
		assertEquals("none", SensitiveDataMasker.describeText(""));
		assertEquals("present(length=6)", SensitiveDataMasker.describeText(" secret "));
		assertEquals("present(items=2)", SensitiveDataMasker.describeText(List.of("one", "two")));
	}

	@Test
	void shouldDescribeAmountBySignOnly() {
		assertEquals("none", SensitiveDataMasker.describeAmount((BigDecimal) null));
		assertEquals("negative", SensitiveDataMasker.describeAmount(new BigDecimal("-1.23")));
		assertEquals("zero", SensitiveDataMasker.describeAmount(BigDecimal.ZERO));
		assertEquals("positive", SensitiveDataMasker.describeAmount(new BigDecimal("1.23")));
	}
}
