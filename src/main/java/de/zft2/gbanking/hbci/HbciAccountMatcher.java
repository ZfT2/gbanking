package de.zft2.gbanking.hbci;

import static de.zft2.gbanking.mapper.HbciMapper.normalizeAccountNumber;
import static de.zft2.gbanking.util.TextValues.firstWithText;
import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.util.Locale;
import java.util.function.Function;

import org.kapott.hbci.structures.Konto;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.logging.SensitiveDataMasker;
import de.zft2.gbanking.messages.Messages;

/**
 * Resolves the HBCI account ({@link Konto}) that belongs to a locally stored {@link BankAccount}.
 * <p>
 * If both sides provide an IBAN, only the IBAN decides. Otherwise the account number must match and, if both sides
 * provide one, the subaccount number as well (leading zeroes are ignored). IBAN matches take precedence over account
 * number matches. Several equally good matches are reported as ambiguous instead of silently picking the first one.
 */
public final class HbciAccountMatcher {

	private static final int NO_MATCH = 0;
	private static final int NUMBER_MATCH = 1;
	private static final int IBAN_MATCH = 2;

	private HbciAccountMatcher() {
	}

	/**
	 * Returns the HBCI account matching the given bank account.
	 *
	 * @return the matching account, or {@code null} if none matches
	 * @throws GBankingException if several accounts match equally well
	 */
	public static Konto findAccount(Konto[] accounts, BankAccount bankAccount) {
		return findUnique(accounts, Function.identity(), bankAccount);
	}

	/**
	 * Returns the candidate whose HBCI account matches the given bank account best.
	 *
	 * @param accountOf extracts the HBCI account of a candidate, may return {@code null}
	 * @return the matching candidate, or {@code null} if none matches
	 * @throws GBankingException if several candidates match equally well
	 */
	public static <T> T findUnique(T[] candidates, Function<T, Konto> accountOf, BankAccount bankAccount) {
		if (candidates == null || bankAccount == null) {
			return null;
		}
		T bestMatch = null;
		int bestQuality = NO_MATCH;
		boolean ambiguous = false;
		for (T candidate : candidates) {
			int quality = candidate != null ? matchQuality(bankAccount, accountOf.apply(candidate)) : NO_MATCH;
			if (quality > bestQuality) {
				bestMatch = candidate;
				bestQuality = quality;
				ambiguous = false;
			} else if (quality != NO_MATCH && quality == bestQuality) {
				ambiguous = true;
			}
		}
		if (ambiguous) {
			String accountIdentifier = firstWithText(SensitiveDataMasker.maskIban(bankAccount.getIban()),
					SensitiveDataMasker.maskAccountNumber(bankAccount.getNumber()));
			throw new GBankingException(Messages.getInstance().getFormattedMessage("EXCEPTION_HBCI_ACCOUNT_AMBIGUOUS", accountIdentifier));
		}
		return bestMatch;
	}

	private static int matchQuality(BankAccount bankAccount, Konto konto) {
		if (konto == null) {
			return NO_MATCH;
		}
		String iban = normalizeIban(bankAccount.getIban());
		String kontoIban = normalizeIban(konto.iban);
		if (iban != null && kontoIban != null) {
			return iban.equals(kontoIban) ? IBAN_MATCH : NO_MATCH;
		}
		String number = normalizeAccountNumber(bankAccount.getNumber());
		boolean numberMatches = number != null && number.equals(normalizeAccountNumber(konto.number));
		return numberMatches && subnumbersMatch(bankAccount.getSubnumber(), konto.subnumber) ? NUMBER_MATCH : NO_MATCH;
	}

	private static boolean subnumbersMatch(String left, String right) {
		String normalizedLeft = normalizeAccountNumber(left);
		String normalizedRight = normalizeAccountNumber(right);
		return normalizedLeft == null || normalizedRight == null || normalizedLeft.equals(normalizedRight);
	}

	private static String normalizeIban(String iban) {
		String trimmed = trimToNull(iban);
		return trimmed != null ? trimmed.replaceAll("\\s+", "").toUpperCase(Locale.ROOT) : null;
	}
}
