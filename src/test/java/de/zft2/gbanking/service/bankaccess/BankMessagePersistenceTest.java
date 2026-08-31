package de.zft2.gbanking.service.bankaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankMessage;
import de.zft2.gbanking.testdata.TestDataFactory;

class BankMessagePersistenceTest {

	@TempDir
	Path tempDir;

	@AfterEach
	void closeDatabaseConnection() {
		DBController.resetConnection();
	}

	@Test
	void insertOrUpdateShouldPersistBankMessageViaDaoRegistry() {
		DBController dbController = DBController.getInstance(tempDir.resolve("db").toString());
		BankAccess bankAccess = dbController.insertOrUpdate(TestDataFactory.createSampleBankAccess("12345678"));
		BankMessage bankMessage = createBankMessage(bankAccess);

		BankMessage savedMessage = dbController.insertOrUpdate(bankMessage);
		List<BankMessage> bankMessages = dbController.getAllByParentFull(BankMessage.class, bankAccess.getId());

		assertTrue(savedMessage.getId() > 0);
		assertEquals(1, bankMessages.size());
		assertEquals("MSG001", bankMessages.get(0).getCode());
		assertEquals("Important bank message", bankMessages.get(0).getDescription());
		assertEquals("Full message text", bankMessages.get(0).getMessage());
	}

	@Test
	void insertOrUpdateShouldUpdateBankMessageViaDaoRegistry() {
		DBController dbController = DBController.getInstance(tempDir.resolve("db").toString());
		BankAccess bankAccess = dbController.insertOrUpdate(TestDataFactory.createSampleBankAccess("23456789"));
		BankMessage bankMessage = dbController.insertOrUpdate(createBankMessage(bankAccess));

		bankMessage.setMessage("Updated message text");
		dbController.insertOrUpdate(bankMessage);
		BankMessage reloadedMessage = dbController.getById(BankMessage.class, bankMessage.getId());

		assertNotNull(reloadedMessage);
		assertEquals("Updated message text", reloadedMessage.getMessage());
		assertEquals(LocalDate.of(2026, Month.JULY, 1), reloadedMessage.getVersionDate());
	}

	private BankMessage createBankMessage(BankAccess bankAccess) {
		BankMessage bankMessage = new BankMessage();
		bankMessage.setBankAccessId(bankAccess.getId());
		bankMessage.setBankName(bankAccess.getBankName());
		bankMessage.setMessageKey("test-message-key");
		bankMessage.setCode("MSG001");
		bankMessage.setType("F");
		bankMessage.setFormat("TXT");
		bankMessage.setDescription("Important bank message");
		bankMessage.setVersionDate(LocalDate.of(2026, Month.JULY, 1));
		bankMessage.setComments("Comment");
		bankMessage.setMessage("Full message text");
		bankMessage.setRetrievedAt(LocalDateTime.of(2026, Month.JULY, 16, 10, 30));
		bankMessage.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return bankMessage;
	}
}
