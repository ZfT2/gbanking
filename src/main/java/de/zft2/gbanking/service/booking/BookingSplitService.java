package de.zft2.gbanking.service.booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.AbstractDbService;

public class BookingSplitService extends AbstractDbService {

	public List<Booking> getSplitBookings(Booking parentBooking) {
		if (parentBooking == null || parentBooking.getId() <= 0) {
			return List.of();
		}
		return dbController.getSplitBookings(parentBooking.getId());
	}

	List<Booking> saveSplitBookings(Booking parentBooking, List<Booking> splitBookings, Collection<Integer> deletedSplitBookingIds) {
		return saveSplitBookings(parentBooking, splitBookings,
				deletedSplitBookingIds.stream().collect(java.util.stream.Collectors.toMap(id -> id, id -> Boolean.TRUE)));
	}

	public List<Booking> saveSplitBookings(Booking parentBooking, List<Booking> splitBookings, Map<Integer, Boolean> deletedSplitBookingActions) {
		validateParentBooking(parentBooking);
		for (Map.Entry<Integer, Boolean> entry : deletedSplitBookingActions.entrySet()) {
			deleteSplitBooking(entry.getKey(), Boolean.TRUE.equals(entry.getValue()));
		}
		for (Booking splitBooking : splitBookings) {
			saveSplitBooking(parentBooking, splitBooking);
		}
		return getSplitBookings(parentBooking);
	}

	private void deleteSplitBooking(Integer splitBookingId) {
		deleteSplitBooking(splitBookingId, true);
	}

	private void deleteSplitBooking(Integer splitBookingId, boolean deleteCounterBooking) {
		if (splitBookingId == null || splitBookingId <= 0) {
			return;
		}

		Booking splitBooking = dbController.getById(Booking.class, splitBookingId);
		if (splitBooking == null) {
			return;
		}
		handleCrossBookingBeforeSplitDelete(splitBooking, deleteCounterBooking);
		dbController.delete(splitBooking, null);
	}

	public boolean deleteBookingWithSplits(Booking booking) {
		if (booking == null || booking.getId() <= 0) {
			return false;
		}
		if (booking.getParentBookingId() != null) {
			deleteSplitBooking(booking.getId());
			return true;
		}

		for (Booking splitBooking : getSplitBookings(booking)) {
			deleteSplitBooking(splitBooking.getId());
		}
		return dbController.delete(booking, null);
	}

	private void saveSplitBooking(Booking parentBooking, Booking splitBooking) {
		validateCrossAccount(parentBooking, splitBooking);
		prepareSplitBooking(parentBooking, splitBooking);
		Booking savedSplitBooking = dbController.insertOrUpdate(splitBooking);
		if (savedSplitBooking.getId() <= 0) {
			throw new GBankingException(getText("EXCEPTION_SPLIT_BOOKING_SAVE_FAILED"));
		}
		saveOrDeleteCrossBooking(parentBooking, savedSplitBooking);
	}

	private void validateCrossAccount(Booking parentBooking, Booking splitBooking) {
		if (!hasCrossAccount(splitBooking)) {
			return;
		}
		if (parentBooking.getForeignCurrencyDetails() != null) {
			throw new GBankingException(getText("ALERT_REBOOKING_FOREIGN_CURRENCY"));
		}
		BankAccount sourceAccount = dbController.getById(BankAccount.class, parentBooking.getAccountId());
		BankAccount targetAccount = dbController.getById(BankAccount.class, splitBooking.getCrossAccountId());
		if (sourceAccount != null && targetAccount != null
				&& sourceAccount.getBaseCurrency() != targetAccount.getBaseCurrency()) {
			throw new GBankingException(getText("ALERT_REBOOKING_CURRENCY_MISMATCH"));
		}
	}

	private void prepareSplitBooking(Booking parentBooking, Booking splitBooking) {
		splitBooking.setAccountId(parentBooking.getAccountId());
		splitBooking.setParentBookingId(parentBooking.getId());
		splitBooking.setDateBooking(defaultDate(splitBooking.getDateBooking(), parentBooking.getDateBooking(), parentBooking.getDate()));
		splitBooking.setDateValue(defaultDate(splitBooking.getDateValue(), parentBooking.getDateValue(), splitBooking.getDateBooking()));
		splitBooking.setDate(splitBooking.getDateValue());
		splitBooking.setSource(Source.MANUELL);
		splitBooking.setAmount(defaultAmount(splitBooking.getAmount()));
		splitBooking.setBookingType(resolveBookingType(splitBooking.getAmount(), splitBooking.getCrossAccountId()));
	}

	private void saveOrDeleteCrossBooking(Booking parentBooking, Booking splitBooking) {
		if (!hasCrossAccount(splitBooking)) {
			deleteCrossBooking(splitBooking);
			splitBooking.setCrossBookingId(null);
			dbController.insertOrUpdate(splitBooking);
			return;
		}

		Booking crossBooking = loadOrCreateCrossBooking(splitBooking.getCrossBookingId());
		prepareCrossBooking(parentBooking, splitBooking, crossBooking);
		dbController.insertOrUpdate(crossBooking);
		splitBooking.setCrossBookingId(crossBooking.getId());
		dbController.insertOrUpdate(splitBooking);
	}

	private Booking loadOrCreateCrossBooking(Integer crossBookingId) {
		if (crossBookingId != null && crossBookingId > 0) {
			Booking existingCrossBooking = dbController.getById(Booking.class, crossBookingId);
			if (existingCrossBooking != null) {
				return existingCrossBooking;
			}
		}
		return new Booking();
	}

	private void prepareCrossBooking(Booking parentBooking, Booking splitBooking, Booking crossBooking) {
		BigDecimal crossAmount = splitBooking.getAmount().negate();
		crossBooking.setAccountId(splitBooking.getCrossAccountId());
		crossBooking.setParentBookingId(null);
		crossBooking.setDateBooking(splitBooking.getDateBooking());
		crossBooking.setDateValue(splitBooking.getDateValue());
		crossBooking.setDate(splitBooking.getDate());
		crossBooking.setPurpose(splitBooking.getPurpose());
		crossBooking.setAmount(crossAmount);
		crossBooking.setSource(Source.MANUELL);
		crossBooking.setBookingType(resolveBookingType(crossAmount, parentBooking.getAccountId()));
		crossBooking.setCrossAccountId(parentBooking.getAccountId());
		crossBooking.setCrossBookingId(splitBooking.getId());
		crossBooking.setCategory(splitBooking.getCategory());
	}

	private void deleteCrossBooking(Booking splitBooking) {
		Integer crossBookingId = splitBooking.getCrossBookingId();
		if (crossBookingId != null && crossBookingId > 0) {
			Booking crossBooking = dbController.getById(Booking.class, crossBookingId);
			if (crossBooking != null) {
				dbController.delete(crossBooking, null);
			}
		}
	}

	private void handleCrossBookingBeforeSplitDelete(Booking splitBooking, boolean deleteCounterBooking) {
		if (deleteCounterBooking) {
			deleteCrossBooking(splitBooking);
			return;
		}

		Integer crossBookingId = splitBooking.getCrossBookingId();
		if (crossBookingId == null || crossBookingId <= 0) {
			return;
		}

		Booking crossBooking = dbController.getById(Booking.class, crossBookingId);
		if (crossBooking == null) {
			return;
		}
		crossBooking.setCrossAccountId(null);
		crossBooking.setCrossBookingId(null);
		crossBooking.setBookingType(resolveBookingType(crossBooking.getAmount(), null));
		dbController.insertOrUpdate(crossBooking);
	}

	private void validateParentBooking(Booking parentBooking) {
		if (parentBooking == null || parentBooking.getId() <= 0 || parentBooking.getAccountId() <= 0) {
			throw new IllegalArgumentException(getText("ALERT_SPLIT_BOOKING_PARENT_MISSING"));
		}
	}

	private static boolean hasCrossAccount(Booking splitBooking) {
		Integer crossAccountId = splitBooking.getCrossAccountId();
		return crossAccountId != null && crossAccountId > 0;
	}

	private static BookingType resolveBookingType(BigDecimal amount, Integer crossAccountId) {
		boolean isNegative = amount.compareTo(BigDecimal.ZERO) < 0;
		if (crossAccountId != null && crossAccountId > 0) {
			return isNegative ? BookingType.REBOOKING_OUT : BookingType.REBOOKING_IN;
		}
		return isNegative ? BookingType.REMOVAL : BookingType.DEPOSIT;
	}

	private static BigDecimal defaultAmount(BigDecimal amount) {
		return amount != null ? amount : BigDecimal.ZERO;
	}

	private static LocalDate defaultDate(LocalDate value, LocalDate fallback, LocalDate defaultValue) {
		if (value != null) {
			return value;
		}
		if (fallback != null) {
			return fallback;
		}
		return defaultValue != null ? defaultValue : LocalDate.now(ZoneId.systemDefault());
	}

}
