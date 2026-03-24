package de.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Category;
import de.gbanking.db.dao.CategoryRule;

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
		assertEquals(dateUpdatedAtWithoutTime, getCalendarWithoutTime(LocalDate.now()));
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

		int baId = db.insertOrUpdate(TestData.createSampleBankAccess("12030000")).getId();
		List<Integer> bAccIdList = new ArrayList<Integer>();
		
		BankAccount bAcc1 = db.insertOrUpdate(TestData.createSampleAccount(baId));
		BankAccount bAcc2 = db.insertOrUpdate(TestData.createSampleAccount(baId));
		
		bAccIdList.add(bAcc1.getId());
		bAccIdList.add(bAcc2.getId());
		
		Category cg = TestData.createSampleCategory("Bahn");
		cg = db.insertOrUpdate(cg);
		
		CategoryRule cr = new CategoryRule();
		
		//cr.setCategoryId(cg.getId());
		cr.setCategory(cg);
		cr.setFilterPurpose("Fahrkarte");
		
		db.insertOrUpdate(cr);
		assertTrue(cr.getId() > 0);
		
		cr = db.getById(CategoryRule.class, cr.getId());

		assertTrue(cr.getId() > 0);
		assertEquals("Fahrkarte", cr.getFilterPurpose());
		
		LocalDate dateUpdatedAtWithoutTime = getCalendarWithoutTime(cr.getUpdatedAt());
		assertEquals(dateUpdatedAtWithoutTime, getCalendarWithoutTime(LocalDate.now()));
	}

}