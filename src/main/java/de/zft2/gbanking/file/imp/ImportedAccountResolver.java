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
		String crossIban = Counterpart.ibanOf(importBooking.getCounterpart());
		if (crossIban == null || accountIdsByIdentifier == null) {
			return null;
		}
		Integer crossAccountId = accountIdsByIdentifier.get(normalizeIdentifier(crossIban));
		if (crossAccountId == null && crossIban.length() >= MIN_IBAN_LENGTH_WITH_ACCOUNT_NUMBER) {
			String accountNumber = crossIban.substring(IBAN_ACCOUNT_NUMBER_OFFSET);
			crossAccountId = accountIdsByIdentifier.get(normalizeIdentifier(accountNumber));
		}
		return allowedCrossAccountId(sourceAccountId, crossAccountId, importBooking);
	}

	private static String normalizeIdentifier(String identifier) {
		return identifier.replaceFirst("^0+(?!$)", "");
	}

	private static Integer allowedCrossAccountId(int sourceAccountId, Integer crossAccountId, Booking importBooking) {
		return RebookingRules.isForbiddenSameAccountRebooking(sourceAccountId, crossAccountId, isCancellation(importBooking)) ? null : crossAccountId;
	}

	private static boolean isCancellation(Booking importBooking) {
		return importBooking != null && (importBooking.getTyp() == Typ.CANCEL
				|| importBooking instanceof ImportBooking booking && Boolean.TRUE.equals(booking.getAddIsStorno()));
	}
}
