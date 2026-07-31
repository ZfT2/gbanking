package de.zft2.gbanking.file.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import de.zft2.core.dto.Booking;
import de.zft2.core.dto.Booking.Typ;
import de.zft2.core.dto.Counterpart;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.imp.dto.ImportBooking;

class ImportedAccountResolverTest {

	@Test
	void resolveAccountId_shouldUseFallbackAndRejectUnknownAccounts() {
		ImportBooking booking = new ImportBooking();
		ImportBooking unknownAccountBooking = new ImportBooking();
		Map<String, Integer> noAccounts = Map.of();

		assertEquals(7, ImportedAccountResolver.resolveAccountId("Girokonto", booking, Map.of("Girokonto", 7)));
		assertEquals("Girokonto", booking.getAccountName());
		assertThrows(GBankingException.class,
				() -> ImportedAccountResolver.resolveAccountId("Unbekannt", unknownAccountBooking, noAccounts));
	}

	@Test
	void resolveCrossAccountId_shouldResolveFullIbanAndGermanAccountNumber() {
		Booking booking = bookingWithIban("DE44500105170000001234");
		Map<String, Integer> accountIdsByIdentifier = Map.of("DE44500105170000001234", 8, "1234", 9);

		assertEquals(8, ImportedAccountResolver.resolveCrossAccountId(booking, 7, Map.of(), accountIdsByIdentifier));

		booking = bookingWithIban("DE44500105170000001234");
		assertEquals(9, ImportedAccountResolver.resolveCrossAccountId(booking, 7, Map.of(), Map.of("1234", 9)));
	}

	@Test
	void resolveCrossAccountId_shouldOnlyAllowSameAccountForCancellations() {
		ImportBooking booking = new ImportBooking();
		booking.setCrossAccountName("Girokonto");
		Map<String, Integer> accountIdsByName = Map.of("Girokonto", 7);

		assertNull(ImportedAccountResolver.resolveCrossAccountId(booking, 7, accountIdsByName, Map.of()));

		booking.setTyp(Typ.CANCEL);
		assertEquals(7, ImportedAccountResolver.resolveCrossAccountId(booking, 7, accountIdsByName, Map.of()));

		booking.setTyp(null);
		booking.setAddIsStorno(true);
		assertEquals(7, ImportedAccountResolver.resolveCrossAccountId(booking, 7, accountIdsByName, Map.of()));
	}

	private static Booking bookingWithIban(String iban) {
		Booking booking = mock(Booking.class);
		Counterpart counterpart = mock(Counterpart.class);
		when(booking.getCounterpart()).thenReturn(counterpart);
		when(counterpart.getIban()).thenReturn(iban);
		return booking;
	}
}
