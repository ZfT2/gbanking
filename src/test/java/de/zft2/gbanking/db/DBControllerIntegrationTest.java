package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;

/**
 * Integration tests for DBController.
 *
 * - The database file is created once for the entire test run. - Before each
 * test, all tables are cleared. - After all tests, the database file is
 * deleted.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DBControllerIntegrationTest extends DBControllerIntegrationBaseTest {

	// ------------------------------------------------------------
	// Tests - BankAccess
	// ------------------------------------------------------------
	@Test
	void insertAndQueryBankAccess_shouldWork() {
		BankAccess ba = TestData.createSampleBankAccess("11111111");
		db.insertOrUpdate(ba);

		assertTrue(ba.getId() > 0, "BankAccess id gesetzt");

		List<BankAccess> all = db.getAll(BankAccess.class);
		assertEquals(1, all.size());
		assertEquals("11111111", all.get(0).getBlz());

		BankAccess byBlz = db.getBankAccessByBlz("11111111");
		assertNotNull(byBlz);
		assertEquals(ba.getBankName(), byBlz.getBankName());
	}

	@Test
	void insertUpdateBankAccess_shouldUpdate() {
		BankAccess ba = TestData.createSampleBankAccess("22222222");
		db.insertOrUpdate(ba);
		int idBefore = ba.getId();
		assertTrue(idBefore > 0);

		// Update Feld
		ba.setBankName("UpdatedName");
		db.insertOrUpdate(ba);

		// Nachlesen
		BankAccess loaded = db.getBankAccessById(idBefore);
		assertNotNull(loaded);
		assertEquals("UpdatedName", loaded.getBankName());
		assertEquals(idBefore, loaded.getId());
	}

	// ------------------------------------------------------------
	// Tests - BankAccount
	// ------------------------------------------------------------
	@Test
	void insertAndQueryBankAccount_shouldWork() {
		BankAccess ba = TestData.createSampleBankAccess("33333333");
		db.insertOrUpdate(ba);

		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		assertTrue(acc.getId() > 0);

		List<BankAccount> accounts = db.getAll(BankAccount.class);
		assertEquals(1, accounts.size());
		assertEquals(acc.getAccountName(), accounts.get(0).getAccountName());
	}

	@Test
	void insertAccountWithoutBankAccess_shouldSucceedOrSetNullBankAccess() {
		// BankAccount.bankAccess_id is nullable (schema: ON DELETE SET NULL) —
		// test that insert works even when bankAccessId is null.
		BankAccount acc = TestData.createSampleAccount(null);
		// ensure no NPE: set updatedAt inherited via Dao if required
		acc.setUpdatedAt(LocalDate.now());
		db.insertOrUpdate(acc);

		List<BankAccount> accounts = db.getAll(BankAccount.class);
		assertEquals(1, accounts.size());
	}

	// ------------------------------------------------------------
	// Tests - Booking, recipients/categories updates
	// ------------------------------------------------------------
	@Test
	void insertBooking_and_updateBookingWithRecipientAndCategory_shouldWork() {
		// Erzeuge BankAccess + Account
		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		// Erzeuge Booking(s) über insertOrUpdate
		Booking booking = TestData.createSampleBooking(acc.getId());
		db.insertOrUpdate(booking);

		assertTrue(booking.getId() > 0);

		// Insert a Recipient and a Category
		Recipient rec = new Recipient();
		rec.setName("Vermieter");
		rec.setIban("DE22222222222222222222");
		rec.setSource(Source.IMPORT_INITIAL);
		db.insertOrUpdate(rec);

		Category cat = new Category("Miete", null);
		// Category insert uses insertOrUpdate path -> getPreparedStatement handles it
		db.insertOrUpdate(cat);

		// Map Recipient -> BookingIds
		Map<Recipient, Set<Integer>> rmap = new HashMap<>();
		rmap.put(rec, Collections.singleton(booking.getId()));
		boolean rres = db.updateBookingsWithRecipients(rmap);
		assertTrue(rres, "updateBookingsWithRecipients sollte true zurückgeben");

		// Map Category -> BookingIds (verwende dieselbe Booking)
		Map<Category, Set<Integer>> cmap = new HashMap<>();
		cmap.put(cat, Collections.singleton(booking.getId()));
		boolean cres = db.updateBookingsWithCategories(cmap);
		assertTrue(cres, "updateBookingsWithCategories sollte true zurückgeben");

		// Nun Abfrage mit full flag true, dann erwartet Booking.recipient und
		// Booking.category gefüllt
		List<Booking> bookingsFull = db.getAllByParentFull(Booking.class, acc.getId());
		assertEquals(1, bookingsFull.size());
		Booking bf = bookingsFull.get(0);
		assertNotNull(bf.getRecipient(), "Recipient sollte im full-Query vorhanden sein");
		assertEquals("Vermieter", bf.getRecipient().getName());
		assertNotNull(bf.getCategory());
		assertEquals("Miete", bf.getCategory().getFullName());
	}

	@Test
	void getAccountLastBookingDate_shouldReturnLatestDate() {
		BankAccess ba = TestData.createSampleBankAccess("55555555");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		LocalDate d1 = LocalDate.of(2020, 1, 1);
		LocalDate d2 = LocalDate.of(2020, 2, 2);

		Booking b1 = new Booking();
		b1.setAccountId(acc.getId());
		b1.setDateBooking(d1);
		b1.setDateValue(d1);
		b1.setPurpose("p1");
		b1.setAmount(new BigDecimal("10.00"));
		b1.setCurrency("EUR");
		b1.setSource(Source.ONLINE);
		db.insertOrUpdate(b1);

		Booking b2 = new Booking();
		b2.setAccountId(acc.getId());
		b2.setDateBooking(d2);
		b2.setDateValue(d2);
		b2.setPurpose("p2");
		b2.setAmount(new BigDecimal("20.00"));
		b2.setCurrency("EUR");
		b2.setSource(Source.ONLINE);
		db.insertOrUpdate(b2);

		LocalDate last = db.getSingleResultField(acc, StatementsConfig.StatementType.SELECT_ACCOUNT_LAST_BOOKING_DATE, LocalDate.class);
		assertNotNull(last);
		assertEquals(d2, last);
	}

	// ------------------------------------------------------------
	// Tests - BusinessCase
	// ------------------------------------------------------------
	@Test
	void insertBusinessCases_fromAccount_shouldInsertMissingAndLink() {
		BankAccess ba = TestData.createSampleBankAccess("66666666");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());

		BusinessCase bc1 = new BusinessCase();
		bc1.setCaseValue("ZahlungSEPA");
		BusinessCase bc2 = new BusinessCase();
		bc2.setCaseValue("Dauerauftrag");

		acc.setAllowedBusinessCases(Arrays.asList(bc1, bc2));
		db.insertOrUpdate(acc); // ensure account has id (getPreparedStatement will insert it)

		// Aufruf der Logik: fügt BusinessCases falls nötig und verknüpft account <-> bc
		boolean res = db.insertBusinessCases(acc);
		assertTrue(res);

		List<BusinessCase> all = db.getAll(BusinessCase.class);
		// mind. 2 Einträge erwartet (evtl. bereits vorhandene -> assert >= 2)
		assertTrue(all.size() >= 2, "mind. 2 BusinessCases erwartet");
		List<String> values = all.stream().map(BusinessCase::getCaseValue).toList();
		assertTrue(values.contains("ZahlungSEPA"));
		assertTrue(values.contains("Dauerauftrag"));
		
		acc = db.getByIdFull(BankAccount.class, acc.getId());
		
		List<BusinessCase> allByAcc = acc.getAllowedBusinessCases();
		assertNotNull(allByAcc, "account must have business cases!");
		values = allByAcc.stream().map(BusinessCase::getCaseValue).toList();
		assertTrue(values.contains("ZahlungSEPA"));
		assertTrue(values.contains("Dauerauftrag"));
	}

	// ------------------------------------------------------------
	// Tests - MoneyTransfer
	// ------------------------------------------------------------
	@Test
	void insertAndQueryMoneyTransfer_shouldWork() {
		BankAccess ba = TestData.createSampleBankAccess("77777777");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		Recipient rec = new Recipient();
		rec.setName("Payee");
		rec.setIban("DE77777777777777777777");
		rec.setSource(Source.IMPORT_INITIAL);
		db.insertOrUpdate(rec);

		MoneyTransfer mt = new MoneyTransfer();
		mt.setAccountId(acc.getId());
		mt.setOrderType(OrderType.TRANSFER);
		mt.setRecipientId(rec.getId());
		mt.setPurpose("Rent");
		mt.setAmount(new BigDecimal("750.00"));
		mt.setExecutionDate(LocalDate.now());
		mt.setExecutionDay(15);
		mt.setStandingorderMode(StandingorderMode.MONTHLY);
		mt.setMoneytransferStatus(MoneyTransferStatus.NEW);
		db.insertOrUpdate(mt);

		List<MoneyTransfer> transfers = db.getAllByParent(MoneyTransfer.class, acc.getId());
		assertEquals(1, transfers.size());
		assertEquals("Rent", transfers.get(0).getPurpose());
		assertEquals(Integer.valueOf(15), transfers.get(0).getExecutionDay());
		assertEquals(StandingorderMode.MONTHLY, transfers.get(0).getStandingorderMode());
	}

	// ------------------------------------------------------------
	// Tests - deleteBankAccess
	// ------------------------------------------------------------
	@Test
	void deleteBankAccess_shouldRemove() {
		BankAccess ba = TestData.createSampleBankAccess("99999999");
		db.insertOrUpdate(ba);
		assertFalse(db.getAll(BankAccess.class).isEmpty());

		boolean deleted = db.delete(ba, StatementsConfig.StatementType.DELETE_BANKACCESS_BY_BLZ);
		assertTrue(deleted);

		List<BankAccess> left = db.getAll(BankAccess.class);
		assertTrue(left.isEmpty());
	}
}
