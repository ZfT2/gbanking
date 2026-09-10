package de.zft2.gbanking.file.imp;

import java.util.Collection;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.service.account.BookingDuplicateChecker;

final class ImportedBookingMatcher {
	private static final BookingDuplicateChecker DUPLICATE_CHECKER = new BookingDuplicateChecker();

	private ImportedBookingMatcher() {
	}

	static Booking findMatchingBooking(Collection<Booking> existingBookings, Collection<Booking> processedBookings,
			Booking bookingToMatch) {
		if (bookingToMatch == null || existingBookings == null) {
			return null;
		}

		Booking duplicate = DUPLICATE_CHECKER.findDuplicate(bookingToMatch, existingBookings, processedBookings);
		if (duplicate != null) {
			return duplicate;
		}

		long processedMatches = processedBookings.stream().filter(booking -> bookingToMatch.equals(booking)).count();
		return existingBookings.stream()
				.filter(booking -> bookingToMatch.equals(booking))
				.skip(processedMatches)
				.findFirst()
				.orElse(null);
	}
}
