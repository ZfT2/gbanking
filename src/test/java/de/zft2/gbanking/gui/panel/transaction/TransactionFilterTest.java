package de.zft2.gbanking.gui.panel.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingNoteDetails;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.Source;

class TransactionFilterTest {

	@ParameterizedTest
	@ValueSource(strings = { "26 Grundbesitzabgaben", "Grundbesitz 2026", "Grundbesitz 2026 11", "Stadtkasse 11 26",
			"DE1234567891012", "Musterstadt HELADEF1822", "118259", "  grundbesitz\tSTADTKASSE   26  " })
	void matches_shouldFindAllTermsAcrossFieldsInBothExampleBookings(String search) {
		assertTrue(matchesText(municipalBooking(LocalDate.of(2026, Month.NOVEMBER, 16)), search));
		assertTrue(matchesText(municipalBooking(LocalDate.of(2026, Month.AUGUST, 15)), search));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			16.11.2026           | true
			16.11.26             | true
			16.11.2026 Stadtkasse | true
			Stadtkasse 16.11.26   | true
			15.08.2026           | false
			15.08.26             | false
			15.08.2026 Stadtkasse | false
			Stadtkasse 15.08.26   | false
			""")
	void matches_shouldCombineFullDatesWithTermsFromOtherFields(String search, boolean november) {
		assertEquals(november, matchesText(municipalBooking(LocalDate.of(2026, Month.NOVEMBER, 16)), search));
		assertEquals(!november, matchesText(municipalBooking(LocalDate.of(2026, Month.AUGUST, 15)), search));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Stadtkasse fehlt", "17.11.26 Stadtkasse", "Grundbesitz 2025", "118259 DE9999" })
	void matches_shouldRejectBookingsWhenAnyTermIsMissing(String search) {
		assertFalse(matchesText(municipalBooking(LocalDate.of(2026, Month.NOVEMBER, 16)), search));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "\t\n" })
	void matches_shouldKeepBlankSearchesAndMissingFieldsSupported(String search) {
		assertTrue(matchesText(new Booking(), search));
		assertFalse(matchesText(null, search));
	}

	@Test
	void matches_shouldCombineExistingSearchFieldsWithAmountAndValueDate() {
		Booking booking = municipalBooking(LocalDate.of(2026, Month.NOVEMBER, 16));
		booking.setDateValue(LocalDate.of(2026, Month.NOVEMBER, 18));
		booking.setCategory(new Category(7, "Steuern"));
		BookingNoteDetails note = new BookingNoteDetails();
		note.setNote("Rücksprache");
		booking.setNoteDetails(note);

		assertTrue(matchesText(booking, "Steuern rücksprache 18.11.26 75,50 Stadtkasse"));
		assertFalse(TransactionFilter.matches(booking, new TransactionFilter.Criteria("Steuern Stadtkasse",
				null, null, null, null, 8, TransactionFilter.BookingState.ALL)));
	}

	@Test
	void matches_shouldFindGermanFormattedAmountsAndDates() {
		Booking booking = booking("1234.56", LocalDate.of(2026, Month.JULY, 15));
		booking.setDateValue(LocalDate.of(2026, Month.JULY, 16));

		assertTrue(matchesText(booking, "1.234,56"));
		assertTrue(matchesText(booking, "1234,56"));
		assertTrue(matchesText(booking, "15.07.26"));
		assertTrue(matchesText(booking, "15.07.2026"));
		assertTrue(matchesText(booking, "16.07.2026"));
		assertTrue(matchesText(booking, "2026-07-15"));
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
	void matches_shouldFindAllRecipientTextFields() {
		Booking booking = booking("10.00", LocalDate.of(2026, Month.JULY, 15));
		Recipient recipient = new Recipient("Erika Mustermann", "DE44123456789012345678", "GENODEF1S01", "1234567890", "50010517",
				"Musterbank", Source.MANUELL);
		recipient.setNote("Hausverwaltung");
		booking.setRecipient(recipient);

		for (String searchText : List.of("mustermann", "de44123456789012345678", "1234567890", "genodef1s01", "50010517", "musterbank",
				"hausverwaltung")) {
			assertTrue(matchesText(booking, searchText), () -> "Expected recipient field to match: " + searchText);
		}
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
	void matches_shouldSwapReversedDateAndAmountRanges() {
		Booking booking = booking("-75.50", LocalDate.of(2026, Month.JULY, 15));

		assertTrue(TransactionFilter.matches(booking,
				criteria(LocalDate.of(2026, Month.JULY, 31), LocalDate.of(2026, Month.JULY, 1), "-10", "-100", null,
						TransactionFilter.BookingState.ALL)));
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
	void germanAmountFormatting_shouldUseGermanSeparators() {
		assertEquals("1.234,56", TransactionFilter.formatGermanAmount(new BigDecimal("1234.56")));
		assertEquals("-1.234.567,89", TransactionFilter.formatGermanAmount(new BigDecimal("-1234567.891")));
		assertEquals("-123,40", TransactionFilter.formatGermanAmount(new BigDecimal("-123.4")));
		assertEquals("1.000,00", TransactionFilter.formatGermanAmount(new BigDecimal("999.995")));
		assertEquals("0,12", TransactionFilter.formatGermanAmount(new BigDecimal("0.125")));
		assertEquals("", TransactionFilter.formatGermanAmount(null));
	}

	private static boolean matchesText(Booking booking, String searchText) {
		return TransactionFilter.matches(booking,
				new TransactionFilter.Criteria(searchText, null, null, null, null, null, TransactionFilter.BookingState.ALL));
	}

	private static Booking municipalBooking(LocalDate date) {
		Booking booking = booking("-75.50", date);
		booking.setPurpose("118259 Grundbesitzabgaben Musterstrase 4");
		booking.setRecipient(new Recipient("Stadtkasse Musterstadt", "DE1234567891012", "HELADEF1822",
				null, null, null, Source.MANUELL));
		return booking;
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
