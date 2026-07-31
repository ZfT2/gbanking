package de.zft2.gbanking.file.imp;

import java.util.Collection;

import de.zft2.gbanking.db.dao.Booking;

final class ImportedBookingMatcher {

	private ImportedBookingMatcher() {
	}

	static Booking findMatchingBooking(Collection<Booking> existingBookings, Booking bookingToMatch) {
		if (bookingToMatch == null || existingBookings == null) {
			return null;
		}

		for (Booking existingBooking : existingBookings) {
			if (bookingToMatch.equals(existingBooking)) {
				return existingBooking;
			}
		}

		return null;
	}
}
