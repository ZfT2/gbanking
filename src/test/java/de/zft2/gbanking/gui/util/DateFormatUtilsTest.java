package de.zft2.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.Date;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.util.DateFormatUtils;

class DateFormatUtilsTest {

	@Test
	void shouldFormatShortAndLongDates() {
		LocalDate date = LocalDate.of(2026, 4, 10);

		assertEquals("10.04.26", DateFormatUtils.formatShort(date));
		assertEquals("10.04.2026", DateFormatUtils.formatLong(date));
	}

	@Test
	void shouldFormatBookingAndValueDateInOneOrTwoLines() {
		LocalDate bookingDate = LocalDate.of(2026, 4, 10);
		LocalDate sameValueDate = LocalDate.of(2026, 4, 10);
		LocalDate otherValueDate = LocalDate.of(2026, 4, 9);

		assertEquals("10.04.26", DateFormatUtils.formatBookingAndValue(bookingDate, sameValueDate));
		assertEquals("10.04.26\n(09.04.26)", DateFormatUtils.formatBookingAndValue(bookingDate, otherValueDate));
		assertEquals("", DateFormatUtils.formatBookingAndValue(null, otherValueDate));
	}

	@Test
	void shouldFormatJavaUtilDate() {
		Date date = java.sql.Date.valueOf(LocalDate.of(2026, 4, 10));

		assertEquals("10.04.26", DateFormatUtils.formatShort(date));
		assertEquals("", DateFormatUtils.formatShort((Date) null));
	}
}
