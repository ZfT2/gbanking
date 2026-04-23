package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.Source;

class DBControllerBankAccountTest extends DBControllerIntegrationBaseTest {

	// ------------------------------------------------------------
	// Tests - BankAccount update
	// ------------------------------------------------------------

	@Test
	void updateBankAccountSource_shouldWork() {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		BankAccount acc02 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc02);

		assertEquals(Source.ONLINE, acc01.getSource());
		assertEquals(Source.ONLINE, acc02.getSource());

		acc01.setSource(Source.MANUELL);
		acc02.setSource(Source.MANUELL);

		// boolean result = db.updateAccountsSource(Arrays.asList(acc01, acc02));
		boolean result = db.executeSimpleUpdate(Arrays.asList(acc01, acc02), StatementsConfig.StatementType.UPDATE_ACCOUNT_SOURCE, null) >= 0;

		assertTrue(result);

		assertEquals(Source.MANUELL, acc01.getSource());
		assertEquals(Source.MANUELL, acc02.getSource());
	}
	
	@Test
	void deleteBankAccount_shouldRemove() {
		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);
		
		assertTrue(db.getById(BankAccount.class, acc01.getId()).getId() > 0);

		boolean deleted = db.delete(acc01, null);
		assertTrue(deleted);

		List<BankAccount> left = db.getAll(BankAccount.class);
		assertTrue(left.isEmpty());
	}
	
	@Test
	void getAccountsIdsByAccountName_shouldWork() {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		BankAccount acc02 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc02);

		Map<String, Integer> accountIdMap = db.getAccountsIdsByAccountName();

		assertTrue(!accountIdMap.isEmpty());
		
		assertEquals(2, accountIdMap.size());
		
		Iterator<Integer> mapIterator = accountIdMap.values().iterator();
		Integer firstId = mapIterator.next();
		Integer secondId = mapIterator.next();
		
		assertNotEquals(firstId, secondId);
	}
	
	@Test
	void getCrossAccountsIdsByIbanOrNumber_shouldWork() {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		BankAccount acc02 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc02);

		Map<String, Integer> accountIdMap = db.getCrossAccountsIdsByIbanOrNumber();

		assertTrue(!accountIdMap.isEmpty());
		
		assertEquals(4, accountIdMap.size());
		
		Set<Integer> idSet = new HashSet<Integer>(accountIdMap.values());
		
		assertEquals(2, idSet.size());
		
		Iterator<Integer> setIterator = idSet.iterator();
		Integer firstId = setIterator.next();
		Integer secondId = setIterator.next();
		
		assertNotEquals(firstId, secondId);
	}
}