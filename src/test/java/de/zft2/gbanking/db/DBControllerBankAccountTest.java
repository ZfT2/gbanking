package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountIdentifier;
import de.zft2.gbanking.db.dao.BankAccountRetrievalStatus;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.AccountIdentifierType;
import de.zft2.gbanking.db.dao.enu.AccountRetrievalStatus;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.testdata.TestDataFactory;

class DBControllerBankAccountTest extends DBControllerIntegrationBaseTest {

	// ------------------------------------------------------------
	// Tests - BankAccount update
	// ------------------------------------------------------------

	@Test
	void updateBankAccountSource_shouldPersistEveryAccount() {

		BankAccess ba = TestDataFactory.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestDataFactory.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		BankAccount acc02 = TestDataFactory.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc02);

		assertEquals(Source.ONLINE, acc01.getSource());
		assertEquals(Source.ONLINE, acc02.getSource());

		acc01.setSource(Source.MANUELL);
		acc02.setSource(Source.MANUELL);

		int updatedRows = db.executeSimpleUpdate(List.of(acc01, acc02),
				StatementsConfig.StatementType.UPDATE_ACCOUNT_SOURCE, null);

		assertEquals(2, updatedRows);
		assertEquals(Source.MANUELL, db.getById(BankAccount.class, acc01.getId()).getSource());
		assertEquals(Source.MANUELL, db.getById(BankAccount.class, acc02.getId()).getSource());
	}

	@Test
	void bankAccountSchema_shouldContainCreatedAtBeforeUpdatedAt() throws SQLException {

		int createdAtIndex = -1;
		int updatedAtIndex = -1;
		int createdAtNotNull = 0;
		int columnIndex = 0;

		try (Statement statement = DBController.getConnection().createStatement();
				ResultSet resultSet = statement.executeQuery("PRAGMA table_info(bankAccount)")) {
			while (resultSet.next()) {
				String columnName = resultSet.getString("name");
				if ("createdAt".equals(columnName)) {
					createdAtIndex = columnIndex;
					createdAtNotNull = resultSet.getInt("notnull");
				} else if ("updatedAt".equals(columnName)) {
					updatedAtIndex = columnIndex;
				}
				columnIndex++;
			}
		}

		assertTrue(createdAtIndex >= 0);
		assertTrue(updatedAtIndex >= 0);
		assertEquals(1, createdAtNotNull);
		assertEquals(updatedAtIndex - 1, createdAtIndex);
	}

	@Test
	void insertUpdateBankAccount_shouldSetCreatedAtOnce() {

		BankAccount account = TestDataFactory.createSampleAccount(null);
		db.insertOrUpdate(account);

		BankAccount createdAccount = db.getById(BankAccount.class, account.getId());
		assertNotNull(createdAccount.getCreatedAt());
		LocalDate createdAt = createdAccount.getCreatedAt();

		createdAccount.setAccountName("Updated account name");
		db.insertOrUpdate(createdAccount);

		BankAccount updatedAccount = db.getById(BankAccount.class, createdAccount.getId());
		assertEquals(createdAt, updatedAccount.getCreatedAt());
		assertEquals("Updated account name", updatedAccount.getAccountName());
	}

	@Test
	void hbciAccountType_shouldSurviveInsertLoadsAndUpdates() {
		BankAccess bankAccess = db.insertOrUpdate(TestDataFactory.createSampleBankAccess("44444444"));
		BankAccount account = TestDataFactory.createSampleAccount(bankAccess.getId());
		account.setHbciAccountType(7);
		db.insertOrUpdate(account);

		BankAccount loadedAccount = db.getById(BankAccount.class, account.getId());
		assertNotNull(loadedAccount);
		assertEquals(7, loadedAccount.getHbciAccountType());

		loadedAccount.setAccountName("Updated account");
		db.insertOrUpdate(loadedAccount);
		assertEquals(7, db.getById(BankAccount.class, account.getId()).getHbciAccountType());

		loadedAccount.setHbciAccountType(9);
		db.insertOrUpdate(loadedAccount);

		assertEquals(9, db.getAll(BankAccount.class).get(0).getHbciAccountType());
		assertEquals(9, db.getAllByParent(BankAccount.class, bankAccess.getId()).get(0).getHbciAccountType());
		assertEquals(9, db.getAll(BankAccount.class, "SQL_SELECT_ALL_ONLINE_BANKACCOUNTS").get(0).getHbciAccountType());
	}

	@Test
	void providerAccountIdAndBaseCurrencyShouldSurviveAllAccessPathsAndUpdates() {
		BankAccess bankAccess = db.insertOrUpdate(TestDataFactory.createSampleBankAccess("44444444"));
		BankAccount account = TestDataFactory.createSampleAccount(bankAccess.getId());
		account.setProviderAccountId("provider-account-initial");
		account.setBaseCurrency(Currency.USD);
		db.insertOrUpdate(account);

		assertProviderFields(db.getById(BankAccount.class, account.getId()), "provider-account-initial", Currency.USD);
		assertProviderFields(findById(db.getAll(BankAccount.class), account.getId()), "provider-account-initial", Currency.USD);
		assertProviderFields(findById(db.getAllByParent(BankAccount.class, bankAccess.getId()), account.getId()),
				"provider-account-initial", Currency.USD);

		account.setProviderAccountId("provider-account-updated");
		account.setBaseCurrency(Currency.GBP);
		db.insertOrUpdate(account);

		assertProviderFields(db.getById(BankAccount.class, account.getId()), "provider-account-updated", Currency.GBP);
		assertProviderFields(findById(db.getAll(BankAccount.class), account.getId()), "provider-account-updated", Currency.GBP);
		assertProviderFields(findById(db.getAllByParent(BankAccount.class, bankAccess.getId()), account.getId()),
				"provider-account-updated", Currency.GBP);
	}

	private static void assertProviderFields(BankAccount account, String providerAccountId, Currency baseCurrency) {
		assertNotNull(account);
		assertEquals(providerAccountId, account.getProviderAccountId());
		assertEquals(baseCurrency, account.getBaseCurrency());
	}

	@Test
	void bankAccountRetrievalStatus_shouldBeInsertedUpdatedAndDeletedWithAccount() {
		BankAccount account = db.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		LocalDateTime firstRetrieval = LocalDateTime.of(2026, Month.JULY, 16, 10, 15, 30, 123_000_000);
		db.upsertBankAccountRetrievalStatus(new BankAccountRetrievalStatus(account.getId(), firstRetrieval,
				AccountRetrievalStatus.FAILED, 0, 0, "Bank temporarily unavailable"));

		BankAccountRetrievalStatus failedStatus = db.getBankAccountRetrievalStatus(account.getId());
		assertEquals(firstRetrieval, failedStatus.retrievedAt());
		assertEquals(AccountRetrievalStatus.FAILED, failedStatus.result());
		assertEquals("Bank temporarily unavailable", failedStatus.lastError());

		LocalDateTime successfulRetrieval = firstRetrieval.plusMinutes(5);
		db.upsertBankAccountRetrievalStatus(new BankAccountRetrievalStatus(account.getId(), successfulRetrieval,
				AccountRetrievalStatus.SUCCESS, 4, 2, null));

		BankAccountRetrievalStatus successfulStatus = db.getBankAccountRetrievalStatus(account.getId());
		assertEquals(successfulRetrieval, successfulStatus.retrievedAt());
		assertEquals(AccountRetrievalStatus.SUCCESS, successfulStatus.result());
		assertEquals(4, successfulStatus.newBookingCount());
		assertEquals(2, successfulStatus.pendingBookingCount());
		assertNull(successfulStatus.lastError());

		db.delete(account, null);
		assertNull(db.getBankAccountRetrievalStatus(account.getId()));
	}

	@Test
	void bankAccountIdentifiers_shouldBeReplacedNormalizedAndDeletedWithAccount() throws SQLException {
		BankAccount account = db.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		db.replaceBankAccountIdentifiers(account.getId(), List.of(
				new BankAccountIdentifier(0, account.getId(), AccountIdentifierType.ACCOUNT, "  Special-123  "),
				new BankAccountIdentifier(0, account.getId(), AccountIdentifierType.ACCOUNT, "special-123"),
				new BankAccountIdentifier(0, account.getId(), AccountIdentifierType.ACCOUNT_TRANSFER, "DE1234567890")));

		List<BankAccountIdentifier> identifiers = db.getBankAccountIdentifiers(account.getId());
		assertEquals(2, identifiers.size());
		assertTrue(identifiers.stream().anyMatch(identifier -> identifier.propertyType() == AccountIdentifierType.ACCOUNT
				&& "Special-123".equals(identifier.value())));
		try (Statement statement = DBController.getConnection().createStatement();
				ResultSet resultSet = statement.executeQuery(
						"SELECT propertyType, typeof(propertyType) FROM bankAccountIdentifiers WHERE value = 'Special-123'")) {
			assertTrue(resultSet.next());
			assertEquals(AccountIdentifierType.ACCOUNT.getDbStateId(), resultSet.getInt(1));
			assertEquals("integer", resultSet.getString(2));
		}

		db.replaceBankAccountIdentifiers(account.getId(), List.of(
				new BankAccountIdentifier(0, account.getId(), AccountIdentifierType.ACCOUNT_TRANSFER, "NEW-IDENTIFIER")));
		identifiers = db.getBankAccountIdentifiers(account.getId());
		assertEquals(1, identifiers.size());
		assertEquals("NEW-IDENTIFIER", identifiers.get(0).value());

		db.delete(account, null);
		assertTrue(db.getBankAccountIdentifiers(account.getId()).isEmpty());
	}

	@Test
	void insertManualBankAccountWithoutBankIdentifiers_shouldWork() {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setIban(null);
		account.setNumber(null);
		account.setSource(Source.MANUELL);
		account.setOfflineAccount(true);

		db.insertOrUpdate(account);

		BankAccount savedAccount = db.getById(BankAccount.class, account.getId());
		assertNotNull(savedAccount);
		assertEquals(Source.MANUELL, savedAccount.getSource());
		assertTrue(savedAccount.isOfflineAccount());
	}

	@Test
	void selectOnlineBankAccounts_shouldReturnOnlyAccountsLinkedToBankAccess() {
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("44444444");
		db.insertOrUpdate(bankAccess);
		BankAccount onlineAccount = TestDataFactory.createSampleAccount(bankAccess.getId());
		onlineAccount.setAccountName("Online account");
		db.insertOrUpdate(onlineAccount);
		BankAccount offlineAccount = TestDataFactory.createSampleAccount(null);
		offlineAccount.setAccountName("Offline account");
		db.insertOrUpdate(offlineAccount);

		List<BankAccount> accounts = db.getAll(BankAccount.class, "SQL_SELECT_ALL_ONLINE_BANKACCOUNTS");

		assertEquals(1, accounts.size());
		assertEquals(onlineAccount.getId(), accounts.get(0).getId());
		assertEquals("Online account", accounts.get(0).getAccountName());
	}
	
	@Test
	void deleteBankAccount_shouldRemove() {
		BankAccess ba = TestDataFactory.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestDataFactory.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);
		Booking booking = TestDataFactory.createSampleBooking(acc01.getId());
		db.insertOrUpdate(booking);
		
		assertTrue(db.getById(BankAccount.class, acc01.getId()).getId() > 0);
		assertEquals(1, db.getAllByParentFull(Booking.class, acc01.getId()).size());

		boolean deleted = db.delete(acc01, null);
		assertTrue(deleted);

		List<BankAccount> left = db.getAll(BankAccount.class);
		assertTrue(left.isEmpty());
		assertTrue(db.getAllByParentFull(Booking.class, acc01.getId()).isEmpty());
		assertNotNull(db.getById(BankAccess.class, ba.getId()));
	}
	
	@Test
	void getAccountsIdsByAccountName_shouldWork() {

		BankAccess ba = TestDataFactory.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestDataFactory.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		BankAccount acc02 = TestDataFactory.createSampleAccount(ba.getId());
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

		BankAccess ba = TestDataFactory.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestDataFactory.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		BankAccount acc02 = TestDataFactory.createSampleAccount(ba.getId());
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
