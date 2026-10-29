package de.zft2.gbanking.util;

public final class Ranges {

	private Ranges() {
	}

	/**
	 * Returns {@code true} if both bounds are set and {@code from} lies after {@code to}.
	 */
	public static <T extends Comparable<? super T>> boolean isReversed(T from, T to) {
		return from != null && to != null && from.compareTo(to) > 0;
	}
}
