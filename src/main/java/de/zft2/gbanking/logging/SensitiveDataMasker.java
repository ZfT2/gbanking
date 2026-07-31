package de.zft2.gbanking.logging;

import java.math.BigDecimal;
import java.util.Collection;

public final class SensitiveDataMasker {

	private SensitiveDataMasker() {
	}

	public static String maskIban(String iban) {
		return maskTail(iban, 4);
	}

	public static String maskAccountNumber(String value) {
		return maskTail(value, 3);
	}

	public static String maskIdentifier(String value) {
		return maskTail(value, 2);
	}

	public static String describePresence(Object value) {
		return value == null ? "none" : "present";
	}

	public static String describeText(String value) {
		if (value == null || value.isBlank()) {
			return "none";
		}
		return "present(length=" + value.trim().length() + ")";
	}

	public static String describeText(Object value) {
		if (value instanceof String text) {
			return describeText(text);
		}
		if (value instanceof Collection<?> collection) {
			return collection.isEmpty() ? "none" : "present(items=" + collection.size() + ")";
		}
		return describePresence(value);
	}

	public static String describeAmount(BigDecimal amount) {
		if (amount == null) {
			return "none";
		}
		String amountDescription = null;
		switch (amount.signum()) {
		case -1:
			amountDescription = "negative";
			break;
		case 1:
			amountDescription = "positive";
			break;
		default:
			amountDescription = "zero";
		}
		return amountDescription;
	}

	public static String describeAmount(Object amount) {
		if (amount instanceof BigDecimal bigDecimal) {
			return describeAmount(bigDecimal);
		}
		return describePresence(amount);
	}

	private static String maskTail(String value, int visibleChars) {
		if (value == null || value.isBlank()) {
			return value;
		}
		String normalized = value.replaceAll("\\s+", "");
		if (normalized.length() <= visibleChars) {
			return "*".repeat(normalized.length());
		}
		return "*".repeat(normalized.length() - visibleChars) + normalized.substring(normalized.length() - visibleChars);
	}
}
