package de.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.Source;

class DBControllerRecipientTest extends DBControllerIntegrationBaseTest {

	// ------------------------------------------------------------
	// Tests - Recipient
	// ------------------------------------------------------------
	@Test
	void insertAndQueryRecipient_shouldWork() {
		Recipient r = TestData.createSampleRecipient01();
		db.insertOrUpdate(r);

		assertTrue(r.getId() > 0);

		List<Recipient> recipients = db.getAll(Recipient.class);
		assertEquals(1, recipients.size());
		assertEquals("Erika Mustermann", recipients.get(0).getName());

		//Recipient byId = db.getRecipientById(r.getId());
		Recipient byId = db.getByIdFull(Recipient.class, r.getId());
		assertNotNull(byId);
		assertEquals(r.getIban(), byId.getIban());
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
		
		Boolean isEditable = db.getSingleResultField(rp, StatementsConfig.StatementType.SELECT_SPECIFIC_EDITABLE, Boolean.class);
		assertTrue(isEditable);
	}
	
	@Test
	void recipient_editable_shouldReturnTFalse() {
		Recipient rp = TestData.createSamplerecipient02();
		rp.setNote("Lieblings-Empfänger");
		rp = db.insertOrUpdate(rp);
		BankAccount acc = TestData.createSampleAccount(null);
		db.insertOrUpdate(acc);
		MoneyTransfer mt = TestData.createSampleMoneytransfer01(acc.getId());
		mt.setRecipientId(rp.getId());
		db.insertOrUpdate(mt);
		
		Boolean isEditable = db.getSingleResultField(rp, StatementsConfig.StatementType.SELECT_SPECIFIC_EDITABLE, Boolean.class);
		assertFalse(isEditable);
	}

	@Test
	void insertNullRecipient_shouldFail() {
		Recipient r = new Recipient();
		r.setSource(Source.MANUELL);
		db.insertOrUpdate(r);

		assertEquals(0, r.getId());

		List<Recipient> recipients = db.getAll(Recipient.class);
		assertEquals(0, recipients.size());
	}

}