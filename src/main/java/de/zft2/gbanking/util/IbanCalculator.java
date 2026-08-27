package de.zft2.gbanking.util;

import java.util.Locale;
import java.util.regex.Pattern;

public class IbanCalculator {
	private static final Pattern IBAN_PATTERN = Pattern.compile("[A-Z]{2}\\d{2}[A-Z0-9]{11,30}");

	private IbanCalculator() {
		/* This utility class should not be instantiated */
	}

	public static String calculateIban(String accountNumber, String blz) {
		if (accountNumber == null || blz == null) {
			return null;
		}

		accountNumber = accountNumber.trim().replace(" ", "");
		blz = blz.trim().replace(" ", "");

		// BLZ needs exactly 8 digits
		if (!blz.matches("\\d{8}")) {
			return null;
		}

		// account number must have between 1 and 10 digits
		if (!accountNumber.matches("\\d{1,10}")) {
			return null;
		}

		// german account number in IBAN-format always 10 digits, fill with zeros on
		// left side
		String paddedAccountNumber = String.format("%10s", accountNumber).replace(' ', '0');

		// IBAN for germany: BLZ + 10 digit account number
		String bban = blz + paddedAccountNumber;

		// For DE: D = 13, E = 14
		// checksum starts with "00"
		String checkString = bban + "131400";

		int remainder = mod97(checkString);
		int checkDigits = 98 - remainder;

		if (checkDigits < 1 || checkDigits > 98) {
			return null;
		}

		return "DE" + String.format("%02d", checkDigits) + bban;
	}

	public static boolean isValidIban(String iban) {
		if (iban == null) {
			return false;
		}

		String normalizedIban = iban.trim().replace(" ", "").replace("\u00A0", "").toUpperCase(Locale.ROOT);
		if (!IBAN_PATTERN.matcher(normalizedIban).matches()) {
			return false;
		}

		String rearrangedIban = normalizedIban.substring(4) + normalizedIban.substring(0, 4);
		return mod97(rearrangedIban) == 1;
	}

	private static int mod97(String value) {
		int remainder = 0;

		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			if (Character.isDigit(character)) {
				remainder = (remainder * 10 + (character - '0')) % 97;
			} else if (character >= 'A' && character <= 'Z') {
				remainder = (remainder * 100 + character - 'A' + 10) % 97;
			} else {
				return -1;
			}
		}

		return remainder;
	}
}
