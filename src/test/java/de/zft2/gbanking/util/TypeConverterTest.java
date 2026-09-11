package de.zft2.gbanking.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.Test;

class TypeConverterTest {

	@Test
	void shouldParseAndFormatDates() {
		LocalDate date = LocalDate.of(2026, Month.APRIL, 10);

		assertEquals("10.04.26", TypeConverter.toDateStringShort(date));
		assertEquals("10.04.2026", TypeConverter.toDateStringLong(date));
		assertEquals(date, TypeConverter.toLocalDateFromDateStr("10.04.2026"));
		assertEquals(date, TypeConverter.toLocalDateFromDateStrShort("10.04.26"));
		assertEquals(date, TypeConverter.toLocalDateFromDateStrFlexible("10.04.26"));
		assertEquals(date, TypeConverter.toLocalDateFromDateStrFlexible("10.04.2026"));
	}

	@Test
	void shouldResolveTwoDigitYearsUsing1990AsBoundary() {
		assertEquals(LocalDate.of(2089, Month.DECEMBER, 31), TypeConverter.toLocalDateFromDateStrShort("31.12.89"));
		assertEquals(LocalDate.of(1990, Month.JANUARY, 1), TypeConverter.toLocalDateFromDateStrShort("01.01.90"));
		assertEquals(LocalDate.of(1999, Month.MAY, 20), TypeConverter.toLocalDateFromDateStrShort("20.05.99"));
		assertEquals(2089, TypeConverter.expandTwoDigitYear(89));
		assertEquals(1990, TypeConverter.expandTwoDigitYear(90));
		assertEquals(1999, TypeConverter.expandTwoDigitYear(1999));
	}

	@Test
	void shouldReturnNullForInvalidDateStrings() {
		assertNull(TypeConverter.toLocalDateFromDateStr("2026-04-10"));
		assertNull(TypeConverter.toLocalDateFromDateStrShort("10/04/26"));
		assertNull(TypeConverter.toLocalDateFromDateStr(null));
		assertNull(TypeConverter.toLocalDateFromDateStrFlexible("10/04/2026"));
	}

	@Test
	void shouldConvertListsAndBooleans() {
		assertEquals(List.of("a", "b", "c"), TypeConverter.toList("a,b,c"));
		assertTrue(TypeConverter.toList(null).isEmpty());
		assertEquals("a,b,c", TypeConverter.toCommaSeparatedString(List.of("a", "b", "c")));
		assertNull(TypeConverter.toCommaSeparatedString(null));

		assertTrue(TypeConverter.toBoolean("yes"));
		assertTrue(TypeConverter.toBoolean("Ja"));
		assertTrue(TypeConverter.toBoolean("1"));
		assertFalse(TypeConverter.toBoolean("no"));
		assertFalse(TypeConverter.toBoolean("Nein"));
		assertFalse(TypeConverter.toBoolean("0"));
		assertNull(TypeConverter.toBoolean("maybe"));
	}
}
