package de.zft2.gbanking.service.account;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.Source;

class BookingDuplicateCheckerTest {

	private static final LocalDate BOOKING_DATE = LocalDate.of(2026, Month.JUNE, 1);

	private final BookingDuplicateChecker checker = new BookingDuplicateChecker();

	@Test
	void shouldDetectDuplicateByNormalizedStableReference() {
		Booking newBooking = createBooking(Source.ONLINE_NEW, "Neuer Verwendungszweck");
		setInstituteReference(newBooking, " reference-123 ");
		Booking existingBooking = createBooking(Source.IMPORT, "Anderer Verwendungszweck");
		setInstituteReference(existingBooking, "REFERENCE-123");

		assertTrue(checker.isDuplicate(newBooking, List.of(existingBooking), List.of()));
	}

	@Test
	void shouldPreserveMultiplicityOfOtherwiseIdenticalBookings() {
		Booking newBooking = createBooking(Source.ONLINE_NEW, "Identische Buchung");
		Booking existingBooking = createBooking(Source.ONLINE, "Identische Buchung");
		Booking alreadyProcessedBooking = createBooking(Source.ONLINE_NEW, "Identische Buchung");

		assertTrue(checker.isDuplicate(newBooking, List.of(existingBooking), List.of()));
		assertFalse(checker.isDuplicate(newBooking, List.of(existingBooking),
				List.of(alreadyProcessedBooking)));
	}

	@Test
	void shouldRequireMatchingRecipientForManualBooking() {
		Booking newBooking = createBooking(Source.ONLINE_NEW, "Manuelle Buchung");
		newBooking.setRecipient(createRecipient("DE12345678901234567890"));
		Booking existingBooking = createBooking(Source.MANUELL, "Manuelle Buchung");

		assertFalse(checker.isDuplicate(newBooking, List.of(existingBooking), List.of()));

		existingBooking.setRecipient(createRecipient("DE12345678901234567890"));
		assertTrue(checker.isDuplicate(newBooking, List.of(existingBooking), List.of()));
	}

	private static Booking createBooking(Source source, String purpose) {
		Booking booking = new Booking();
		booking.setAccountId(1);
		booking.setDateBooking(BOOKING_DATE);
		booking.setDateValue(BOOKING_DATE);
		booking.setPurpose(purpose);
		booking.setAmount(new BigDecimal("42.00"));
		booking.setSource(source);
		return booking;
	}

	private static void setInstituteReference(Booking booking, String reference) {
		BookingAdditionalDetails details = new BookingAdditionalDetails();
		details.setInstref(reference);
		booking.setAdditionalDetails(details);
	}

	private static Recipient createRecipient(String iban) {
		Recipient recipient = new Recipient();
		recipient.setName("Max Mustermann");
		recipient.setIban(iban);
		return recipient;
	}
}
