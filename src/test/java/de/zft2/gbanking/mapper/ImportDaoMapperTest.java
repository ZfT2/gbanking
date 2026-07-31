package de.zft2.gbanking.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.zft2.core.util.CoreBookingUtil;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.Source;

class ImportDaoMapperTest extends CoreBookingUtil {

	@Test
	void maptoBookingDao_shouldHandleMissingCrossAccountLookupMap() {
		de.zft2.fp3xmlextract.data.Fp3XmlBooking xmlBooking = new de.zft2.fp3xmlextract.data.Fp3XmlBooking(asLocalDate("14.10.2025"), asLocalDate("14.10.2025"),
				"Testbuchung",
				BigDecimal.valueOf(25.00), null, null, "Girokonto");
		xmlBooking.setCrossAccountIBAN("DE00000000000000000099");

		Booking booking = ImportDaoMapper.maptoBookingDaoList("Girokonto", List.of(xmlBooking), Map.of("Girokonto", 7), null, Source.IMPORT_INITIAL).iterator()
				.next();

		assertEquals(7, booking.getAccountId());
		assertNull(booking.getCrossAccountId());
	}

	@Test
	void maptoBookingDao_shouldUseFallbackAccountNameWhenBookingHasNoAccountName() {
		de.zft2.fp3xmlextract.data.Fp3XmlBooking xmlBooking = new de.zft2.fp3xmlextract.data.Fp3XmlBooking(asLocalDate("14.10.2025"), asLocalDate("14.10.2025"),
				"Testbuchung",
				BigDecimal.valueOf(25.00), null, null, null);
		xmlBooking.setAccountName(null);

		Booking booking = ImportDaoMapper.maptoBookingDaoList("Fallbackkonto", List.of(xmlBooking), Map.of("Fallbackkonto", 3), null, Source.IMPORT_INITIAL)
				.iterator().next();

		assertEquals(3, booking.getAccountId());
	}
}
