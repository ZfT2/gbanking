package de.zft2.gbanking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Saldo;
import org.kapott.hbci.structures.Value;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.TestData;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.ForeignChargeBearer;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import de.zft2.gbanking.gui.dto.MoneyTransferForm;
import de.zft2.gbanking.service.moneytransfer.BankOrderOperation;
import de.zft2.gbanking.util.TypeConverter;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GBankingBeanTest {

	private DBController dbController;
	private Path tempDir;

	private GBankingBean gBankingBean;

	@BeforeAll
	void setupDatabase() throws Exception {

		// Create fresh DBControllerForTest instance
		tempDir = Files.createTempDirectory("gb_test_");
		dbController = DBController.getInstance(tempDir.toString());

		gBankingBean = new GBankingBean();
	}

	@BeforeEach
	void clearDatabase() {
		DBControllerTestUtil.clearAllTables(DBController.getConnection());
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void testGenerateRebookings_Success() {

		BankAccount bankAccount01 = new BankAccount();
		bankAccount01.setIban("DE00000000000000000001");
		bankAccount01.setNumber("00000001");
		bankAccount01.setBic("BANKDE00001");
		bankAccount01.setCurrency("EUR");
		bankAccount01.setAccountType(AccountType.CURRENT_ACCOUNT);
		bankAccount01.setSource(Source.IMPORT_INITIAL);
		bankAccount01.setAccountState(AccountState.ACTIVE);
		
		bankAccount01 = dbController.insertOrUpdate(bankAccount01);

		Recipient recipient01 = new Recipient();
		recipient01.setIban("DE00000000000000000005");
		recipient01.setBic("BANKDE00005");
		recipient01.setName("Zahler 01");
		recipient01.setSource(Source.IMPORT_INITIAL);
		recipient01 = dbController.insertOrUpdate(recipient01);

		Booking bookingAccount0101 = new Booking();
		bookingAccount0101.setAccountId(bankAccount01.getId());
		bookingAccount0101.setDateBooking(TypeConverter.toLocalDateFromDateStr("14.10.2025"));
		bookingAccount0101.setAmount(BigDecimal.valueOf(200.00));
		bookingAccount0101.setRecipient(recipient01);
		bookingAccount0101.setRecipientId(recipient01.getId());
		bookingAccount0101.setBookingType(BookingType.DEPOSIT);
		bookingAccount0101.setSource(Source.ONLINE);
		dbController.insertOrUpdate(bookingAccount0101);

		Recipient recipient02 = new Recipient();
		recipient02.setIban("DE00000000000000000002");
		recipient02.setBic("BANKDE00002");
		recipient02.setName("Empfänger 02");
		recipient02.setSource(Source.IMPORT_INITIAL);
		recipient02 = dbController.insertOrUpdate(recipient02);

		Booking bookingAccount0102 = new Booking();
		bookingAccount0102.setAccountId(bankAccount01.getId());
		bookingAccount0102.setDateBooking(TypeConverter.toLocalDateFromDateStr("15.10.2025"));
		bookingAccount0102.setAmount(BigDecimal.valueOf(-50.00));
		bookingAccount0102.setRecipient(recipient02);
		bookingAccount0102.setRecipientId(recipient02.getId());
		bookingAccount0102.setBookingType(BookingType.REMOVAL);
		bookingAccount0102.setSource(Source.ONLINE);
		bookingAccount0102.setPurpose("Vwz. Ausgabe - Umbuchung");
		dbController.insertOrUpdate(bookingAccount0102);

		Booking bookingAccount0103 = new Booking();
		bookingAccount0103.setAccountId(bankAccount01.getId());
		bookingAccount0103.setDateBooking(TypeConverter.toLocalDateFromDateStr("16.10.2025"));
		bookingAccount0103.setAmount(BigDecimal.valueOf(400.00));
		bookingAccount0103.setRecipient(recipient01);
		bookingAccount0103.setRecipientId(recipient01.getId());
		bookingAccount0102.setBookingType(BookingType.DEPOSIT);
		bookingAccount0103.setSource(Source.ONLINE);
		dbController.insertOrUpdate(bookingAccount0103);

		BankAccount bankAccount02 = new BankAccount();
		bankAccount02.setIban("DE00000000000000000002");
		bankAccount02.setNumber("00000002");
		bankAccount02.setBic("BANKDE00002");
		bankAccount02.setCurrency("EUR");
		bankAccount02.setAccountType(AccountType.CURRENT_ACCOUNT);
		bankAccount02.setSource(Source.IMPORT_INITIAL);
		bankAccount02.setAccountState(AccountState.ACTIVE);
		
		bankAccount02 = dbController.insertOrUpdate(bankAccount02);

		Recipient recipient03 = new Recipient();
		recipient03.setIban("DE00000000000000000001");
		recipient03.setBic("BANKDE00001");
		recipient03.setName("Empfänger 03");
		recipient03.setSource(Source.IMPORT_INITIAL);
		recipient03 = dbController.insertOrUpdate(recipient03);

		Booking bookingAccount0201 = new Booking();
		bookingAccount0201.setAccountId(bankAccount02.getId());
		bookingAccount0201.setDateBooking(TypeConverter.toLocalDateFromDateStr("15.10.2025"));
		bookingAccount0201.setAmount(BigDecimal.valueOf(50.00));
		bookingAccount0201.setRecipient(recipient03);
		bookingAccount0201.setRecipientId(recipient03.getId());
		bookingAccount0201.setBookingType(BookingType.DEPOSIT);
		bookingAccount0201.setSource(Source.ONLINE_NEW);
		bookingAccount0201.setPurpose("Vwz. Einnahme - Umbuchung");
		dbController.insertOrUpdate(bookingAccount0201);

		bankAccount01 = dbController.getByIdFull(BankAccount.class, bankAccount01.getId());
		assertEquals(3, bankAccount01.getBookings().size());

		bankAccount02 = dbController.getByIdFull(BankAccount.class, bankAccount02.getId());
		assertEquals(1, bankAccount02.getBookings().size());

		Booking toModifyFromAccount02 = bankAccount02.getBookings().stream()
				.filter(booking -> "Vwz. Einnahme - Umbuchung".equals(booking.getPurpose())).findAny().orElse(null);
		assertEquals(BookingType.DEPOSIT, toModifyFromAccount02.getBookingType());

		Booking toModifiyFromAccount01 = bankAccount01.getBookings().stream()
				.filter(booking -> "Vwz. Ausgabe - Umbuchung".equals(booking.getPurpose())).findAny().orElse(null);
		assertEquals(BookingType.REMOVAL, toModifiyFromAccount01.getBookingType());

		gBankingBean.adjustRebookings(bankAccount02);

		bankAccount02 = dbController.getByIdFull(BankAccount.class, bankAccount02.getId());
		toModifyFromAccount02 = bankAccount02.getBookings().stream()
				.filter(booking -> "Vwz. Einnahme - Umbuchung".equals(booking.getPurpose())).findAny().orElse(null);
		assertEquals(BookingType.REBOOKING_IN, toModifyFromAccount02.getBookingType());

		bankAccount01 = dbController.getByIdFull(BankAccount.class, bankAccount01.getId());
		toModifiyFromAccount01 = bankAccount01.getBookings().stream()
				.filter(booking -> "Vwz. Ausgabe - Umbuchung".equals(booking.getPurpose())).findAny().orElse(null);
		assertEquals(BookingType.REBOOKING_OUT, toModifiyFromAccount01.getBookingType());
		assertEquals(toModifiyFromAccount01.getId(), toModifyFromAccount02.getCrossBookingId());
		assertEquals(toModifyFromAccount02.getId(), toModifiyFromAccount01.getCrossBookingId());
	}

	@Test
	void testSaveHbciBookingsForAccount_Success() {

		BankAccount bankAccount01 = new BankAccount();
		bankAccount01.setIban("DE00000000000000000001");
		bankAccount01.setNumber("00000001");
		bankAccount01.setBic("BANKDE00001");
		bankAccount01.setCurrency("EUR");
		bankAccount01.setAccountType(AccountType.CURRENT_ACCOUNT);
		bankAccount01.setSource(Source.IMPORT_INITIAL);
		bankAccount01.setAccountState(AccountState.ACTIVE);

		bankAccount01 = dbController.insertOrUpdate(bankAccount01);

		List<UmsLine> buchungen = new ArrayList<>();

		Konto konto = createKonto("DE123456789012", "12030000", "Test Inhaber");

		UmsLine umsLine01 = createUmsLine(new Date(), "NONREF", "805", "905", "EUR", 100.00, 50.00, "Abschluss", "ABSCHLUSS PER 31.12.2023",
				null, null);
		UmsLine umsLine02 = createUmsLine(new Date(), "NONREF", "201", "804", "EUR", 200.00, -60.00, "Überweisungsauftrag", "Auszahlung 01",
				null, konto);
		UmsLine umsLine03 = createUmsLine(new Date(), "NONREF", "805", "931", "EUR", 300.00, 70.00, "Überweisungsgutschr.", "Einzahlung 01",
				null, konto);
		UmsLine umsLine04 = createUmsLine(new Date(), "KREF+", "118", "8300", "EUR", 100.00, 50.00, "Überw.-Auftrag eilig",
				"KREF+2025110306782806096200", "SecureGo plus IBAN: DE92500", konto);

		buchungen.add(umsLine01);
		buchungen.add(umsLine02);
		buchungen.add(umsLine03);
		buchungen.add(umsLine04);

		gBankingBean.saveHbciBookingsForAccount(bankAccount01, buchungen);

		List<Booking> bookingListDb = dbController.getAllByParent(Booking.class, bankAccount01.getId());

		assertEquals(4, bookingListDb.size());

		List<Recipient> recipientListDb = dbController.getAll(Recipient.class);

		assertEquals(1, recipientListDb.size());

	}

	@Test
	void testUnreferencedRecipientIsEditable() {
		Recipient r1 = TestData.createSampleRecipient01();
		dbController.insertOrUpdate(r1);

		boolean editable = gBankingBean.isRecipientEditable(r1);

		assertTrue(editable);
	}
	
	@Test
	void testReferencedRecipientIsNotEditable() {
		Recipient r1 = TestData.createSampleRecipient01();
		dbController.insertOrUpdate(r1);
		
		BankAccount acc1 = TestData.createSampleAccount(null);
		dbController.insertOrUpdate(acc1);
		Booking b1 = TestData.createSampleBookingWithRecipient(acc1.getId(), r1.getId());
		dbController.insertOrUpdate(b1);

		boolean editable = gBankingBean.isRecipientEditable(r1);

		assertFalse(editable);
	}

	@Test
	void testApplyCategoryRule_WithAndFilter_SetsCategoryOnlyForMatchingBooking() {
		BankAccount account = TestData.createSampleAccount(null);
		account = dbController.insertOrUpdate(account);

		Recipient matchRecipient = new Recipient("Supermarkt Nord", "DE00000000000000001000");
		matchRecipient.setSource(Source.IMPORT_INITIAL);
		matchRecipient.setAccountNumber("1000");
		matchRecipient = dbController.insertOrUpdate(matchRecipient);

		Recipient otherRecipient = new Recipient("Baeckerei", "DE00000000000000002000");
		otherRecipient.setSource(Source.IMPORT_INITIAL);
		otherRecipient.setAccountNumber("2000");
		otherRecipient = dbController.insertOrUpdate(otherRecipient);

		Booking matchingBooking = new Booking();
		matchingBooking.setAccountId(account.getId());
		matchingBooking.setDateBooking(LocalDate.of(2025, Month.OCTOBER, 14));
		matchingBooking.setDateValue(LocalDate.of(2025, Month.OCTOBER, 14));
		matchingBooking.setAmount(BigDecimal.valueOf(-42.50));
		matchingBooking.setPurpose("Einkauf Wochenende");
		matchingBooking.setBookingType(BookingType.REMOVAL);
		matchingBooking.setSource(Source.IMPORT_INITIAL);
		matchingBooking.setRecipientId(matchRecipient.getId());
		matchingBooking = dbController.insertOrUpdate(matchingBooking);

		Booking nonMatchingBooking = new Booking();
		nonMatchingBooking.setAccountId(account.getId());
		nonMatchingBooking.setDateBooking(LocalDate.of(2025, Month.OCTOBER, 14));
		nonMatchingBooking.setDateValue(LocalDate.of(2025, Month.OCTOBER, 14));
		nonMatchingBooking.setAmount(BigDecimal.valueOf(-12.00));
		nonMatchingBooking.setPurpose("Kaffee");
		nonMatchingBooking.setBookingType(BookingType.REMOVAL);
		nonMatchingBooking.setSource(Source.IMPORT_INITIAL);
		nonMatchingBooking.setRecipientId(otherRecipient.getId());
		nonMatchingBooking = dbController.insertOrUpdate(nonMatchingBooking);

		Category category = dbController.insertOrUpdate(TestData.createSampleCategory("Lebensmittel"));

		CategoryRule categoryRule = new CategoryRule();
		categoryRule.setCategory(category);
		categoryRule.setJoinType(CategoryRule.JoinType.AND);
		categoryRule.setFilterPurpose("einkauf");
		categoryRule.setFilterRecipientName("supermarkt.*");
		categoryRule.setFilterRecipientIban("001000");
		categoryRule.setFilterRecipientAccountNumber("1000");
		categoryRule.setFilterRecipientIsRegex(true);
		categoryRule.setBankAccountList(List.of(account));

		gBankingBean.applyCategoryRule(categoryRule);

		matchingBooking = dbController.getByIdFull(Booking.class, matchingBooking.getId());
		nonMatchingBooking = dbController.getByIdFull(Booking.class, nonMatchingBooking.getId());

		assertEquals(category.getId(), matchingBooking.getCategory().getId());
		assertEquals(0, nonMatchingBooking.getCategoryId());
	}

	@Test
	void testApplyCategoryRulesToBookings_RespectsOverwriteExistingCategoriesChoice() {
		BankAccount account = TestData.createSampleAccount(null);
		account = dbController.insertOrUpdate(account);

		Category existingCategory = dbController.insertOrUpdate(TestData.createSampleCategory("Alt"));
		Category targetCategory = dbController.insertOrUpdate(TestData.createSampleCategory("Neu"));

		Booking categorizedBooking = TestData.createSampleBooking(account.getId());
		categorizedBooking.setPurpose("Ticket Monatskarte");
		categorizedBooking.setCategory(existingCategory);
		categorizedBooking = dbController.insertOrUpdate(categorizedBooking);

		Booking uncategorizedBooking = TestData.createSampleBooking(account.getId());
		uncategorizedBooking.setPurpose("Ticket Einzelfahrt");
		uncategorizedBooking = dbController.insertOrUpdate(uncategorizedBooking);

		CategoryRule categoryRule = new CategoryRule();
		categoryRule.setCategory(targetCategory);
		categoryRule.setJoinType(CategoryRule.JoinType.AND);
		categoryRule.setFilterPurpose("ticket");
		categoryRule = dbController.insertOrUpdate(categoryRule);

		int updatedBookings = gBankingBean.applyCategoryRulesToBookings(List.of(categorizedBooking, uncategorizedBooking), false);

		assertEquals(1, updatedBookings);
		categorizedBooking = dbController.getByIdFull(Booking.class, categorizedBooking.getId());
		uncategorizedBooking = dbController.getByIdFull(Booking.class, uncategorizedBooking.getId());
		assertEquals(existingCategory.getId(), categorizedBooking.getCategory().getId());
		assertEquals(targetCategory.getId(), uncategorizedBooking.getCategory().getId());
		assertNull(categorizedBooking.getCategoryRuleId());
		assertEquals(categoryRule.getId(), uncategorizedBooking.getCategoryRuleId());
		assertEquals(categoryRule.getName(), uncategorizedBooking.getCategoryRuleName());

		updatedBookings = gBankingBean.applyCategoryRulesToBookings(List.of(categorizedBooking, uncategorizedBooking), true);

		assertEquals(1, updatedBookings);
		categorizedBooking = dbController.getByIdFull(Booking.class, categorizedBooking.getId());
		uncategorizedBooking = dbController.getByIdFull(Booking.class, uncategorizedBooking.getId());
		assertEquals(targetCategory.getId(), categorizedBooking.getCategory().getId());
		assertEquals(targetCategory.getId(), uncategorizedBooking.getCategory().getId());
		assertEquals(categoryRule.getId(), categorizedBooking.getCategoryRuleId());
		assertEquals(categoryRule.getName(), categorizedBooking.getCategoryRuleName());
	}

	@Test
	void assignCategoryToBookings_ShouldAssignSelectedCategoryOnlyToGivenBookings() {
		BankAccount account = dbController.insertOrUpdate(TestData.createSampleAccount(null));
		Category oldCategory = dbController.insertOrUpdate(TestData.createSampleCategory("Alt"));
		Category newCategory = dbController.insertOrUpdate(TestData.createSampleCategory("Neu"));

		Booking categorizedBooking = TestData.createSampleBooking(account.getId());
		categorizedBooking.setCategory(oldCategory);
		categorizedBooking = dbController.insertOrUpdate(categorizedBooking);

		Booking uncategorizedBooking = dbController.insertOrUpdate(TestData.createSampleBooking2(account.getId()));
		Booking untouchedBooking = dbController.insertOrUpdate(TestData.createSampleBooking(account.getId()));

		int updatedBookings = gBankingBean.assignCategoryToBookings(newCategory, List.of(categorizedBooking, uncategorizedBooking));

		assertEquals(2, updatedBookings);
		categorizedBooking = dbController.getByIdFull(Booking.class, categorizedBooking.getId());
		uncategorizedBooking = dbController.getByIdFull(Booking.class, uncategorizedBooking.getId());
		untouchedBooking = dbController.getByIdFull(Booking.class, untouchedBooking.getId());
		assertEquals(newCategory.getId(), categorizedBooking.getCategory().getId());
		assertEquals(newCategory.getId(), uncategorizedBooking.getCategory().getId());
		assertEquals(0, untouchedBooking.getCategoryId());
	}

	@Test
	void clearCategoryFromBookings_ShouldRemoveOnlyExistingCategoriesFromGivenBookings() {
		BankAccount account = dbController.insertOrUpdate(TestData.createSampleAccount(null));
		Category category = dbController.insertOrUpdate(TestData.createSampleCategory("Kategorie"));

		Booking categorizedBooking = TestData.createSampleBooking(account.getId());
		categorizedBooking.setCategory(category);
		categorizedBooking = dbController.insertOrUpdate(categorizedBooking);

		Booking uncategorizedBooking = dbController.insertOrUpdate(TestData.createSampleBooking2(account.getId()));

		Booking untouchedBooking = TestData.createSampleBooking(account.getId());
		untouchedBooking.setCategory(category);
		untouchedBooking = dbController.insertOrUpdate(untouchedBooking);

		int updatedBookings = gBankingBean.clearCategoryFromBookings(List.of(categorizedBooking, uncategorizedBooking));

		assertEquals(1, updatedBookings);
		categorizedBooking = dbController.getByIdFull(Booking.class, categorizedBooking.getId());
		uncategorizedBooking = dbController.getByIdFull(Booking.class, uncategorizedBooking.getId());
		untouchedBooking = dbController.getByIdFull(Booking.class, untouchedBooking.getId());
		assertNull(categorizedBooking.getCategory());
		assertEquals(0, categorizedBooking.getCategoryId());
		assertNull(uncategorizedBooking.getCategory());
		assertEquals(category.getId(), untouchedBooking.getCategory().getId());
	}

	@Test
	void getCategoryDeleteImpact_ShouldCountBookingsAndRulesInCategoryTree() {
		BankAccount account = dbController.insertOrUpdate(TestData.createSampleAccount(null));
		Category parentCategory = dbController.insertOrUpdate(new Category("Mobilitaet", null));
		Category childCategory = dbController.insertOrUpdate(new Category("Bahn", parentCategory.getId()));
		Category otherCategory = dbController.insertOrUpdate(TestData.createSampleCategory("Freizeit"));

		Booking parentBooking = TestData.createSampleBooking(account.getId());
		parentBooking.setCategory(parentCategory);
		parentBooking = dbController.insertOrUpdate(parentBooking);

		Booking childBooking = TestData.createSampleBooking2(account.getId());
		childBooking.setCategory(childCategory);
		childBooking = dbController.insertOrUpdate(childBooking);

		Booking otherBooking = TestData.createSampleBooking(account.getId());
		otherBooking.setCategory(otherCategory);
		otherBooking = dbController.insertOrUpdate(otherBooking);

		dbController.insertOrUpdate(categoryRule(parentCategory, "Mobilitaet"));
		dbController.insertOrUpdate(categoryRule(childCategory, "Bahn"));
		dbController.insertOrUpdate(categoryRule(otherCategory, "Freizeit"));

		GBankingBean.CategoryDeleteImpact parentImpact = gBankingBean.getCategoryDeleteImpact(parentCategory);
		GBankingBean.CategoryDeleteImpact childImpact = gBankingBean.getCategoryDeleteImpact(childCategory);

		assertEquals(2, parentImpact.bookingCount());
		assertEquals(2, parentImpact.categoryRuleCount());
		assertEquals(1, childImpact.bookingCount());
		assertEquals(1, childImpact.categoryRuleCount());

		assertTrue(gBankingBean.deleteCategoryFromDB(parentCategory));

		parentBooking = dbController.getByIdFull(Booking.class, parentBooking.getId());
		childBooking = dbController.getByIdFull(Booking.class, childBooking.getId());
		otherBooking = dbController.getByIdFull(Booking.class, otherBooking.getId());
		assertNull(parentBooking.getCategory());
		assertNull(childBooking.getCategory());
		assertEquals(otherCategory.getId(), otherBooking.getCategory().getId());
		assertFalse(dbController.getAll(Category.class).stream()
				.anyMatch(category -> category.getId() == parentCategory.getId() || category.getId() == childCategory.getId()));
		assertEquals(1, dbController.getAll(CategoryRule.class).size());
	}

	@Test
	void testPostRetrieveActions_AppliesMatchingCategoryRules() {
		BankAccount account = TestData.createSampleAccount(null);
		account = dbController.insertOrUpdate(account);

		Recipient recipient = new Recipient("Supermarkt Sued", "DE00000000000000003000");
		recipient.setSource(Source.IMPORT_INITIAL);
		recipient = dbController.insertOrUpdate(recipient);

		Booking booking = new Booking();
		booking.setAccountId(account.getId());
		booking.setDateBooking(LocalDate.of(2025, Month.OCTOBER, 20));
		booking.setDateValue(LocalDate.of(2025, Month.OCTOBER, 20));
		booking.setAmount(BigDecimal.valueOf(-25.00));
		booking.setPurpose("Wocheneinkauf");
		booking.setBookingType(BookingType.REMOVAL);
		booking.setSource(Source.IMPORT_INITIAL);
		booking.setRecipientId(recipient.getId());
		booking = dbController.insertOrUpdate(booking);

		Category category = dbController.insertOrUpdate(TestData.createSampleCategory("Lebensmittel"));

		CategoryRule categoryRule = new CategoryRule();
		categoryRule.setCategory(category);
		categoryRule.setJoinType(CategoryRule.JoinType.AND);
		categoryRule.setFilterPurpose("einkauf");
		categoryRule = dbController.insertOrUpdate(categoryRule);

		account = dbController.getByIdFull(BankAccount.class, account.getId());
		gBankingBean.postRetriveActions(List.of(account));

		booking = dbController.getByIdFull(Booking.class, booking.getId());
		assertEquals(category.getId(), booking.getCategory().getId());
		assertEquals(categoryRule.getId(), booking.getCategoryRuleId());
		assertEquals(categoryRule.getName(), booking.getCategoryRuleName());
	}

	private CategoryRule categoryRule(Category category, String purpose) {
		CategoryRule categoryRule = new CategoryRule();
		categoryRule.setCategory(category);
		categoryRule.setFilterPurpose(purpose);
		return categoryRule;
	}

	@Test
	void testSaveMoneyTransferToDB_WithStandingOrderData_PersistsExtendedFields() {
		BankAccount account = TestData.createSampleAccount(null);
		account = dbController.insertOrUpdate(account);

		Recipient recipient = new Recipient("Stromanbieter", "DE12345678901234567890", "TESTDEFFXXX", null, null, "Testbank", Source.ONLINE);

		MoneyTransferForm form = new MoneyTransferForm(account, OrderType.STANDING_ORDER, recipient, BigDecimal.valueOf(89.45), "Monatlicher Abschlag",
				LocalDate.of(2026, Month.APRIL, 1));
		form.setStandingorderInfo(15, StandingorderMode.MONTHLY);

		MoneyTransfer savedTransfer = gBankingBean.saveMoneyTransferToDB(form);
		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, account.getId());

		assertEquals(1, transfers.size());
		assertEquals(savedTransfer.getId(), transfers.get(0).getId());
		assertEquals(OrderType.STANDING_ORDER, transfers.get(0).getOrderType());
		assertEquals(LocalDate.of(2026, Month.APRIL, 1), transfers.get(0).getExecutionDate());
		assertEquals(Integer.valueOf(15), transfers.get(0).getExecutionDay());
		assertEquals(StandingorderMode.MONTHLY, transfers.get(0).getStandingorderMode());
		assertEquals(MoneyTransferStatus.NEW, transfers.get(0).getMoneytransferStatus());
	}

	@Test
	void testSaveMoneyTransferToDB_WithRecipientBlz_PersistsBlzInsteadOfBic() {
		BankAccount account = TestData.createSampleAccount(null);
		account = dbController.insertOrUpdate(account);

		Recipient recipient = new Recipient("Domestic Recipient", "DE12345678901234567890", null, null, null, "Testbank", Source.ONLINE);

		MoneyTransferForm form = new MoneyTransferForm(account, OrderType.TRANSFER, recipient, BigDecimal.valueOf(23.45), "Invoice", LocalDate.of(2026, Month.MAY, 28));
		form.setRecipientBlz("50010517");

		gBankingBean.saveMoneyTransferToDB(form);
		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, account.getId());

		assertEquals(1, transfers.size());
		assertNull(transfers.get(0).getRecipient().getBic());
		assertEquals("50010517", transfers.get(0).getRecipient().getBlz());
	}

	@Test
	void testSaveMoneyTransferToDB_WithForeignTransfer_PersistsCurrencyAndRecipientBank() {
		BankAccount account = dbController.insertOrUpdate(TestData.createSampleAccount(null));
		dbController.insertOrUpdate(new Recipient("Foreign Recipient", "GB29NWBK60161331926819", null, null, null, null, Source.MONEYTRANSFER));

		Recipient foreignRecipient = new Recipient("Foreign Recipient", "GB29NWBK60161331926819", "BICCODE", null, null, "Recipient Bank", Source.ONLINE);

		MoneyTransferForeign foreignTransfer = new MoneyTransferForeign();
		foreignTransfer.setCurrency("GBP");
		foreignTransfer.setRecipientCountry("GB");
		foreignTransfer.setRecipientAccountNumber("31926819");
		foreignTransfer.setRecipientBankCode("NWBK601613");
		foreignTransfer.setChargeBearer(ForeignChargeBearer.SENDER);

		MoneyTransferForm form = new MoneyTransferForm(account, OrderType.FOREIGN_TRANSFER, foreignRecipient, BigDecimal.valueOf(45.67), "Invoice",
				LocalDate.of(2026, Month.MAY, 21), foreignTransfer);

		gBankingBean.saveMoneyTransferToDB(form);
		List<MoneyTransfer> transfers = dbController.getAllByParent(MoneyTransfer.class, account.getId());

		assertEquals(1, transfers.size());
		assertEquals(OrderType.FOREIGN_TRANSFER, transfers.get(0).getOrderType());
		assertEquals("GBP", transfers.get(0).getCurrency());
		assertEquals("Recipient Bank", transfers.get(0).getRecipient().getBank());
		assertEquals("GB", transfers.get(0).getForeignTransfer().getRecipientCountry());
		assertEquals("31926819", transfers.get(0).getForeignTransfer().getRecipientAccountNumber());
		assertEquals("NWBK601613", transfers.get(0).getForeignTransfer().getRecipientBankCode());
		assertEquals(ForeignChargeBearer.SENDER, transfers.get(0).getForeignTransfer().getChargeBearer());
		assertEquals("31926819", transfers.get(0).getRecipient().getAccountNumber());
		assertEquals("NWBK601613", transfers.get(0).getRecipient().getBlz());
	}

	@Test
	void testSupportsTransferOrderType_WithAllowedBusinessCases_ReturnsExpectedResult() {
		BankAccess bankAccess = insertBankAccessWithBpd("UebSEPA", "InstUebSEPA", "UebEil");
		BankAccount account = new BankAccount();
		account.setBankAccessId(bankAccess.getId());
		account.setAllowedBusinessCases(List.of(createBusinessCase("UebSEPA"), createBusinessCase("InstUebSEPA"), createBusinessCase("UebEil")));

		assertTrue(gBankingBean.supportsTransferOrderType(account, OrderType.TRANSFER));
		assertTrue(gBankingBean.supportsTransferOrderType(account, OrderType.REALTIME_TRANSFER));
		assertTrue(gBankingBean.supportsTransferOrderType(account, OrderType.URGENT_TRANSFER));
		assertFalse(gBankingBean.supportsTransferOrderType(account, OrderType.SCHEDULED_TRANSFER));
		assertFalse(gBankingBean.supportsTransferOrderType(account, OrderType.STANDING_ORDER));
	}

	@Test
	void testSupportsTransferOrderType_WithoutBusinessCases_BlocksTransfer() {
		BankAccess bankAccess = insertBankAccessWithBpd("UebSEPA", "DauerSEPANew");
		BankAccount account = new BankAccount();
		account.setBankAccessId(bankAccess.getId());

		assertFalse(gBankingBean.supportsTransferOrderType(account, OrderType.TRANSFER));
		assertFalse(gBankingBean.supportsTransferOrderType(account, OrderType.STANDING_ORDER));
	}

	@Test
	void testSupportsTransferOrderType_WithUpd_UsesUpdAsFallback() {
		BankAccess bankAccess = insertBankAccessWithBpdAndUpd(new String[] { "HKCCS", "HKCDE" }, new String[] { "HKCCS" });
		BankAccount account = new BankAccount();
		account.setBankAccessId(bankAccess.getId());

		assertTrue(gBankingBean.supportsTransferOrderType(account, OrderType.TRANSFER));
		assertFalse(gBankingBean.supportsTransferOrderType(account, OrderType.STANDING_ORDER));
	}

	@Test
	void testSupportsTransferOrderType_WithAccountBusinessCaseAndPartialUpd_KeepsStandingOrderEnabled() {
		BankAccess bankAccess = insertBankAccessWithBpdAndUpd(new String[] { "HKCCS", "HKCDE" }, new String[] { "HKCCS" });
		BankAccount account = new BankAccount();
		account.setBankAccessId(bankAccess.getId());
		account.setAllowedBusinessCases(List.of(createBusinessCase("HKCDE")));

		assertTrue(gBankingBean.supportsTransferOrderType(account, OrderType.TRANSFER));
		assertTrue(gBankingBean.supportsTransferOrderType(account, OrderType.STANDING_ORDER));
	}

	@Test
	void testSupportsTransferOrderType_WithAtruviaStandingOrderCode_ReturnsTrue() {
		BankAccess bankAccess = insertBankAccessWithBpdAndUpd(new String[] { "HKCDE" }, new String[] { "HKCDE" });
		BankAccount account = new BankAccount();
		account.setBankAccessId(bankAccess.getId());

		assertTrue(gBankingBean.supportsTransferOrderType(account, OrderType.STANDING_ORDER));
	}

	@Test
	void testSupportsTransferOrderType_WithUrgentTransferCode_ReturnsTrue() {
		BankAccess bankAccess = insertBankAccessWithBpdAndUpd(new String[] { "HKEIL" }, new String[] { "HKEIL" });
		BankAccount account = new BankAccount();
		account.setBankAccessId(bankAccess.getId());

		assertTrue(gBankingBean.supportsTransferOrderType(account, OrderType.URGENT_TRANSFER));
	}

	@Test
	void testSupportsTransferOrderType_WithSepaDirectDebitCode_DoesNotEnableStandingOrder() {
		BankAccess bankAccess = insertBankAccessWithBpdAndUpd(new String[] { "HKDSE" }, new String[] { "HKDSE" });
		BankAccount account = new BankAccount();
		account.setBankAccessId(bankAccess.getId());

		assertFalse(gBankingBean.supportsTransferOrderType(account, OrderType.STANDING_ORDER));
	}

	@Test
	void testSupportsOrderInventory_WithAtruviaInventoryCodes_ReturnsTrue() {
		BankAccess bankAccess = insertBankAccessWithBpdAndUpd(new String[] { "HKCDB", "HKCSB" }, new String[] { "HKCDB", "HKCSB" });
		BankAccount account = new BankAccount();
		account.setBankAccessId(bankAccess.getId());

		assertTrue(gBankingBean.supportsOrderInventory(account, OrderType.STANDING_ORDER));
		assertTrue(gBankingBean.supportsOrderInventory(account, OrderType.SCHEDULED_TRANSFER));
	}

	@Test
	void testSupportsBankOrderOperation_WithEditAndDeleteCodes_ReturnsExpectedResult() {
		String[] operationCodes = { "HKCDN", "HKCDL", "HKCSA", "HKCSL" };
		BankAccess bankAccess = insertBankAccessWithBpdAndUpd(operationCodes, operationCodes);
		BankAccount account = new BankAccount();
		account.setBankAccessId(bankAccess.getId());

		assertTrue(gBankingBean.supportsBankOrderOperation(account, OrderType.STANDING_ORDER, BankOrderOperation.EDIT));
		assertTrue(gBankingBean.supportsBankOrderOperation(account, OrderType.STANDING_ORDER, BankOrderOperation.DELETE));
		assertTrue(gBankingBean.supportsBankOrderOperation(account, OrderType.SCHEDULED_TRANSFER, BankOrderOperation.EDIT));
		assertTrue(gBankingBean.supportsBankOrderOperation(account, OrderType.SCHEDULED_TRANSFER, BankOrderOperation.DELETE));
		assertFalse(gBankingBean.supportsBankOrderOperation(account, OrderType.TRANSFER, BankOrderOperation.EDIT));
	}

	@Test
	void testSupportsAccountStatements_WithHkekaAndHkekpCodes_ReturnsTrue() {
		BankAccess bankAccess = insertBankAccessWithBpdAndUpd(new String[] { "HKEKA", "HKEKP" }, new String[] { "HKEKA" });
		BankAccount account = new BankAccount();
		account.setBankAccessId(bankAccess.getId());

		assertTrue(gBankingBean.supportsAccountStatements(account));
	}

	@Test
	void testSupportsBankMessages_WithActiveBankAccess_ReturnsTrue() {
		BankAccess bankAccess = insertBankAccessWithBpd("HIKIAS");

		assertTrue(gBankingBean.supportsBankMessages(bankAccess));
	}

	@Test
	void testSupportsBankMessages_WithoutInfoListParameter_ReturnsTrue() {
		BankAccess bankAccess = insertBankAccessWithBpd("HKEKA");

		assertTrue(gBankingBean.supportsBankMessages(bankAccess));
	}

	@Test
	void testSupportsBankMessages_WithInactiveBankAccess_ReturnsFalse() {
		BankAccess bankAccess = insertBankAccessWithBpd("HIKIAS");
		bankAccess.setActive(false);
		bankAccess = dbController.insertOrUpdate(bankAccess);

		assertFalse(gBankingBean.supportsBankMessages(bankAccess));
	}

	@Test
	void testSupportsTransferOrderType_WithAtruviaForeignTransferCode_ReturnsTrue() {
		BankAccess bankAccess = insertBankAccessWithBpdAndUpd(new String[] { "HKAUB" }, new String[] { "HKAUB" });
		BankAccount account = new BankAccount();
		account.setBankAccessId(bankAccess.getId());

		assertTrue(gBankingBean.supportsTransferOrderType(account, OrderType.FOREIGN_TRANSFER));
	}

	@Test
	void testSupportsTransferOrderType_WithForeignTransferOnlyInBpd_ReturnsFalse() {
		BankAccess bankAccess = insertBankAccessWithBpd("HKAUB");
		BankAccount account = new BankAccount();
		account.setBankAccessId(bankAccess.getId());

		assertFalse(gBankingBean.supportsTransferOrderType(account, OrderType.FOREIGN_TRANSFER));
	}

	@Test
	void testSupportsTransferOrderType_WithMultipleUpdAccounts_UsesMatchingAccountOnly() {
		BankAccess bankAccess = dbController.insertOrUpdate(TestData.createSampleBankAccess("51390000"));
		bankAccess.setBpd(TestData.buildCapabilityBPD("HKCCS", "HKCDE"));

		Properties upd = new Properties();
		upd.setProperty("KInfo.iban", "DE11111111111111111111");
		upd.setProperty("KInfo.KTV.number", "11111111");
		upd.setProperty("KInfo.KTV.KIK.blz", "51390000");
		upd.setProperty("KInfo.AllowedGV.code", "HKCDE");
		upd.setProperty("KInfo_2.iban", "DE22222222222222222222");
		upd.setProperty("KInfo_2.KTV.number", "22222222");
		upd.setProperty("KInfo_2.KTV.KIK.blz", "51390000");
		upd.setProperty("KInfo_2.AllowedGV.code", "HKCCS");
		bankAccess.setUpd(upd);
		dbController.insertOrUpdatePD(bankAccess);

		BankAccount standingOrderAccount = new BankAccount();
		standingOrderAccount.setBankAccessId(bankAccess.getId());
		standingOrderAccount.setIban("DE11111111111111111111");
		standingOrderAccount.setNumber("11111111");
		standingOrderAccount.setBlz("51390000");

		BankAccount transferOnlyAccount = new BankAccount();
		transferOnlyAccount.setBankAccessId(bankAccess.getId());
		transferOnlyAccount.setIban("DE22222222222222222222");
		transferOnlyAccount.setNumber("22222222");
		transferOnlyAccount.setBlz("51390000");

		assertTrue(gBankingBean.supportsTransferOrderType(standingOrderAccount, OrderType.STANDING_ORDER));
		assertFalse(gBankingBean.supportsTransferOrderType(transferOnlyAccount, OrderType.STANDING_ORDER));
		assertTrue(gBankingBean.supportsTransferOrderType(transferOnlyAccount, OrderType.TRANSFER));
	}

	@Test
	void testSupportsTransferOrderType_WithConflictingUpdIban_PrefersAccountNumberAndBlz() {
		BankAccess bankAccess = dbController.insertOrUpdate(TestData.createSampleBankAccess("51390000"));
		bankAccess.setBpd(TestData.buildCapabilityBPD("HKCCS", "HKIPZ"));

		Properties upd = new Properties();
		upd.setProperty("KInfo.iban", "DE11111111111111111111");
		upd.setProperty("KInfo.KTV.number", "11111111");
		upd.setProperty("KInfo.KTV.KIK.blz", "51390000");
		upd.setProperty("KInfo.konto", "Kontokorrent");
		upd.setProperty("KInfo.AllowedGV.code", "HKIPZ");
		upd.setProperty("KInfo_2.iban", "DE22222222222222222222");
		upd.setProperty("KInfo_2.KTV.number", "22222222");
		upd.setProperty("KInfo_2.KTV.KIK.blz", "51390000");
		upd.setProperty("KInfo_2.konto", "Kreditkartenkonto");
		upd.setProperty("KInfo_2.AllowedGV.code", "HKCCS");
		bankAccess.setUpd(upd);
		dbController.insertOrUpdatePD(bankAccess);

		BankAccount giroAccount = new BankAccount();
		giroAccount.setBankAccessId(bankAccess.getId());
		giroAccount.setAccountName("Kontokorrent - 11111111");
		giroAccount.setIban("DE22222222222222222222");
		giroAccount.setNumber("11111111");
		giroAccount.setBlz("51390000");

		assertTrue(gBankingBean.supportsTransferOrderType(giroAccount, OrderType.REALTIME_TRANSFER));
		assertFalse(gBankingBean.supportsTransferOrderType(giroAccount, OrderType.TRANSFER));
	}

	private BankAccess insertBankAccessWithBpd(String... businessCases) {
		return insertBankAccessWithBpdAndUpd(businessCases, new String[0]);
	}

	private BankAccess insertBankAccessWithBpdAndUpd(String[] bpdBusinessCases, String[] updBusinessCases) {
		BankAccess bankAccess = dbController.insertOrUpdate(TestData.createSampleBankAccess("10020030"));
		bankAccess.setBpd(TestData.buildCapabilityBPD(bpdBusinessCases));
		if (updBusinessCases.length > 0) {
			bankAccess.setUpd(TestData.buildCapabilityUPD(updBusinessCases));
		}
		dbController.insertOrUpdatePD(bankAccess);
		return bankAccess;
	}

	private Konto createKonto(String iban, String blz, String name1) {

		Konto konto = new Konto();
		konto.iban = iban;
		konto.blz = blz;
		konto.name = name1;

		return konto;
	}

	private BusinessCase createBusinessCase(String caseValue) {
		BusinessCase businessCase = new BusinessCase();
		businessCase.setCaseValue(caseValue);
		return businessCase;
	}

	private UmsLine createUmsLine(Date date, String customerref, String gvcode, String primanota, String currency, Double balance,
			Double amount, String text, String usage1, String usage2, Konto konto) {

		UmsLine umsLine = new UmsLine();
		umsLine.bdate = date;
		umsLine.customerref = customerref;
		umsLine.gvcode = gvcode;
		umsLine.instref = "";
		umsLine.isCamt = false;
		umsLine.isSepa = false;
		umsLine.isStorno = false;
		umsLine.primanota = primanota;
		Saldo saldo = new Saldo();
		saldo.timestamp = date;
		Value value01 = new Value();
		value01.setCurr(currency);
		value01.setValue(BigDecimal.valueOf(balance));
		saldo.value = value01;
		umsLine.saldo = saldo;
		umsLine.text = text;
		umsLine.usage = Arrays.asList(usage1, usage2);
		value01 = new Value();
		value01.setValue(BigDecimal.valueOf(amount));
		umsLine.value = value01;
		umsLine.valuta = date;
		umsLine.other = konto;

		return umsLine;
	}
}
