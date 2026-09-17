package de.zft2.gbanking.gui.panel.account;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountType;

class AccountListPanelTest {

	@Test
	void shouldHideOnlyPortfolioAccountsFromGeneralAccountList() {
		assertFalse(AccountListPanel.isGeneralAccount(account(AccountType.DEPOT)));
		assertTrue(AccountListPanel.isGeneralAccount(account(AccountType.CURRENT_ACCOUNT)));
		assertTrue(AccountListPanel.isGeneralAccount(account(AccountType.DEPOT_ACCOUNT)));
	}

	@Test
	void allScopeShouldIgnoreOnlineViewSetting() {
		assertFalse(AccountListScope.ALL.usesOnlineFilter(true));
		assertFalse(AccountListScope.FOLLOW_VIEW_SETTING.usesOnlineFilter(false));
		assertTrue(AccountListScope.FOLLOW_VIEW_SETTING.usesOnlineFilter(true));
		assertTrue(AccountListScope.ONLINE_ONLY.usesOnlineFilter(false));
	}

	private BankAccount account(AccountType accountType) {
		BankAccount account = new BankAccount();
		account.setAccountType(accountType);
		return account;
	}
}
