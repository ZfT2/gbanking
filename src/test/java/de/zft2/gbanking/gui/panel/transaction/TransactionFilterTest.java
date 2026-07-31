package de.zft2.gbanking.gui.panel.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingNoteDetails;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.enu.Source;

class TransactionFilterTest {

	@Test
	void matches_shouldFindGermanFormattedAmountsAndDates() {
		Booking booking = booking("1234.56", LocalDate.of(2026, Month.JULY, 15));
		booking.setDateValue(LocalDate.of(2026, Month.JULY, 16));

		assertTrue(matchesText(booking, "1.234,56"));
		assertTrue(matchesText(booking, "1234,56"));
		assertTrue(matchesText(booking, "15.07.26"));
		assertTrue(matchesText(booking, "15.07.2026"));
		assertTrue(matchesText(booking, "16.07.2026"));
		assertFalse(matchesText(booking, "17.07.2026"));
	}

	@Test
	void matches_shouldFindBookingNote() {
		Booking booking = booking("10.00", LocalDate.of(2026, Month.JULY, 15));
		BookingNoteDetails noteDetails = new BookingNoteDetails();
		noteDetails.setNote("Beleg mit Steuerberater klären");
		booking.setNoteDetails(noteDetails);

		assertTrue(matchesText(booking, "steuerberater"));
		assertFalse(matchesText(booking, "bezahlt"));
	}

	@Test
	void matches_shouldApplyInclusiveDateAndSignedAmountRanges() {
		Booking booking = booking("-75.50", LocalDate.of(2026, Month.JULY, 15));

		assertTrue(TransactionFilter.matches(booking,
				criteria(LocalDate.of(2026, Month.JULY, 15), LocalDate.of(2026, Month.JULY, 15), "-75.50", "-75.50", null,
						TransactionFilter.BookingState.ALL)));
		assertFalse(TransactionFilter.matches(booking,
				criteria(LocalDate.of(2026, Month.JULY, 16), null, null, null, null, TransactionFilter.BookingState.ALL)));
		assertFalse(TransactionFilter.matches(booking,
				criteria(null, null, "-75.49", null, null, TransactionFilter.BookingState.ALL)));
		assertFalse(TransactionFilter.matches(booking,
				criteria(null, null, null, "-75.51", null, TransactionFilter.BookingState.ALL)));
	}

	@Test
	void matches_shouldCombineCategoryAndBookingState() {
		Booking pendingBooking = booking("10.00", LocalDate.of(2026, Month.JULY, 15));
		pendingBooking.setCategory(new Category(7, "Lebensmittel"));
		pendingBooking.setSource(Source.ONLINE_PRENO);

		TransactionFilter.Criteria matchingCriteria = criteria(null, null, null, null, 7,
				TransactionFilter.BookingState.PRENOTIFICATION);
		assertTrue(TransactionFilter.matches(pendingBooking, matchingCriteria));
		assertFalse(TransactionFilter.matches(pendingBooking,
				criteria(null, null, null, null, 8, TransactionFilter.BookingState.PRENOTIFICATION)));
		assertFalse(TransactionFilter.matches(pendingBooking,
				criteria(null, null, null, null, 7, TransactionFilter.BookingState.BOOKED)));

		pendingBooking.setSource(Source.ONLINE);
		assertTrue(TransactionFilter.matches(pendingBooking,
				criteria(null, null, null, null, 7, TransactionFilter.BookingState.BOOKED)));
	}

	@Test
	void matches_shouldSupportUncategorizedBookings() {
		Booking booking = booking("10.00", LocalDate.of(2026, Month.JULY, 15));

		assertTrue(TransactionFilter.matches(booking,
				criteria(null, null, null, null, TransactionFilter.UNCATEGORIZED_ID, TransactionFilter.BookingState.ALL)));
		booking.setCategory(new Category(7, "Lebensmittel"));
		assertFalse(TransactionFilter.matches(booking,
				criteria(null, null, null, null, TransactionFilter.UNCATEGORIZED_ID, TransactionFilter.BookingState.ALL)));
	}

	@Test
	void germanAmountConversion_shouldSupportGermanAndPlainNotation() {
		assertEquals(new BigDecimal("1234.56"), TransactionFilter.parseGermanAmount("1.234,56"));
		assertEquals(new BigDecimal("1234.56"), TransactionFilter.parseGermanAmount("1234,56"));
		assertEquals(new BigDecimal("1234.56"), TransactionFilter.parseGermanAmount("1234.56"));
		assertEquals(new BigDecimal("1234"), TransactionFilter.parseGermanAmount("1.234"));
		assertNull(TransactionFilter.parseGermanAmount(" "));
		assertEquals("1.234,56", TransactionFilter.formatGermanAmount(new BigDecimal("1234.56")));
	}

	private static boolean matchesText(Booking booking, String searchText) {
		return TransactionFilter.matches(booking,
				new TransactionFilter.Criteria(searchText, null, null, null, null, null, TransactionFilter.BookingState.ALL));
	}

	private static TransactionFilter.Criteria criteria(LocalDate dateFrom, LocalDate dateTo, String amountFrom, String amountTo,
			Integer categoryId, TransactionFilter.BookingState bookingState) {
		return new TransactionFilter.Criteria("", dateFrom, dateTo, amountFrom != null ? new BigDecimal(amountFrom) : null,
				amountTo != null ? new BigDecimal(amountTo) : null, categoryId, bookingState);
	}

	private static Booking booking(String amount, LocalDate bookingDate) {
		Booking booking = new Booking();
		booking.setAmount(new BigDecimal(amount));
		booking.setDateBooking(bookingDate);
		return booking;
	}
}
