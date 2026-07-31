package de.zft2.gbanking.db.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.Test;

class BookingTest {

	@Test
	void copyConstructor_shouldDeepCopyDetailObjects() {
		BookingSepaDetails sepaDetails = new BookingSepaDetails();
		sepaDetails.setCustomerRef("original-sepa");
		BookingAdditionalDetails additionalDetails = new BookingAdditionalDetails();
		additionalDetails.setInstref("original-additional");
		BookingCreditCardDetails creditCardDetails = new BookingCreditCardDetails();
		creditCardDetails.setTransactionDate(LocalDate.of(2026, Month.JULY, 13));
		BookingNoteDetails noteDetails = new BookingNoteDetails();
		noteDetails.setNote("original-note");
		noteDetails.setReviewRequired(true);

		Booking original = new Booking();
		original.setSepaDetails(sepaDetails);
		original.setAdditionalDetails(additionalDetails);
		original.setCreditCardDetails(creditCardDetails);
		original.setNoteDetails(noteDetails);

		Booking copy = new Booking(original);

		assertNotSame(original.getSepaDetails(), copy.getSepaDetails());
		assertNotSame(original.getAdditionalDetails(), copy.getAdditionalDetails());
		assertNotSame(original.getCreditCardDetails(), copy.getCreditCardDetails());
		assertNotSame(original.getNoteDetails(), copy.getNoteDetails());
		assertEquals("original-note", copy.getNoteDetails().getNote());
		assertTrue(copy.getNoteDetails().isReviewRequired());

		BookingSepaDetails copiedSepaDetails = copy.getSepaDetails();
		assertNotNull(copiedSepaDetails);
		copiedSepaDetails.setCustomerRef("copy-sepa");
		copy.setSepaDetails(copiedSepaDetails);
		BookingAdditionalDetails copiedAdditionalDetails = copy.getAdditionalDetails();
		assertNotNull(copiedAdditionalDetails);
		copiedAdditionalDetails.setInstref("copy-additional");
		copy.setAdditionalDetails(copiedAdditionalDetails);
		BookingCreditCardDetails copiedCreditCardDetails = copy.getCreditCardDetails();
		assertNotNull(copiedCreditCardDetails);
		copiedCreditCardDetails.setTransactionDate(LocalDate.of(2026, Month.JULY, 14));
		copy.setCreditCardDetails(copiedCreditCardDetails);
		BookingNoteDetails copiedNoteDetails = copy.getNoteDetails();
		assertNotNull(copiedNoteDetails);
		copiedNoteDetails.setNote("copy-note");
		copy.setNoteDetails(copiedNoteDetails);

		assertDetailValues(copy, "copy-sepa", "copy-additional", LocalDate.of(2026, Month.JULY, 14), "copy-note");
		assertDetailValues(original, "original-sepa", "original-additional", LocalDate.of(2026, Month.JULY, 13), "original-note");
	}

	@Test
	void bookingDetailsInterfaceMethods_shouldDelegateToSepaDetails() {
		Booking booking = new Booking();

		booking.setSepaCustomerRef("customer-reference");

		BookingSepaDetails details = booking.getSepaDetails();
		assertNotNull(details);
		assertEquals("customer-reference", details.getCustomerRef());
		assertEquals("customer-reference", booking.getSepaCustomerRef());

		booking.setSepaCustomerRef(null);

		assertNull(booking.getSepaDetails());
		assertNull(booking.getSepaCustomerRef());
	}

	private void assertDetailValues(Booking booking, String customerRef, String instref, LocalDate transactionDate, String note) {
		BookingSepaDetails sepaDetails = booking.getSepaDetails();
		BookingAdditionalDetails additionalDetails = booking.getAdditionalDetails();
		BookingCreditCardDetails creditCardDetails = booking.getCreditCardDetails();
		BookingNoteDetails noteDetails = booking.getNoteDetails();
		assertNotNull(sepaDetails);
		assertNotNull(additionalDetails);
		assertNotNull(creditCardDetails);
		assertNotNull(noteDetails);
		assertEquals(customerRef, sepaDetails.getCustomerRef());
		assertEquals(instref, additionalDetails.getInstref());
		assertEquals(transactionDate, creditCardDetails.getTransactionDate());
		assertEquals(note, noteDetails.getNote());
	}
}
