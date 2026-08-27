package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.exception.GBankingException;

class DbExecutorLookupParameterTest extends DBControllerIntegrationBaseTest {

	@Test
	void insertOrUpdateBankAccount_shouldBindIdenticalLookupValuesToEveryPosition() {
		String sharedIdentifier = "SHARED-LOOKUP-VALUE";
		BankAccount existingAccount = TestData.createSampleAccount(null);
		existingAccount.setIban("DIFFERENT-IBAN");
		existingAccount.setNumber(sharedIdentifier);
		db.insertOrUpdate(existingAccount);

		BankAccount matchingAccount = TestData.createSampleAccount(null);
		matchingAccount.setIban(sharedIdentifier);
		matchingAccount.setNumber(sharedIdentifier);
		db.insertOrUpdate(matchingAccount);

		assertEquals(existingAccount.getId(), matchingAccount.getId());
		assertEquals(1, db.getAll(BankAccount.class).size());
	}

	@Test
	void insertOrUpdateBankAccount_shouldRejectAmbiguousLookup() {
		BankAccount firstAccount = TestData.createSampleAccount(null);
		firstAccount.setIban("FIRST-IBAN");
		firstAccount.setNumber("FIRST-NUMBER");
		db.insertOrUpdate(firstAccount);

		BankAccount secondAccount = TestData.createSampleAccount(null);
		secondAccount.setIban("SECOND-IBAN");
		secondAccount.setNumber("SECOND-NUMBER");
		db.insertOrUpdate(secondAccount);

		BankAccount ambiguousAccount = TestData.createSampleAccount(null);
		ambiguousAccount.setIban(firstAccount.getIban());
		ambiguousAccount.setNumber(secondAccount.getNumber());

		assertThrows(GBankingException.class, () -> db.insertOrUpdate(ambiguousAccount));
		assertEquals(0, ambiguousAccount.getId());
		assertEquals(2, db.getAll(BankAccount.class).size());
	}

	@Test
	void insertOrUpdateBankAccess_shouldUseBankCodeAndUserIdAsIdentity() {
		BankAccess firstAccess = TestData.createSampleBankAccess("12345678");
		firstAccess.getFints().setUserId("first-user");
		db.insertOrUpdate(firstAccess);

		BankAccess secondAccess = TestData.createSampleBankAccess("12345678");
		secondAccess.getFints().setUserId("second-user");
		db.insertOrUpdate(secondAccess);

		assertNotEquals(firstAccess.getId(), secondAccess.getId());
		assertEquals(firstAccess.getId(),
				db.getBankAccessByBlzAndUserId(firstAccess.getFints().getBlz(), firstAccess.getFints().getUserId()).getId());
		assertEquals(secondAccess.getId(),
				db.getBankAccessByBlzAndUserId(secondAccess.getFints().getBlz(), secondAccess.getFints().getUserId()).getId());
	}

	@Test
	void accountIdentifierMaps_shouldRejectAmbiguousKeys() {
		BankAccount firstAccount = TestData.createSampleAccount(null);
		firstAccount.setAccountName("Duplicate account");
		firstAccount.setIban("FIRST-UNIQUE-IBAN");
		firstAccount.setNumber("FIRST-UNIQUE-NUMBER");
		db.insertOrUpdate(firstAccount);

		BankAccount secondAccount = TestData.createSampleAccount(null);
		secondAccount.setAccountName(firstAccount.getAccountName());
		secondAccount.setIban("SECOND-UNIQUE-IBAN");
		secondAccount.setNumber("SECOND-UNIQUE-NUMBER");
		db.insertOrUpdate(secondAccount);

		assertThrows(GBankingException.class, db::getAccountsIdsByAccountName);
	}
}
