package de.zft2.gbanking.service.bankaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kapott.hbci.GV_Result.GVRInfoList;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.status.HBCIExecStatus;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.TestData;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankMessage;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.logging.GBankingLoggingHandler;
import de.zft2.gbanking.service.HbciSessionRunner;

class BankMessageServiceTest {

	@TempDir
	Path tempDir;

	@AfterEach
	void closeDatabaseConnection() {
		DBController.resetConnection();
	}

	@Test
	void saveBankMessagesShouldUpdateExistingMessageWithSameMessageKey() {
		DBController dbController = DBController.getInstance(tempDir.resolve("db").toString());
		BankAccess bankAccess = dbController.insertOrUpdate(TestData.createSampleBankAccess("34567890"));
		BankMessageService service = new BankMessageService(new BankAccessService(), GBankingLoggingHandler.getInstance());
		GVRInfoList.Info info = createInfo("MSG001", "Important bank message");

		service.saveBankMessages(bankAccess, List.of(info), Map.of("MSG001", "First text"),
				LocalDateTime.of(2026, Month.JULY, 16, 10, 0));
		service.saveBankMessages(bankAccess, List.of(info), Map.of("MSG001", "Updated text"),
				LocalDateTime.of(2026, Month.JULY, 16, 11, 0));

		List<BankMessage> messages = dbController.getAllByParentFull(BankMessage.class, bankAccess.getId());
		assertEquals(1, messages.size());
		assertEquals("Updated text", messages.get(0).getMessage());
		assertEquals(LocalDateTime.of(2026, Month.JULY, 16, 11, 0), messages.get(0).getRetrievedAt());
		assertFalse(messages.get(0).getMessageKey().isBlank());
	}

	@Test
	void saveBankMessagesShouldReturnDuplicateOverviewEntriesOnlyOnce() {
		DBController dbController = DBController.getInstance(tempDir.resolve("db").toString());
		BankAccess bankAccess = dbController.insertOrUpdate(TestData.createSampleBankAccess("45678901"));
		BankMessageService service = new BankMessageService(new BankAccessService(), GBankingLoggingHandler.getInstance());
		GVRInfoList.Info info = createInfo("MSG002", "Duplicate bank message");

		List<BankMessage> savedMessages = service.saveBankMessages(bankAccess, List.of(info, info), Map.of("MSG002", "Text"),
				LocalDateTime.of(2026, Month.JULY, 16, 12, 0));

		List<BankMessage> messages = dbController.getAllByParentFull(BankMessage.class, bankAccess.getId());
		assertEquals(1, savedMessages.size());
		assertEquals(1, messages.size());
	}

	@Test
	void saveInstitutionMessagesShouldPersistSubjectAndDeduplicateRepeatedMessages() {
		DBController dbController = DBController.getInstance(tempDir.resolve("db").toString());
		BankAccess bankAccess = dbController.insertOrUpdate(TestData.createSampleBankAccess("56789012"));
		String messageText = "Wichtiger Hinweis: Wartungsarbeiten am Sonntag";

		BankMessageService.saveInstitutionMessages(bankAccess, List.of(messageText),
				LocalDateTime.of(2026, Month.JULY, 16, 13, 0));
		BankMessageService.saveInstitutionMessages(bankAccess, List.of(messageText),
				LocalDateTime.of(2026, Month.JULY, 16, 14, 0));

		List<BankMessage> messages = dbController.getAllByParentFull(BankMessage.class, bankAccess.getId());
		assertEquals(1, messages.size());
		assertEquals("Wichtiger Hinweis", messages.get(0).getDescription());
		assertEquals("Wartungsarbeiten am Sonntag", messages.get(0).getMessage());
		assertEquals("F", messages.get(0).getType());
		assertEquals("TXT", messages.get(0).getFormat());
		assertEquals(LocalDateTime.of(2026, Month.JULY, 16, 14, 0), messages.get(0).getRetrievedAt());
	}

	@Test
	void retrieveInstitutionMessagesShouldExecuteEmptyDialog() {
		BankMessageService service = new BankMessageService(new BankAccessService(), GBankingLoggingHandler.getInstance());
		HBCIHandler handler = mock(HBCIHandler.class);
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		GBankingHBCICallback callback = mock(GBankingHBCICallback.class);
		when(handler.execute()).thenReturn(status);
		when(status.isOK()).thenReturn(true);
		when(callback.drainInstitutionMessages()).thenReturn(List.of());

		BankMessageRetrievalResult result = service.retrieveInstitutionMessages(new BankAccess(),
				new HbciSessionRunner.HbciSession(callback, null, handler));

		assertTrue(result.successful());
		verify(handler).createEmptyDialog();
		verify(handler).execute();
	}

	private GVRInfoList.Info createInfo(String code, String description) {
		GVRInfoList.Info info = new GVRInfoList.Info();
		info.code = code;
		info.description = description;
		info.type = "F";
		info.format = "TXT";
		info.date = Date.valueOf(LocalDate.of(2026, Month.JULY, 1));
		info.addComment("Comment");
		return info;
	}
}
