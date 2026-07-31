package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;

class DBControllerCategoryTest extends DBControllerIntegrationBaseTest {

	// ------------------------------------------------------------
	// Tests - Booking insertion
	// ------------------------------------------------------------

	@Test
	void insertSingleCategory_shouldWork() {

		Category cg = TestData.createSampleCategory("Auto");
		db.insertOrUpdate(cg);
		assertTrue(cg.getId() > 0);
		
		cg = db.getById(Category.class, cg.getId());

		assertEquals("Auto", cg.getName());
		assertEquals("Auto", cg.getFullName());
		
		LocalDate dateUpdatedAtWithoutTime = getCalendarWithoutTime(cg.getUpdatedAt());
		assertEquals(dateUpdatedAtWithoutTime, getCalendarWithoutTime(LocalDate.now(ZoneId.systemDefault())));
	}

	@Test
	void deleteCategory_shouldUseCategoryTableInsteadOfView() {

		Category category = db.insertOrUpdate(TestData.createSampleCategory("Loeschtest"));

		assertTrue(db.delete(category, null));
		assertFalse(db.getAll(Category.class).stream().anyMatch(existingCategory -> existingCategory.getId() == category.getId()));
	}

	@Test
	void deleteCategoryShouldClearBookingCategoryAndDeleteCategoryRules() {
		BankAccount account = db.insertOrUpdate(TestData.createSampleAccount(null));
		Category category = db.insertOrUpdate(TestData.createSampleCategory("Loeschtest"));

		Booking booking = TestData.createSampleBooking(account.getId());
		booking.setCategory(category);
		booking = db.insertOrUpdate(booking);

		CategoryRule categoryRule = new CategoryRule();
		categoryRule.setCategory(category);
		categoryRule.setFilterPurpose("Ticket");
		db.insertOrUpdate(categoryRule);

		assertTrue(db.delete(category, null));

		Booking storedBooking = db.getByIdFull(Booking.class, booking.getId());
		assertNull(storedBooking.getCategory());
		assertEquals(0, storedBooking.getCategoryId());
		assertTrue(db.getAll(CategoryRule.class).isEmpty());
	}
	
	@Test
	void insertCategories_shouldWork() {

		Category cat1 = new Category("Miete");
		Category cat2 = new Category("Miete:Wohnung");
		Category cat3 = new Category("Miete:Büro");
		Category cat4 = new Category("Bargeldauszahlung");
		Category cat5 = new Category("Freizeit:Sport:Fitness");
		Category cat6 = new Category("Freizeit:Sport:Schwimmbad");
		Category cat7 = new Category("Freizeit:Sport:Freibad");
		Category cat8 = new Category("Freizeit:Sport:Freibad:Pommes");
		Category cat9 = new Category("Lebensmittel:Pommes");

		Set<Category> categories = new HashSet<Category>();
		categories.add(cat1);
		categories.add(cat2);
		categories.add(cat3);
		categories.add(cat4);
		categories.add(cat5);
		categories.add(cat6);
		categories.add(cat7);
		categories.add(cat8);
		categories.add(cat9);

		db.insertAll(categories);

		List<Category> all = db.getAll(Category.class);

		assertEquals(12, all.size(), "12 Kategorien erwartet");
		List<String> values = all.stream().map(Category::getFullName).distinct().toList();
		assertEquals(12, values.size(), "12 Kategorien erwartet");
		assertTrue(values.contains("Bargeldauszahlung"));
		assertTrue(values.contains("Freizeit"));
		assertTrue(values.contains("Miete"));
		assertTrue(values.contains("Freizeit:Sport"));
		assertTrue(values.contains("Miete:Büro"));
		assertTrue(values.contains("Miete:Wohnung"));
		assertTrue(values.contains("Freizeit:Sport:Fitness"));
		assertTrue(values.contains("Freizeit:Sport:Freibad"));
		assertTrue(values.contains("Freizeit:Sport:Schwimmbad"));
		assertTrue(values.contains("Freizeit:Sport:Freibad:Pommes"));
		assertTrue(values.contains("Lebensmittel"));
		assertTrue(values.contains("Lebensmittel:Pommes"));
		assertFalse(values.contains("blahBlah"));
		
		values = all.stream().map(Category::getName).distinct().toList();
		assertEquals(11, values.size(), "11 Kategorien erwartet");
	}
	
	@Test
	void insertCategoryRule_shouldWork() {
		
		Category cg = TestData.createSampleCategory("Bahn");
		cg = db.insertOrUpdate(cg);
		
		CategoryRule cr = new CategoryRule();
		
		cr.setCategory(cg);
		cr.setFilterPurpose("Fahrkarte");
		
		db.insertOrUpdate(cr);
		assertTrue(cr.getId() > 0);
		
		cr = db.getById(CategoryRule.class, cr.getId());

		assertTrue(cr.getId() > 0);
		assertNotNull(cr.getName());
		assertTrue(cr.getName().startsWith("Regel:"));
		assertTrue(cr.getName().length() <= 40);
		assertEquals(cg.getId(), cr.getCategory().getId());
		assertEquals("Fahrkarte", cr.getFilterPurpose());
		
		LocalDate dateUpdatedAtWithoutTime = getCalendarWithoutTime(cr.getUpdatedAt());
		assertEquals(dateUpdatedAtWithoutTime, getCalendarWithoutTime(LocalDate.now(ZoneId.systemDefault())));
	}

	@Test
	void insertCategoryRulesWithBlankNames_shouldGenerateUniqueNames() {

		Category category = db.insertOrUpdate(TestData.createSampleCategory("Bahn"));

		CategoryRule firstRule = new CategoryRule();
		firstRule.setCategory(category);
		firstRule.setFilterPurpose("Ticket");

		CategoryRule secondRule = new CategoryRule();
		secondRule.setCategory(category);
		secondRule.setFilterPurpose("Ticket");

		db.insertOrUpdate(firstRule);
		db.insertOrUpdate(secondRule);

		firstRule = db.getById(CategoryRule.class, firstRule.getId());
		secondRule = db.getById(CategoryRule.class, secondRule.getId());

		assertNotNull(firstRule.getName());
		assertNotNull(secondRule.getName());
		assertNotEquals(firstRule.getName(), secondRule.getName());
		assertTrue(secondRule.getName().endsWith("(2)"));
		assertTrue(secondRule.getName().length() <= 40);
	}

	@Test
	void deleteCategoryRuleShouldClearRuleReferenceAndKeepBookingCategory() {
		BankAccount account = db.insertOrUpdate(TestData.createSampleAccount(null));
		Category category = db.insertOrUpdate(TestData.createSampleCategory("Bahn"));

		Booking booking = TestData.createSampleBooking(account.getId());
		booking = db.insertOrUpdate(booking);

		CategoryRule categoryRule = new CategoryRule();
		categoryRule.setCategory(category);
		categoryRule.setFilterPurpose("Ticket");
		categoryRule = db.insertOrUpdate(categoryRule);

		assertTrue(db.updateBookingsWithCategoryRule(categoryRule, Set.of(booking.getId())));

		Booking storedBooking = db.getByIdFull(Booking.class, booking.getId());
		assertEquals(category.getId(), storedBooking.getCategory().getId());
		assertEquals(categoryRule.getId(), storedBooking.getCategoryRuleId());
		assertEquals(categoryRule.getName(), storedBooking.getCategoryRuleName());

		assertTrue(db.delete(categoryRule, null));

		storedBooking = db.getByIdFull(Booking.class, booking.getId());
		assertEquals(category.getId(), storedBooking.getCategory().getId());
		assertNull(storedBooking.getCategoryRuleId());
		assertNull(storedBooking.getCategoryRuleName());
	}

	@Test
	void getAllCategoryRules_shouldIncludeCategoryDetails() {

		Category cg = db.insertOrUpdate(TestData.createSampleCategory("Mobilitaet:Bahn"));

		CategoryRule cr = new CategoryRule();
		cr.setCategory(cg);
		cr.setFilterPurpose("Ticket");
		db.insertOrUpdate(cr);

		List<CategoryRule> categoryRules = db.getAll(CategoryRule.class);

		assertEquals(1, categoryRules.size());
		assertEquals(cg.getId(), categoryRules.get(0).getCategory().getId());
		assertEquals("Mobilitaet:Bahn", categoryRules.get(0).getCategory().getFullName());
	}

	@Test
	void insertCategoryRule_shouldPersistBankAccounts() {

		BankAccess bankAccess = db.insertOrUpdate(TestData.createSampleBankAccess("12345678"));
		BankAccount account01 = db.insertOrUpdate(TestData.createSampleAccount(bankAccess.getId()));
		BankAccount account02 = db.insertOrUpdate(TestData.createSampleAccount(bankAccess.getId()));
		Category category = db.insertOrUpdate(TestData.createSampleCategory("Mobilitaet:Bahn"));

		CategoryRule categoryRule = new CategoryRule();
		categoryRule.setCategory(category);
		categoryRule.setFilterPurpose("Ticket");
		categoryRule.setBankAccountList(List.of(account01, account02));

		db.insertOrUpdate(categoryRule);

		CategoryRule storedRule = findById(db.getAllFull(CategoryRule.class), categoryRule.getId());
		assertEquals(Set.of(account01.getId(), account02.getId()), accountIds(storedRule));

		categoryRule.setBankAccountList(List.of(account02));
		db.insertOrUpdate(categoryRule);

		storedRule = findById(db.getAllFull(CategoryRule.class), categoryRule.getId());
		assertEquals(Set.of(account02.getId()), accountIds(storedRule));

		categoryRule.setBankAccountList(List.of());
		db.insertOrUpdate(categoryRule);

		storedRule = findById(db.getAllFull(CategoryRule.class), categoryRule.getId());
		assertTrue(storedRule.getBankAccountList().isEmpty());
	}

	private Set<Integer> accountIds(CategoryRule categoryRule) {
		return categoryRule.getBankAccountList().stream().map(BankAccount::getId).collect(Collectors.toSet());
	}

}
