package de.zft2.gbanking.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Central helper for the comparison and normalization of monetary amounts.
 *
 * <p>
 * The methods clarify how a missing amount is treated: a missing amount is either never equal to
 * a present one or it counts as zero. Both variants are needed in the application, so they are
 * offered under explicit names instead of a single method with a flag.
 * </p>
 */
public final class Amounts {

	private static final int CURRENCY_SCALE = 2;

	private Amounts() {
	}

	/**
	 * Compares two amounts. A missing amount is never equal to a present one.
	 *
	 * @param left  the first amount, may be {@code null}
	 * @param right the second amount, may be {@code null}
	 * @return {@code true} if both amounts are present and have the same value
	 */
	public static boolean sameAmount(BigDecimal left, BigDecimal right) {
		return left != null && right != null && left.compareTo(right) == 0;
	}

	/**
	 * Compares two amounts. Two missing amounts are considered equal.
	 *
	 * @param left  the first amount, may be {@code null}
	 * @param right the second amount, may be {@code null}
	 * @return {@code true} if both amounts are missing or have the same value
	 */
	public static boolean sameAmountIncludingMissing(BigDecimal left, BigDecimal right) {
		return left == null ? right == null : right != null && left.compareTo(right) == 0;
	}

	/**
	 * Rounds the amount to the currency scale. A missing amount is normalized to zero.
	 *
	 * @param amount the amount to normalize, may be {@code null}
	 * @return the rounded amount, never {@code null}
	 */
	public static BigDecimal normalizeAmount(BigDecimal amount) {
		return amount == null ? BigDecimal.ZERO : amount.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
	}

	/**
	 * Compares two amounts rounded to the currency scale. A missing amount counts as zero.
	 *
	 * @param left  the first amount, may be {@code null}
	 * @param right the second amount, may be {@code null}
	 * @return {@code true} if both normalized amounts have the same value
	 */
	public static boolean sameRoundedAmount(BigDecimal left, BigDecimal right) {
		return normalizeAmount(left).compareTo(normalizeAmount(right)) == 0;
	}
}
