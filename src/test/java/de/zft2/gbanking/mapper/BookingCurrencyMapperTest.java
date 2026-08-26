package de.zft2.gbanking.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingFee;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.exception.GBankingException;

class BookingCurrencyMapperTest {

	@Test
	void mapAmounts_shouldDefaultOnlyMissingAmountCurrencyToAccountCurrency() {
		Booking booking = new Booking();

		BookingCurrencyMapper.mapAmounts(booking, new BigDecimal("12.34"), null, Currency.CHF,
				null, null, null);

		assertEquals(new BigDecimal("12.34"), booking.getAmount());
		assertNull(booking.getForeignCurrencyDetails());
	}

	@Test
	void mapAmounts_shouldRejectForeignAmountWithoutConvertedBaseAmount() {
		Booking booking = new Booking();

		GBankingException exception = assertThrows(GBankingException.class,
				() -> BookingCurrencyMapper.mapAmounts(booking, new BigDecimal("10.00"), "USD", Currency.EUR,
						null, null, null));

		assertTrue(exception.getMessage().contains("keinen Betrag in der Kontowährung EUR"));
	}

	@Test
	void mapAmounts_shouldNormalizeExchangeRateToForeignTimesRateEqualsBase() {
		Booking booking = new Booking();

		BookingCurrencyMapper.mapAmounts(booking, new BigDecimal("8.75"), "EUR", Currency.EUR,
				new BigDecimal("10.00"), "USD", new BigDecimal("1.142857"));

		assertEquals(new BigDecimal("0.875"), booking.getForeignCurrencyDetails().getExchangeRateToBaseCurrency());
	}

	@Test
	void mapAmounts_shouldNormalizeForeignAmountSign() {
		Booking booking = new Booking();

		BookingCurrencyMapper.mapAmounts(booking, new BigDecimal("-8.75"), "EUR", Currency.EUR,
				new BigDecimal("10.00"), "USD", null);

		assertEquals(new BigDecimal("-10.00"), booking.getForeignCurrencyDetails().getForeignAmount());
	}

	@Test
	void currency_shouldRejectExplicitUnknownCodeAndFeeShouldDefaultMissingCurrency() {
		GBankingException exception = assertThrows(GBankingException.class, () -> Currency.forCode("ZZZ"));
		BookingFee fee = BookingCurrencyMapper.createFee(new BigDecimal("1.25"), null, Currency.GBP);

		assertTrue(exception.getMessage().contains("Währung ZZZ wird noch nicht unterstützt"));
		assertEquals(Currency.GBP, fee.getCurrency());
	}

	@Test
	void currency_shouldMapExtendedCurrencySet() {
		assertEquals(40, Currency.values().length);
		assertEquals(Currency.PEN, Currency.forInt(40));
		assertEquals(Currency.MXN, Currency.forCode("mxn"));
	}

	@Test
	void createFee_shouldRejectCurrencyWithoutAmount() {
		assertThrows(GBankingException.class,
				() -> BookingCurrencyMapper.createFee(null, "USD", Currency.EUR));
	}
}
