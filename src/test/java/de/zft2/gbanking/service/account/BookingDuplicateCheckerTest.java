package de.zft2.gbanking.service.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.service.account.BookingDuplicateChecker.Candidates;

class BookingDuplicateCheckerTest {

	private static final LocalDate BOOKING_DATE = LocalDate.of(2026, Month.JUNE, 1);

	private final BookingDuplicateChecker checker = new BookingDuplicateChecker();

	@ParameterizedTest
	@CsvSource({ "true,true", "true,false", "false,true", "false,false" })
	void potentialDuplicatesShouldAcceptEveryDatePairWithoutChangingStrictMatching(boolean incomingBookingDate, boolean existingBookingDate) {
		Booking incoming = createBooking(Source.IMPORT, "File purpose");
		Booking existing = createBooking(Source.MANUELL, "Different account purpose");
		incoming.setDateBooking(incomingBookingDate ? BOOKING_DATE : null);
		incoming.setDateValue(incomingBookingDate ? null : BOOKING_DATE);
		existing.setDateBooking(existingBookingDate ? BOOKING_DATE : null);
		existing.setDateValue(existingBookingDate ? null : BOOKING_DATE);

		assertEquals(List.of(existing), checker.findPotentialDuplicates(incoming, new Candidates(List.of(existing))));
		assertFalse(isDuplicate(incoming, List.of(existing), List.of()));
		existing.setAccountId(incoming.getAccountId() + 1);
		assertTrue(checker.findPotentialDuplicates(incoming, new Candidates(List.of(existing))).isEmpty());
		existing.setAccountId(incoming.getAccountId());
		existing.setAmount(incoming.getAmount().negate());
		assertTrue(checker.findPotentialDuplicates(incoming, new Candidates(List.of(existing))).isEmpty());
	}

	@Test
	void potentialDuplicatesShouldNotMatchMissingOrDifferentDates() {
		Booking incoming = createBooking(Source.IMPORT, "File");
		Booking existing = createBooking(Source.ONLINE, "Account");
		existing.setDateBooking(BOOKING_DATE.plusDays(2));
		existing.setDateValue(BOOKING_DATE.plusDays(3));
		assertTrue(checker.findPotentialDuplicates(incoming, new Candidates(List.of(existing))).isEmpty());
		incoming.setDateBooking(null);
		incoming.setDateValue(null);
		existing.setDateBooking(null);
		existing.setDateValue(null);
		assertTrue(checker.findPotentialDuplicates(incoming, new Candidates(List.of(existing))).isEmpty());
	}

	@Test
	void shouldDetectDuplicateByNormalizedStableReference() {
		Booking newBooking = createBooking(Source.ONLINE_NEW, "Neuer Verwendungszweck");
		setInstituteReference(newBooking, " reference-123 ");
		Booking existingBooking = createBooking(Source.IMPORT, "Anderer Verwendungszweck");
		setInstituteReference(existingBooking, "REFERENCE-123");

		assertTrue(isDuplicate(newBooking, List.of(existingBooking), List.of()));
	}

	@Test
	void shouldPreserveMultiplicityOfOtherwiseIdenticalBookings() {
		Booking newBooking = createBooking(Source.ONLINE_NEW, "Identische Buchung");
		Booking existingBooking = createBooking(Source.ONLINE, "Identische Buchung");
		Booking alreadyProcessedBooking = createBooking(Source.ONLINE_NEW, "Identische Buchung");

		assertTrue(isDuplicate(newBooking, List.of(existingBooking), List.of()));
		assertFalse(isDuplicate(newBooking, List.of(existingBooking),
				List.of(alreadyProcessedBooking)));
	}

	@Test
	void shouldRequireMatchingRecipientForManualBooking() {
		Booking newBooking = createBooking(Source.ONLINE_NEW, "Manuelle Buchung");
		newBooking.setRecipient(createRecipient("DE12345678901234567890"));
		Booking existingBooking = createBooking(Source.MANUELL, "Manuelle Buchung");

		assertFalse(isDuplicate(newBooking, List.of(existingBooking), List.of()));

		existingBooking.setRecipient(createRecipient("DE12345678901234567890"));
		assertTrue(isDuplicate(newBooking, List.of(existingBooking), List.of()));
	}

	@Test
	void shouldFindExistingOnlineDuplicateForNormalizedImportBooking() {
		Booking onlineBooking = createBooking(Source.ONLINE, "Kartenzahlung");
		onlineBooking.setDateValue(null);
		Booking importedBooking = createBooking(Source.IMPORT_INITIAL, "Kartenzahlung  V00010");
		importedBooking.setAmount(new BigDecimal("42.000"));

		assertSame(onlineBooking, checker.findDuplicate(importedBooking, new Candidates(List.of(onlineBooking)), new Candidates(List.of())));
	}

	@Test
	void shouldGroupMissingAmountWithZeroAmount() {
		Booking newBooking = createBooking(Source.ONLINE_NEW, "Nullbuchung");
		newBooking.setAmount(null);
		Booking existingBooking = createBooking(Source.ONLINE, "Nullbuchung");
		existingBooking.setAmount(new BigDecimal("0.00"));

		assertTrue(isDuplicate(newBooking, List.of(existingBooking), List.of()));
	}

	@Test
	void shouldDetectDuplicateWhenOnlineBookingHasAdditionalPurposeLines() {
		Booking onlineBooking = createBooking(Source.ONLINE_NEW,
				"Abschluss per 30.06.2026\nSaldo Rechnungsabschluss per 30.06.2026\nincl. Abschlussbetrag 3.155,05\nSollzinsen: - 0,25");
		Booking importedBooking = createBooking(Source.IMPORT_INITIAL, "ABSCHLUSS PER 30.06.2026");

		assertTrue(isDuplicate(onlineBooking, List.of(importedBooking), List.of()));
	}

	@Test
	void shouldDetectDuplicateInReverseDirectionWhenOnlineBookingHasAdditionalPurposeLines() {
		Booking importedBooking = createBooking(Source.IMPORT_INITIAL, "ABSCHLUSS PER 30.06.2026");
		Booking onlineBooking = createBooking(Source.ONLINE_NEW,
				"Abschluss per 30.06.2026\nSaldo Rechnungsabschluss per 30.06.2026\nincl. Abschlussbetrag 3.155,05\nSollzinsen: - 0,25");

		assertTrue(isDuplicate(importedBooking, List.of(onlineBooking), List.of()));
	}

	@Test
	void shouldDetectDuplicateWhenPurposeDiffersOnlyByLineBreaks() {
		Booking onlineBooking = createBooking(Source.ONLINE_NEW, "Kartenzahlung\n15.05.2026\nV00010");
		Booking importedBooking = createBooking(Source.IMPORT_INITIAL, "Kartenzahlung 15.05.2026");

		assertTrue(isDuplicate(onlineBooking, List.of(importedBooking), List.of()));
	}

	@Test
	void shouldDetectDuplicateWhenPurposeDiffersOnlyByLineBreaks_02() {
		Booking onlineBooking = createBooking(Source.ONLINE_NEW,
				"Rechnung VG260400524 1x Werkzeug TAN1:Auftrag nicht TAN-pflichtig IBAN: DE70760200700049183526 BIC: HYVE\r\n" + "DEMM460");
		Booking importedBooking = createBooking(Source.IMPORT_INITIAL,
				"Rechnung VG260400524 1x Werkzeug TAN1:Auftrag nicht TAN-pflichtig IBAN: DE70760200700049183526 BIC: HYVEDEMM460");

		assertTrue(isDuplicate(onlineBooking, List.of(importedBooking), List.of()));
	}

	@Test
	void shouldNotDetectDuplicateForDifferentPurposes() {
		Booking onlineBooking = createBooking(Source.ONLINE_NEW, "Abschluss per 30.06.2026\nSollzinsen: - 0,25");
		Booking importedBooking = createBooking(Source.IMPORT_INITIAL, "ABSCHLUSS PER 31.05.2026");

		assertFalse(isDuplicate(onlineBooking, List.of(importedBooking), List.of()));
	}

	@Test
	void shouldNotDetectDuplicateWhenPurposeMatchesOnlyInsideAWord() {
		Booking onlineBooking = createBooking(Source.ONLINE_NEW, "Abschluss per 30.06.2026\nSollzinsen: - 0,25");
		Booking importedBooking = createBooking(Source.IMPORT_INITIAL, "Zinsen");

		assertFalse(isDuplicate(onlineBooking, List.of(importedBooking), List.of()));
	}

	@Test
	void shouldKeepManualBookingComparisonStrict() {
		Booking onlineBooking = createBooking(Source.ONLINE_NEW, "Kartenzahlung\nDetails der Bank");
		Booking manualBooking = createBooking(Source.MANUELL, "Kartenzahlung");
		manualBooking.setRecipient(createRecipient("DE12345678901234567890"));
		onlineBooking.setRecipient(createRecipient("DE12345678901234567890"));

		assertFalse(isDuplicate(onlineBooking, List.of(manualBooking), List.of()));
	}

	private boolean isDuplicate(Booking newBooking, List<Booking> existingBookings, List<Booking> processedBookings) {
		return checker.isDuplicate(newBooking, new Candidates(existingBookings), new Candidates(processedBookings));
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
