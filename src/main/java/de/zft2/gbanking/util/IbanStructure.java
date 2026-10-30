package de.zft2.gbanking.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Derives the national account number from an IBAN.
 *
 * <p>The BBAN of an IBAN is built from country specific fields. Only the field holding the national
 * account number is relevant for the account number, so its position and length are needed for every
 * country. The layouts use the ISO 13616 field notation, where {@code b} is the bank code, {@code s}
 * the branch code, {@code c} the account number and {@code x} the national check digits. The national
 * account number is the {@code c} field including the trailing national check digits, because these
 * digits belong to the account number in the national notation.
 */
public class IbanStructure {

	private static final int BBAN_START = 4;

	/** Position and length of the account number field within the BBAN. */
	private record AccountNumberRange(int start, int length) {
	}

	/** ISO 13616 field layouts of the BBAN, keyed by ISO 3166 country code. */
	private static final Map<String, String> BBAN_LAYOUTS = Map.ofEntries(
			Map.entry("AD", "bbbbsssscccccccccccc"),
			Map.entry("AE", "bbbcccccccccccccccc"),
			Map.entry("AL", "bbbssssxcccccccccccccccc"),
			Map.entry("AT", "bbbbbccccccccccc"),
			Map.entry("AZ", "bbbbcccccccccccccccccccc"),
			Map.entry("BA", "bbbsssccccccccxx"),
			Map.entry("BE", "bbbcccccccxx"),
			Map.entry("BG", "bbbbssssttcccccccc"),
			Map.entry("BH", "bbbbcccccccccccccc"),
			Map.entry("BI", "bbbbbsssssccccccccccccc"),
			Map.entry("BR", "bbbbbbbbssssscccccccccctn"),
			Map.entry("BY", "bbbbaaaacccccccccccccccc"),
			Map.entry("CH", "bbbbbcccccccccccc"),
			Map.entry("CR", "0bbbcccccccccccccc"),
			Map.entry("CY", "bbbssssscccccccccccccccc"),
			Map.entry("CZ", "bbbbppppppcccccccccc"),
			Map.entry("DE", "bbbbbbbbcccccccccc"),
			Map.entry("DJ", "bbbbbsssssccccccccccccc"),
			Map.entry("DK", "bbbbcccccccccx"),
			Map.entry("DO", "bbbbcccccccccccccccccccc"),
			Map.entry("EE", "bbsscccccccccccx"),
			Map.entry("EG", "bbbbssssccccccccccccccccc"),
			Map.entry("ES", "bbbbssssxxcccccccccc"),
			Map.entry("FI", "bbbbbbcccccccx"),
			Map.entry("FK", "bbcccccccccccc"),
			Map.entry("FO", "bbbbcccccccccx"),
			Map.entry("FR", "bbbbbssssscccccccccccxx"),
			Map.entry("GB", "bbbbsssssscccccccc"),
			Map.entry("GE", "bbcccccccccccccccc"),
			Map.entry("GI", "bbbbccccccccccccccc"),
			Map.entry("GL", "bbbbcccccccccx"),
			Map.entry("GR", "bbbsssscccccccccccccccc"),
			Map.entry("GT", "bbbbmmttcccccccccccccccc"),
			Map.entry("HN", "bbbbcccccccccccccccccccc"),
			Map.entry("HR", "bbbbbbbcccccccccc"),
			Map.entry("HU", "bbbssssxcccccccccccccccx"),
			Map.entry("IE", "qqqqbbbbbbcccccccc"),
			Map.entry("IL", "bbbsssccccccccccccc"),
			Map.entry("IQ", "bbbssscccccccccccc"),
			Map.entry("IS", "bbssttcccccciiiiiiiiii"),
			Map.entry("IT", "xbbbbbssssscccccccccccc"),
			Map.entry("JO", "bbbbsssscccccccccccccccccc"),
			Map.entry("KW", "bbbbcccccccccccccccccccccc"),
			Map.entry("KZ", "bbbccccccccccccc"),
			Map.entry("LB", "bbbbcccccccccccccccccccc"),
			Map.entry("LC", "bbbbcccccccccccccccccccccccc"),
			Map.entry("LI", "bbbbbcccccccccccc"),
			Map.entry("LT", "bbbbbccccccccccc"),
			Map.entry("LU", "bbbccccccccccccc"),
			Map.entry("LV", "bbbbccccccccccccc"),
			Map.entry("LY", "bbbsssccccccccccccccc"),
			Map.entry("MC", "bbbbbssssscccccccccccxx"),
			Map.entry("MD", "bbcccccccccccccccccc"),
			Map.entry("ME", "bbbcccccccccccccxx"),
			Map.entry("MK", "bbbccccccccccxx"),
			Map.entry("MN", "bbbbcccccccccccc"),
			Map.entry("MR", "bbbbbssssscccccccccccxx"),
			Map.entry("MT", "bbbbssssscccccccccccccccccc"),
			Map.entry("MU", "bbbbbbsscccccccccccc000mmm"),
			Map.entry("NI", "aaaacccccccccccccccccccc"),
			Map.entry("NL", "bbbbcccccccccx"),
			Map.entry("NO", "bbbbccccccx"),
			Map.entry("OM", "bbbcccccccccccccccc"),
			Map.entry("PK", "bbbbcccccccccccccccc"),
			Map.entry("PL", "bbbbbbbbcccccccccccccccc"),
			Map.entry("PS", "bbbbccccccccccccccccccccc"),
			Map.entry("PT", "bbbbsssscccccccccccxx"),
			Map.entry("QA", "bbbbccccccccccccccccccccc"),
			Map.entry("RO", "bbbbcccccccccccccccc"),
			Map.entry("RS", "bbbcccccccccccccxx"),
			Map.entry("RU", "bbbbbbbbbsssssccccccccccccccc"),
			Map.entry("SA", "bbcccccccccccccccccc"),
			Map.entry("SC", "bbbbbbssccccccccccccccccmmm"),
			Map.entry("SD", "bbcccccccccccc"),
			Map.entry("SE", "bbbccccccccccccccccx"),
			Map.entry("SI", "bbsssccccccccxx"),
			Map.entry("SK", "bbbbppppppcccccccccc"),
			Map.entry("SM", "xbbbbbssssscccccccccccc"),
			Map.entry("SO", "bbbssscccccccccccc"),
			Map.entry("ST", "bbbbssssccccccccccccc"),
			Map.entry("SV", "bbbbcccccccccccccccccccc"),
			Map.entry("TL", "bbbccccccccccccccxx"),
			Map.entry("TN", "bbssscccccccccccccxx"),
			Map.entry("TR", "bbbbb0cccccccccccccccc"),
			Map.entry("UA", "bbbbbbccccccccccccccccccc"),
			Map.entry("VA", "bbbccccccccccccccc"),
			Map.entry("VG", "bbbbcccccccccccccccc"),
			Map.entry("XK", "bbbbcccccccccccc"),
			Map.entry("YE", "bbbbsssscccccccccccccccccc"));

	private static final Map<String, AccountNumberRange> ACCOUNT_NUMBER_RANGES = buildAccountNumberRanges();

	private IbanStructure() {
		/* This utility class should not be instantiated */
	}

	/**
	 * Returns the national account number contained in the given IBAN.
	 *
	 * @param iban the IBAN in electronic or print format
	 * @return the national account number, or {@code null} if the country is unknown or the IBAN is
	 *         malformed
	 */
	public static String extractAccountNumber(String iban) {
		String normalizedIban = normalizeIban(iban);
		if (normalizedIban == null) {
			return null;
		}
		AccountNumberRange range = ACCOUNT_NUMBER_RANGES.get(normalizedIban.substring(0, 2));
		if (range == null) {
			return null;
		}
		int start = BBAN_START + range.start();
		int end = start + range.length();
		if (end > normalizedIban.length()) {
			return null;
		}
		return normalizedIban.substring(start, end);
	}

	/**
	 * Returns the IBAN in electronic format, or {@code null} if the value is not a valid IBAN.
	 *
	 * @param iban the IBAN in electronic or print format
	 * @return the normalized IBAN
	 */
	public static String normalizeIban(String iban) {
		String normalized = IbanCalculator.toElectronicFormat(iban);
		return IbanCalculator.isValidIban(normalized) ? normalized : null;
	}

	private static Map<String, AccountNumberRange> buildAccountNumberRanges() {
		Map<String, AccountNumberRange> ranges = new LinkedHashMap<>();
		BBAN_LAYOUTS.forEach((country, layout) -> {
			AccountNumberRange range = parseAccountNumberRange(layout);
			if (range != null) {
				ranges.put(country, range);
			}
		});
		return Map.copyOf(ranges);
	}

	private static AccountNumberRange parseAccountNumberRange(String layout) {
		int accountStart = layout.indexOf('c');
		if (accountStart < 0) {
			return null;
		}
		int accountEnd = layout.lastIndexOf('c');
		// The national check digits belong to the account number in the national notation.
		while (accountEnd + 1 < layout.length() && layout.charAt(accountEnd + 1) == 'x') {
			accountEnd++;
		}
		return new AccountNumberRange(accountStart, accountEnd - accountStart + 1);
	}
}
