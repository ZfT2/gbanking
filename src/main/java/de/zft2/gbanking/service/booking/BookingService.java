package de.zft2.gbanking.service.booking;

import java.time.LocalDate;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.ServiceRegistry;

public class BookingService extends AbstractDbService {

	private static final Logger log = LogManager.getLogger(BookingService.class);

	private final BookingSplitService bookingSplitService;

	public BookingService() {
		this.bookingSplitService = ServiceRegistry.getService(BookingSplitService.class);
	}

	public List<Booking> getAllBookings() {
		return dbController.getAllFull(Booking.class);
	}

	public List<Booking> getBookingsForAccount(int accountId) {
		return dbController.getAllByParentFull(Booking.class, accountId);
	}

	public int deleteBookingsInBlock(Booking referenceBooking, boolean deleteFromDate) {
		if (referenceBooking == null || referenceBooking.getAccountId() <= 0 || !isBlockDeleteSource(referenceBooking.getSource())) {
			return 0;
		}

		LocalDate referenceDate = getRelevantBookingDate(referenceBooking);
		if (referenceDate == null) {
			return 0;
		}

		List<Booking> bookingsToDelete = dbController.getAllByParentFull(Booking.class, referenceBooking.getAccountId()).stream()
				.filter(booking -> booking != null && isSameDeletionSourceFamily(referenceBooking.getSource(), booking.getSource())).filter(booking -> {
					LocalDate bookingDate = getRelevantBookingDate(booking);
					if (bookingDate == null) {
						return false;
					}
					return deleteFromDate ? !bookingDate.isBefore(referenceDate) : !bookingDate.isAfter(referenceDate);
				}).toList();

		int deletedCount = 0;
		for (Booking booking : bookingsToDelete) {
			if (deleteBooking(booking)) {
				deletedCount++;
			}
		}
		log.info("Deleted {} bookings in block for account id {}, direction={}", deletedCount, referenceBooking.getAccountId(),
				deleteFromDate ? "from-date" : "until-date");
		return deletedCount;
	}

	private boolean isBlockDeleteSource(Source source) {
		return isOnlineSource(source) || isImportSource(source);
	}

	private boolean isSameDeletionSourceFamily(Source referenceSource, Source checkedSource) {
		if (referenceSource == null || checkedSource == null) {
			return false;
		}
		return isOnlineSource(referenceSource) && isOnlineSource(checkedSource) || isImportSource(referenceSource) && isImportSource(checkedSource);
	}

	private boolean isOnlineSource(Source source) {
		return source == Source.ONLINE || source == Source.ONLINE_NEW || source == Source.ONLINE_PRENO || source == Source.ONLINE_PRENO_NEW;
	}

	private boolean isImportSource(Source source) {
		return source == Source.IMPORT || source == Source.IMPORT_NEW || source == Source.IMPORT_INITIAL || source == Source.IMPORT_INITIAL_NEW;
	}

	private LocalDate getRelevantBookingDate(Booking booking) {
		if (booking == null) {
			return null;
		}
		return booking.getDateBooking() != null ? booking.getDateBooking() : booking.getDateValue();
	}

	private boolean deleteBooking(Booking booking) {
		return bookingSplitService.deleteBookingWithSplits(booking);
	}

}
