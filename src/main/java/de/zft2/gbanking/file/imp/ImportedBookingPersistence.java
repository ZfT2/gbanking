package de.zft2.gbanking.file.imp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.Booking;

final class ImportedBookingPersistence {

	private final DBController dbController;
	private final Map<Integer, List<Booking>> existingBookingsByAccountId = new HashMap<>();
	private final Map<Integer, List<Booking>> processedBookingsByAccountId = new HashMap<>();

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
		int accountId = booking.getAccountId();
		List<Booking> processedBookings = processedBookings(accountId);
		Booking existingBooking = ImportedBookingMatcher.findMatchingBooking(existingBookings(accountId), processedBookings, booking);
		if (existingBooking != null) {
			processedBookings.add(existingBooking);
			return new Result(true, existingBooking);
		}

		Booking persistedBooking = dbController.insertOrUpdate(booking);
		if (persistedBooking != null) {
			processedBookings.add(persistedBooking);
		}
		return new Result(false, persistedBooking);
	}

	private List<Booking> existingBookings(int accountId) {
		return existingBookingsByAccountId.computeIfAbsent(accountId,
				id -> new ArrayList<>(dbController.getAllByParentFull(Booking.class, id)));
	}

	private List<Booking> processedBookings(int accountId) {
		return processedBookingsByAccountId.computeIfAbsent(accountId, id -> new ArrayList<>());
	}

	record Result(boolean existing, Booking booking) {
	}
}
