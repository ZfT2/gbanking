package de.zft2.gbanking.util;

public class IbanCalculator {

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

	private static int mod97(String numericString) {
		int remainder = 0;

		for (int i = 0; i < numericString.length(); i++) {
			char c = numericString.charAt(i);

			if (!Character.isDigit(c)) {
				return -1;
			}

			remainder = (remainder * 10 + (c - '0')) % 97;
		}

		return remainder;
	}
}
