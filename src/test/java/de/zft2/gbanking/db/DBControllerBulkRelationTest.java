package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.testdata.TestDataFactory;

class DBControllerBulkRelationTest extends DBControllerIntegrationBaseTest {

	@Test
	void fullBankAccessListShouldLoadAccountGraphsWithFourQueries() {
		BankAccess firstAccess = db.insertOrUpdate(TestDataFactory.createSampleBankAccess("12345678"));
		BankAccess secondAccess = db.insertOrUpdate(TestDataFactory.createSampleBankAccess("87654321"));
		BankAccount firstAccount = db.insertOrUpdate(TestDataFactory.createSampleAccount(firstAccess.getId()));
		BankAccount secondAccount = db.insertOrUpdate(TestDataFactory.createSampleAccount(firstAccess.getId()));
		BankAccount thirdAccount = db.insertOrUpdate(TestDataFactory.createSampleAccount(secondAccess.getId()));
		Booking booking = db.insertOrUpdate(TestDataFactory.createSampleBooking(firstAccount.getId()));
		linkBusinessCase(secondAccount, "ZahlungSEPA");

		DatabaseQueryCounter.Measurement<List<BankAccess>> measurement =
				DatabaseQueryCounter.measure(() -> db.getAllFull(BankAccess.class));

		assertEquals(4, measurement.queryCount());
		BankAccess storedFirstAccess = findById(measurement.result(), firstAccess.getId());
		assertEquals(Set.of(firstAccount.getId(), secondAccount.getId()), accountIds(storedFirstAccess.getAccounts()));
		assertEquals(Set.of(thirdAccount.getId()),
				accountIds(findById(measurement.result(), secondAccess.getId()).getAccounts()));
		assertEquals(Set.of(booking.getId()),
				bookingIds(findById(storedFirstAccess.getAccounts(), firstAccount.getId())));
		assertEquals(Set.of("ZahlungSEPA"),
				businessCaseValues(findById(storedFirstAccess.getAccounts(), secondAccount.getId())));
	}

	@Test
	void fullBankAccountListShouldLoadBookingsAndBusinessCasesWithThreeQueries() {
		BankAccess bankAccess = db.insertOrUpdate(TestDataFactory.createSampleBankAccess("12345678"));
		BankAccount firstAccount = db.insertOrUpdate(TestDataFactory.createSampleAccount(bankAccess.getId()));
		BankAccount secondAccount = db.insertOrUpdate(TestDataFactory.createSampleAccount(bankAccess.getId()));
		Recipient recipient = db.insertOrUpdate(TestDataFactory.createSampleRecipient01());
		Category category = db.insertOrUpdate(TestDataFactory.createSampleCategory("Wohnen:Miete"));

		Booking firstBooking = TestDataFactory.createSampleBookingWithRecipient(firstAccount.getId(), recipient.getId());
		firstBooking.setCategory(category);
		db.insertOrUpdate(firstBooking);
		Booking secondBooking = db.insertOrUpdate(TestDataFactory.createSampleBooking(secondAccount.getId()));

		linkBusinessCase(firstAccount, "ZahlungSEPA");
		linkBusinessCase(secondAccount, "Dauerauftrag");

		DatabaseQueryCounter.Measurement<List<BankAccount>> measurement =
				DatabaseQueryCounter.measure(() -> db.getAllFull(BankAccount.class));

		assertEquals(3, measurement.queryCount());
		BankAccount storedFirstAccount = findById(measurement.result(), firstAccount.getId());
		BankAccount storedSecondAccount = findById(measurement.result(), secondAccount.getId());
		assertEquals(Set.of(firstBooking.getId()), bookingIds(storedFirstAccount));
		assertEquals(Set.of(secondBooking.getId()), bookingIds(storedSecondAccount));
		assertEquals(Set.of("ZahlungSEPA"), businessCaseValues(storedFirstAccount));
		assertEquals(Set.of("Dauerauftrag"), businessCaseValues(storedSecondAccount));

		Booking storedFirstBooking = storedFirstAccount.getBookings().get(0);
		assertNotNull(storedFirstBooking.getRecipient());
		assertEquals(recipient.getId(), storedFirstBooking.getRecipient().getId());
		assertNotNull(storedFirstBooking.getCategory());
		assertEquals(category.getId(), storedFirstBooking.getCategory().getId());
		assertEquals("Wohnen:Miete", storedFirstBooking.getCategory().getFullName());
	}

	@Test
	void fullBankAccountListShouldOrderBookingsByDescendingIdPerAccount() {
		BankAccess bankAccess = db.insertOrUpdate(TestDataFactory.createSampleBankAccess("12345678"));
		BankAccount firstAccount = db.insertOrUpdate(TestDataFactory.createSampleAccount(bankAccess.getId()));
		BankAccount secondAccount = db.insertOrUpdate(TestDataFactory.createSampleAccount(bankAccess.getId()));

		Booking firstAccountOlder = db.insertOrUpdate(TestDataFactory.createSampleBooking(firstAccount.getId()));
		Booking secondAccountOlder = db.insertOrUpdate(TestDataFactory.createSampleBooking(secondAccount.getId()));
		Booking firstAccountNewer = db.insertOrUpdate(TestDataFactory.createSampleBooking(firstAccount.getId()));
		Booking secondAccountNewer = db.insertOrUpdate(TestDataFactory.createSampleBooking(secondAccount.getId()));

		List<BankAccount> storedAccounts = db.getAllFull(BankAccount.class);

		assertEquals(List.of(firstAccountNewer.getId(), firstAccountOlder.getId()),
				bookingIdList(findById(storedAccounts, firstAccount.getId())));
		assertEquals(List.of(secondAccountNewer.getId(), secondAccountOlder.getId()),
				bookingIdList(findById(storedAccounts, secondAccount.getId())));
	}

	@Test
	void fullCategoryRuleListShouldLoadAccountGraphsWithFourQueries() {
		BankAccess bankAccess = db.insertOrUpdate(TestDataFactory.createSampleBankAccess("12345678"));
		BankAccount firstAccount = db.insertOrUpdate(TestDataFactory.createSampleAccount(bankAccess.getId()));
		BankAccount secondAccount = db.insertOrUpdate(TestDataFactory.createSampleAccount(bankAccess.getId()));
		Category category = db.insertOrUpdate(TestDataFactory.createSampleCategory("Mobilitaet:Bahn"));
		CategoryRule firstRule = insertRule("Bahn", category, List.of(firstAccount));
		CategoryRule secondRule = insertRule("Taxi", category, List.of(firstAccount, secondAccount));
		Booking booking = db.insertOrUpdate(TestDataFactory.createSampleBooking(firstAccount.getId()));
		linkBusinessCase(secondAccount, "Dauerauftrag");

		DatabaseQueryCounter.Measurement<List<CategoryRule>> measurement =
				DatabaseQueryCounter.measure(() -> db.getAllFull(CategoryRule.class));

		assertEquals(4, measurement.queryCount());
		CategoryRule storedFirstRule = findById(measurement.result(), firstRule.getId());
		CategoryRule storedSecondRule = findById(measurement.result(), secondRule.getId());
		assertEquals(Set.of(firstAccount.getId()), accountIds(storedFirstRule.getBankAccountList()));
		assertEquals(Set.of(firstAccount.getId(), secondAccount.getId()),
				accountIds(storedSecondRule.getBankAccountList()));
		assertEquals(Set.of(booking.getId()),
				bookingIds(findById(storedFirstRule.getBankAccountList(), firstAccount.getId())));
		assertEquals(Set.of("Dauerauftrag"),
				businessCaseValues(findById(storedSecondRule.getBankAccountList(), secondAccount.getId())));
		assertEquals("Mobilitaet:Bahn", storedFirstRule.getCategory().getFullName());
		assertEquals("Mobilitaet:Bahn", storedSecondRule.getCategory().getFullName());
	}

	@Test
	void fullAggregateLookupByIdShouldLoadNestedAccountRelations() {
		BankAccess bankAccess = db.insertOrUpdate(TestDataFactory.createSampleBankAccess("12345678"));
		BankAccount account = db.insertOrUpdate(TestDataFactory.createSampleAccount(bankAccess.getId()));
		Booking booking = db.insertOrUpdate(TestDataFactory.createSampleBooking(account.getId()));
		linkBusinessCase(account, "ZahlungSEPA");
		Category category = db.insertOrUpdate(TestDataFactory.createSampleCategory("Wohnen:Miete"));
		CategoryRule rule = insertRule("Miete", category, List.of(account));

		BankAccount accountFromAccess = db.getByIdFull(BankAccess.class, bankAccess.getId()).getAccounts().get(0);
		BankAccount accountFromRule = db.getByIdFull(CategoryRule.class, rule.getId()).getBankAccountList().get(0);

		assertEquals(Set.of(booking.getId()), bookingIds(accountFromAccess));
		assertEquals(Set.of("ZahlungSEPA"), businessCaseValues(accountFromAccess));
		assertEquals(Set.of(booking.getId()), bookingIds(accountFromRule));
		assertEquals(Set.of("ZahlungSEPA"), businessCaseValues(accountFromRule));
	}

	private void linkBusinessCase(BankAccount bankAccount, String value) {
		BusinessCase businessCase = new BusinessCase();
		businessCase.setCaseValue(value);
		bankAccount.setAllowedBusinessCases(List.of(businessCase));
		db.insertBusinessCases(bankAccount);
	}

	private CategoryRule insertRule(String name, Category category, List<BankAccount> accounts) {
		CategoryRule rule = new CategoryRule();
		rule.setName(name);
		rule.setCategory(category);
		rule.setFilterPurpose(name);
		rule.setBankAccountList(accounts);
		return db.insertOrUpdate(rule);
	}

	private Set<Integer> accountIds(List<BankAccount> accounts) {
		return accounts.stream().map(BankAccount::getId).collect(Collectors.toSet());
	}

	private Set<Integer> bookingIds(BankAccount account) {
		return account.getBookings().stream().map(Booking::getId).collect(Collectors.toSet());
	}

	private List<Integer> bookingIdList(BankAccount account) {
		return account.getBookings().stream().map(Booking::getId).toList();
	}

	private Set<String> businessCaseValues(BankAccount account) {
		return account.getAllowedBusinessCases().stream()
				.map(BusinessCase::getCaseValue)
				.collect(Collectors.toSet());
	}
}
