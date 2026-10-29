package de.zft2.gbanking.file.imp;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.service.account.BookingDuplicateChecker;
import de.zft2.gbanking.service.account.BookingDuplicateChecker.Candidates;

final class ImportedBookingMatcher {
	private static final BookingDuplicateChecker DUPLICATE_CHECKER = new BookingDuplicateChecker();

	private ImportedBookingMatcher() {
	}

	static Booking findMatchingBooking(Candidates existingBookings, Candidates processedBookings, Booking bookingToMatch) {
		if (bookingToMatch == null || existingBookings == null) {
			return null;
		}

		Booking duplicate = DUPLICATE_CHECKER.findDuplicate(bookingToMatch, existingBookings, processedBookings);
		if (duplicate != null) {
			return duplicate;
		}

		// Booking.equals requires the same amount, so the bookings with the same rounded amount are sufficient
		long processedMatches = processedBookings.withSameAmount(bookingToMatch).stream()
				.filter(booking -> bookingToMatch.equals(booking))
				.count();
		return existingBookings.withSameAmount(bookingToMatch).stream()
				.filter(booking -> bookingToMatch.equals(booking))
				.skip(processedMatches)
				.findFirst()
				.orElse(null);
	}
}
