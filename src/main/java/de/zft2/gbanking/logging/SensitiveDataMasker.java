package de.zft2.gbanking.logging;

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
