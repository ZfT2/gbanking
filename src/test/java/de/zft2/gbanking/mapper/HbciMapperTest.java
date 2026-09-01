package de.zft2.gbanking.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;

class HbciMapperTest {

	@Test
	void mapKontoToBankAccount_shouldUseZeroWhenHbciAccountTypeIsMissing() {
		Konto konto = createKonto(null);

		BankAccount bankAccount = HbciMapper.mapKontoToBankAccount("Sparkasse", konto);

		assertEquals(0, bankAccount.getHbciAccountType());
	}

	@Test
	void mapKontoToBankAccount_shouldUseZeroWhenHbciAccountTypeIsInvalid() {
		Konto konto = createKonto("Girokonto");

		BankAccount bankAccount = HbciMapper.mapKontoToBankAccount("Sparkasse", konto);

		assertEquals(0, bankAccount.getHbciAccountType());
	}

	@Test
	void mapKontoToBankAccount_shouldPreserveNumericHbciAccountType() {
		Konto konto = createKonto("0001");

		BankAccount bankAccount = HbciMapper.mapKontoToBankAccount("Sparkasse", konto);

		assertEquals(1, bankAccount.getHbciAccountType());
	}

	@Test
	void mapUmsLineToBooking_shouldSeparateBaseAmountForeignAmountAndFee() {
		UmsLine line = new UmsLine();
		line.bdate = new Date();
		line.valuta = new Date();
		line.value = new Value(new BigDecimal("-8.75"), "EUR");
		line.orig_value = new Value(new BigDecimal("-10.00"), "USD");
		line.charge_value = new Value(new BigDecimal("0.25"), "USD");

		Booking booking = HbciMapper.mapUmsLineToBooking(42, line, Currency.EUR, Source.ONLINE_NEW);

		assertEquals(new BigDecimal("-8.75"), booking.getAmount());
		assertEquals(0, new BigDecimal("-10.00").compareTo(booking.getForeignCurrencyDetails().getForeignAmount()));
		assertEquals(Currency.USD, booking.getForeignCurrencyDetails().getForeignCurrency());
		assertEquals(new BigDecimal("0.875"), booking.getForeignCurrencyDetails().getExchangeRateToBaseCurrency());
		assertEquals(new BigDecimal("0.25"), booking.getFee().getAmount());
		assertEquals(Currency.USD, booking.getFee().getCurrency());
	}

	@Test
	void mapKontoToBankAccount_shouldRejectUnsupportedExplicitCurrency() {
		Konto konto = createKonto("0001");
		konto.curr = "ZZZ";

		assertThrows(GBankingException.class, () -> HbciMapper.mapKontoToBankAccount("Sparkasse", konto));
	}

	private static Konto createKonto(String hbciAccountType) {
		Konto konto = new Konto();
		konto.country = "DE";
		konto.blz = "12345678";
		konto.number = "1234567890";
		konto.iban = "DE02123456781234567890";
		konto.bic = "TESTDEFFXXX";
		konto.name = "Max Mustermann";
		konto.type = "Girokonto";
		konto.acctype = hbciAccountType;
		konto.curr = "EUR";
		konto.allowedGVs = List.of();
		return konto;
	}
}
