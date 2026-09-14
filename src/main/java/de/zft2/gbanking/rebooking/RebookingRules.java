package de.zft2.gbanking.rebooking;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.enu.BookingType;

public final class RebookingRules {

	private RebookingRules() {
	}

	public static boolean isForbiddenSameAccountRebooking(int sourceAccountId, int targetAccountId, boolean cancellation) {
		return sourceAccountId > 0 && targetAccountId > 0 && sourceAccountId == targetAccountId && !cancellation;
	}

	public static boolean isForbiddenSameAccountRebooking(int sourceAccountId, Integer targetAccountId, boolean cancellation) {
		return targetAccountId != null && isForbiddenSameAccountRebooking(sourceAccountId, targetAccountId.intValue(), cancellation);
	}

	public static boolean hasCancellationSignal(Booking booking) {
		if (booking == null) {
			return false;
		}
		BookingAdditionalDetails details = booking.getAdditionalDetails();
		return booking.getBookingType() == BookingType.CANCEL || details != null && Boolean.TRUE.equals(details.getStorno());
	}
}
