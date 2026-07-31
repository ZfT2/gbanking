package de.zft2.gbanking.gui.panel.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.zft2.gbanking.db.dao.Booking;

final class BookingRunningBalanceCalculator {

	private BookingRunningBalanceCalculator() {
	}

	static void applyTo(List<Booking> bookings) {
		if (bookings == null || bookings.isEmpty()) {
			return;
		}

		Map<Integer, BigDecimal> balanceByAccount = new HashMap<>();
		bookings.stream()
				.filter(Objects::nonNull)
				.sorted(Comparator.comparingInt(Booking::getAccountId)
						.thenComparing(Booking::getDateBooking, Comparator.nullsFirst(LocalDate::compareTo))
						.thenComparingInt(Booking::getId))
				.forEach(booking -> applyBalance(booking, balanceByAccount));
	}

	private static void applyBalance(Booking booking, Map<Integer, BigDecimal> balanceByAccount) {
		if (booking.getSource() != null && booking.getSource().isPrenotification()) {
			booking.setBalance(null);
			return;
		}

		BigDecimal balance = balanceByAccount.getOrDefault(booking.getAccountId(), BigDecimal.ZERO)
				.add(booking.getAmount() != null ? booking.getAmount() : BigDecimal.ZERO);
		balanceByAccount.put(booking.getAccountId(), balance);
		booking.setBalance(balance.setScale(2, RoundingMode.HALF_UP));
	}
}
