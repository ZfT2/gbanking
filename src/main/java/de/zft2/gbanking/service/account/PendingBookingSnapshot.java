package de.zft2.gbanking.service.account;

import java.time.LocalDate;
import java.util.List;

import de.zft2.gbanking.db.dao.Booking;

public record PendingBookingSnapshot(List<Booking> bookings, LocalDate fromInclusive) {

	public PendingBookingSnapshot {
		bookings = bookings != null ? List.copyOf(bookings) : List.of();
	}
}
