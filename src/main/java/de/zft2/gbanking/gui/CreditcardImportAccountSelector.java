package de.zft2.gbanking.gui;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;

final class CreditcardImportAccountSelector {

	private CreditcardImportAccountSelector() {
	}

	static boolean isEligible(BankAccount account) {
		return account != null
				&& account.getAccountType() == AccountType.CREDIT_CARD
				&& account.getAccountState() == AccountState.ACTIVE
				&& (account.getBankAccessId() == null || account.getBankAccessId() <= 0);
	}

	static List<BankAccount> eligibleAccounts(Collection<BankAccount> accounts) {
		if (accounts == null) {
			return List.of();
		}
		return accounts.stream()
				.filter(account -> isEligible(account))
				.sorted(Comparator.comparing(account -> account.getAccountName(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
				.toList();
	}
}
