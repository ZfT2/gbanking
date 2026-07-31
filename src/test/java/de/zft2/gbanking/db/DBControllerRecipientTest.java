package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.cache.InstituteLookupCache;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;

class DBControllerRecipientTest extends DBControllerIntegrationBaseTest {

	@BeforeEach
	void clearInstituteCacheBeforeTest() {
		InstituteLookupCache.clear();
	}

	@AfterEach
	void clearInstituteCacheAfterTest() {
		InstituteLookupCache.clear();
	}

	// ------------------------------------------------------------
	// Tests - Recipient
	// ------------------------------------------------------------
	@Test
	void insertAndQueryRecipient_shouldWork() {
		Recipient r = TestData.createSampleRecipient01();
		r.setDefault(true);
		db.insertOrUpdate(r);

		assertTrue(r.getId() > 0);

		List<Recipient> recipients = db.getAll(Recipient.class);
		assertEquals(1, recipients.size());
		assertEquals("Erika Mustermann", recipients.get(0).getName());

		Recipient byId = db.getByIdFull(Recipient.class, r.getId());
		assertNotNull(byId);
		assertEquals(r.getIban(), byId.getIban());
		assertTrue(byId.isDefault());
	}

	@Test
	void recipientsShouldBeSortedByNameWithUnnamedRecipientsLast() {
		db.insertOrUpdate(createRecipient("Beta", "DE00000000000000000002"));
		db.insertOrUpdate(createRecipient(null, "DE00000000000000000003"));
		db.insertOrUpdate(createRecipient("Alpha", "DE00000000000000000001"));

		List<String> recipientNames = db.getAll(Recipient.class).stream().map(Recipient::getName).toList();

		assertEquals(Arrays.asList("Alpha", "Beta", null), recipientNames);
	}

	@Test
	void orderedMoneyTransferRecipientsShouldBeSortedByUsageThenName() {
		BankAccount account = db.insertOrUpdate(TestData.createSampleAccount(null));
		Recipient mostUsedRecipient = db.insertOrUpdate(createRecipient("Charlie", "DE00000000000000000003"));
		Recipient firstSingleUseRecipient = db.insertOrUpdate(createRecipient("Alpha", "DE00000000000000000001"));
		Recipient secondSingleUseRecipient = db.insertOrUpdate(createRecipient("Beta", "DE00000000000000000002"));
		Recipient unusedRecipient = db.insertOrUpdate(createRecipient("Delta", "DE00000000000000000004"));

		insertMoneyTransfers(account, mostUsedRecipient, 2);
		insertMoneyTransfers(account, secondSingleUseRecipient, 1);
		insertMoneyTransfers(account, firstSingleUseRecipient, 1);

		List<String> recipientNames = db.getAll(Recipient.class, "SQL_SELECT_ALL_RECIPIENTS_ORDERED_MT").stream()
				.map(Recipient::getName)
				.toList();

		assertEquals(List.of(mostUsedRecipient.getName(), firstSingleUseRecipient.getName(), secondSingleUseRecipient.getName(),
				unusedRecipient.getName()), recipientNames);
	}

	@Test
	void recipientSchema_shouldContainDefaultFlagBeforeUpdatedAt() throws SQLException {

		int defaultIndex = -1;
		int updatedAtIndex = -1;
		int defaultNotNull = 0;
		int columnIndex = 0;

		try (Statement statement = DBController.getConnection().createStatement();
				ResultSet resultSet = statement.executeQuery("PRAGMA table_info(recipient)")) {
			while (resultSet.next()) {
				String columnName = resultSet.getString("name");
				if ("isDefault".equals(columnName)) {
					defaultIndex = columnIndex;
					defaultNotNull = resultSet.getInt("notnull");
				} else if ("updatedAt".equals(columnName)) {
					updatedAtIndex = columnIndex;
				}
				columnIndex++;
			}
		}

		assertTrue(defaultIndex >= 0);
		assertTrue(updatedAtIndex >= 0);
		assertEquals(1, defaultNotNull);
		assertEquals(updatedAtIndex - 1, defaultIndex);
	}

	@Test
	void duplicateRecipientInsert_shouldUpdateNotCreateNew() {
		Recipient r1 = TestData.createSamplerecipient02();
		db.insertOrUpdate(r1);
		int firstId = r1.getId();
		assertTrue(firstId > 0);

		Recipient r2 = TestData.createSampleRecipient03();
		db.insertOrUpdate(r2);

		List<Recipient> recipients = db.getAll(Recipient.class);
		assertEquals(1, recipients.size(),
				"Es darf nur ein Empfänger mit gleicher IBAN existieren, sonlange noch nicht in Aufträgen oder Umsätzen referenziert.");
		assertEquals("DupUpdated", recipients.get(0).getName());
	}

	@Test
	void referencedCaseVariant_shouldReuseRecipientAndPreferReadableSpelling() {
		Recipient upperCaseRecipient = new Recipient("MAX MUSTERMANN", "DE1234567890", "BANKDEFFXXX", "67890", "12020010", "A-BANK BERLIN",
				Source.IMPORT);
		db.insertOrUpdate(upperCaseRecipient);
		referenceRecipient(upperCaseRecipient);

		Recipient readableRecipient = new Recipient("Max Mustermann", "de1234567890", "bankdeffxxx", "67890", "12020010", "A-Bank Berlin",
				Source.ONLINE);
		Recipient resolvedRecipient = db.resolveRecipient(readableRecipient);

		assertEquals(upperCaseRecipient.getId(), resolvedRecipient.getId());
		assertEquals(1, db.getAll(Recipient.class).size());
		assertEquals("Max Mustermann", db.getByIdFull(Recipient.class, upperCaseRecipient.getId()).getName());
		assertEquals("A-Bank Berlin", db.getByIdFull(Recipient.class, upperCaseRecipient.getId()).getBank());
	}

	@Test
	void referencedRecipientWithDifferentBank_shouldNotBeReused() {
		Recipient bankBranchRecipient = new Recipient("Max Mustermann", "DE1234567890", "BANKDEFFXXX", "67890", "12020010", "A-Bank Berlin",
				Source.IMPORT);
		db.insertOrUpdate(bankBranchRecipient);
		referenceRecipient(bankBranchRecipient);

		Recipient shortenedBankRecipient = new Recipient("Max Mustermann", "DE1234567890", "BANKDEFFXXX", "67890", "12020010", "A-Bank",
				Source.ONLINE);
		Recipient resolvedRecipient = db.resolveRecipient(shortenedBankRecipient);

		assertNotEquals(bankBranchRecipient.getId(), resolvedRecipient.getId());
		assertEquals(2, db.getAll(Recipient.class).size());
	}

	@Test
	void importedRecipientWithoutBank_shouldReuseOtherwiseIdenticalReferencedRecipient() {
		Recipient existingRecipient = new Recipient("Max Mustermann", "DE1234567890", "BANKDEFFXXX", "67890", "12020010",
				"Existing Bank", Source.IMPORT);
		db.insertOrUpdate(existingRecipient);
		referenceRecipient(existingRecipient);

		Recipient importedRecipient = new Recipient("Max Mustermann", "de1234567890", "bankdeffxxx", "67890", "12020010", "   ",
				Source.IMPORT);
		Recipient resolvedRecipient = db.resolveRecipient(importedRecipient);

		assertEquals(existingRecipient.getId(), resolvedRecipient.getId());
		assertEquals("Existing Bank", resolvedRecipient.getBank());
		assertEquals(1, db.getAll(Recipient.class).size());
	}

	@Test
	void importedBankName_shouldEnrichOtherwiseIdenticalReferencedRecipient() {
		Recipient existingRecipient = new Recipient("Max Mustermann", "DE1234567890", "BANKDEFFXXX", "67890", "12020010", null,
				Source.ONLINE);
		db.insertOrUpdate(existingRecipient);
		referenceRecipientWithBooking(existingRecipient);

		Recipient importedRecipient = new Recipient("Max Mustermann", "de1234567890", "bankdeffxxx", "67890", "12020010",
				"A-Bank Berlin", Source.IMPORT);
		Recipient resolvedRecipient = db.resolveRecipient(importedRecipient);

		assertEquals(existingRecipient.getId(), resolvedRecipient.getId());
		assertEquals("A-Bank Berlin", db.getByIdFull(Recipient.class, existingRecipient.getId()).getBank());
		assertEquals(1, db.getAll(Recipient.class).size());
	}

	@Test
	void importedRecipientWithoutBank_shouldResolveBankFromGermanIban() {
		insertInstitute("50010517", "Lookup Bank", "LOOKDEFFXXX");
		Recipient importedRecipient = new Recipient("Max Mustermann", "DE44500105175407324931", null, null, null, null, Source.IMPORT);

		Recipient resolvedRecipient = db.resolveRecipient(importedRecipient);

		assertEquals("Lookup Bank", resolvedRecipient.getBank());
		assertEquals("Lookup Bank", db.getByIdFull(Recipient.class, resolvedRecipient.getId()).getBank());
	}

	@Test
	void importedRecipientsWithDifferentKnownBanks_shouldRemainDistinct() {
		Recipient existingRecipient = new Recipient("Max Mustermann", "DE1234567890", "BANKDEFFXXX", "67890", "12020010", "Bank A",
				Source.IMPORT);
		db.insertOrUpdate(existingRecipient);
		referenceRecipient(existingRecipient);

		Recipient incomingRecipient = new Recipient("Max Mustermann", "DE1234567890", "BANKDEFFXXX", "67890", "12020010", "Bank B",
				Source.IMPORT);
		Recipient resolvedRecipient = db.resolveRecipient(incomingRecipient);

		assertNotEquals(existingRecipient.getId(), resolvedRecipient.getId());
		assertEquals(2, db.getAll(Recipient.class).size());
	}

	@Test
	void duplicateRecipientWithAccountNumberOnly_shouldReuseUnreferencedRecipient() {
		Recipient existingRecipient = new Recipient("Account Number Old", null, null, "99112", "40040000", "Lookup Bank", Source.IMPORT);
		db.insertOrUpdate(existingRecipient);

		Recipient incomingRecipient = new Recipient("Account Number New", null, null, "99112", "40040000", "Lookup Bank", Source.ONLINE);
		Recipient resolvedRecipient = db.resolveRecipient(incomingRecipient);

		assertEquals(existingRecipient.getId(), resolvedRecipient.getId());
		assertEquals(1, db.getAll(Recipient.class).size());
		assertEquals("Account Number New", db.getByIdFull(Recipient.class, existingRecipient.getId()).getName());
	}

	@Test
	void importedRecipientWithoutBank_shouldNotReuseBankFromSameAccountNumberAtDifferentInstitute() {
		Recipient existingRecipient = new Recipient("Existing Recipient", null, "WRNGDEFFXXX", "99112", "40040000", "Wrong Bank",
				Source.IMPORT);
		db.insertOrUpdate(existingRecipient);
		insertInstitute("50050000", "Correct Bank", "CORRDEFFXXX");

		Recipient incomingRecipient = new Recipient("Incoming Recipient", null, "CORRDEFFXXX", "99112", "50050000", null,
				Source.IMPORT);
		Recipient resolvedRecipient = db.resolveRecipient(incomingRecipient);

		assertEquals("Correct Bank", resolvedRecipient.getBank());
		assertNotEquals(existingRecipient.getId(), resolvedRecipient.getId());
		assertEquals(2, db.getAll(Recipient.class).size());
	}

	@Test
	void fullRecipientList_shouldIncludeAccountNumberOnlyRecipients() {
		Recipient recipient = new Recipient("Account Number Only", null, null, "99112", "40040000", "Lookup Bank", Source.MANUELL);
		db.insertOrUpdate(recipient);

		List<Recipient> recipients = db.getAllFull(Recipient.class);

		assertEquals(1, recipients.size());
		assertEquals("99112", recipients.get(0).getAccountNumber());
	}

	@Test
	void recipientWithoutAccountIdentifier_shouldUseUnicodeMatchingFallback() {
		Recipient upperCaseRecipient = new Recipient("MÜLLER GMBH", null, "TESTDEFFXXX", null, null, "MUSTERBANK", Source.IMPORT);
		db.insertOrUpdate(upperCaseRecipient);
		referenceRecipient(upperCaseRecipient);

		Recipient readableRecipient = new Recipient("Müller GmbH", null, "testdeffxxx", null, null, "Musterbank", Source.ONLINE);
		Recipient resolvedRecipient = db.resolveRecipient(readableRecipient);

		assertEquals(upperCaseRecipient.getId(), resolvedRecipient.getId());
		assertEquals(1, db.getAll(Recipient.class).size());
		assertEquals("Müller GmbH", db.getByIdFull(Recipient.class, upperCaseRecipient.getId()).getName());
	}

	@Test
	void referencedRecipient_shouldUpdateNote() {
		
		Recipient rp = TestData.createSamplerecipient02();
		rp.setNote("Lieblings-Empfänger");
		rp = db.insertOrUpdate(rp);
		BankAccount acc = TestData.createSampleAccount(null);
		db.insertOrUpdate(acc);
		MoneyTransfer mt = TestData.createSampleMoneytransfer01(acc.getId());
		mt.setRecipientId(rp.getId());
		db.insertOrUpdate(mt);
		
		Recipient rpDb = db.getById(Recipient.class, rp.getId());
		assertEquals("Lieblings-Empfänger", rpDb.getNote());
		
		rpDb.setNote("nicht mehr verwenden");
		db.insertOrUpdate(rpDb);
		
		assertEquals(rp.getId(), rpDb.getId());
		assertEquals("nicht mehr verwenden", rpDb.getNote());
		
	}

	@Test
	void referencedRecipient_shouldKeepIdentityFieldsImmutableButAllowNameAndBank() throws SQLException {
		Recipient recipient = new Recipient("MAX MUSTERMANN", "DE1234567890", "BANKDEFFXXX", "67890", "12020010", "A-BANK BERLIN",
				Source.ONLINE);
		recipient = db.insertOrUpdate(recipient);
		referenceRecipientWithBooking(recipient);

		updateRecipientNameAndBank(recipient.getId(), "Max Mustermann", "A-Bank Berlin");
		Recipient readableRecipient = db.getByIdFull(Recipient.class, recipient.getId());
		assertEquals("Max Mustermann", readableRecipient.getName());
		assertEquals("A-Bank Berlin", readableRecipient.getBank());

		int recipientId = recipient.getId();
		assertThrows(SQLException.class, () -> updateRecipientNameAndBank(recipientId, "Erika Musterfrau", "A-Bank Berlin"));
		assertThrows(SQLException.class, () -> updateRecipientNameAndBank(recipientId, "Max Mustermann", "Andere Bank"));
		assertThrows(SQLException.class, () -> updateRecipientIban(recipientId, "DE0000000000"));
		assertEquals("DE1234567890", db.getByIdFull(Recipient.class, recipientId).getIban());
	}

	@Test
	void recipientUsedOnlyByManualBooking_shouldRemainEditableButNotDeletable() {
		Recipient recipient = db.insertOrUpdate(createRecipient("Alter Name", "DE1234567890"));
		Booking manualBooking = referenceRecipientWithBooking(recipient, Source.MANUELL);

		assertTrue(db.isRecipientEditable(recipient));
		assertFalse(db.isRecipientDeletable(recipient));

		Recipient changedRecipient = createRecipient("Neuer Name", "DE0000000000");
		Recipient resolvedRecipient = db.resolveRecipientForManualBooking(manualBooking, changedRecipient);

		assertEquals(recipient.getId(), resolvedRecipient.getId());
		assertEquals("Neuer Name", db.getByIdFull(Recipient.class, recipient.getId()).getName());
		assertEquals(recipient.getId(), db.getById(Booking.class, manualBooking.getId()).getRecipientId());
	}

	@Test
	void changedProtectedRecipient_shouldBeCopiedAndReassignedOnlyToManualBooking() {
		Recipient recipient = db.insertOrUpdate(createRecipient("Historischer Name", "DE1234567890"));
		Booking onlineBooking = referenceRecipientWithBooking(recipient, Source.ONLINE);
		Booking manualBooking = referenceRecipientWithBooking(recipient, Source.MANUELL);
		Recipient changedRecipient = createRecipient("Geänderter Name", "DE1234567890");

		assertFalse(db.isRecipientEditable(recipient));
		Recipient resolvedRecipient = db.resolveRecipientForManualBooking(manualBooking, changedRecipient);
		manualBooking.setRecipientId(resolvedRecipient.getId());
		db.insertOrUpdate(manualBooking);

		assertNotEquals(recipient.getId(), resolvedRecipient.getId());
		assertEquals(recipient.getId(), db.getById(Booking.class, onlineBooking.getId()).getRecipientId());
		assertEquals(resolvedRecipient.getId(), db.getById(Booking.class, manualBooking.getId()).getRecipientId());
		assertEquals(2, db.getAll(Recipient.class).size());
	}

	@Test
	void importedBooking_shouldRejectRecipientReassignment() {
		Recipient originalRecipient = db.insertOrUpdate(createRecipient("Original", "DE1234567890"));
		Recipient replacementRecipient = db.insertOrUpdate(createRecipient("Ersatz", "DE0000000000"));
		Booking importedBooking = referenceRecipientWithBooking(originalRecipient, Source.IMPORT);

		assertFalse(db.isRecipientEditable(originalRecipient));
		importedBooking.setRecipientId(replacementRecipient.getId());

		assertThrows(GBankingException.class, () -> db.insertOrUpdate(importedBooking));
		assertEquals(originalRecipient.getId(), db.getById(Booking.class, importedBooking.getId()).getRecipientId());
	}
	
	@Test
	void referencedRecipient_shouldNotUpdateName() {
		
		Recipient rp = TestData.createSamplerecipient02();
		rp = db.insertOrUpdate(rp);
		BankAccount acc = TestData.createSampleAccount(null);
		db.insertOrUpdate(acc);
		MoneyTransfer mt = TestData.createSampleMoneytransfer01(acc.getId());
		mt.setRecipientId(rp.getId());
		db.insertOrUpdate(mt);
		
		Recipient rpDb = db.getById(Recipient.class, rp.getId());
		
		rpDb.setName("Dup 22");
		Recipient rpDbNew = db.insertOrUpdate(rpDb);
		
		assertNotEquals(rp.getId(), rpDb.getId());
		assertEquals("Dup 22", rpDbNew.getName());
		
	}
	
	@Test
	void findRecipient_Existing_shouldReturn() {
		Recipient probe = new Recipient();
		probe.setName("Max Mustermann");
		probe.setIban("DE00001234");
		probe.setSource(Source.IMPORT);
		db.insertOrUpdate(probe);
		
		Recipient found = db.find(Recipient.class, probe);
		assertNotNull(found);
		
		assertEquals("Max Mustermann", found.getName());
		assertEquals("DE00001234", found.getIban());
		assertEquals(Source.IMPORT, found.getSource());
	}

	@Test
	void findRecipient_nonExisting_shouldReturnNull() {
		Recipient probe = new Recipient();
		probe.setName("Nobody");
		probe.setIban("DE0000%");
		Recipient found = db.find(Recipient.class, probe);
		assertNull(found);
	}

	@Test
	void recipient_editable_shouldReturnTrue() {
		Recipient rp = TestData.createSamplerecipient02();
		rp.setNote("Lieblings-Empfänger");
		rp = db.insertOrUpdate(rp);
		
		assertTrue(db.isRecipientEditable(rp));
		assertTrue(db.isRecipientDeletable(rp));
	}
	
	@Test
	void recipientReferencedByMoneyTransfer_shouldNotBeEditableOrDeletable() {
		Recipient rp = TestData.createSamplerecipient02();
		rp.setNote("Lieblings-Empfänger");
		rp = db.insertOrUpdate(rp);
		BankAccount acc = TestData.createSampleAccount(null);
		db.insertOrUpdate(acc);
		MoneyTransfer mt = TestData.createSampleMoneytransfer01(acc.getId());
		mt.setRecipientId(rp.getId());
		db.insertOrUpdate(mt);
		
		assertFalse(db.isRecipientEditable(rp));
		assertFalse(db.isRecipientDeletable(rp));
	}

	@Test
	void insertNullRecipient_shouldFail() {
		Recipient r = new Recipient();
		r.setSource(Source.MANUELL);

		assertThrows(GBankingException.class, () -> db.insertOrUpdate(r));

		assertEquals(0, r.getId());

		List<Recipient> recipients = db.getAll(Recipient.class);
		assertEquals(0, recipients.size());
	}

	private Recipient createRecipient(String name, String iban) {
		Recipient recipient = new Recipient();
		recipient.setName(name);
		recipient.setIban(iban);
		recipient.setSource(Source.MANUELL);
		return recipient;
	}

	private void insertMoneyTransfers(BankAccount account, Recipient recipient, int count) {
		for (int i = 0; i < count; i++) {
			MoneyTransfer moneyTransfer = TestData.createSampleMoneytransfer01(account.getId());
			moneyTransfer.setRecipientId(recipient.getId());
			moneyTransfer.setPurpose("Sortierung " + i);
			db.insertOrUpdate(moneyTransfer);
		}
	}

	private void referenceRecipient(Recipient recipient) {
		BankAccount acc = TestData.createSampleAccount(null);
		db.insertOrUpdate(acc);
		MoneyTransfer mt = TestData.createSampleMoneytransfer01(acc.getId());
		mt.setRecipientId(recipient.getId());
		db.insertOrUpdate(mt);
	}

	private Booking referenceRecipientWithBooking(Recipient recipient) {
		return referenceRecipientWithBooking(recipient, Source.ONLINE);
	}

	private Booking referenceRecipientWithBooking(Recipient recipient, Source source) {
		BankAccount acc = TestData.createSampleAccount(null);
		db.insertOrUpdate(acc);
		Booking booking = TestData.createSampleBookingWithRecipient(acc.getId(), recipient.getId());
		booking.setSource(source);
		booking.setRecipient(recipient);
		return db.insertOrUpdate(booking);
	}

	private void insertInstitute(String blz, String bankName, String bic) {
		int importHistoryId = db.insertOrUpdate(new ImportHistory("recipient-bank-lookup.csv")).getId();
		Institute institute = new Institute();
		institute.setBlz(blz);
		institute.setBankName(bankName);
		institute.setBic(bic);
		institute.setImportNumber(1);
		institute.setLastChanged(LocalDate.of(2026, Month.APRIL, 10));
		institute.setImportFile(importHistoryId);
		institute.setStateType(InstituteStatus.ACTIVE);
		db.insertOrUpdate(institute);
	}

	private void updateRecipientNameAndBank(int recipientId, String name, String bank) throws SQLException {
		try (PreparedStatement ps = DBController.getConnection().prepareStatement("UPDATE recipient SET name = ?, bank = ? WHERE id = ?")) {
			ps.setString(1, name);
			ps.setString(2, bank);
			ps.setInt(3, recipientId);
			ps.executeUpdate();
		}
	}

	private void updateRecipientIban(int recipientId, String iban) throws SQLException {
		try (PreparedStatement ps = DBController.getConnection().prepareStatement("UPDATE recipient SET iban = ? WHERE id = ?")) {
			ps.setString(1, iban);
			ps.setInt(2, recipientId);
			ps.executeUpdate();
		}
	}

}
