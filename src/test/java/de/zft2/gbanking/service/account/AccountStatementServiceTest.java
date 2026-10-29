package de.zft2.gbanking.service.account;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kapott.hbci.GV.GVKontoauszugUebersicht;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRKontoauszug;
import org.kapott.hbci.GV_Result.GVRKontoauszug.Format;
import org.kapott.hbci.GV_Result.GVRKontoauszug.GVRKontoauszugEntry;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountStatement;
import de.zft2.gbanking.db.dao.enu.DataType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.service.Service;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.ServiceStubbingUtil;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.service.settings.SettingDefinition;
import de.zft2.gbanking.service.settings.SettingsStore;
import de.zft2.gbanking.testdata.TestDataFactory;

class AccountStatementServiceTest {

	private static final List<Class<? extends Service>> SERVICES_TO_STUB = List.of(BankAccessService.class);
	private static final String STORED_FILE_NAME = "account-DE123_2026-0007_statement.pdf";
	private static final String PDF_JOB = "KontoauszugPdf";
	private static final String RECEIPT_JOB = "Receipt";
	// used both as HBCI receipt parameter name and as receipt content
	private static final String RECEIPT = "receipt";
	private static final String OVERVIEW_JOB = GVKontoauszugUebersicht.LOWLEVEL_NAME;

	@TempDir
	Path tempDir;

	private final HBCIPassport passport = mock(HBCIPassport.class);
	private final HBCIHandler handler = mock(HBCIHandler.class);
	private final char[] pin = "1234".toCharArray();
	private DBController dbController;
	private BankAccessService hbciSupport;
	private AccountStatementService service;

	@BeforeEach
	void setupDatabaseContext() {
		DBController.resetConnection();
		dbController = DBController.getInstance(tempDir.resolve("db").toString());
		ServiceStubbingUtil.initStubbedServicesInContext(SERVICES_TO_STUB);
		hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		service = new AccountStatementService(tempDir);
	}

	@AfterEach
	void closeDatabaseConnection() {
		ServiceStubbingUtil.unloadStubbedServicesInContext(SERVICES_TO_STUB);
		DBController.resetConnection();
	}

	@Test
	void saveStatementsShouldSkipDuplicateEntriesInRetrievalSession() throws Exception {
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		GVRKontoauszugEntry entry = createEntry("statement".getBytes(StandardCharsets.ISO_8859_1));

		List<BankAccountStatement> savedStatements = service.saveStatements(account, List.of(entry, entry), PDF_JOB, new HashSet<>());

		assertEquals(1, savedStatements.size());
		assertEquals(1, dbController.getAllByParentFull(BankAccountStatement.class, account.getId()).size());
		assertEquals(1, regularFileCount(tempDir));
	}

	@Test
	void saveStatementsShouldRestoreMissingKnownStatementWithOriginalFileName() throws Exception {
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		BankAccountStatement storedStatement = dbController.insertOrUpdate(createStoredStatement(account, true));
		GVRKontoauszugEntry entry = createEntry("restored statement".getBytes(StandardCharsets.ISO_8859_1));

		List<BankAccountStatement> savedStatements = service.saveStatements(account, List.of(entry), PDF_JOB, new HashSet<>());
		List<BankAccountStatement> persistedStatements = dbController.getAllByParentFull(BankAccountStatement.class, account.getId());

		assertEquals(1, savedStatements.size());
		assertEquals(storedStatement.getId(), savedStatements.get(0).getId());
		assertEquals(1, persistedStatements.size());
		assertEquals(STORED_FILE_NAME, persistedStatements.get(0).getFileName());
		assertTrue(persistedStatements.get(0).isAcknowledged());
		assertTrue(Files.exists(tempDir.resolve(STORED_FILE_NAME)));
		assertEquals(1, regularFileCount(tempDir));
	}

	@Test
	void saveStatementsShouldSkipKnownStatementWhenFileAlreadyExists() throws Exception {
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		storeWithFile(createStoredStatement(account, true));
		GVRKontoauszugEntry entry = createEntry("duplicate statement".getBytes(StandardCharsets.ISO_8859_1));

		List<BankAccountStatement> savedStatements = service.saveStatements(account, List.of(entry), PDF_JOB, new HashSet<>());

		assertTrue(savedStatements.isEmpty());
		assertEquals(1, dbController.getAllByParentFull(BankAccountStatement.class, account.getId()).size());
		assertEquals(1, regularFileCount(tempDir));
	}

	@Test
	void listStatementsShouldOnlyReturnStatementsWithExistingFiles() throws IOException {
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		BankAccountStatement acknowledgedStatement = storeWithFile(createStoredStatement(account, true));
		BankAccountStatement pendingStatement = createStoredStatement(account, false);
		pendingStatement.setFileName("pending.pdf");
		pendingStatement = storeWithFile(pendingStatement);
		BankAccountStatement statementWithoutFile = createStoredStatement(account, false);
		statementWithoutFile.setFileName("missing.pdf");
		dbController.insertOrUpdate(statementWithoutFile);

		List<AccountStatement> pendingStatements = service.listPendingReceiptStatements(account);

		assertEquals(List.of(acknowledgedStatement.getId(), pendingStatement.getId()),
				service.listAccountStatements(account).stream().map(statement -> statement.id()).toList());
		assertEquals(1, pendingStatements.size());
		assertEquals(pendingStatement.getId(), pendingStatements.get(0).id());
		assertEquals(tempDir.resolve("pending.pdf"), pendingStatements.get(0).file());
	}

	@Test
	void hbciOperationsShouldFailAndClearPinWithoutBankAccess() {
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));

		assertEquals(AccountStatementRetrievalResult.failure(), service.retrieveAccountStatementsWithResult(account, pin));
		assertEquals(AccountStatementAcknowledgementResult.failure(), service.acknowledgeAccountStatementsWithResult(account, pin));
		assertArrayEquals(new char[pin.length], pin);
	}

	@Test
	void retrieveAccountStatementsWithResultShouldSaveAndAcknowledgeStatementsOfInitialRetrieval() {
		BankAccount account = prepareSession(PDF_JOB, RECEIPT_JOB);
		HBCIJob<GVRKontoauszug> statementJob = statementJob(createEntry("statement".getBytes(StandardCharsets.ISO_8859_1)));
		HBCIJob<HBCIJobResult> receiptJob = job(null);
		doReturn(statementJob).when(hbciSupport).newHbciJob(handler, PDF_JOB);
		doReturn(receiptJob).when(hbciSupport).newHbciJob(handler, RECEIPT_JOB);

		AccountStatementRetrievalResult result = withMockedCallback(() -> service.retrieveAccountStatementsWithResult(account, pin));

		int currentYear = Year.now(ZoneId.systemDefault()).getValue();
		assertTrue(result.successful());
		assertEquals(1, result.statements().size());
		assertTrue(result.statements().get(0).acknowledged());
		assertTrue(dbController.getAllByParentFull(BankAccountStatement.class, account.getId()).get(0).isAcknowledged());
		verify(statementJob).setParam("year", Integer.toString(currentYear));
		verify(statementJob).setParam("year", Integer.toString(currentYear - 4));
		verify(statementJob, times(5)).addToQueue();
		verify(receiptJob).setParam(RECEIPT, RECEIPT);
		verify(handler, times(2)).execute();
		assertArrayEquals(new char[pin.length], pin);
	}

	@ParameterizedTest
	@CsvSource({ "HIRMG:2:2+9050::Teilweise fehlerhaft., false", "HIRMG:2:2+9942::Anmeldedaten sind ungueltig., true" })
	void retrieveAccountStatementsWithResultShouldReportRejectedStatementJob(String errorString, boolean wrongPin) throws IOException {
		BankAccount account = prepareSession("Kontoauszug");
		storeWithFile(createStoredStatement(account, true));
		HBCIJob<GVRKontoauszug> statementJob = job(null);
		HBCIExecStatus status = status(false, errorString);
		doReturn(statementJob).when(hbciSupport).newHbciJob(handler, "Kontoauszug");
		when(handler.execute()).thenReturn(status);

		try (MockedConstruction<GBankingHBCICallback> callbacks = mockConstruction(GBankingHBCICallback.class)) {
			AccountStatementRetrievalResult result = service.retrieveAccountStatementsWithResult(account, pin);

			assertEquals(wrongPin ? AccountStatementRetrievalResult.wrongPinFailure() : AccountStatementRetrievalResult.failure(), result);
			verify(callbacks.constructed().get(0)).handleFailure(errorString);
		}
		verify(statementJob).setParam(eq("my"), any(Konto.class));
		verify(statementJob, never()).setParam(eq("year"), anyString());
	}

	@Test
	void retrieveAccountStatementsWithResultShouldRejectUnsupportedJobsAndUnknownAccounts() {
		BankAccount account = prepareSession();

		GBankingException unsupportedJobs = assertThrows(GBankingException.class,
				() -> withMockedCallback(() -> service.retrieveAccountStatementsWithResult(account, pin)));
		when(passport.getAccounts()).thenReturn(new Konto[0]);
		GBankingException unknownAccount = assertThrows(GBankingException.class,
				() -> withMockedCallback(() -> service.retrieveAccountStatementsWithResult(account, pin)));

		assertTrue(unsupportedJobs.getMessage().startsWith("Kontoauszuege werden von diesem Bankzugang nicht unterstuetzt."));
		assertTrue(unknownAccount.getMessage().startsWith("Kein passendes HBCI-Konto"));
	}

	@Test
	void retrieveAccountStatementsWithResultShouldDownloadStatementsListedInOverview() throws IOException {
		BankAccount account = prepareSession(PDF_JOB, RECEIPT_JOB, OVERVIEW_JOB);
		BankAccountStatement statementWithoutNumber = createStoredStatement(account, true);
		statementWithoutNumber.setNumber(0);
		storeWithFile(statementWithoutNumber);
		GVRKontoauszugEntry entry = createEntry("overview statement".getBytes(StandardCharsets.ISO_8859_1));
		entry.setNumber(8);
		HBCIJob<GVRKontoauszug> requestedJob = statementJob(entry);
		Properties overviewData = new Properties();
		overviewData.setProperty("content.number", "8");
		overviewData.setProperty("content.year", "2026");
		overviewData.setProperty("content.retrievable", "J");
		HBCIJobResult overviewResult = mock(HBCIJobResult.class);
		when(overviewResult.isOK()).thenReturn(true);
		when(overviewResult.getResultData()).thenReturn(overviewData);
		HBCIJob<HBCIJobResult> overviewJob = job(overviewResult);
		doReturn(statementJob(), requestedJob).when(hbciSupport).newHbciJob(handler, PDF_JOB);
		doReturn(overviewJob).when(hbciSupport).newHbciJob(handler, OVERVIEW_JOB);
		doReturn(job(null)).when(hbciSupport).newHbciJob(handler, RECEIPT_JOB);

		AccountStatementRetrievalResult result;
		try (MockedStatic<GVKontoauszugUebersicht> overview = mockStatic(GVKontoauszugUebersicht.class)) {
			overview.when(() -> GVKontoauszugUebersicht.ensureSyntax(handler)).thenReturn(true);
			result = withMockedCallback(() -> service.retrieveAccountStatementsWithResult(account, pin));
		}

		assertTrue(result.successful());
		assertEquals(List.of(8), result.statements().stream().map(statement -> statement.number()).toList());
		verify(overviewJob).setParam("my.iban", account.getIban());
		verify(overviewJob).setParam("my.country", "DE");
		verify(requestedJob).setParam("year", "2026");
		verify(requestedJob).setParam("idx", "8");
		verify(handler, times(4)).execute();
	}

	@Test
	void retrieveAccountStatementsWithResultShouldStopRedownloadAfterEmptyFallbackResults() throws IOException {
		BankAccount account = prepareSession(PDF_JOB);
		BankAccountStatement acknowledgedStatement = createStoredStatement(account, true);
		acknowledgedStatement.setNumber(42);
		storeWithFile(acknowledgedStatement);
		new SettingsStore(dbController).save(new SettingDefinition(AccountStatementSettings.SETTING_REDOWNLOAD_ACKNOWLEDGED, "false",
				DataType.BOOLEAN, true, true, "test"), "true");
		HBCIJob<GVRKontoauszug> statementJob = statementJob();
		doReturn(statementJob).when(hbciSupport).newHbciJob(handler, PDF_JOB);

		AccountStatementRetrievalResult result = withMockedCallback(() -> service.retrieveAccountStatementsWithResult(account, pin));

		assertEquals(AccountStatementRetrievalResult.success(List.of()), result);
		verify(statementJob).setParam("idx", "42");
		// current retrieval, exact redownload of statement 42 and six empty monthly fallback requests
		verify(handler, times(8)).execute();
	}

	@Test
	void acknowledgeAccountStatementsWithResultShouldAcknowledgePendingReceipts() {
		BankAccount account = prepareSession(RECEIPT_JOB);
		BankAccountStatement statement = dbController.insertOrUpdate(createStoredStatement(account, false));
		HBCIJob<HBCIJobResult> receiptJob = job(null);
		doReturn(receiptJob).when(hbciSupport).newHbciJob(handler, RECEIPT_JOB);

		AccountStatementAcknowledgementResult result = withMockedCallback(() -> service.acknowledgeAccountStatementsWithResult(account, pin));

		BankAccountStatement acknowledgedStatement = dbController.getByIdFull(BankAccountStatement.class, statement.getId());
		assertEquals(AccountStatementAcknowledgementResult.success(1), result);
		assertTrue(acknowledgedStatement.isAcknowledged());
		assertNotNull(acknowledgedStatement.getAcknowledgedAt());
		verify(receiptJob).setParam(RECEIPT, RECEIPT);
	}

	@Test
	void acknowledgeAccountStatementsWithResultShouldSkipMissingReceiptsAndFailWithoutReceiptJob() {
		BankAccount account = prepareSession();

		AccountStatementAcknowledgementResult withoutReceipts = withMockedCallback(() -> service.acknowledgeAccountStatementsWithResult(account, pin));
		dbController.insertOrUpdate(createStoredStatement(account, false));
		AccountStatementAcknowledgementResult withoutReceiptJob = withMockedCallback(() -> service.acknowledgeAccountStatementsWithResult(account, pin));

		assertEquals(AccountStatementAcknowledgementResult.success(0), withoutReceipts);
		assertEquals(AccountStatementAcknowledgementResult.failure(), withoutReceiptJob);
		assertFalse(dbController.getAllByParentFull(BankAccountStatement.class, account.getId()).get(0).isAcknowledged());
		verify(handler, never()).execute();
	}

	private BankAccount prepareSession(String... supportedJobs) {
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("10020030");
		Konto konto = new Konto();
		konto.iban = account.getIban();
		Properties jobs = new Properties();
		for (String supportedJob : supportedJobs) {
			jobs.setProperty(supportedJob, "1");
		}
		HBCIExecStatus okStatus = status(true, null);
		when(hbciSupport.initBankAccess(account, pin)).thenReturn(bankAccess);
		doReturn(passport).when(hbciSupport).initBankConnection(eq(bankAccess), any(GBankingHBCICallback.class));
		doReturn(handler).when(hbciSupport).createHBCIHandler(BaseMessagesDb.getVersion().getId(), passport);
		when(passport.getAccounts()).thenReturn(new Konto[] { konto });
		when(handler.getSupportedLowlevelJobs()).thenReturn(jobs);
		when(handler.execute()).thenReturn(okStatus);
		return account;
	}

	private <T> T withMockedCallback(Supplier<T> operation) {
		try (MockedConstruction<GBankingHBCICallback> ignored = mockConstruction(GBankingHBCICallback.class)) {
			return operation.get();
		}
	}

	@SuppressWarnings("unchecked")
	private static <R extends HBCIJobResult> HBCIJob<R> job(R result) {
		HBCIJob<R> job = mock(HBCIJob.class);
		when(job.getJobResult()).thenReturn(result);
		return job;
	}

	private static HBCIJob<GVRKontoauszug> statementJob(GVRKontoauszugEntry... entries) {
		GVRKontoauszug result = mock(GVRKontoauszug.class);
		when(result.isOK()).thenReturn(true);
		when(result.getEntries()).thenReturn(List.of(entries));
		return job(result);
	}

	private static HBCIExecStatus status(boolean ok, String errorString) {
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		when(status.isOK()).thenReturn(ok);
		when(status.getErrorString()).thenReturn(errorString);
		return status;
	}

	private BankAccountStatement storeWithFile(BankAccountStatement statement) throws IOException {
		Files.write(tempDir.resolve(statement.getFileName()), "existing".getBytes(StandardCharsets.ISO_8859_1));
		return dbController.insertOrUpdate(statement);
	}

	private BankAccountStatement createStoredStatement(BankAccount account, boolean acknowledged) {
		BankAccountStatement statement = new BankAccountStatement();
		statement.setAccountId(account.getId());
		statement.setAccountName("Girokonto");
		statement.setFileName(STORED_FILE_NAME);
		statement.setFormat("PDF");
		statement.setRetrievedAt(LocalDateTime.of(2026, Month.JULY, 9, 11, 30));
		statement.setStatementDate(LocalDate.of(2026, Month.JUNE, 30));
		statement.setStartDate(LocalDate.of(2026, Month.JUNE, 1));
		statement.setEndDate(LocalDate.of(2026, Month.JUNE, 30));
		statement.setYear(2026);
		statement.setNumber(7);
		statement.setSize(1234L);
		statement.setIban("DE123");
		statement.setBic("TESTDEFFXXX");
		statement.setSourceJob(PDF_JOB);
		statement.setReceiptAvailable(true);
		statement.setReceipt(RECEIPT.getBytes(StandardCharsets.ISO_8859_1));
		statement.setAcknowledged(acknowledged);
		statement.setAcknowledgedAt(acknowledged ? LocalDateTime.of(2026, Month.JULY, 9, 12, 30) : null);
		statement.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return statement;
	}

	private GVRKontoauszugEntry createEntry(byte[] content) {
		GVRKontoauszugEntry entry = new GVRKontoauszugEntry();
		entry.setFormat(Format.PDF);
		entry.setData(content);
		entry.setDate(toDate(LocalDate.of(2026, Month.JUNE, 30)));
		entry.setStartDate(toDate(LocalDate.of(2026, Month.JUNE, 1)));
		entry.setEndDate(toDate(LocalDate.of(2026, Month.JUNE, 30)));
		entry.setYear(2026);
		entry.setNumber(7);
		entry.setReceipt(RECEIPT.getBytes(StandardCharsets.ISO_8859_1));
		entry.setIBAN("DE123");
		entry.setBIC("TESTDEFFXXX");
		entry.setFilename("statement.pdf");
		return entry;
	}

	private Date toDate(LocalDate date) {
		return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	private long regularFileCount(Path directory) throws IOException {
		try (var files = Files.list(directory)) {
			return files.filter(path -> Files.isRegularFile(path)).count();
		}
	}
}