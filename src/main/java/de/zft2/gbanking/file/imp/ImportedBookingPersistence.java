package de.zft2.gbanking.file.imp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.service.account.BookingDuplicateChecker.Candidates;

final class ImportedBookingPersistence {

	private static final Logger log = LogManager.getLogger(ImportedBookingPersistence.class);

	private final DBController dbController;
	private final Map<Integer, Candidates> existingBookingsByAccountId = new HashMap<>();
	private final Map<Integer, Candidates> processedBookingsByAccountId = new HashMap<>();

	ImportedBookingPersistence(DBController dbController) {
		this.dbController = dbController;
	}

	void reset() {
		existingBookingsByAccountId.clear();
		processedBookingsByAccountId.clear();
	}

	int existingBookingCount(int accountId) {
		return existingBookings(accountId).size();
	}

	Result persist(Booking booking) {
		if (!hasDate(booking)) {
			// Bookings without any date are ignored. They cannot be demarcated and they would be imported
			// a second time as soon as they are delivered again with a date.
			log.warn("Skipped imported booking without booking date and value date. accountId={}, amount={}",
					booking.getAccountId(), booking.getAmount());
			return new Result(false, true, null);
		}

		int accountId = booking.getAccountId();
		Candidates processedBookings = processedBookings(accountId);
		Booking existingBooking = ImportedBookingMatcher.findMatchingBooking(existingBookings(accountId), processedBookings, booking);
		if (existingBooking != null) {
			processedBookings.add(existingBooking);
			return new Result(true, false, existingBooking);
		}

		Booking persistedBooking = dbController.insertOrUpdate(booking);
		if (persistedBooking != null) {
			processedBookings.add(persistedBooking);
		}
		return new Result(false, false, persistedBooking);
	}

	private static boolean hasDate(Booking booking) {
		return booking.getDateBooking() != null || booking.getDateValue() != null;
	}

	private Candidates existingBookings(int accountId) {
		return existingBookingsByAccountId.computeIfAbsent(accountId,
				id -> new Candidates(dbController.getAllByParentFull(Booking.class, id)));
	}

	private Candidates processedBookings(int accountId) {
		return processedBookingsByAccountId.computeIfAbsent(accountId, id -> new Candidates(List.of()));
	}

	record Result(boolean existing, boolean skipped, Booking booking) {
	}
}
