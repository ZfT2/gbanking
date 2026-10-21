package de.zft2.gbanking.util;

import java.time.LocalDate;
import java.time.YearMonth;

import de.zft2.gbanking.db.dao.enu.StandingorderMode;

/**
 * Calculates the execution schedule of a standing order (Dauerauftrag) from the first
 * execution date, the execution day and the interval of the standing order.
 */
public final class StandingOrderSchedule {

	private static final int MIN_EXECUTION_DAY = 1;
	private static final int MAX_EXECUTION_DAY = 31;

	private StandingOrderSchedule() {
	}

	/**
	 * Returns the next execution date of a standing order that is not before the given date.
	 *
	 * <p>
	 * The execution day is limited to the length of the month, an execution day of 31 therefore
	 * leads to the last day of the month in months with less days.
	 * </p>
	 *
	 * @param firstExecutionDate date of the first execution
	 * @param executionDay        day of the month of the execution, 1 to 31
	 * @param standingorderMode   interval of the standing order
	 * @param fromDate            date the next execution has to be on or after
	 * @return next execution date or {@code null} if the schedule of the standing order is incomplete
	 */
	public static LocalDate nextExecutionDate(LocalDate firstExecutionDate, Integer executionDay, StandingorderMode standingorderMode,
			LocalDate fromDate) {
		if (firstExecutionDate == null || fromDate == null || standingorderMode == null || executionDay == null
				|| executionDay < MIN_EXECUTION_DAY || executionDay > MAX_EXECUTION_DAY) {
			return null;
		}

		YearMonth month = YearMonth.from(firstExecutionDate);
		LocalDate nextExecutionDate = firstExecutionDate;
		while (nextExecutionDate.isBefore(fromDate)) {
			month = month.plusMonths(standingorderMode.getIntervalMonths());
			nextExecutionDate = month.atDay(Math.min(executionDay, month.lengthOfMonth()));
		}
		return nextExecutionDate;
	}
}
