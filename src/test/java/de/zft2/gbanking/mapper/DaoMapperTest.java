package de.zft2.gbanking.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.mapper.DaoMapper;

class DaoMapperTest {

	@Test
	void maptoBookingDao_shouldHandleMissingCrossAccountLookupMap() {
		de.zft2.fp3xmlextract.data.Booking xmlBooking = new de.zft2.fp3xmlextract.data.Booking("14.10.2025", "14.10.2025", "Testbuchung",
				BigDecimal.valueOf(25.00), null, null, "Girokonto");
		xmlBooking.setCrossAccountIBAN("DE00000000000000000099");

		Booking booking = DaoMapper.maptoBookingDao("Girokonto", xmlBooking, Map.of("Girokonto", 7), null, Source.IMPORT_INITIAL);

		assertEquals(7, booking.getAccountId());
		assertNull(booking.getCrossAccountId());
	}

	@Test
	void maptoBookingDao_shouldUseFallbackAccountNameWhenBookingHasNoAccountName() {
		de.zft2.fp3xmlextract.data.Booking xmlBooking = new de.zft2.fp3xmlextract.data.Booking("14.10.2025", "14.10.2025", "Testbuchung",
				BigDecimal.valueOf(25.00), null, null, null);
		xmlBooking.setAccountNamePP(null);

		Booking booking = DaoMapper.maptoBookingDao("Fallbackkonto", xmlBooking, Map.of("Fallbackkonto", 3), null, Source.IMPORT_INITIAL);

		assertEquals(3, booking.getAccountId());
	}
}
