package de.zft2.gbanking.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingFee;
import de.zft2.gbanking.db.dao.BookingForeignCurrencyDetails;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.exception.GBankingException;

public final class BookingCurrencyMapper {

	private static final int EXCHANGE_RATE_SCALE = 12;

	private BookingCurrencyMapper() {
	}

	public static Currency baseCurrency(String currencyCode) {
		return Currency.forCodeOrDefault(currencyCode, Currency.EUR);
	}

	public static void mapAmounts(Booking booking, BigDecimal baseAmount, String amountCurrencyCode,
			Currency baseCurrency, BigDecimal foreignAmount, String foreignCurrencyCode, BigDecimal suppliedExchangeRate) {
		if (booking == null || baseAmount == null) {
			throw new GBankingException("Eine Buchung enthält keinen Betrag in der Kontowährung.");
		}
		Currency effectiveBaseCurrency = baseCurrency != null ? baseCurrency : Currency.EUR;
		Currency amountCurrency = Currency.forCodeOrDefault(amountCurrencyCode, effectiveBaseCurrency);
		if (amountCurrency != effectiveBaseCurrency) {
			throw missingBaseAmount(amountCurrency, effectiveBaseCurrency);
		}

		booking.setAmount(baseAmount.setScale(2, RoundingMode.HALF_UP));
		booking.setForeignCurrencyDetails(createForeignCurrencyDetails(baseAmount, effectiveBaseCurrency,
				foreignAmount, foreignCurrencyCode, suppliedExchangeRate));
	}

	public static BookingForeignCurrencyDetails createForeignCurrencyDetails(BigDecimal baseAmount,
			Currency baseCurrency, BigDecimal foreignAmount, String foreignCurrencyCode, BigDecimal suppliedExchangeRate) {
		Currency foreignCurrency = Currency.forCode(foreignCurrencyCode);
		if (foreignAmount == null && foreignCurrency == null && suppliedExchangeRate == null) {
			return null;
		}
		if (foreignAmount == null || foreignCurrency == null) {
			throw new GBankingException("Die Fremdwährungsdaten einer Buchung sind unvollständig.");
		}
		Currency effectiveBaseCurrency = baseCurrency != null ? baseCurrency : Currency.EUR;
		if (foreignCurrency == effectiveBaseCurrency) {
			return null;
		}

		BigDecimal normalizedForeignAmount = normalizeSign(baseAmount, foreignAmount);
		BigDecimal rate = normalizedRate(baseAmount, normalizedForeignAmount, suppliedExchangeRate);
		BookingForeignCurrencyDetails details = new BookingForeignCurrencyDetails();
		details.setForeignAmount(normalizedForeignAmount);
		details.setForeignCurrency(foreignCurrency);
		details.setExchangeRateToBaseCurrency(rate);
		return details;
	}

	public static BookingFee createFee(BigDecimal amount, String currencyCode, Currency baseCurrency) {
		Currency currency = Currency.forCode(currencyCode);
		if (amount == null) {
			if (currency != null) {
				throw new GBankingException("Die Gebührenangaben einer Buchung sind unvollständig.");
			}
			return null;
		}
		BookingFee fee = new BookingFee();
		fee.setAmount(amount);
		fee.setCurrency(currency != null ? currency : baseCurrency != null ? baseCurrency : Currency.EUR);
		return fee;
	}

	public static void validate(Booking booking, Currency baseCurrency) {
		if (booking == null || booking.getAmount() == null) {
			throw new GBankingException("Eine Buchung enthält keinen Betrag in der Kontowährung.");
		}
		BookingForeignCurrencyDetails foreign = booking.getForeignCurrencyDetails();
		if (foreign != null && (foreign.getForeignAmount() == null || foreign.getForeignCurrency() == null
				|| foreign.getExchangeRateToBaseCurrency() == null || foreign.getExchangeRateToBaseCurrency().signum() <= 0
				|| foreign.getForeignCurrency() == baseCurrency)) {
			throw new GBankingException("Die Fremdwährungsdaten einer Buchung sind ungültig oder unvollständig.");
		}
		BookingFee fee = booking.getFee();
		if (fee != null && (fee.getAmount() == null || fee.getCurrency() == null)) {
			throw new GBankingException("Die Gebührenangaben einer Buchung sind unvollständig.");
		}
	}

	private static BigDecimal normalizedRate(BigDecimal baseAmount, BigDecimal foreignAmount,
			BigDecimal suppliedExchangeRate) {
		if (baseAmount == null || (baseAmount.signum() == 0) != (foreignAmount.signum() == 0)) {
			throw new GBankingException("Für die Fremdwährungsbuchung fehlt ein gültiger Betrag in der Kontowährung.");
		}
		if (baseAmount.signum() != 0 && foreignAmount.signum() != 0) {
			return baseAmount.abs().divide(foreignAmount.abs(), EXCHANGE_RATE_SCALE, RoundingMode.HALF_UP)
					.stripTrailingZeros();
		}
		if (suppliedExchangeRate == null || suppliedExchangeRate.signum() <= 0) {
			throw new GBankingException("Für die Fremdwährungsbuchung fehlt ein gültiger Wechselkurs.");
		}
		return suppliedExchangeRate.abs().stripTrailingZeros();
	}

	private static BigDecimal normalizeSign(BigDecimal baseAmount, BigDecimal foreignAmount) {
		if (baseAmount == null || baseAmount.signum() == 0 || foreignAmount.signum() == 0) {
			return foreignAmount;
		}
		return baseAmount.signum() < 0 ? foreignAmount.abs().negate() : foreignAmount.abs();
	}

	private static GBankingException missingBaseAmount(Currency amountCurrency, Currency baseCurrency) {
		return new GBankingException("Eine Buchung in " + amountCurrency
				+ " enthält keinen Betrag in der Kontowährung " + baseCurrency + ". Der Vorgang wurde abgebrochen.");
	}
}
