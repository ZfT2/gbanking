package de.zft2.gbanking.service.account;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.GVKontoauszugUebersicht;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV.HBCIJobImpl;
import org.kapott.hbci.GV_Result.GVRKontoauszug.Format;
import org.kapott.hbci.GV_Result.GVRKontoauszug.GVRKontoauszugEntry;
import org.kapott.hbci.GV_Result.GVRKontoauszug;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountStatement;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.hbci.HbciStatusMessageExtractor;
import de.zft2.gbanking.logging.GBankingLoggingHandler;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.HbciSessionRunner;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.service.account.AccountStatementRequestPlanner.StatementOverviewEntry;
import de.zft2.gbanking.service.account.AccountStatementRequestPlanner.StatementRequest;
import de.zft2.gbanking.util.TypeConverter;

public class AccountStatementService extends AbstractDbService {

	private static final Logger log = LogManager.getLogger(AccountStatementService.class);

	private static final String STATEMENT_JOB = "Kontoauszug";
	private static final String STATEMENT_JOB_PDF = "KontoauszugPdf";
	private static final String STATEMENT_OVERVIEW_JOB = GVKontoauszugUebersicht.LOWLEVEL_NAME;
	private static final String RECEIPT_JOB = "Receipt";
	private static final String OVERVIEW_BUSINESS_CASE = "HKKAU";
	private static final String UI_JOB_STATEMENT = "UI_DIALOG_HBCI_JOB_STATEMENT";
	private static final int REDOWNLOAD_EMPTY_RESULT_LIMIT = 6;

	private final BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
	private final GBankingLoggingHandler logHandler = GBankingLoggingHandler.getInstance();
	private final HbciSessionRunner hbciSessionRunner = new HbciSessionRunner();
	private final AccountStatementFileManager statementFileManager;

	public AccountStatementService() {
		this.statementFileManager = new AccountStatementFileManager();
	}

	AccountStatementService(Path dir) {
		this.statementFileManager = new AccountStatementFileManager(dir);
	}

	public List<AccountStatement> listAccountStatements(BankAccount bankAccount) {
		return toAccountStatements(listExistingAccountStatementDaos(bankAccount));
	}

	public Path prepareForOpening(AccountStatement statement) {
		return statementFileManager.prepareForOpening(statement.fileName());
	}

	public void updateFileEncryption(boolean enabled) {
		statementFileManager.updateEncryption(enabled);
	}

	public AccountStatementRetrievalResult retrieveAccountStatementsWithResult(BankAccount bankAccount, char[] pin) {
		log.info("Starting HBCI account statement retrieval for account id {}", bankAccount != null ? bankAccount.getId() : null);
		BankAccess bankAccess = hbciSupport.initBankAccess(bankAccount, pin);
		if (bankAccess == null) {
			log.info("HBCI account statement retrieval skipped, no bank access available.");
			HbciSessionRunner.clearSecret(pin);
			return AccountStatementRetrievalResult.failure();
		}

		String bankAccountId = getNullableBankAccountId(bankAccount);
		try {
			AccountStatementRetrievalResult result = hbciSessionRunner.run(bankAccess, pin, session -> retrieveAccountStatements(bankAccount, session));
			log.info("Finished HBCI account statement retrieval for account id {}, success={}, statements={}", bankAccountId, result.successful(),
					result.statements().size());
			return result;
		} catch (InterruptedException e) {
			log.error("Error handling HBCI account statement retrieval for account id {}", bankAccountId, e);
			Thread.currentThread().interrupt();
			return AccountStatementRetrievalResult.failure();
		} catch (HBCI_Exception e) {
			if (HbciStatusMessageExtractor.containsWrongPinFeedback(e)) {
				log.warn("Stopping HBCI account statement retrieval for account id {} because the bank reported invalid PIN credentials.", bankAccountId);
				return AccountStatementRetrievalResult.wrongPinFailure();
			}
			throw e;
		}
	}

	public AccountStatementAcknowledgementResult acknowledgeAccountStatementsWithResult(BankAccount bankAccount, char[] pin) {
		log.info("Starting HBCI account statement receipt acknowledgement for account id {}", bankAccount != null ? bankAccount.getId() : null);
		BankAccess bankAccess = hbciSupport.initBankAccess(bankAccount, pin);
		if (bankAccess == null) {
			log.info("HBCI account statement receipt acknowledgement skipped, no bank access available.");
			HbciSessionRunner.clearSecret(pin);
			return AccountStatementAcknowledgementResult.failure();
		}

		String bankAccountId = getNullableBankAccountId(bankAccount);
		try {
			AccountStatementAcknowledgementResult result = hbciSessionRunner.run(bankAccess, pin,
					session -> acknowledgeAccountStatements(bankAccount, session));
			log.info("Finished HBCI account statement receipt acknowledgement for account id {}, success={}, acknowledged={}", bankAccountId,
					result.successful(), result.acknowledgedCount());
			return result;
		} catch (InterruptedException e) {
			log.error("Error handling HBCI account statement receipt acknowledgement for account id {}", bankAccountId, e);
			Thread.currentThread().interrupt();
			return AccountStatementAcknowledgementResult.failure();
		} catch (HBCI_Exception e) {
			if (HbciStatusMessageExtractor.containsWrongPinFeedback(e)) {
				log.warn("Stopping HBCI account statement receipt acknowledgement for account id {} because the bank reported invalid PIN credentials.",
						bankAccountId);
				return AccountStatementAcknowledgementResult.wrongPinFailure();
			}
			throw e;
		}
	}

	private AccountStatementRetrievalResult retrieveAccountStatements(BankAccount bankAccount, HbciSessionRunner.HbciSession session) {
		logHandler.logRetrivedBankAccessInfo(session.passport(), false);

		Konto konto = findMatchingKonto(session.passport(), bankAccount);
		if (konto == null) {
			throw new GBankingException("Kein passendes HBCI-Konto fuer Konto " + getNullableBankAccountId(bankAccount) + " gefunden.");
		}
		konto.bic = firstText(bankAccount.getBic(), konto.bic);
		logHandler.logRetrievedAccountInfo(konto);

		String statementJobName = resolveStatementJobName(session.handler());
		List<BankAccountStatement> storedStatementsBeforeRetrieval = listAccountStatementDaos(bankAccount);
		Set<String> retrievedStatementKeys = new HashSet<>();
		StatementRetrievalBatchResult currentResult = retrieveCurrentAccountStatements(bankAccount, session, konto, statementJobName,
				retrievedStatementKeys);
		if (currentResult.wrongPin()) {
			return AccountStatementRetrievalResult.wrongPinFailure();
		}

		List<AccountStatement> savedStatements = new ArrayList<>(currentResult.statements());
		boolean successful = currentResult.successful();
		if (shouldRetrieveStatementOverview(storedStatementsBeforeRetrieval)) {
			StatementRetrievalBatchResult overviewResult = retrieveAccountStatementsFromOverview(bankAccount, session, konto, statementJobName,
					storedStatementsBeforeRetrieval, savedStatements, retrievedStatementKeys);
			if (overviewResult.wrongPin()) {
				return AccountStatementRetrievalResult.wrongPinFailure();
			}
			savedStatements.addAll(overviewResult.statements());
			successful = successful && overviewResult.successful();
		}
		if (AccountStatementSettings.isRedownloadAcknowledgedEnabled()) {
			StatementRetrievalBatchResult redownloadResult = redownloadAcknowledgedAccountStatements(bankAccount, session, konto, statementJobName,
					storedStatementsBeforeRetrieval, retrievedStatementKeys);
			if (redownloadResult.wrongPin()) {
				return AccountStatementRetrievalResult.wrongPinFailure();
			}
			savedStatements.addAll(redownloadResult.statements());
			successful = successful && redownloadResult.successful();
		}

		return successful ? AccountStatementRetrievalResult.success(savedStatements) : AccountStatementRetrievalResult.failure();
	}

	private StatementRetrievalBatchResult retrieveCurrentAccountStatements(BankAccount bankAccount, HbciSessionRunner.HbciSession session, Konto konto,
			String statementJobName, Set<String> retrievedStatementKeys) {
		List<Integer> retrievalYears = determineRetrievalYears(bankAccount);
		List<HBCIJob<GVRKontoauszug>> statementJobs = createStatementJobs(session.handler(), statementJobName, konto, retrievalYears,
				session.callback(), session.callback().getAccountDescription(bankAccount));

		HBCIExecStatus status = session.handler().execute();
		boolean resultOk = status.isOK();
		if (!resultOk) {
			log.error("HBCI account statement error, status: {}", status);
			session.callback().handleFailure(status.getErrorString());
			if (HbciStatusMessageExtractor.containsWrongPinFeedback(status)) {
				return StatementRetrievalBatchResult.wrongPinFailure();
			}
		}

		List<GVRKontoauszug> statementResults = readStatementResults(statementJobs);
		List<GVRKontoauszugEntry> entries = readStatementEntries(statementResults);
		List<BankAccountStatement> savedStatements = saveStatements(bankAccount, entries, statementJobName, retrievedStatementKeys);
		boolean receiptsOk = acknowledgeSavedStatementsIfEnabled(session.callback(), session.handler(), savedStatements);
		return resultOk && areStatementResultsOk(statementResults) && receiptsOk
				? StatementRetrievalBatchResult.success(toAccountStatements(savedStatements))
				: StatementRetrievalBatchResult.failure(toAccountStatements(savedStatements));
	}

	private StatementRetrievalBatchResult retrieveAccountStatementsFromOverview(BankAccount bankAccount, HbciSessionRunner.HbciSession session,
			Konto konto, String statementJobName, List<BankAccountStatement> storedStatements, List<AccountStatement> currentStatements,
			Set<String> retrievedStatementKeys) {
		boolean forceOverview = AccountStatementSettings.isDownloadOverviewEnabled();
		StatementOverviewResult overviewResult = retrieveStatementOverview(bankAccount, session, konto, forceOverview);
		if (overviewResult.wrongPin()) {
			return StatementRetrievalBatchResult.wrongPinFailure();
		}
		if (!overviewResult.successful() || overviewResult.entries().isEmpty()) {
			return StatementRetrievalBatchResult.success(List.of());
		}

		Set<String> knownStatementIds = AccountStatementRequestPlanner.createKnownStatementIds(storedStatements, currentStatements);
		List<StatementRequest> statementRequests = AccountStatementRequestPlanner.createOverviewDownloadRequests(overviewResult.entries(), knownStatementIds);
		if (statementRequests.isEmpty()) {
			log.info("No downloadable account statements from HKKAU overview are missing for account id {}.", bankAccount.getId());
			return StatementRetrievalBatchResult.success(List.of());
		}

		log.info("Downloading {} account statement(s) discovered by HKKAU overview for account id {}.", statementRequests.size(), bankAccount.getId());
		return retrieveStatementRequests(bankAccount, session, konto, statementJobName, statementRequests, retrievedStatementKeys);
	}

	private StatementOverviewResult retrieveStatementOverview(BankAccount bankAccount, HbciSessionRunner.HbciSession session, Konto konto,
			boolean forceOverview) {
		HBCIHandler handler = session.handler();
		String bankAccountId = getNullableBankAccountId(bankAccount);
		if (!GVKontoauszugUebersicht.ensureSyntax(handler)) {
			log.warn("Skipping HKKAU account statement overview because the HBCI syntax extension could not be installed.");
			return StatementOverviewResult.success(List.of());
		}
		if (!forceOverview && !isStatementOverviewSupported(handler, bankAccount)) {
			log.debug("Skipping HKKAU account statement overview because it is not advertised for account id {}.", bankAccountId);
			return StatementOverviewResult.success(List.of());
		}

		HBCIJob<HBCIJobResult> overviewJob = createStatementOverviewJob(handler, konto, bankAccount);
		session.callback().registerJobDescription(overviewJob, getText("UI_DIALOG_HBCI_JOB_STATEMENT_OVERVIEW"));
		HBCIExecStatus status = handler.execute();
		if (!status.isOK()) {
			if (HbciStatusMessageExtractor.containsWrongPinFeedback(status)) {
				session.callback().handleFailure(status.getErrorString());
				return StatementOverviewResult.wrongPinFailure();
			}
			log.info("No HKKAU account statement overview returned for account id {}. HBCI status: {}", bankAccountId, status);
			return StatementOverviewResult.success(List.of());
		}

		HBCIJobResult overviewJobResult = overviewJob.getJobResult();
		if (overviewJobResult == null || !overviewJobResult.isOK()) {
			log.info("No successful HKKAU account statement overview result returned for account id {}: {}", bankAccountId, overviewJobResult);
			return StatementOverviewResult.success(List.of());
		}

		List<StatementOverviewEntry> overviewEntries = AccountStatementRequestPlanner.readOverviewEntries(overviewJobResult.getResultData());
		log.info("Received {} account statement overview entries from HKKAU for account id {}.", overviewEntries.size(), bankAccountId);
		return StatementOverviewResult.success(overviewEntries);
	}

	private HBCIJob<HBCIJobResult> createStatementOverviewJob(HBCIHandler handler, Konto konto, BankAccount bankAccount) {
		HBCIJob<HBCIJobResult> job = hbciSupport.newHbciJob(handler, STATEMENT_OVERVIEW_JOB);
		if (job instanceof HBCIJobImpl<?> jobImplementation) {
			jobImplementation.setSegVersion(GVKontoauszugUebersicht.resolveSegmentVersion(handler));
		}
		setOverviewAccountParams(job, konto, bankAccount);
		job.addToQueue();
		log.debug("Queued HBCI account statement overview job {} for account id {}.", STATEMENT_OVERVIEW_JOB,
				bankAccount != null ? bankAccount.getId() : null);
		return job;
	}

	private void setOverviewAccountParams(HBCIJob<?> job, Konto konto, BankAccount bankAccount) {
		if (!"1".equals(job.getSegVersion())) {
			setInternationalOverviewAccountParams(job, konto, bankAccount);
		}
		setNationalOverviewAccountParams(job, konto, bankAccount);
	}

	private void setInternationalOverviewAccountParams(HBCIJob<?> job, Konto konto, BankAccount bankAccount) {
		setParamIfPresent(job, "my.iban", firstText(konto != null ? konto.iban : null, bankAccount != null ? bankAccount.getIban() : null));
		setParamIfPresent(job, "my.bic", firstText(konto != null ? konto.bic : null, bankAccount != null ? bankAccount.getBic() : null));
	}

	private void setNationalOverviewAccountParams(HBCIJob<?> job, Konto konto, BankAccount bankAccount) {
		setParamIfPresent(job, "my.number", firstText(konto != null ? konto.number : null, bankAccount != null ? bankAccount.getNumber() : null));
		setParamIfPresent(job, "my.subnumber", firstText(konto != null ? konto.subnumber : null, bankAccount != null ? bankAccount.getSubnumber() : null));
		setParamIfPresent(job, "my.country", firstText(konto != null ? konto.country : null, bankAccount != null ? bankAccount.getCountry() : null, "DE"));
		setParamIfPresent(job, "my.blz", firstText(konto != null ? konto.blz : null, bankAccount != null ? bankAccount.getBlz() : null));
	}

	private StatementRetrievalBatchResult redownloadAcknowledgedAccountStatements(BankAccount bankAccount, HbciSessionRunner.HbciSession session,
			Konto konto, String statementJobName, List<BankAccountStatement> storedStatements, Set<String> retrievedStatementKeys) {
		YearMonth startMonth = YearMonth.now(ZoneId.systemDefault()).minusMonths(1);
		List<StatementRequest> statementRequests = AccountStatementRequestPlanner.createRedownloadRequests(storedStatements, startMonth);
		log.info("Starting redownload of acknowledged account statements for account id {} with {} request(s).", bankAccount.getId(),
				statementRequests.size());
		return retrieveStatementRequests(bankAccount, session, konto, statementJobName, statementRequests, retrievedStatementKeys);
	}

	private StatementRetrievalBatchResult retrieveStatementRequests(BankAccount bankAccount, HbciSessionRunner.HbciSession session, Konto konto,
			String statementJobName, List<StatementRequest> statementRequests, Set<String> retrievedStatementKeys) {
		int emptyFallbackResults = 0;
		List<AccountStatement> savedStatements = new ArrayList<>();
		StatementRequestContext context = new StatementRequestContext(bankAccount, session, konto, statementJobName, retrievedStatementKeys);
		for (int requestIndex = 0; requestIndex < statementRequests.size(); requestIndex++) {
			StatementRequest statementRequest = statementRequests.get(requestIndex);
			StatementRequestProcessingResult processingResult = processStatementRequest(context, statementRequest, savedStatements,
					emptyFallbackResults, requestIndex + 1, statementRequests.size());
			if (processingResult.terminalResult() != null) {
				return processingResult.terminalResult();
			}
			emptyFallbackResults = processingResult.emptyFallbackResults();
		}
		return StatementRetrievalBatchResult.success(savedStatements);
	}

	private StatementRequestProcessingResult processStatementRequest(StatementRequestContext context, StatementRequest statementRequest,
			List<AccountStatement> savedStatements, int emptyFallbackResults, int requestPosition, int requestCount) {
		if (isRequestAlreadyRetrieved(context.bankAccount(), statementRequest, context.retrievedStatementKeys())) {
			log.info("Skipping account statement request {}/{} for account id {} because it was already retrieved in this session.",
					statementRequest.number(), statementRequest.year(), context.bankAccount().getId());
			return StatementRequestProcessingResult.proceed(emptyFallbackResults);
		}

		StatementRetrievalBatchResult result = retrieveSingleStatement(context, statementRequest, requestPosition, requestCount);
		if (result.wrongPin()) {
			return StatementRequestProcessingResult.stop(StatementRetrievalBatchResult.wrongPinFailure(), emptyFallbackResults);
		}
		if (!result.successful()) {
			StatementRetrievalBatchResult terminalResult = result.statements().isEmpty()
					? StatementRetrievalBatchResult.success(savedStatements)
					: StatementRetrievalBatchResult.failure(combine(savedStatements, result.statements()));
			return StatementRequestProcessingResult.stop(terminalResult, emptyFallbackResults);
		}
		if (result.statements().isEmpty()) {
			return processEmptyStatementResult(context.bankAccount(), statementRequest, savedStatements, emptyFallbackResults);
		}

		savedStatements.addAll(result.statements());
		return StatementRequestProcessingResult.proceed(statementRequest.exactStatement() ? emptyFallbackResults : 0);
	}

	private StatementRequestProcessingResult processEmptyStatementResult(BankAccount bankAccount, StatementRequest statementRequest,
			List<AccountStatement> savedStatements, int emptyFallbackResults) {
		if (statementRequest.exactStatement()) {
			return StatementRequestProcessingResult.proceed(emptyFallbackResults);
		}

		int updatedEmptyFallbackResults = emptyFallbackResults + 1;
		if (updatedEmptyFallbackResults < REDOWNLOAD_EMPTY_RESULT_LIMIT) {
			return StatementRequestProcessingResult.proceed(updatedEmptyFallbackResults);
		}

		log.info("Stopping account statement redownload for account id {} after {} empty fallback request(s).", bankAccount.getId(),
				updatedEmptyFallbackResults);
		return StatementRequestProcessingResult.stop(StatementRetrievalBatchResult.success(savedStatements), updatedEmptyFallbackResults);
	}

	private StatementRetrievalBatchResult retrieveSingleStatement(StatementRequestContext context, StatementRequest statementRequest,
			int requestPosition, int requestCount) {
		BankAccount bankAccount = context.bankAccount();
		HbciSessionRunner.HbciSession session = context.session();
		HBCIJob<GVRKontoauszug> statementJob = createStatementJob(session.handler(), context.statementJobName(), context.konto(), statementRequest.year(),
				statementRequest.number());
		session.callback().registerJobDescription(statementJob, getText(UI_JOB_STATEMENT, Integer.toString(requestPosition),
				Integer.toString(requestCount), session.callback().getAccountDescription(bankAccount)));
		HBCIExecStatus status = session.handler().execute();
		if (!status.isOK()) {
			if (HbciStatusMessageExtractor.containsWrongPinFeedback(status)) {
				session.callback().handleFailure(status.getErrorString());
				return StatementRetrievalBatchResult.wrongPinFailure();
			}
			log.info("No redownloadable account statement returned for account id {}, statement {}/{}. HBCI status: {}", bankAccount.getId(),
					statementRequest.number(), statementRequest.year(), status);
			return StatementRetrievalBatchResult.success(List.of());
		}

		GVRKontoauszug statementResult = statementJob.getJobResult();
		if (statementResult == null || !statementResult.isOK()) {
			log.info("No redownloadable account statement result returned for account id {}, statement {}/{}: {}", bankAccount.getId(),
					statementRequest.number(), statementRequest.year(), statementResult);
			return StatementRetrievalBatchResult.success(List.of());
		}

		List<GVRKontoauszugEntry> entries = readStatementEntries(List.of(statementResult));
		if (entries.isEmpty()) {
			return StatementRetrievalBatchResult.success(List.of());
		}

		List<BankAccountStatement> savedStatements = saveStatements(bankAccount, entries, context.statementJobName(),
				context.retrievedStatementKeys());
		boolean receiptsOk = acknowledgeSavedStatementsIfEnabled(session.callback(), session.handler(), savedStatements);
		List<AccountStatement> accountStatements = toAccountStatements(savedStatements);
		return receiptsOk ? StatementRetrievalBatchResult.success(accountStatements) : StatementRetrievalBatchResult.failure(accountStatements);
	}

	private boolean shouldRetrieveStatementOverview(List<BankAccountStatement> storedStatements) {
		return AccountStatementSettings.isDownloadOverviewEnabled()
				|| !AccountStatementRequestPlanner.hasStoredStatementNumbers(storedStatements);
	}

	private boolean isStatementOverviewSupported(HBCIHandler handler, BankAccount bankAccount) {
		if (isSupported(handler.getSupportedLowlevelJobs(), STATEMENT_OVERVIEW_JOB)) {
			return true;
		}
		return hasBusinessCase(bankAccount, OVERVIEW_BUSINESS_CASE);
	}

	private boolean hasBusinessCase(BankAccount bankAccount, String businessCaseCode) {
		BankAccount resolvedAccount = bankAccount;
		if (bankAccount != null && bankAccount.getId() > 0) {
			BankAccount accountFromDb = dbController.getByIdFull(BankAccount.class, bankAccount.getId());
			if (accountFromDb != null) {
				resolvedAccount = accountFromDb;
			}
		}
		List<BusinessCase> businessCases = resolvedAccount != null ? resolvedAccount.getAllowedBusinessCases() : null;
		if (businessCases == null || businessCases.isEmpty()) {
			return false;
		}
		String normalizedBusinessCaseCode = normalizeBusinessCaseCode(businessCaseCode);
		for (BusinessCase businessCase : businessCases) {
			if (normalizedBusinessCaseCode.equals(normalizeBusinessCaseCode(businessCase.getCaseValue()))) {
				return true;
			}
		}
		return false;
	}

	private String normalizeBusinessCaseCode(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
	}

	private List<HBCIJob<GVRKontoauszug>> createStatementJobs(HBCIHandler handler, String statementJobName, Konto konto, List<Integer> years,
			GBankingHBCICallback callback, String accountName) {
		if (years.isEmpty()) {
			HBCIJob<GVRKontoauszug> job = createStatementJob(handler, statementJobName, konto, null, null);
			callback.registerJobDescription(job, getText(UI_JOB_STATEMENT, "1", "1", accountName));
			return List.of(job);
		}

		List<HBCIJob<GVRKontoauszug>> jobs = new ArrayList<>();
		for (int yearIndex = 0; yearIndex < years.size(); yearIndex++) {
			HBCIJob<GVRKontoauszug> job = createStatementJob(handler, statementJobName, konto, years.get(yearIndex), null);
			callback.registerJobDescription(job, getText(UI_JOB_STATEMENT, Integer.toString(yearIndex + 1),
					Integer.toString(years.size()), accountName));
			jobs.add(job);
		}
		return jobs;
	}

	private HBCIJob<GVRKontoauszug> createStatementJob(HBCIHandler handler, String statementJobName, Konto konto, Integer year, Integer statementNumber) {
		HBCIJob<GVRKontoauszug> job = hbciSupport.newHbciJob(handler, statementJobName);
		job.setParam("my", konto);
		if (year != null) {
			job.setParam("year", Integer.toString(year));
		}
		if (statementNumber != null) {
			job.setParam("idx", Integer.toString(statementNumber));
		}
		job.addToQueue();
		log.debug("Queued HBCI account statement job {}, year={}, idx={}.", statementJobName, year, statementNumber);
		return job;
	}

	private List<Integer> determineRetrievalYears(BankAccount bankAccount) {
		if (!listAccountStatements(bankAccount).isEmpty()) {
			return List.of();
		}

		int currentYear = Year.now(ZoneId.systemDefault()).getValue();
		List<Integer> years = AccountStatementRequestPlanner.createInitialRetrievalYears(currentYear);
		log.info("No stored account statements found for account id {}, requesting years {}.", bankAccount.getId(), years);
		return years;
	}

	private String resolveStatementJobName(HBCIHandler handler) {
		Properties supportedJobs = handler.getSupportedLowlevelJobs();
		if (isSupported(supportedJobs, STATEMENT_JOB_PDF)) {
			return STATEMENT_JOB_PDF;
		}
		if (isSupported(supportedJobs, STATEMENT_JOB)) {
			return STATEMENT_JOB;
		}
		throw new GBankingException("Kontoauszuege werden von diesem Bankzugang nicht unterstuetzt.");
	}

	private boolean isSupported(Properties supportedJobs, String jobName) {
		return supportedJobs != null && supportedJobs.containsKey(jobName);
	}

	private List<GVRKontoauszug> readStatementResults(List<HBCIJob<GVRKontoauszug>> statementJobs) {
		List<GVRKontoauszug> results = new ArrayList<>();
		for (HBCIJob<GVRKontoauszug> statementJob : statementJobs) {
			results.add(statementJob.getJobResult());
		}
		return results;
	}

	private List<GVRKontoauszugEntry> readStatementEntries(List<GVRKontoauszug> statementResults) {
		List<GVRKontoauszugEntry> allEntries = new ArrayList<>();
		for (GVRKontoauszug statementResult : statementResults) {
			addStatementEntries(allEntries, statementResult);
		}
		return allEntries;
	}

	private void addStatementEntries(List<GVRKontoauszugEntry> allEntries, GVRKontoauszug statementResult) {
		if (statementResult == null) {
			log.debug("No account statement result returned.");
			return;
		}
		if (!statementResult.isOK()) {
			log.error("Error in retrieving account statements: {}", statementResult);
			return;
		}

		List<GVRKontoauszugEntry> entries = statementResult.getEntries();
		log.info("Received {} account statement entries from HBCI.", entries != null ? entries.size() : 0);
		if (entries != null) {
			allEntries.addAll(entries);
		}
	}

	List<BankAccountStatement> saveStatements(BankAccount bankAccount, List<GVRKontoauszugEntry> entries, String sourceJob,
			Set<String> retrievedStatementKeys) {
		LocalDateTime retrievedAt = LocalDateTime.now(ZoneId.systemDefault());
		Set<String> existingFileNames = existingFileNames(bankAccount);
		List<BankAccountStatement> storedStatements = new ArrayList<>(listAccountStatementDaos(bankAccount));
		List<BankAccountStatement> savedStatements = new ArrayList<>();
		for (GVRKontoauszugEntry entry : entries) {
			BankAccountStatement statement = saveStatement(bankAccount, entry, sourceJob, retrievedAt, existingFileNames, storedStatements,
					retrievedStatementKeys);
			if (statement != null) {
				savedStatements.add(statement);
				existingFileNames.add(statement.getFileName());
				replaceStoredStatement(storedStatements, statement);
			}
		}
		return savedStatements;
	}

	private BankAccountStatement saveStatement(BankAccount bankAccount, GVRKontoauszugEntry entry, String sourceJob, LocalDateTime retrievedAt,
			Set<String> existingFileNames, List<BankAccountStatement> storedStatements, Set<String> retrievedStatementKeys) {
		if (bankAccount == null || entry == null || entry.getData() == null || entry.getData().length == 0) {
			return null;
		}

		List<String> statementKeys = statementSessionKeys(bankAccount, entry);
		if (isDuplicateInRetrievalSession(retrievedStatementKeys, statementKeys)) {
			log.info("Skipping duplicate account statement for account id {} in current retrieval session.", bankAccount.getId());
			return null;
		}

		BankAccountStatement storedStatement = findMatchingStoredStatement(bankAccount, entry, storedStatements);
		if (storedStatement != null && statementFileExists(storedStatement)) {
			log.info("Skipping account statement {}/{} for account id {} because it already exists as {}.", entry.getNumber(), entry.getYear(),
					bankAccount.getId(), storedStatement.getFileName());
			return null;
		}

		Path statementFile = storedStatement != null && storedStatement.getFileName() != null && !storedStatement.getFileName().isBlank()
				? statementFileManager.saveAs(entry, storedStatement.getFileName())
				: statementFileManager.save(bankAccount, entry, existingFileNames);
		BankAccountStatement statement = toBankAccountStatement(bankAccount, entry, sourceJob, retrievedAt, statementFile, storedStatement);
		BankAccountStatement savedStatement = dbController.insertOrUpdate(statement);
		if (savedStatement != null) {
			statement = savedStatement;
		}
		log.info("Stored account statement {} for account id {}.", statement.getFileName(), bankAccount.getId());
		return statement;
	}

	private BankAccountStatement toBankAccountStatement(BankAccount bankAccount, GVRKontoauszugEntry entry, String sourceJob, LocalDateTime retrievedAt,
			Path statementFile, BankAccountStatement storedStatement) {
		BankAccountStatement statement = new BankAccountStatement();
		if (storedStatement != null && storedStatement.getId() > 0) {
			statement.setId(storedStatement.getId());
		}
		statement.setAccountId(bankAccount.getId());
		statement.setAccountName(Objects.toString(bankAccount.getAccountName(), ""));
		statement.setFileName(statementFileManager.logicalFileName(statementFile));
		statement.setFormat(formatName(entry.getFormat()));
		statement.setRetrievedAt(retrievedAt);
		statement.setStatementDate(TypeConverter.toLocalDate(entry.getDate()));
		statement.setStartDate(TypeConverter.toLocalDate(entry.getStartDate()));
		statement.setEndDate(TypeConverter.toLocalDate(entry.getEndDate()));
		statement.setYear(entry.getYear());
		statement.setNumber(entry.getNumber());
		statement.setSize(entry.getData().length);
		statement.setIban(firstText(entry.getIBAN(), bankAccount.getIban()));
		statement.setBic(firstText(entry.getBIC(), bankAccount.getBic()));
		statement.setSourceJob(Objects.toString(sourceJob, ""));
		statement.setReceiptAvailable(entry.getReceipt() != null && entry.getReceipt().length > 0);
		statement.setReceipt(entry.getReceipt());
		statement.setAcknowledged(storedStatement != null && storedStatement.isAcknowledged());
		statement.setAcknowledgedAt(storedStatement != null && storedStatement.isAcknowledged() ? storedStatement.getAcknowledgedAt() : null);
		statement.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return statement;
	}

	private boolean isRequestAlreadyRetrieved(BankAccount bankAccount, StatementRequest statementRequest, Set<String> retrievedStatementKeys) {
		if (statementRequest == null || statementRequest.year() == null || statementRequest.number() <= 0) {
			return false;
		}
		return retrievedStatementKeys != null
				&& retrievedStatementKeys.contains(statementNumberKey(bankAccount.getId(), statementRequest.year(), statementRequest.number()));
	}

	private boolean isDuplicateInRetrievalSession(Set<String> retrievedStatementKeys, List<String> statementKeys) {
		if (retrievedStatementKeys == null || statementKeys == null || statementKeys.isEmpty()) {
			return false;
		}
		for (String statementKey : statementKeys) {
			if (retrievedStatementKeys.contains(statementKey)) {
				return true;
			}
		}
		retrievedStatementKeys.addAll(statementKeys);
		return false;
	}

	private List<String> statementSessionKeys(BankAccount bankAccount, GVRKontoauszugEntry entry) {
		List<String> statementKeys = new ArrayList<>();
		int year = resolvedStatementYear(entry.getYear(), TypeConverter.toLocalDate(entry.getDate()));
		if (year > 0 && entry.getNumber() > 0) {
			statementKeys.add(statementNumberKey(bankAccount.getId(), year, entry.getNumber()));
		}
		statementKeys.add(statementDataKey(bankAccount.getId(), entry.getData()));
		return statementKeys;
	}

	private String statementNumberKey(int accountId, int year, int number) {
		return accountId + ":statement:" + year + ":" + number;
	}

	private String statementDataKey(int accountId, byte[] data) {
		return accountId + ":data:" + sha256(data);
	}

	private String sha256(byte[] data) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return Base64.getEncoder().encodeToString(digest.digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available.", e);
		}
	}

	private BankAccountStatement findMatchingStoredStatement(BankAccount bankAccount, GVRKontoauszugEntry entry,
			List<BankAccountStatement> storedStatements) {
		BankAccountStatement missingFileStatement = null;
		if (storedStatements == null) {
			return null;
		}
		for (BankAccountStatement storedStatement : storedStatements) {
			if (!isMatchingStoredStatement(bankAccount, entry, storedStatement)) {
				continue;
			}
			if (statementFileExists(storedStatement)) {
				return storedStatement;
			}
			if (missingFileStatement == null) {
				missingFileStatement = storedStatement;
			}
		}
		return missingFileStatement;
	}

	private boolean isMatchingStoredStatement(BankAccount bankAccount, GVRKontoauszugEntry entry, BankAccountStatement storedStatement) {
		if (bankAccount == null || entry == null || storedStatement == null || storedStatement.getAccountId() != bankAccount.getId()) {
			return false;
		}
		if (entry.getNumber() <= 0 || storedStatement.getNumber() <= 0 || entry.getNumber() != storedStatement.getNumber()) {
			return false;
		}
		int entryYear = resolvedStatementYear(entry.getYear(), TypeConverter.toLocalDate(entry.getDate()));
		int storedYear = resolvedStatementYear(storedStatement.getYear(), storedStatement.getStatementDate());
		return entryYear > 0 && storedYear > 0 && entryYear == storedYear;
	}

	private int resolvedStatementYear(int explicitYear, LocalDate statementDate) {
		if (explicitYear > 0) {
			return explicitYear;
		}
		return statementDate != null ? statementDate.getYear() : 0;
	}

	private boolean statementFileExists(BankAccountStatement statement) {
		return statement != null && statement.getFileName() != null && !statement.getFileName().isBlank()
				&& Files.isRegularFile(statementFileManager.resolve(statement.getFileName()));
	}

	private void replaceStoredStatement(List<BankAccountStatement> storedStatements, BankAccountStatement statement) {
		if (storedStatements == null || statement == null || statement.getId() <= 0) {
			return;
		}
		for (int i = 0; i < storedStatements.size(); i++) {
			BankAccountStatement storedStatement = storedStatements.get(i);
			if (storedStatement != null && storedStatement.getId() == statement.getId()) {
				storedStatements.set(i, statement);
				return;
			}
		}
		storedStatements.add(statement);
	}

	private boolean acknowledgeSavedStatementsIfEnabled(GBankingHBCICallback callback, HBCIHandler handler,
			List<BankAccountStatement> savedStatements) {
		if (!AccountStatementSettings.isAutoAcknowledgeEnabled()) {
			return true;
		}
		boolean acknowledged = acknowledgeReceipts(callback, handler, readReceipts(savedStatements));
		if (acknowledged) {
			markAcknowledged(savedStatements);
		}
		return acknowledged;
	}

	private AccountStatementAcknowledgementResult acknowledgeAccountStatements(BankAccount bankAccount, HbciSessionRunner.HbciSession session) {
		logHandler.logRetrivedBankAccessInfo(session.passport(), false);
		List<BankAccountStatement> statementReceipts = listUnacknowledgedReceipts(bankAccount);
		if (statementReceipts.isEmpty()) {
			log.info("No unacknowledged account statement receipts found for account id {}.", bankAccount != null ? bankAccount.getId() : null);
			return AccountStatementAcknowledgementResult.success(0);
		}

		List<byte[]> receipts = new ArrayList<>();
		for (BankAccountStatement statementReceipt : statementReceipts) {
			receipts.add(statementReceipt.getReceipt());
		}

		boolean acknowledged = acknowledgeReceipts(session.callback(), session.handler(), receipts);
		if (!acknowledged) {
			return AccountStatementAcknowledgementResult.failure();
		}
		int acknowledgedCount = markAcknowledged(statementReceipts);
		return AccountStatementAcknowledgementResult.success(acknowledgedCount);
	}

	private List<byte[]> readReceipts(List<BankAccountStatement> statements) {
		List<byte[]> receipts = new ArrayList<>();
		if (statements == null) {
			return receipts;
		}
		for (BankAccountStatement statement : statements) {
			byte[] receipt = statement != null ? statement.getReceipt() : null;
			if (statement != null && !statement.isAcknowledged() && receipt != null && receipt.length > 0) {
				receipts.add(receipt);
			}
		}
		return receipts;
	}

	private List<BankAccountStatement> listAccountStatementDaos(BankAccount bankAccount) {
		if (bankAccount == null || bankAccount.getId() <= 0) {
			return List.of();
		}
		List<BankAccountStatement> statements = dbController.getAllByParentFull(BankAccountStatement.class, bankAccount.getId());
		return statements != null ? statements : List.of();
	}

	private List<BankAccountStatement> listExistingAccountStatementDaos(BankAccount bankAccount) {
		List<BankAccountStatement> existingStatements = new ArrayList<>();
		for (BankAccountStatement statement : listAccountStatementDaos(bankAccount)) {
			Path statementFile = statementFileManager.resolve(statement.getFileName());
			if (Files.isRegularFile(statementFile)) {
				existingStatements.add(statement);
			} else {
				log.warn("Account statement file referenced by database does not exist: {}", statementFile);
			}
		}
		return existingStatements;
	}

	private List<BankAccountStatement> listUnacknowledgedReceipts(BankAccount bankAccount) {
		List<BankAccountStatement> unacknowledgedStatements = new ArrayList<>();
		for (BankAccountStatement statement : listAccountStatementDaos(bankAccount)) {
			byte[] receipt = statement.getReceipt();
			if (!statement.isAcknowledged() && statement.isReceiptAvailable() && receipt != null && receipt.length > 0) {
				unacknowledgedStatements.add(statement);
			}
		}
		return unacknowledgedStatements;
	}

	private int markAcknowledged(List<BankAccountStatement> statements) {
		if (statements == null || statements.isEmpty()) {
			return 0;
		}

		int acknowledgedCount = 0;
		LocalDateTime acknowledgedAt = LocalDateTime.now(ZoneId.systemDefault());
		LocalDate updatedAt = LocalDate.now(ZoneId.systemDefault());
		for (BankAccountStatement statement : statements) {
			if (statement == null || statement.isAcknowledged() || !statement.isReceiptAvailable()) {
				continue;
			}
			statement.setAcknowledged(true);
			statement.setAcknowledgedAt(acknowledgedAt);
			statement.setUpdatedAt(updatedAt);
			dbController.insertOrUpdate(statement);
			acknowledgedCount++;
		}
		return acknowledgedCount;
	}

	private Set<String> existingFileNames(BankAccount bankAccount) {
		Set<String> fileNames = new HashSet<>();
		for (BankAccountStatement statement : listAccountStatementDaos(bankAccount)) {
			if (statement.getFileName() != null && !statement.getFileName().isBlank()) {
				fileNames.add(statement.getFileName());
			}
		}
		return fileNames;
	}

	private List<AccountStatement> toAccountStatements(List<BankAccountStatement> statements) {
		List<AccountStatement> accountStatements = new ArrayList<>();
		for (BankAccountStatement statement : statements) {
			accountStatements.add(toAccountStatement(statement));
		}
		return accountStatements;
	}

	private AccountStatement toAccountStatement(BankAccountStatement statement) {
		return new AccountStatement(
				statement.getId(),
				statement.getAccountId(),
				statement.getAccountName(),
				statementFileManager.resolve(statement.getFileName()),
				statement.getFileName(),
				statement.getFormat(),
				statement.getRetrievedAt(),
				statement.getStatementDate(),
				statement.getStartDate(),
				statement.getEndDate(),
				statement.getYear(),
				statement.getNumber(),
				statement.getSize(),
				statement.getIban(),
				statement.getBic(),
				statement.getSourceJob(),
				statement.isReceiptAvailable(),
				statement.isAcknowledged(),
				statement.getAcknowledgedAt());
	}

	private boolean acknowledgeReceipts(GBankingHBCICallback callback, HBCIHandler handler, List<byte[]> receipts) {
		if (receipts.isEmpty()) {
			return true;
		}
		if (!isSupported(handler.getSupportedLowlevelJobs(), RECEIPT_JOB)) {
			log.warn("Bank returned {} account statement receipt(s), but HBCI job {} is not supported.", receipts.size(), RECEIPT_JOB);
			return false;
		}

		for (int receiptIndex = 0; receiptIndex < receipts.size(); receiptIndex++) {
			HBCIJob<HBCIJobResult> receiptJob = hbciSupport.newHbciJob(handler, RECEIPT_JOB);
			receiptJob.setParam("receipt", receiptToString(receipts.get(receiptIndex)));
			receiptJob.addToQueue();
			callback.registerJobDescription(receiptJob, getText("UI_DIALOG_HBCI_JOB_STATEMENT_RECEIPT", Integer.toString(receiptIndex + 1),
					Integer.toString(receipts.size())));
		}

		HBCIExecStatus receiptStatus = handler.execute();
		if (!receiptStatus.isOK()) {
			log.error("HBCI account statement receipt error, status: {}", receiptStatus);
		}
		return receiptStatus.isOK();
	}

	private List<AccountStatement> combine(List<AccountStatement> first, List<AccountStatement> second) {
		List<AccountStatement> combined = new ArrayList<>();
		combined.addAll(first);
		combined.addAll(second);
		return combined;
	}

	private String receiptToString(byte[] receipt) {
		return new String(receipt, StandardCharsets.ISO_8859_1);
	}

	private boolean areStatementResultsOk(List<GVRKontoauszug> statementResults) {
		return !statementResults.isEmpty() && statementResults.stream().allMatch(result -> result != null && result.isOK());
	}

	private Konto findMatchingKonto(HBCIPassport passport, BankAccount bankAccount) {
		Konto[] konten = passport.getAccounts();
		if (konten == null || konten.length == 0) {
			log.error("No accounts were found on bank site");
			return null;
		}
		log.info("Number of accounts found: {}", konten.length);
		for (Konto konto : konten) {
			if (hbciKontosMatches(bankAccount, konto)) {
				return konto;
			}
		}
		return null;
	}

	private boolean hbciKontosMatches(BankAccount bankAccount, Konto konto) {
		return bankAccount != null && konto != null
				&& (bankAccount.getIban() != null && bankAccount.getIban().equalsIgnoreCase(konto.iban)
						|| bankAccount.getNumber() != null && bankAccount.getNumber().equalsIgnoreCase(konto.number));
	}

	private String formatName(Format format) {
		return format != null ? format.name() : "UNKNOWN";
	}

	private String firstText(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private void setParamIfPresent(HBCIJob<?> job, String parameterName, String value) {
		if (value != null && !value.isBlank()) {
			job.setParam(parameterName, value);
		}
	}

	private record StatementRetrievalBatchResult(boolean successful, boolean wrongPin, List<AccountStatement> statements) {

		private static StatementRetrievalBatchResult success(List<AccountStatement> statements) {
			return new StatementRetrievalBatchResult(true, false, statements != null ? List.copyOf(statements) : List.of());
		}

		private static StatementRetrievalBatchResult failure(List<AccountStatement> statements) {
			return new StatementRetrievalBatchResult(false, false, statements != null ? List.copyOf(statements) : List.of());
		}

		private static StatementRetrievalBatchResult wrongPinFailure() {
			return new StatementRetrievalBatchResult(false, true, List.of());
		}
	}

	private record StatementOverviewResult(boolean successful, boolean wrongPin, List<StatementOverviewEntry> entries) {

		private static StatementOverviewResult success(List<StatementOverviewEntry> entries) {
			return new StatementOverviewResult(true, false, entries != null ? List.copyOf(entries) : List.of());
		}

		private static StatementOverviewResult wrongPinFailure() {
			return new StatementOverviewResult(false, true, List.of());
		}
	}

	private record StatementRequestContext(BankAccount bankAccount, HbciSessionRunner.HbciSession session, Konto konto, String statementJobName,
			Set<String> retrievedStatementKeys) {
	}

	private record StatementRequestProcessingResult(StatementRetrievalBatchResult terminalResult, int emptyFallbackResults) {

		private static StatementRequestProcessingResult proceed(int emptyFallbackResults) {
			return new StatementRequestProcessingResult(null, emptyFallbackResults);
		}

		private static StatementRequestProcessingResult stop(StatementRetrievalBatchResult terminalResult, int emptyFallbackResults) {
			return new StatementRequestProcessingResult(terminalResult, emptyFallbackResults);
		}
	}

}
