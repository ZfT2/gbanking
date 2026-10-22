package de.zft2.gbanking.util;

import de.zft2.gbanking.db.dao.BankAccount;

/**
 * Central definition of the display names of an account.
 *
 * <p>
 * An account is shown with its name, if it has no name the IBAN or the account number is used.
 * The label of an account contains the name and the identifier of the account.
 * </p>
 */
public final class AccountDisplay {

	private AccountDisplay() {
	}

	/**
	 * Returns the display name of the account: the name of the account, the IBAN or the account
	 * number.
	 *
	 * @param account the account
	 * @return the display name, an empty string if the account is unknown
	 */
	public static String displayName(BankAccount account) {
		return displayName(account, "");
	}

	/**
	 * Returns the display name of the account: the name of the account, the IBAN or the account
	 * number.
	 *
	 * @param account  the account
	 * @param fallback the name used if the account has no name, no IBAN and no account number
	 * @return the display name
	 */
	public static String displayName(BankAccount account, String fallback) {
		if (account == null) {
			return fallback;
		}
		String accountName = trimmed(account.getAccountName());
		if (!accountName.isEmpty()) {
			return accountName;
		}
		String identifier = identifier(account);
		return identifier.isEmpty() ? fallback : identifier;
	}

	/**
	 * Returns the label of the account: the name of the account followed by the identifier of the
	 * account in brackets.
	 *
	 * @param account the account
	 * @return the label, an empty string if the account is unknown
	 */
	public static String label(BankAccount account) {
		if (account == null) {
			return "";
		}
		String accountName = trimmed(account.getAccountName());
		String identifier = identifier(account);
		if (accountName.isEmpty()) {
			return identifier;
		}
		return identifier.isEmpty() ? accountName : accountName + " (" + identifier + ')';
	}

	/**
	 * Returns the identifier of the account: the IBAN or the account number.
	 *
	 * @param account the account
	 * @return the identifier, an empty string if the account has no identifier
	 */
	public static String identifier(BankAccount account) {
		if (account == null) {
			return "";
		}
		String iban = trimmed(account.getIban());
		if (!iban.isEmpty()) {
			return iban;
		}
		return trimmed(account.getNumber());
	}

	private static String trimmed(String value) {
		return value == null ? "" : value.trim();
	}
}
