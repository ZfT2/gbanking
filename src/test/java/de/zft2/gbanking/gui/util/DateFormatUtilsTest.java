package de.zft2.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;

import org.junit.jupiter.api.Test;

class DateFormatUtilsTest {

	@Test
	void shouldFormatShortAndLongDates() {
		LocalDate date = LocalDate.of(2026, Month.APRIL, 10);

		assertEquals("10.04.26", DateFormatUtils.formatShort(date));
		assertEquals("10.04.2026", DateFormatUtils.formatLong(date));
		assertEquals("10.04.2026 14:35", DateFormatUtils.formatDateTime(LocalDateTime.of(2026, Month.APRIL, 10, 14, 35)));
	}

	@Test
	void shouldFormatBookingAndValueDateInOneOrTwoLines() {
		LocalDate bookingDate = LocalDate.of(2026, Month.APRIL, 10);
		LocalDate sameValueDate = LocalDate.of(2026, Month.APRIL, 10);
		LocalDate otherValueDate = LocalDate.of(2026, Month.APRIL, 9);

		assertEquals("10.04.26", DateFormatUtils.formatBookingAndValue(bookingDate, sameValueDate));
		assertEquals("10.04.26\n(09.04.26)", DateFormatUtils.formatBookingAndValue(bookingDate, otherValueDate));
		assertEquals("", DateFormatUtils.formatBookingAndValue(null, otherValueDate));
	}
}
