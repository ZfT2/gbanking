package de.zft2.gbanking.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.TestData;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;

class CreditcardImportAccountSelectorTest {

	@Test
	void isEligible_shouldRequireActiveCreditcardWithoutBankAccess() {
		BankAccount eligible = account("Kreditkarte", AccountType.CREDIT_CARD, AccountState.ACTIVE, null);
		BankAccount currentAccount = account("Girokonto", AccountType.CURRENT_ACCOUNT, AccountState.ACTIVE, null);
		BankAccount inactiveCreditcard = account("Inaktiv", AccountType.CREDIT_CARD, AccountState.INACTIVE, null);
		BankAccount linkedCreditcard = account("Online", AccountType.CREDIT_CARD, AccountState.ACTIVE, 42);

		assertTrue(CreditcardImportAccountSelector.isEligible(eligible));
		assertFalse(CreditcardImportAccountSelector.isEligible(currentAccount));
		assertFalse(CreditcardImportAccountSelector.isEligible(inactiveCreditcard));
		assertFalse(CreditcardImportAccountSelector.isEligible(linkedCreditcard));
		assertFalse(CreditcardImportAccountSelector.isEligible(null));
	}

	@Test
	void eligibleAccounts_shouldFilterAndSortByAccountName() {
		BankAccount second = account("Visa", AccountType.CREDIT_CARD, AccountState.ACTIVE, null);
		BankAccount ignored = account("Giro", AccountType.CURRENT_ACCOUNT, AccountState.ACTIVE, null);
		BankAccount first = account("Mastercard", AccountType.CREDIT_CARD, AccountState.ACTIVE, null);

		assertEquals(List.of(first, second), CreditcardImportAccountSelector.eligibleAccounts(List.of(second, ignored, first)));
	}

	private BankAccount account(String name, AccountType type, AccountState state, Integer bankAccessId) {
		BankAccount account = TestData.createSampleAccount(bankAccessId);
		account.setAccountName(name);
		account.setAccountType(type);
		account.setAccountState(state);
		return account;
	}
}
