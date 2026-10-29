package de.zft2.gbanking.service.bankaccess;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kapott.hbci.GV.GVInfoList;
import org.kapott.hbci.GV.GVInfoOrder;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRInfoList;
import org.kapott.hbci.GV_Result.GVRInfoOrder;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.mockito.MockedConstruction;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankMessage;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.service.Service;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.ServiceStubbingUtil;
import de.zft2.gbanking.testdata.TestDataFactory;

class BankMessageServiceTest {

	private static final List<Class<? extends Service>> SERVICES_TO_STUB = List.of(BankAccessService.class);
	private static final String INFO_LIST_JOB = GVInfoList.getLowlevelName();
	private static final String INFO_DETAILS_JOB = GVInfoOrder.getLowlevelName();
	private static final String INSTITUTION_MESSAGE = "Wichtiger Hinweis: Wartungsarbeiten am Sonntag";
	private static final String PARTIAL_FAILURE = "HIRMG:2:2+9050::Teilweise fehlerhaft.";
	private static final String WRONG_PIN = "HIRMG:2:2+9942::Anmeldedaten sind ungueltig.";
	private static final LocalDateTime RETRIEVED_AT = LocalDateTime.of(2026, Month.JULY, 16, 10, 0);

	@TempDir
	Path tempDir;

	private final HBCIPassport passport = mock(HBCIPassport.class);
	private final HBCIHandler handler = mock(HBCIHandler.class);
	private final char[] pin = "1234".toCharArray();
	private DBController dbController;
	private BankAccessService hbciSupport;
	private BankMessageService service;
	private BankAccess bankAccess;

	@BeforeEach
	void setupDatabaseContext() {
		DBController.resetConnection();
		dbController = DBController.getInstance(tempDir.resolve("db").toString());
		ServiceStubbingUtil.initStubbedServicesInContext(SERVICES_TO_STUB);
		hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		service = new BankMessageService();
		bankAccess = dbController.insertOrUpdate(TestDataFactory.createSampleBankAccess("34567890"));
	}

	@AfterEach
	void closeDatabaseConnection() {
		ServiceStubbingUtil.unloadStubbedServicesInContext(SERVICES_TO_STUB);
		DBController.resetConnection();
	}

	@Test
	void saveBankMessagesShouldUpdateExistingMessageWithSameMessageKey() {
		GVRInfoList.Info info = createInfo("MSG001", "Important bank message");

		service.saveBankMessages(bankAccess, List.of(info), Map.of("MSG001", "First text"),
				LocalDateTime.of(2026, Month.JULY, 16, 10, 0));
		service.saveBankMessages(bankAccess, List.of(info), Map.of("MSG001", "Updated text"),
				LocalDateTime.of(2026, Month.JULY, 16, 11, 0));

		List<BankMessage> messages = storedMessages();
		assertEquals(1, messages.size());
		assertEquals("Updated text", messages.get(0).getMessage());
		assertEquals(LocalDateTime.of(2026, Month.JULY, 16, 11, 0), messages.get(0).getRetrievedAt());
		assertFalse(messages.get(0).getMessageKey().isBlank());
	}

	@Test
	void saveBankMessagesShouldReturnDuplicateOverviewEntriesOnlyOnce() {
		GVRInfoList.Info info = createInfo("MSG002", "Duplicate bank message");

		List<BankMessage> savedMessages = service.saveBankMessages(bankAccess, List.of(info, info), Map.of("MSG002", "Text"),
				LocalDateTime.of(2026, Month.JULY, 16, 12, 0));

		assertEquals(1, savedMessages.size());
		assertEquals(1, storedMessages().size());
	}

	@Test
	void saveInstitutionMessagesShouldPersistSubjectAndDeduplicateRepeatedMessages() {
		service.saveInstitutionMessages(bankAccess, List.of(INSTITUTION_MESSAGE),
				LocalDateTime.of(2026, Month.JULY, 16, 13, 0));
		service.saveInstitutionMessages(bankAccess, List.of(INSTITUTION_MESSAGE),
				LocalDateTime.of(2026, Month.JULY, 16, 14, 0));

		List<BankMessage> messages = storedMessages();
		assertEquals(1, messages.size());
		assertEquals("Wichtiger Hinweis", messages.get(0).getDescription());
		assertEquals("Wartungsarbeiten am Sonntag", messages.get(0).getMessage());
		assertEquals("F", messages.get(0).getType());
		assertEquals("TXT", messages.get(0).getFormat());
		assertEquals(LocalDateTime.of(2026, Month.JULY, 16, 14, 0), messages.get(0).getRetrievedAt());
	}

	@Test
	void listBankMessagesShouldReturnTheStoredMessagesOfAPersistedBankAccess() {
		service.saveInstitutionMessages(bankAccess, List.of(INSTITUTION_MESSAGE));

		assertTrue(service.listBankMessages(null).isEmpty());
		assertTrue(service.listBankMessages(new BankAccess()).isEmpty());
		assertEquals(1, service.listBankMessages(bankAccess).size());
	}

	@Test
	void saveMessagesShouldIgnoreMissingInput() {
		BankAccess unsavedAccess = new BankAccess();
		List<GVRInfoList.Info> entries = List.of(createInfo("MSG001", "Message"));
		List<String> texts = List.of(INSTITUTION_MESSAGE);

		assertTrue(service.saveBankMessages(null, entries, Map.of(), RETRIEVED_AT).isEmpty());
		assertTrue(service.saveBankMessages(unsavedAccess, entries, Map.of(), RETRIEVED_AT).isEmpty());
		assertTrue(service.saveBankMessages(bankAccess, null, Map.of(), RETRIEVED_AT).isEmpty());
		assertTrue(service.saveBankMessages(bankAccess, List.of(), Map.of(), RETRIEVED_AT).isEmpty());
		assertTrue(service.saveInstitutionMessages(null, texts, RETRIEVED_AT).isEmpty());
		assertTrue(service.saveInstitutionMessages(unsavedAccess, texts, RETRIEVED_AT).isEmpty());
		assertTrue(service.saveInstitutionMessages(bankAccess, null, RETRIEVED_AT).isEmpty());
		assertTrue(service.saveInstitutionMessages(bankAccess, List.of(), RETRIEVED_AT).isEmpty());
		assertTrue(storedMessages().isEmpty());
	}

	@Test
	void saveBankMessagesShouldSkipMissingEntriesAndJoinTheComments() {
		GVRInfoList.Info info = createInfo(" MSG001 ", " Message ");
		info.addComment(" ");
		info.addComment("Second comment");
		GVRInfoList.Info withoutComments = createInfo("MSG002", "Without comments");
		withoutComments.comment = null;

		List<BankMessage> savedMessages = service.saveBankMessages(bankAccess, Arrays.asList(null, info, withoutComments),
				null, RETRIEVED_AT);

		assertEquals(2, savedMessages.size());
		assertEquals("MSG001", savedMessages.get(0).getCode());
		assertEquals("Message", savedMessages.get(0).getDescription());
		assertEquals("Comment" + System.lineSeparator() + "Second comment", savedMessages.get(0).getComments());
		assertEquals("", savedMessages.get(0).getMessage());
		assertEquals(LocalDate.of(2026, Month.JULY, 1), savedMessages.get(0).getVersionDate());
		assertEquals("", savedMessages.get(1).getComments());
		assertEquals(2, storedMessages().size());
	}

	@ParameterizedTest
	@CsvSource({ "'Betreff: Text', Betreff, Text", "Nur Text, '', Nur Text", "': Text', '', ': Text'" })
	void saveInstitutionMessagesShouldSplitTheSubjectFromTheMessage(String text, String description, String message) {
		List<BankMessage> savedMessages = service.saveInstitutionMessages(bankAccess, Arrays.asList(" ", null, text),
				RETRIEVED_AT);

		assertEquals(1, savedMessages.size());
		assertEquals(description, savedMessages.get(0).getDescription());
		assertEquals(message, savedMessages.get(0).getMessage());
	}

	@Test
	void saveInstitutionMessagesShouldReturnNoMessagesIfTheyCannotBePersisted() {
		BankAccess missingAccess = new BankAccess();
		missingAccess.setId(4711);

		assertTrue(service.saveInstitutionMessages(missingAccess, List.of(INSTITUTION_MESSAGE), RETRIEVED_AT).isEmpty());
	}

	@Test
	void retrieveBankMessagesWithResultShouldFailAndClearThePinWithoutAStoredBankAccess() {
		BankAccess missingAccess = new BankAccess();
		missingAccess.setId(4711);

		assertEquals(BankMessageRetrievalResult.failure(), service.retrieveBankMessagesWithResult(null, pin));
		assertEquals(BankMessageRetrievalResult.failure(), service.retrieveBankMessagesWithResult(new BankAccess(), pin));
		assertEquals(BankMessageRetrievalResult.failure(), service.retrieveBankMessagesWithResult(missingAccess, pin));
		assertArrayEquals(new char[pin.length], pin);
		verifyNoInteractions(hbciSupport);
	}

	@Test
	void retrieveBankMessagesWithResultShouldReportAWrongPinException() {
		doThrow(new HBCI_Exception(WRONG_PIN)).when(hbciSupport).initBankConnection(any(BankAccess.class),
				any(GBankingHBCICallback.class));

		assertEquals(BankMessageRetrievalResult.wrongPinFailure(), retrieve());
		assertArrayEquals(new char[pin.length], pin);
	}

	@ParameterizedTest
	@CsvSource({ "true, , 1, false", "false, " + PARTIAL_FAILURE + ", 1, false", "false, " + WRONG_PIN + ", 0, true" })
	void retrieveBankMessagesWithResultShouldUseAnEmptyDialogWithoutInfoListSupport(boolean statusOk, String errorString,
			int messageCount, boolean wrongPin) {
		prepareSession();
		HBCIExecStatus status = status(statusOk, errorString);
		when(handler.execute()).thenReturn(status);

		BankMessageRetrievalResult result = retrieve(INSTITUTION_MESSAGE);

		assertEquals(statusOk, result.successful());
		assertEquals(wrongPin, result.wrongPin());
		assertEquals(messageCount, result.messages().size());
		assertEquals(1, storedMessages().size());
		verify(handler).createEmptyDialog();
		assertArrayEquals(new char[pin.length], pin);
	}

	@ParameterizedTest
	@CsvSource({ "false, " + PARTIAL_FAILURE + ", , false", "false, " + WRONG_PIN + ", , true", "true, , , false",
			"true, , false, false" })
	void retrieveBankMessagesWithResultShouldReportAFailedOverview(boolean statusOk, String errorString, Boolean resultOk,
			boolean wrongPin) {
		prepareSession(INFO_LIST_JOB);
		HBCIExecStatus status = status(statusOk, errorString);
		when(handler.execute()).thenReturn(status);
		HBCIJob<GVRInfoList> overviewJob = job(resultOk != null ? overview(resultOk) : null);
		doReturn(overviewJob).when(hbciSupport).newHbciJob(handler, INFO_LIST_JOB);

		BankMessageRetrievalResult result = retrieve(INSTITUTION_MESSAGE);

		assertFalse(result.successful());
		assertEquals(wrongPin, result.wrongPin());
		assertEquals(wrongPin ? 0 : 1, result.messages().size());
	}

	@Test
	void retrieveBankMessagesWithResultShouldAcceptAnOverviewWithoutEntries() {
		prepareSession(INFO_LIST_JOB);
		HBCIExecStatus status = status(true, null);
		when(handler.execute()).thenReturn(status);
		stubOverview((GVRInfoList.Info[]) null);

		BankMessageRetrievalResult result = retrieve();

		assertTrue(result.successful());
		assertTrue(result.messages().isEmpty());
	}

	@ParameterizedTest
	@CsvSource({ "B, true", "F, false" })
	void retrieveBankMessagesWithResultShouldSaveTheOverviewWithoutDetails(String type, boolean detailsSupported) {
		prepareSession(INFO_LIST_JOB, detailsSupported ? INFO_DETAILS_JOB : "other");
		HBCIExecStatus status = status(true, null);
		when(handler.execute()).thenReturn(status);
		GVRInfoList.Info info = createInfo("MSG001", "Message");
		info.type = type;
		stubOverview(info);

		BankMessageRetrievalResult result = retrieve();

		assertTrue(result.successful());
		assertEquals(1, result.messages().size());
		assertEquals("", result.messages().get(0).getMessage());
		verify(hbciSupport, never()).newHbciJob(handler, INFO_DETAILS_JOB);
	}

	@ParameterizedTest
	@CsvSource({ PARTIAL_FAILURE + ", false", WRONG_PIN + ", true" })
	void retrieveBankMessagesWithResultShouldReportFailedDetails(String errorString, boolean wrongPin) {
		prepareSession(INFO_LIST_JOB, INFO_DETAILS_JOB);
		HBCIExecStatus okStatus = status(true, null);
		HBCIExecStatus failedStatus = status(false, errorString);
		when(handler.execute()).thenReturn(okStatus, failedStatus);
		stubOverview(createInfo("MSG001", "Message"));
		HBCIJob<GVRInfoOrder> detailsJob = job(null);
		doReturn(detailsJob).when(hbciSupport).newHbciJob(handler, INFO_DETAILS_JOB);

		BankMessageRetrievalResult result = retrieve();

		assertFalse(result.successful());
		assertEquals(wrongPin, result.wrongPin());
		assertEquals(wrongPin ? 0 : 1, result.messages().size());
	}

	@Test
	void retrieveBankMessagesWithResultShouldSaveTheOverviewWithTheDetailsOfAllBatches() {
		prepareSession(INFO_LIST_JOB, INFO_DETAILS_JOB);
		HBCIExecStatus status = status(true, null);
		when(handler.execute()).thenReturn(status);
		List<GVRInfoList.Info> entries = new ArrayList<>();
		for (int index = 1; index <= 11; index++) {
			entries.add(createInfo(String.format("MSG%02d", index), "Message " + index));
		}
		entries.add(createInfo(" ", "Without code"));
		entries.add(null);
		stubOverview(entries.toArray(new GVRInfoList.Info[0]));
		HBCIJob<GVRInfoOrder> firstBatch = job(details(detail("MSG01", " First text "), null, detail(" ", "Ignored")));
		HBCIJob<GVRInfoOrder> secondBatch = job(null);
		doReturn(firstBatch, secondBatch).when(hbciSupport).newHbciJob(handler, INFO_DETAILS_JOB);

		BankMessageRetrievalResult result = retrieve(INSTITUTION_MESSAGE);

		assertTrue(result.successful());
		List<BankMessage> messages = result.messages();
		assertEquals(13, messages.size());
		assertEquals("Wartungsarbeiten am Sonntag", messages.get(0).getMessage());
		assertEquals("First text", messages.get(1).getMessage());
		assertEquals("", messages.get(11).getMessage());
		assertEquals(13, storedMessages().size());
		verify(firstBatch).setParam("code", "MSG01");
		verify(firstBatch).setParam("code_10", "MSG10");
		verify(secondBatch).setParam("code", "MSG11");
		verify(handler, times(2)).execute();
	}

	private void prepareSession(String... supportedJobs) {
		Properties jobs = new Properties();
		for (String supportedJob : supportedJobs) {
			jobs.setProperty(supportedJob, "1");
		}
		doReturn(passport).when(hbciSupport).initBankConnection(any(BankAccess.class), any(GBankingHBCICallback.class));
		doReturn(handler).when(hbciSupport).createHBCIHandler(BaseMessagesDb.getVersion().getId(), passport);
		when(handler.getSupportedLowlevelJobs()).thenReturn(jobs);
	}

	private BankMessageRetrievalResult retrieve(String... institutionMessages) {
		try (MockedConstruction<GBankingHBCICallback> ignored = mockConstruction(GBankingHBCICallback.class,
				(callback, context) -> when(callback.drainInstitutionMessages()).thenReturn(List.of(institutionMessages))
						.thenReturn(List.of()))) {
			return service.retrieveBankMessagesWithResult(bankAccess, pin);
		}
	}

	private void stubOverview(GVRInfoList.Info... entries) {
		HBCIJob<GVRInfoList> overviewJob = job(overview(true, entries));
		doReturn(overviewJob).when(hbciSupport).newHbciJob(handler, INFO_LIST_JOB);
	}

	private List<BankMessage> storedMessages() {
		return dbController.getAllByParentFull(BankMessage.class, bankAccess.getId());
	}

	@SuppressWarnings("unchecked")
	private static <R extends HBCIJobResult> HBCIJob<R> job(R result) {
		HBCIJob<R> job = mock(HBCIJob.class);
		when(job.getJobResult()).thenReturn(result);
		return job;
	}

	private static GVRInfoList overview(boolean ok, GVRInfoList.Info... entries) {
		GVRInfoList result = mock(GVRInfoList.class);
		when(result.isOK()).thenReturn(ok);
		when(result.getEntries()).thenReturn(entries);
		return result;
	}

	private static GVRInfoOrder details(GVRInfoOrder.Info... entries) {
		GVRInfoOrder result = mock(GVRInfoOrder.class);
		when(result.isOK()).thenReturn(true);
		when(result.getEntries()).thenReturn(entries);
		return result;
	}

	private static GVRInfoOrder.Info detail(String code, String message) {
		GVRInfoOrder.Info info = new GVRInfoOrder.Info();
		info.code = code;
		info.msg = message;
		return info;
	}

	private static HBCIExecStatus status(boolean ok, String errorString) {
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		when(status.isOK()).thenReturn(ok);
		when(status.getErrorString()).thenReturn(errorString);
		return status;
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
