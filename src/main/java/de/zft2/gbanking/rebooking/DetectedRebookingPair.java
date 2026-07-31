package de.zft2.gbanking.rebooking;

import de.zft2.gbanking.db.dao.Booking;

public record DetectedRebookingPair(Booking booking, Booking crossBooking) {
}
