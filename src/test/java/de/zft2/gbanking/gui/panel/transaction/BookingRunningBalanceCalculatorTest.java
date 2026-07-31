package de.zft2.gbanking.gui.panel.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.Source;

class BookingRunningBalanceCalculatorTest {

	@Test
	void applyTo_shouldCalculateRunningBalancePerAccountByBookingIdAndKeepDisplayOrder() {
		Booking latestAccountOne = booking(3, 1, "-25.00");
		Booking oldestAccountOne = booking(1, 1, "100.00");
		Booking middleAccountOne = booking(2, 1, "-10.00");
		Booking accountTwo = booking(4, 2, "7.50");

		BookingRunningBalanceCalculator.applyTo(List.of(latestAccountOne, oldestAccountOne, middleAccountOne, accountTwo));

		assertEquals(new BigDecimal("65.00"), latestAccountOne.getBalance());
		assertEquals(new BigDecimal("100.00"), oldestAccountOne.getBalance());
		assertEquals(new BigDecimal("90.00"), middleAccountOne.getBalance());
		assertEquals(new BigDecimal("7.50"), accountTwo.getBalance());
	}

	@Test
	void applyTo_shouldLeavePrenotificationBalanceEmptyAndExcludeItFromFollowingBalance() {
		Booking bookedBefore = booking(1, 1, "100.00");
		Booking pending = booking(2, 1, "-80.00");
		pending.setSource(Source.ONLINE_PRENO);
		Booking bookedAfter = booking(3, 1, "-20.00");

		BookingRunningBalanceCalculator.applyTo(List.of(bookedAfter, pending, bookedBefore));

		assertEquals(new BigDecimal("100.00"), bookedBefore.getBalance());
		assertNull(pending.getBalance());
		assertEquals(new BigDecimal("80.00"), bookedAfter.getBalance());
	}

	private static Booking booking(int id, int accountId, String amount) {
		Booking booking = new Booking();
		booking.setId(id);
		booking.setAccountId(accountId);
		booking.setAmount(new BigDecimal(amount));
		return booking;
	}
}
