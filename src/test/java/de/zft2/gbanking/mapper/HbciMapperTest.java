package de.zft2.gbanking.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;

class HbciMapperTest {

	@ParameterizedTest
	@CsvSource(value = { "<null>, 0", "Girokonto, 0", "0001, 1" }, nullValues = "<null>")
	void mapKontoToBankAccount_shouldMapHbciAccountType(String accountType, int expectedAccountType) {
		Konto konto = createKonto(accountType);

		BankAccount bankAccount = HbciMapper.mapKontoToBankAccount("Sparkasse", konto);

		assertEquals(expectedAccountType, bankAccount.getHbciAccountType());
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

	@ParameterizedTest
	@CsvSource({
			"1, Girokonto, CURRENT_ACCOUNT",
			"10, Sparkonto, SAVINGS_ACCOUNT",
			"20, Festgeldkonto, FIXED_DEPOSIT",
			"30, Wertpapierdepot, DEPOT",
			"39, Wertpapierdepot, DEPOT",
			"40, Darlehenskonto, CREDIT_ACCOUNT",
			"50, Kreditkarte, CREDIT_CARD",
			"60, Fondsdepot, DEPOT",
			"70, Bausparkonto, SAVEINGS_HOME",
			"90, Verrechnungskonto, DEPOT_ACCOUNT"
	})
	void getAccountType_shouldUseFinTsCodeRangesAndDescriptionFallback(String code, String description,
			String expectedType) {
		Konto konto = createKonto(code);
		konto.type = description;

		assertEquals(expectedType, HbciMapper.getAccountType(konto).name());
	}

	@Test
	void getAccountType_shouldRecognizePortfolioCapabilityEvenForUnknownCode() {
		Konto konto = createKonto("90");
		konto.type = "Sonstiges Konto";
		konto.allowedGVs = List.of("HKWPD");

		assertEquals("DEPOT", HbciMapper.getAccountType(konto).name());
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
