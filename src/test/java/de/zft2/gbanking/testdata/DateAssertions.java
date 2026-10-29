package de.zft2.gbanking.testdata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
import java.time.ZoneId;

/** Date assertions that stay stable when the day changes while a test is running. */
public final class DateAssertions {

	private DateAssertions() {
	}

	/**
	 * Asserts that {@code actual} is the current day, tolerating a day change after {@code before} was captured (both
	 * bounds inclusive).
	 */
	public static void assertToday(LocalDate before, LocalDate actual) {
		assertNotNull(actual);
		assertFalse(actual.isBefore(before), () -> actual + " is before " + before);
		assertFalse(actual.isAfter(LocalDate.now(ZoneId.systemDefault())), () -> actual + " is in the future");
	}
}
