package de.zft2.gbanking.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;

class GBankingContextTest {

	@BeforeEach
	void setUp() {
		GBankingContext.setOnlyOnlineAccountsVisible(false);
	}

	@AfterEach
	void tearDown() {
		GBankingContext.clearSelectedAccount();
		GBankingContext.setOnlyOnlineAccountsVisible(false);
	}

	@Test
	void selectedAccountShouldStoreAccountId() {
		BankAccount account = new BankAccount();
		account.setId(42);

		GBankingContext.setSelectedAccount(account);

		assertEquals(42, GBankingContext.getSelectedAccountId());
	}

	@Test
	void resetServicesShouldClearSelectedAccount() {
		BankAccount account = new BankAccount();
		account.setId(42);
		GBankingContext.setSelectedAccount(account);

		GBankingContext.resetServices();

		assertNull(GBankingContext.getSelectedAccountId());
	}

	@Test
	void onlyOnlineAccountsVisibilityShouldBeStoredInContext() {
		assertFalse(GBankingContext.isOnlyOnlineAccountsVisible());

		GBankingContext.setOnlyOnlineAccountsVisible(true);

		assertTrue(GBankingContext.isOnlyOnlineAccountsVisible());
	}
}
