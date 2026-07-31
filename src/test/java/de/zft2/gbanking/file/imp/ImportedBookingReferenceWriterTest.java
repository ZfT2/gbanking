package de.zft2.gbanking.file.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.Source;

class ImportedBookingReferenceWriterTest {

	@Test
	void writeRecipients_shouldResolveEqualRecipientsOnceAndUpdateBookingsGrouped() {
		DBController dbController = mock(DBController.class);
		when(dbController.resolveRecipient(any(Recipient.class))).thenAnswer(invocation -> {
			Recipient recipient = invocation.getArgument(0);
			int id = "Different Recipient".equals(recipient.getName()) ? 22 : 11;
			return resolvedRecipient(recipient, id);
		});

		Booking upperCaseBooking = booking(1, recipient("READABLE RECIPIENT", "DE123", "LOUD BANK"));
		Booking readableBooking = booking(2, recipient("Readable Recipient", "de123", "Loud Bank"));
		Booking differentBooking = booking(3, recipient("Different Recipient", "DE123", "Loud Bank"));

		new ImportedBookingReferenceWriter(dbController).writeRecipients(List.of(upperCaseBooking, readableBooking, differentBooking));

		ArgumentCaptor<Recipient> recipientCaptor = ArgumentCaptor.forClass(Recipient.class);
		verify(dbController, times(2)).resolveRecipient(recipientCaptor.capture());
		assertEquals("Readable Recipient", recipientCaptor.getAllValues().get(0).getName());
		assertEquals("Loud Bank", recipientCaptor.getAllValues().get(0).getBank());
		assertEquals("Different Recipient", recipientCaptor.getAllValues().get(1).getName());

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<Recipient, Set<Integer>>> updateCaptor = ArgumentCaptor.forClass(Map.class);
		verify(dbController, times(2)).updateBookingsWithRecipients(updateCaptor.capture());
		assertEquals(Set.of(1, 2), onlyBookingIds(updateCaptor.getAllValues().get(0)));
		assertEquals(Set.of(3), onlyBookingIds(updateCaptor.getAllValues().get(1)));

		assertEquals(11, upperCaseBooking.getRecipient().getId());
		assertEquals(11, readableBooking.getRecipient().getId());
		assertEquals(22, differentBooking.getRecipient().getId());
	}

	private static Set<Integer> onlyBookingIds(Map<Recipient, Set<Integer>> updateMap) {
		assertEquals(1, updateMap.size());
		return updateMap.values().iterator().next();
	}

	private static Booking booking(int id, Recipient recipient) {
		Booking booking = new Booking();
		booking.setId(id);
		booking.setRecipient(recipient);
		return booking;
	}

	private static Recipient recipient(String name, String iban, String bank) {
		return new Recipient(name, iban, null, null, null, bank, Source.IMPORT_INITIAL);
	}

	private static Recipient resolvedRecipient(Recipient recipient, int id) {
		Recipient resolvedRecipient = new Recipient(recipient.getName(), recipient.getIban(), recipient.getBic(), recipient.getAccountNumber(),
				recipient.getBlz(), recipient.getBank(), recipient.getSource());
		resolvedRecipient.setId(id);
		return resolvedRecipient;
	}
}
