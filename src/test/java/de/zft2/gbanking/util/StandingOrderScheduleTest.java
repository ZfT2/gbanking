package de.zft2.gbanking.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.enu.StandingorderMode;

class StandingOrderScheduleTest {

	private static final LocalDate FIRST_EXECUTION_DATE = LocalDate.of(2026, Month.JANUARY, 10);

	@Test
	void shouldReturnTheFirstExecutionDateWhenItIsNotOverdue() {
		LocalDate nextExecutionDate = StandingOrderSchedule.nextExecutionDate(FIRST_EXECUTION_DATE, 10, StandingorderMode.MONTHLY,
				FIRST_EXECUTION_DATE);

		assertEquals(FIRST_EXECUTION_DATE, nextExecutionDate);
	}

	@Test
	void shouldReturnTheNextExecutionDateWhenTheFirstExecutionDateIsOverdue() {
		LocalDate nextExecutionDate = StandingOrderSchedule.nextExecutionDate(FIRST_EXECUTION_DATE, 10, StandingorderMode.MONTHLY,
				LocalDate.of(2026, Month.JANUARY, 11));

		assertEquals(LocalDate.of(2026, Month.FEBRUARY, 10), nextExecutionDate);
	}

	@Test
	void shouldReturnTheCurrentExecutionDateOnTheExecutionDay() {
		LocalDate nextExecutionDate = StandingOrderSchedule.nextExecutionDate(FIRST_EXECUTION_DATE, 10, StandingorderMode.MONTHLY,
				LocalDate.of(2026, Month.FEBRUARY, 10));

		assertEquals(LocalDate.of(2026, Month.FEBRUARY, 10), nextExecutionDate);
	}

	@Test
	void shouldLimitTheExecutionDayToTheLengthOfTheMonth() {
		LocalDate nextExecutionDate = StandingOrderSchedule.nextExecutionDate(LocalDate.of(2026, Month.JANUARY, 31), 31, StandingorderMode.MONTHLY,
				LocalDate.of(2026, Month.FEBRUARY, 1));

		assertEquals(LocalDate.of(2026, Month.FEBRUARY, 28), nextExecutionDate);
	}

	@Test
	void shouldReturnTheFirstDayOfTheMonthAfterAClampedExecutionDay() {
		LocalDate nextExecutionDate = StandingOrderSchedule.nextExecutionDate(LocalDate.of(2026, Month.JANUARY, 31), 31, StandingorderMode.MONTHLY,
				LocalDate.of(2026, Month.MARCH, 1));

		assertEquals(LocalDate.of(2026, Month.MARCH, 31), nextExecutionDate);
	}

	@Test
	void shouldRespectTheIntervalOfTheStandingOrder() {
		LocalDate nextExecutionDate = StandingOrderSchedule.nextExecutionDate(FIRST_EXECUTION_DATE, 10, StandingorderMode.QUARTERLY,
				LocalDate.of(2026, Month.FEBRUARY, 1));

		assertEquals(LocalDate.of(2026, Month.APRIL, 10), nextExecutionDate);
	}

	@Test
	void shouldRespectTheIntervalOfAnAnnualStandingOrder() {
		LocalDate nextExecutionDate = StandingOrderSchedule.nextExecutionDate(LocalDate.of(2026, Month.JUNE, 1), 1, StandingorderMode.ANNUALLY,
				LocalDate.of(2026, Month.JULY, 1));

		assertEquals(LocalDate.of(2027, Month.JUNE, 1), nextExecutionDate);
	}

	@Test
	void shouldReturnNullOnAnIncompleteSchedule() {
		assertNull(StandingOrderSchedule.nextExecutionDate(null, 10, StandingorderMode.MONTHLY, FIRST_EXECUTION_DATE));
		assertNull(StandingOrderSchedule.nextExecutionDate(FIRST_EXECUTION_DATE, null, StandingorderMode.MONTHLY, FIRST_EXECUTION_DATE));
		assertNull(StandingOrderSchedule.nextExecutionDate(FIRST_EXECUTION_DATE, 10, null, FIRST_EXECUTION_DATE));
		assertNull(StandingOrderSchedule.nextExecutionDate(FIRST_EXECUTION_DATE, 10, StandingorderMode.MONTHLY, null));
		assertNull(StandingOrderSchedule.nextExecutionDate(FIRST_EXECUTION_DATE, 0, StandingorderMode.MONTHLY, FIRST_EXECUTION_DATE));
		assertNull(StandingOrderSchedule.nextExecutionDate(FIRST_EXECUTION_DATE, 32, StandingorderMode.MONTHLY, FIRST_EXECUTION_DATE));
	}
}
