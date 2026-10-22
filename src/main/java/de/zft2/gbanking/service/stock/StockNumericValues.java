package de.zft2.gbanking.service.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;

import de.zft2.gbanking.db.dao.enu.Currency;

/**
 * Central helper for the conversion of stock values between decimal values and scaled long values.
 *
 * <p>
 * The stock entities store quantities, prices and rates as scaled long values to avoid rounding
 * errors. The conversion is done here so that the used rounding and the scaling are consistent
 * across the stock services.
 * </p>
 */
public final class StockNumericValues {

	private StockNumericValues() {
	}

	/**
	 * Converts a scaled long value into a decimal value without trailing zeros.
	 *
	 * @param value the scaled value
	 * @param scale the number of decimal digits the value was scaled with
	 * @return the decimal value
	 */
	public static BigDecimal fromScaled(long value, int scale) {
		return BigDecimal.valueOf(value, scale).stripTrailingZeros();
	}

	/**
	 * Converts a decimal value into a scaled long value.
	 *
	 * @param value        the decimal value
	 * @param scale        the number of decimal digits to scale the value with
	 * @param roundingMode the rounding mode used to adapt the scale of the value
	 * @return the scaled value
	 * @throws ArithmeticException if the scaled value does not fit into a long value
	 */
	public static long toScaledLong(BigDecimal value, int scale, RoundingMode roundingMode) {
		return value.setScale(scale, roundingMode).movePointRight(scale).longValueExact();
	}

	/**
	 * Converts a decimal amount into the minor units of the given currency.
	 *
	 * @param value    the amount
	 * @param currency the currency that defines the number of minor unit digits
	 * @return the amount in minor units
	 * @throws ArithmeticException if the amount does not fit into a long value
	 */
	public static long toMinorUnits(BigDecimal value, Currency currency) {
		return toScaledLong(value, currency.getMinorUnitDigits(), RoundingMode.HALF_UP);
	}
}
