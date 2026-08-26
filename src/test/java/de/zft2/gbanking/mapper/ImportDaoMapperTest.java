package de.zft2.gbanking.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.zft2.core.util.CoreBookingUtil;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;

class ImportDaoMapperTest extends CoreBookingUtil {

	@Test
	void maptoBankAccountDao_shouldUseFp3BaseCurrency() {
		de.zft2.fp3xmlextract.data.Fp3XmlBankAccount xmlAccount = new de.zft2.fp3xmlextract.data.Fp3XmlBankAccount();
		xmlAccount.setBaseCurrency("CHF");

		BankAccount account = ImportDaoMapper.maptoBankAccountDao(xmlAccount, Source.IMPORT_INITIAL);

		assertEquals(Currency.CHF, account.getBaseCurrency());
	}

	@Test
	void maptoBookingDao_shouldUseFp3BookingCurrency() {
		de.zft2.fp3xmlextract.data.Fp3XmlBooking xmlBooking = new de.zft2.fp3xmlextract.data.Fp3XmlBooking(asLocalDate("14.10.2025"),
				asLocalDate("14.10.2025"), "Fremdwaehrungsbuchung", BigDecimal.TEN, null, null, "Girokonto");
		xmlBooking.setCurrency("USD");

		assertThrows(GBankingException.class,
				() -> ImportDaoMapper.maptoBookingDao(xmlBooking, 1, null, Source.IMPORT_INITIAL, Currency.EUR));
	}

	@Test
	void maptoBookingDao_shouldHandleMissingCrossAccountLookupMap() {
		de.zft2.fp3xmlextract.data.Fp3XmlBooking xmlBooking = new de.zft2.fp3xmlextract.data.Fp3XmlBooking(asLocalDate("14.10.2025"), asLocalDate("14.10.2025"),
				"Testbuchung",
				BigDecimal.valueOf(25.00), null, null, "Girokonto");
		xmlBooking.setCrossAccountIBAN("DE00000000000000000099");

		Booking booking = ImportDaoMapper.maptoBookingDaoList("Girokonto", List.of(xmlBooking), Map.of("Girokonto", 7), null,
				Map.of(7, Currency.EUR), Source.IMPORT_INITIAL).iterator()
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

		Booking booking = ImportDaoMapper.maptoBookingDaoList("Fallbackkonto", List.of(xmlBooking), Map.of("Fallbackkonto", 3), null,
				Map.of(3, Currency.EUR), Source.IMPORT_INITIAL)
				.iterator().next();

		assertEquals(3, booking.getAccountId());
	}
}
