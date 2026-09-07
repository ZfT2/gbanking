package de.zft2.gbanking.file.imp;

import java.util.Map;

import de.zft2.core.dto.Booking;
import de.zft2.core.dto.Booking.Typ;
import de.zft2.core.dto.Counterpart;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.imp.dto.ImportBooking;
import de.zft2.gbanking.rebooking.RebookingRules;

public final class ImportedAccountResolver {

	private static final int IBAN_ACCOUNT_NUMBER_OFFSET = 12;
	private static final int MIN_IBAN_LENGTH_WITH_ACCOUNT_NUMBER = 15;

	private ImportedAccountResolver() {
	}

	public static int resolveAccountId(String fallbackAccountName, Booking importBooking, Map<String, Integer> accountIdsByName) {
		if (importBooking.getAccountName() == null || importBooking.getAccountName().isBlank()) {
			importBooking.setAccountName(fallbackAccountName);
		}
		Integer accountId = accountIdsByName.get(importBooking.getAccountName());
		if (accountId == null) {
			throw new GBankingException("No account found for imported booking accountName: " + importBooking.getAccountName());
		}
		return accountId;
	}

	public static Integer resolveCrossAccountId(Booking importBooking, int sourceAccountId, Map<String, Integer> accountIdsByName,
			Map<String, Integer> accountIdsByIdentifier) {

		if (importBooking.getCrossAccountName() != null) {
			Integer crossAccountId = accountIdsByName.get(importBooking.getCrossAccountName());
			return allowedCrossAccountId(sourceAccountId, crossAccountId, importBooking);
		}
		if (accountIdsByIdentifier == null) {
			return null;
		}
		Counterpart counterpart = importBooking.getCounterpart();
		String crossIban = Counterpart.ibanOf(counterpart);
		Integer crossAccountId = lookupAccountId(accountIdsByIdentifier, crossIban);
		if (crossAccountId == null && crossIban != null && crossIban.length() >= MIN_IBAN_LENGTH_WITH_ACCOUNT_NUMBER) {
			String accountNumber = crossIban.substring(IBAN_ACCOUNT_NUMBER_OFFSET);
			crossAccountId = lookupAccountId(accountIdsByIdentifier, accountNumber);
		}
		if (crossAccountId == null && counterpart != null) {
			crossAccountId = lookupAccountId(accountIdsByIdentifier,
					bankAccountIdentifier(counterpart.getBlz(), counterpart.getAccountNumber()));
		}
		if (crossAccountId == null && counterpart != null) {
			crossAccountId = lookupAccountId(accountIdsByIdentifier, counterpart.getAccountNumber());
		}
		return allowedCrossAccountId(sourceAccountId, crossAccountId, importBooking);
	}

	static String bankAccountIdentifier(String blz, String accountNumber) {
		if (blz == null || blz.isBlank() || accountNumber == null || accountNumber.isBlank()) {
			return null;
		}
		return blz.trim() + "/" + normalizeAccountNumber(accountNumber);
	}

	static String normalizeAccountNumber(String accountNumber) {
		if (accountNumber == null || accountNumber.isBlank()) {
			return null;
		}
		return accountNumber.trim().replaceFirst("^0+(?!$)", "");
	}

	private static Integer lookupAccountId(Map<String, Integer> accountIdsByIdentifier, String identifier) {
		if (identifier == null || identifier.isBlank()) {
			return null;
		}
		Integer accountId = accountIdsByIdentifier.get(identifier.trim());
		return accountId != null || identifier.indexOf('/') >= 0 ? accountId
				: accountIdsByIdentifier.get(normalizeAccountNumber(identifier));
	}

	private static Integer allowedCrossAccountId(int sourceAccountId, Integer crossAccountId, Booking importBooking) {
		return RebookingRules.isForbiddenSameAccountRebooking(sourceAccountId, crossAccountId, isCancellation(importBooking)) ? null : crossAccountId;
	}

	private static boolean isCancellation(Booking importBooking) {
		return importBooking != null && (importBooking.getTyp() == Typ.CANCEL
				|| importBooking instanceof ImportBooking booking && Boolean.TRUE.equals(booking.getAddIsStorno()));
	}
}
