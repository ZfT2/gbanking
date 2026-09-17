package de.zft2.gbanking.service.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRWPDepotList;
import org.kapott.hbci.GV_Result.GVRWPDepotList.Entry;
import org.kapott.hbci.GV_Result.GVRWPDepotList.Entry.Gattung;
import org.kapott.hbci.GV_Result.GVRWPDepotList.Entry.Gattung.SubSaldo;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.BigDecimalValue;
import org.kapott.hbci.structures.Konto;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockCustodyType;
import de.zft2.gbanking.db.dao.enu.StockDataSourceType;
import de.zft2.gbanking.db.dao.enu.StockIdentifierType;
import de.zft2.gbanking.db.dao.enu.StockImportRecordStatus;
import de.zft2.gbanking.db.dao.enu.StockImportStatus;
import de.zft2.gbanking.db.dao.enu.StockNumericValueType;
import de.zft2.gbanking.db.dao.enu.StockPriceBasis;
import de.zft2.gbanking.db.dao.enu.StockPriceType;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityLegRole;
import de.zft2.gbanking.db.dao.enu.StockSecurityState;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;
import de.zft2.gbanking.db.dao.enu.StockStatementStatus;
import de.zft2.gbanking.db.dao.enu.StockSubBalanceQualifier;
import de.zft2.gbanking.db.dao.enu.StockTransactionStatus;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockExchangeRate;
import de.zft2.gbanking.db.dao.stock.StockExchangeRateSource;
import de.zft2.gbanking.db.dao.stock.StockImportBatch;
import de.zft2.gbanking.db.dao.stock.StockImportRecord;
import de.zft2.gbanking.db.dao.stock.StockPortfolio;
import de.zft2.gbanking.db.dao.stock.StockPortfolioPosition;
import de.zft2.gbanking.db.dao.stock.StockPortfolioSettlementAccount;
import de.zft2.gbanking.db.dao.stock.StockPortfolioStatement;
import de.zft2.gbanking.db.dao.stock.StockPortfolioStatementPosition;
import de.zft2.gbanking.db.dao.stock.StockPortfolioStatementSubBalance;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.db.dao.stock.StockSecurityIdentifier;
import de.zft2.gbanking.db.dao.stock.StockSecurityPrice;
import de.zft2.gbanking.db.dao.stock.StockSecurityPriceSource;
import de.zft2.gbanking.db.dao.stock.StockTransaction;
import de.zft2.gbanking.db.dao.stock.StockTransactionSecurityLeg;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.hbci.HbciStatusMessageExtractor;
import de.zft2.gbanking.logging.GBankingLoggingHandler;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.HbciSessionRunner;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;

public class StockPortfolioFinTsService extends AbstractDbService {

	private static final Logger log = LogManager.getLogger(StockPortfolioFinTsService.class);

	private static final String FINTS_SOURCE_CODE = "FINTS";
	private static final String FINTS_IMPORTER_KEY = "FINTS_WPDEPOTLIST";
	private static final String FINTS_FORMAT = "MT535";
	private static final String FINTS_IMPORTER_VERSION = "1";
	private static final String PORTFOLIO_JOB = "WPDepotList";
	private static final int QUANTITY_SCALE = StockNumericValueType.QUANTITY.getScaleDigits();
	private static final int PRICE_SCALE = StockNumericValueType.PRICE.getScaleDigits();
	private static final int EXCHANGE_RATE_SCALE = StockNumericValueType.FACTOR.getScaleDigits();
	private static final Set<String> SECURITY_QUANTITY_UNITS = Set.of("STK", "STCK");

	private final StockPortfolioService portfolioService = ServiceRegistry.getService(StockPortfolioService.class);
	private final GBankingLoggingHandler logHandler = GBankingLoggingHandler.getInstance();
	private final HbciSessionRunner hbciSessionRunner = new HbciSessionRunner();

	public PortfolioSummary findPortfolioForAccount(int accountId) {
		StockPortfolio portfolio = dbController.getAll(StockPortfolio.class).stream()
				.filter(candidate -> candidate.getAccountId() == accountId)
				.findFirst().orElse(null);
		return portfolio != null ? findPortfolioSummary(portfolio.getId()) : null;
	}

	public List<PortfolioSummary> getPortfoliosForBankAccess(int bankAccessId) {
		Map<Integer, BankAccount> portfolioAccounts = dbController.getAll(BankAccount.class).stream()
				.filter(account -> Objects.equals(account.getBankAccessId(), bankAccessId))
				.filter(account -> account.getAccountType() == AccountType.DEPOT)
				.collect(Collectors.toMap(BankAccount::getId, account -> account));
		return dbController.getAll(StockPortfolio.class).stream()
				.filter(portfolio -> portfolioAccounts.containsKey(portfolio.getAccountId()))
				.map(portfolio -> findPortfolioSummary(portfolio.getId()))
				.filter(Objects::nonNull)
				.toList();
	}

	public BankAccount getPortfolioAccount(PortfolioSummary portfolioSummary) {
		if (portfolioSummary == null) {
			return null;
		}
		StockPortfolio portfolio = dbController.getById(StockPortfolio.class, portfolioSummary.portfolioId());
		return portfolio != null ? dbController.getByIdFull(BankAccount.class, portfolio.getAccountId()) : null;
	}

	public List<BankAccount> resolveAccountsForUpdate(List<BankAccount> selectedAccounts) {
		if (selectedAccounts == null || selectedAccounts.isEmpty()) {
			return List.of();
		}
		if (!StockPortfolioSettings.isSettlementAccountRetrievalEnabled()) {
			return List.copyOf(selectedAccounts);
		}

		Map<Integer, BankAccount> accountsById = new LinkedHashMap<>();
		selectedAccounts.forEach(account -> accountsById.put(account.getId(), account));
		selectedAccounts.stream()
				.filter(account -> account.getAccountType() == AccountType.DEPOT)
				.map(account -> findPortfolioForAccount(account.getId()))
				.filter(Objects::nonNull)
				.map(portfolio -> dbController.getByIdFull(BankAccount.class, portfolio.settlementAccountId()))
				.filter(Objects::nonNull)
				.forEach(account -> accountsById.putIfAbsent(account.getId(), account));
		return List.copyOf(accountsById.values());
	}

	public PortfolioSummary ensurePortfolio(int depotAccountId, int settlementAccountId) {
		BankAccount storedDepot = requireAccount(depotAccountId, AccountType.DEPOT, "Depot");
		PortfolioSummary existing = findPortfolioForAccount(storedDepot.getId());
		if (existing != null) {
			return existing;
		}

		BankAccount storedSettlement = requireSettlementAccount(settlementAccountId);
		int portfolioId = dbController.executeInTransaction(() -> createPortfolio(storedDepot, storedSettlement));
		return findPortfolioSummary(portfolioId);
	}

	public StockPortfolioFinTsRetrievalResult retrievePortfolio(PortfolioSummary portfolioSummary, char[] pin) {
		BankAccount account = getPortfolioAccount(portfolioSummary);
		if (account == null) {
			HbciSessionRunner.clearSecret(pin);
			return StockPortfolioFinTsRetrievalResult.failure("Das Depotkonto wurde nicht gefunden.");
		}

		BankAccess bankAccess = bankAccessService().initBankAccess(account, pin);
		if (bankAccess == null) {
			HbciSessionRunner.clearSecret(pin);
			return StockPortfolioFinTsRetrievalResult.failure("Für das Depot ist kein aktiver FinTS-Zugang hinterlegt.");
		}

		try {
			return hbciSessionRunner.run(bankAccess, pin,
					session -> retrievePortfolio(portfolioSummary.portfolioId(), account, session));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return StockPortfolioFinTsRetrievalResult.failure("Der FinTS-Depotabruf wurde abgebrochen.");
		} catch (HBCI_Exception exception) {
			if (HbciStatusMessageExtractor.containsWrongPinFeedback(exception)) {
				return StockPortfolioFinTsRetrievalResult.wrongPinFailure(exception.getMessage());
			}
			throw exception;
		} finally {
			HbciSessionRunner.clearSecret(pin);
		}
	}

	private StockPortfolioFinTsRetrievalResult retrievePortfolio(int portfolioId, BankAccount account,
			HbciSessionRunner.HbciSession session) {
		logHandler.logRetrivedBankAccessInfo(session.passport(), false);
		Konto konto = findMatchingAccount(session.passport(), account);
		if (konto == null) {
			throw new GBankingException("Das Depotkonto konnte im FinTS-Zugang nicht gefunden werden.");
		}
		konto.bic = firstNonBlank(account.getBic(), konto.bic);
		logHandler.logRetrievedAccountInfo(konto);

		HBCIJob<GVRWPDepotList> job = bankAccessService().newHbciJob(session.handler(), PORTFOLIO_JOB);
		job.setParam("my", konto);
		job.addToQueue();
		session.callback().registerJobDescription(job,
				getText("UI_DIALOG_HBCI_JOB_STOCK_PORTFOLIO", accountDisplayName(account)));

		HBCIExecStatus status = session.handler().execute();
		session.passport().saveChanges();
		if (!status.isOK()) {
			session.callback().handleFailure(status.getErrorString());
			return HbciStatusMessageExtractor.containsWrongPinFeedback(status)
					? StockPortfolioFinTsRetrievalResult.wrongPinFailure(status.getErrorString())
					: StockPortfolioFinTsRetrievalResult.failure(status.getErrorString());
		}

		GVRWPDepotList jobResult = job.getJobResult();
		if (jobResult == null || !jobResult.isOK()) {
			return StockPortfolioFinTsRetrievalResult.failure("Der FinTS-Zugang hat keinen gültigen Depotbestand geliefert.");
		}
		Entry entry = findMatchingEntry(jobResult.getEntries(), account);
		if (entry == null) {
			return StockPortfolioFinTsRetrievalResult.failure("Im FinTS-Ergebnis wurde kein passender Depotbestand gefunden.");
		}
		return persistPortfolioStatement(portfolioId, account, entry);
	}

	public StockPortfolioFinTsRetrievalResult persistPortfolioStatement(int portfolioId, BankAccount account, Entry entry) {
		Objects.requireNonNull(entry, "entry");
		BankAccount storedAccount = requireAccount(account, AccountType.DEPOT, "Depot");
		StockPortfolio portfolio = dbController.getById(StockPortfolio.class, portfolioId);
		if (portfolio == null || portfolio.getAccountId() != storedAccount.getId()) {
			throw new GBankingException("Der FinTS-Depotbestand gehört nicht zum ausgewählten Depot.");
		}
		String fingerprint = createSnapshotFingerprint(storedAccount, entry);
		String externalReference = FINTS_IMPORTER_KEY + ':' + fingerprint;
		StockDataSource source = getOrCreateFinTsSource();
		List<StockPortfolioStatement> statements = dbController.getAllByParent(StockPortfolioStatement.class, portfolioId);
		if (statements.stream().anyMatch(statement -> externalReference.equals(statement.getExternalReference()))) {
			return StockPortfolioFinTsRetrievalResult.unchanged(entry.getEntries().length);
		}

		return dbController.executeInTransaction(() -> persistPortfolioStatement(
				portfolioId, storedAccount, entry, source, fingerprint, externalReference));
	}

	private StockPortfolioFinTsRetrievalResult persistPortfolioStatement(int portfolioId, BankAccount account, Entry entry,
			StockDataSource source, String fingerprint, String externalReference) {
		LocalDateTime statementAt = toLocalDateTime(entry.timestamp, LocalDateTime.now());
		StockImportBatch batch = createImportBatch(source, fingerprint);
		StockImportRecord record = createImportRecord(batch, fingerprint, externalReference);
		StockPortfolioStatement statement = createStatement(portfolioId, source, record, entry, account, statementAt, externalReference);

		Map<PositionKey, Long> currentQuantities = currentQuantities(portfolioId);
		Map<PositionKey, Long> reportedQuantities = new LinkedHashMap<>();
		int positionCount = 0;
		int adjustmentCount = 0;
		Long reportedAccruedInterest = null;
		Currency accruedInterestCurrency = null;
		boolean mixedAccruedInterestCurrencies = false;
		for (Gattung gattung : entry.getEntries()) {
			StockSecurity security = resolveSecurity(source, account, gattung, statement.getValueCurrency());
			MappedPosition mapped = mapPosition(gattung, statement.getValueCurrency());
			PositionKey key = new PositionKey(security.getId(), mapped.quantityType());
			if (reportedQuantities.putIfAbsent(key, mapped.quantityE9()) != null) {
				throw new GBankingException("Der FinTS-Depotbestand enthält das Wertpapier mehrfach mit derselben Bestandsart.");
			}
			StockPortfolioStatementPosition position = saveStatementPosition(statement, security, mapped);
			saveSubBalances(position, gattung.getEntries());
			savePrice(source, record, security, gattung, mapped, statementAt, externalReference);
			saveExchangeRate(source, record, gattung, statementAt, externalReference);
			if (mapped.accruedInterestMinor() != null) {
				if (accruedInterestCurrency == null || accruedInterestCurrency == mapped.valueCurrency()) {
					accruedInterestCurrency = mapped.valueCurrency();
					reportedAccruedInterest = Math.addExact(
							reportedAccruedInterest != null ? reportedAccruedInterest : 0, mapped.accruedInterestMinor());
				} else {
					mixedAccruedInterestCurrencies = true;
				}
			}
			positionCount++;

			long difference = Math.subtractExact(mapped.quantityE9(), currentQuantities.getOrDefault(key, 0L));
			if (difference != 0) {
				createReconciliationTransaction(portfolioId, source, record, statement, security, mapped, difference,
						statementAt, fingerprint);
				adjustmentCount++;
			}
		}

		for (Map.Entry<PositionKey, Long> current : currentQuantities.entrySet()) {
			if (current.getValue() != 0 && !reportedQuantities.containsKey(current.getKey())) {
				StockSecurity security = dbController.getById(StockSecurity.class, current.getKey().securityId());
				MappedPosition emptyPosition = MappedPosition.empty(current.getKey().quantityType());
				createReconciliationTransaction(portfolioId, source, record, statement, security, emptyPosition,
						Math.negateExact(current.getValue()), statementAt, fingerprint);
				adjustmentCount++;
			}
		}

		if (!mixedAccruedInterestCurrencies && reportedAccruedInterest != null
				&& (statement.getValueCurrency() == null || statement.getValueCurrency() == accruedInterestCurrency)) {
			statement.setReportedAccruedInterestMinor(reportedAccruedInterest);
			statement.setValueCurrency(accruedInterestCurrency);
		}
		statement.setStatementStatus(StockStatementStatus.FINAL);
		dbController.insertOrUpdate(statement);
		batch.setImportStatus(StockImportStatus.IMPORTED);
		batch.setCompletedAt(LocalDateTime.now());
		dbController.insertOrUpdate(batch);
		log.info("Saved FinTS portfolio statement for portfolio id {}, positions={}, adjustments={}",
				portfolioId, positionCount, adjustmentCount);
		return StockPortfolioFinTsRetrievalResult.success(positionCount, adjustmentCount);
	}

	private int createPortfolio(BankAccount depotAccount, BankAccount settlementAccount) {
		LocalDate today = LocalDate.now();
		StockPortfolio portfolio = new StockPortfolio();
		portfolio.setAccountId(depotAccount.getId());
		portfolio.setCurrentSettlementRelationId(0);
		portfolio.setOpenedAt(today);
		dbController.insertOrUpdate(portfolio);

		StockPortfolioSettlementAccount relation = new StockPortfolioSettlementAccount();
		relation.setPortfolioId(portfolio.getId());
		relation.setAccountId(settlementAccount.getId());
		relation.setValidFrom(today);
		dbController.insertOrUpdate(relation);

		portfolio.setCurrentSettlementRelationId(relation.getId());
		dbController.insertOrUpdate(portfolio);
		return portfolio.getId();
	}

	private StockImportBatch createImportBatch(StockDataSource source, String fingerprint) {
		StockImportBatch batch = new StockImportBatch();
		batch.setSourceId(source.getId());
		batch.setImporterKey(FINTS_IMPORTER_KEY);
		batch.setFormatType(FINTS_FORMAT);
		batch.setImporterVersion(FINTS_IMPORTER_VERSION);
		batch.setContentHash(fingerprint);
		batch.setImportStatus(StockImportStatus.RUNNING);
		batch.setStartedAt(LocalDateTime.now());
		return dbController.insertOrUpdate(batch);
	}

	private StockImportRecord createImportRecord(StockImportBatch batch, String fingerprint, String externalReference) {
		StockImportRecord record = new StockImportRecord();
		record.setImportBatchId(batch.getId());
		record.setRecordNumber(1);
		record.setRecordType("PORTFOLIO_STATEMENT");
		record.setExternalReference(externalReference);
		record.setFingerprint(fingerprint);
		record.setRecordStatus(StockImportRecordStatus.IMPORTED);
		return dbController.insertOrUpdate(record);
	}

	private StockPortfolioStatement createStatement(int portfolioId, StockDataSource source, StockImportRecord record,
			Entry entry, BankAccount account, LocalDateTime statementAt, String externalReference) {
		StockPortfolioStatement statement = new StockPortfolioStatement();
		statement.setPortfolioId(portfolioId);
		statement.setSourceId(source.getId());
		statement.setImportRecordId(record.getId());
		statement.setStatementAt(statementAt);
		statement.setStatementStatus(StockStatementStatus.DRAFT);
		statement.setExternalReference(externalReference);
		if (entry.total != null) {
			Currency currency = requireCurrency(entry.total.getCurr(), account.getBaseCurrency(), "Gesamtwert des Depots");
			statement.setReportedTotalValueMinor(toMinor(entry.total.getValue(), currency));
			statement.setValueCurrency(currency);
		}
		return dbController.insertOrUpdate(statement);
	}

	private StockPortfolioStatementPosition saveStatementPosition(StockPortfolioStatement statement,
			StockSecurity security, MappedPosition mapped) {
		StockPortfolioStatementPosition position = new StockPortfolioStatementPosition();
		position.setStatementId(statement.getId());
		position.setSecurityId(security.getId());
		position.setQuantityE9(mapped.quantityE9());
		position.setQuantityType(mapped.quantityType());
		position.setReportedPriceE8(mapped.priceE8());
		position.setPriceCurrency(mapped.priceCurrency());
		position.setReportedAcquisitionPriceE8(mapped.acquisitionPriceE8());
		position.setAcquisitionPriceCurrency(mapped.acquisitionPriceCurrency());
		position.setQuotationType(mapped.quotationType());
		position.setPriceBasis(mapped.priceBasis());
		position.setReportedValueMinor(mapped.valueMinor());
		position.setAccruedInterestMinor(mapped.accruedInterestMinor());
		position.setValueCurrency(mapped.valueCurrency());
		return dbController.insertOrUpdate(position);
	}

	private void saveSubBalances(StockPortfolioStatementPosition position, SubSaldo[] subBalances) {
		for (SubSaldo source : subBalances) {
			if (source.saldo == null) {
				continue;
			}
			StockPortfolioStatementSubBalance target = new StockPortfolioStatementSubBalance();
			target.setStatementPositionId(position.getId());
			target.setQualifier(mapSubBalanceQualifier(source));
			target.setQuantityE9(toScaled(source.saldo.getValue(), QUANTITY_SCALE, "Unterbestand"));
			target.setLocked(source.locked);
			target.setLockedUntil(source.locked ? toLocalDate(source.lockeduntil) : null);
			target.setCustodyCountry(normalizeCountry(source.country));
			target.setCustodyType(StockCustodyType.forInt(source.verwahrung));
			target.setCustodyPlace(trimToNull(source.lager));
			target.setComment(trimToNull(source.comment));
			dbController.insertOrUpdate(target);
		}
	}

	private void createReconciliationTransaction(int portfolioId, StockDataSource source, StockImportRecord record,
			StockPortfolioStatement statement, StockSecurity security, MappedPosition mapped, long difference,
			LocalDateTime statementAt, String snapshotFingerprint) {
		if (security == null) {
			throw new GBankingException("Ein bisheriger Depotbestand verweist auf ein unbekanntes Wertpapier.");
		}
		StockTransaction transaction = new StockTransaction();
		transaction.setPortfolioId(portfolioId);
		transaction.setSourceId(source.getId());
		transaction.setImportRecordId(record.getId());
		transaction.setTransactionType(StockTransactionType.RECONCILIATION_ADJUSTMENT);
		transaction.setTransactionStatus(StockTransactionStatus.SETTLED);
		transaction.setTradeAt(statementAt);
		transaction.setSettlementDueAt(statementAt);
		transaction.setSettledAt(statementAt);
		transaction.setProviderBookedAt(statementAt);
		transaction.setReconciliationStatementId(statement.getId());
		transaction.setFingerprint(snapshotFingerprint + ':' + security.getId() + ':' + mapped.quantityType().getDbStateId());
		dbController.insertOrUpdate(transaction);

		StockTransactionSecurityLeg leg = new StockTransactionSecurityLeg();
		leg.setTransactionId(transaction.getId());
		leg.setLegNumber(1);
		leg.setSecurityId(security.getId());
		leg.setLegRole(StockSecurityLegRole.POSITION);
		leg.setQuantityE9(difference);
		leg.setQuantityType(mapped.quantityType());
		leg.setPriceE8(mapped.priceE8());
		leg.setPriceCurrency(mapped.priceCurrency());
		leg.setQuotationType(mapped.quotationType());
		leg.setPriceBasis(mapped.priceBasis());
		leg.setAccruedInterestDays(mapped.accruedInterestDays());
		dbController.insertOrUpdate(leg);
	}

	private StockSecurity resolveSecurity(StockDataSource source, BankAccount account, Gattung gattung,
			Currency statementCurrency) {
		String providerIdentifier = account.getId() + ":" + requireText(gattung.name, "Wertpapiername") + ':' + text(gattung.wptype);
		StockSecurity security = findSecurity(source, gattung, providerIdentifier);
		if (security != null) {
			saveIdentifier(source, security, StockIdentifierType.ISIN, gattung.isin);
			saveIdentifier(source, security, StockIdentifierType.WKN, gattung.wkn);
			if (trimToNull(gattung.isin) == null && trimToNull(gattung.wkn) == null) {
				saveIdentifier(source, security, StockIdentifierType.PROVIDER, providerIdentifier);
			}
			return security;
		}

		StockQuantityType quantityType = quantityType(gattung);
		StockQuotationType quotationType = quotationType(gattung);
		Currency securityCurrency = resolveSecurityCurrency(gattung, statementCurrency, account.getBaseCurrency());
		security = new StockSecurity();
		security.setSecurityType(securityType(gattung));
		security.setName(requireText(gattung.name, "Der FinTS-Depotbestand enthält ein Wertpapier ohne Namen."));
		security.setDomicileCountry(normalizeCountry(gattung.countryEmittent));
		security.setMaturityDate(toLocalDate(gattung.faellig));
		security.setDefaultQuantityType(quantityType);
		security.setNominalCurrency(quantityType == StockQuantityType.NOMINAL ? securityCurrency : null);
		security.setDefaultQuoteCurrency(securityCurrency);
		security.setDefaultQuotationType(quotationType);
		security.setDefaultPriceBasis(quotationType == StockQuotationType.PERCENT_OF_NOMINAL ? StockPriceBasis.CLEAN : null);
		security.setSecurityState(StockSecurityState.ACTIVE);
		dbController.insertOrUpdate(security);

		saveIdentifier(source, security, StockIdentifierType.ISIN, gattung.isin);
		saveIdentifier(source, security, StockIdentifierType.WKN, gattung.wkn);
		if (trimToNull(gattung.isin) == null && trimToNull(gattung.wkn) == null) {
			saveIdentifier(source, security, StockIdentifierType.PROVIDER, providerIdentifier);
		}
		return security;
	}

	private StockSecurity findSecurity(StockDataSource source, Gattung gattung, String providerIdentifier) {
		String isin = normalizeIdentifier(gattung.isin);
		String wkn = normalizeIdentifier(gattung.wkn);
		String provider = isin == null && wkn == null ? normalizeIdentifier(providerIdentifier) : null;
		Map<Integer, StockSecurity> securities = new LinkedHashMap<>();
		for (StockSecurity security : dbController.getAll(StockSecurity.class)) {
			securities.put(security.getId(), security);
		}
		Integer matchedSecurityId = null;
		for (StockSecurityIdentifier identifier : dbController.getAll(StockSecurityIdentifier.class)) {
			boolean isinMatches = identifier.getIdentifierType() == StockIdentifierType.ISIN
					&& isin != null && isin.equals(normalizeIdentifier(identifier.getIdentifierValue()));
			boolean wknMatches = identifier.getIdentifierType() == StockIdentifierType.WKN
					&& wkn != null && wkn.equals(normalizeIdentifier(identifier.getIdentifierValue()));
			boolean providerMatches = identifier.getIdentifierType() == StockIdentifierType.PROVIDER
					&& identifier.getSourceId() != null && identifier.getSourceId() == source.getId()
					&& provider != null && provider.equals(normalizeIdentifier(identifier.getIdentifierValue()));
			if (identifier.getValidTo() == null && (isinMatches || wknMatches || providerMatches)) {
				if (matchedSecurityId != null && matchedSecurityId != identifier.getSecurityId()) {
					throw new GBankingException("ISIN und WKN des FinTS-Wertpapiers verweisen auf unterschiedliche Wertpapiere.");
				}
				matchedSecurityId = identifier.getSecurityId();
			}
		}
		return matchedSecurityId != null ? securities.get(matchedSecurityId) : null;
	}

	private void saveIdentifier(StockDataSource source, StockSecurity security, StockIdentifierType type, String value) {
		String normalized = normalizeIdentifier(value);
		if (normalized == null) {
			return;
		}
		StockSecurityIdentifier existing = dbController.getAll(StockSecurityIdentifier.class).stream()
				.filter(identifier -> identifier.getIdentifierType() == type && identifier.getValidTo() == null)
				.filter(identifier -> normalized.equals(normalizeIdentifier(identifier.getIdentifierValue())))
				.filter(identifier -> type != StockIdentifierType.PROVIDER
						|| identifier.getSourceId() != null && identifier.getSourceId() == source.getId())
				.findFirst().orElse(null);
		if (existing != null) {
			if (existing.getSecurityId() != security.getId()) {
				throw new GBankingException("Die Wertpapierkennung " + normalized + " ist bereits einem anderen Wertpapier zugeordnet.");
			}
			return;
		}
		StockSecurityIdentifier identifier = new StockSecurityIdentifier();
		identifier.setSecurityId(security.getId());
		identifier.setSourceId(source.getId());
		identifier.setIdentifierType(type);
		identifier.setIdentifierValue(normalized);
		dbController.insertOrUpdate(identifier);
	}

	private MappedPosition mapPosition(Gattung gattung, Currency statementCurrency) {
		if (gattung.saldo == null || gattung.saldo.getValue() == null) {
			throw new GBankingException("Der FinTS-Depotbestand enthält ein Wertpapier ohne Bestand.");
		}
		StockQuantityType quantityType = quantityType(gattung);
		StockQuotationType quotationType = quotationType(gattung);
		Currency fallbackCurrency = resolveSecurityCurrency(gattung, statementCurrency, Currency.EUR);
		Currency priceCurrency = gattung.price != null
				? requireCurrency(gattung.price.getCurr(), fallbackCurrency, "Kurs") : null;
		Long priceE8 = gattung.price != null ? toScaled(gattung.price.getValue(), PRICE_SCALE, "Kurs") : null;
		boolean hasAcquisitionPrice = gattung.einstandspreis != null && gattung.einstandspreis.getValue() != null
				&& gattung.einstandspreis.getValue().signum() > 0;
		Currency acquisitionPriceCurrency = hasAcquisitionPrice
				? requireCurrency(gattung.einstandspreis.getCurr(), fallbackCurrency, "Einstandskurs") : null;
		Long acquisitionPriceE8 = hasAcquisitionPrice
				? toScaled(gattung.einstandspreis.getValue(), PRICE_SCALE, "Einstandskurs") : null;
		StockPriceBasis priceBasis = quotationType == StockQuotationType.PERCENT_OF_NOMINAL ? StockPriceBasis.CLEAN : null;

		Currency reportedValueCurrency = monetaryCurrency(gattung.depotwert, gattung.stueckzinsbetrag, fallbackCurrency);
		Long valueMinor = gattung.depotwert != null ? toMinor(gattung.depotwert.getValue(), reportedValueCurrency) : null;
		Long accruedInterestMinor = gattung.stueckzinsbetrag != null
				? toMinor(gattung.stueckzinsbetrag.getValue(), reportedValueCurrency) : null;
		return new MappedPosition(toScaled(gattung.saldo.getValue(), QUANTITY_SCALE, "Bestand"), quantityType,
				priceE8, priceCurrency, acquisitionPriceE8, acquisitionPriceCurrency,
				gattung.price != null ? quotationType : null,
				gattung.price != null ? priceBasis : null, gattung.days != 0 ? Math.abs(gattung.days) : null, valueMinor, accruedInterestMinor,
				valueMinor != null || accruedInterestMinor != null ? reportedValueCurrency : null);
	}

	private void savePrice(StockDataSource source, StockImportRecord record, StockSecurity security, Gattung gattung,
			MappedPosition mapped, LocalDateTime statementAt, String externalReference) {
		if (mapped.priceE8() == null) {
			return;
		}
		String marketIdentifierCode = normalizeMic(gattung.source_comment);
		StockSecurityPriceSource priceSource = getOrCreatePriceSource(source, security, marketIdentifierCode);
		StockSecurityPrice price = new StockSecurityPrice();
		price.setPriceSourceId(priceSource.getId());
		price.setImportRecordId(record.getId());
		price.setQuotedAt(toLocalDateTime(gattung.timestamp_price, statementAt));
		price.setPriceE8(mapped.priceE8());
		price.setQuoteCurrency(mapped.priceCurrency());
		price.setQuotationType(mapped.quotationType());
		price.setPriceBasis(mapped.priceBasis());
		price.setPriceType(gattung.pricequalifier == Gattung.PRICE_QUALIF_MRKT
				? StockPriceType.MARKET : StockPriceType.INDICATIVE);
		price.setExternalReference(externalReference + ":PRICE:" + security.getId());
		dbController.insertOrUpdate(price);
	}

	private StockSecurityPriceSource getOrCreatePriceSource(StockDataSource source, StockSecurity security,
			String marketIdentifierCode) {
		return dbController.getAllByParent(StockSecurityPriceSource.class, security.getId()).stream()
				.filter(candidate -> candidate.getSourceId() == source.getId())
				.filter(candidate -> Objects.equals(candidate.getMarketIdentifierCode(), marketIdentifierCode))
				.filter(candidate -> candidate.getProviderSymbol() == null)
				.findFirst().orElseGet(() -> {
					StockSecurityPriceSource result = new StockSecurityPriceSource();
					result.setSecurityId(security.getId());
					result.setSourceId(source.getId());
					result.setMarketIdentifierCode(marketIdentifierCode);
					result.setPriority(source.getDefaultPriority());
					return dbController.insertOrUpdate(result);
				});
	}

	private void saveExchangeRate(StockDataSource source, StockImportRecord record, Gattung gattung,
			LocalDateTime statementAt, String externalReference) {
		if (gattung.xchg_kurs <= 0 || trimToNull(gattung.xchg_cur1) == null || trimToNull(gattung.xchg_cur2) == null) {
			return;
		}
		Currency baseCurrency = requireCurrency(gattung.xchg_cur1, null, "Basiswährung des Wechselkurses");
		Currency quoteCurrency = requireCurrency(gattung.xchg_cur2, null, "Zielwährung des Wechselkurses");
		if (baseCurrency == quoteCurrency) {
			return;
		}
		StockExchangeRateSource rateSource = dbController.getAll(StockExchangeRateSource.class).stream()
				.filter(candidate -> candidate.getSourceId() == source.getId())
				.filter(candidate -> candidate.getBaseCurrency() == baseCurrency && candidate.getQuoteCurrency() == quoteCurrency)
				.findFirst().orElseGet(() -> {
					StockExchangeRateSource result = new StockExchangeRateSource();
					result.setSourceId(source.getId());
					result.setBaseCurrency(baseCurrency);
					result.setQuoteCurrency(quoteCurrency);
					result.setPriority(source.getDefaultPriority());
					return dbController.insertOrUpdate(result);
				});
		StockExchangeRate rate = new StockExchangeRate();
		rate.setExchangeRateSourceId(rateSource.getId());
		rate.setImportRecordId(record.getId());
		rate.setQuotedAt(toLocalDateTime(gattung.timestamp_price, statementAt));
		rate.setRateE12(toScaled(BigDecimal.valueOf(gattung.xchg_kurs), EXCHANGE_RATE_SCALE, "Wechselkurs"));
		rate.setExternalReference(externalReference + ":FX:" + baseCurrency + ':' + quoteCurrency);
		dbController.insertOrUpdate(rate);
	}

	private Map<PositionKey, Long> currentQuantities(int portfolioId) {
		Map<PositionKey, Long> result = new LinkedHashMap<>();
		for (StockPortfolioPosition position : dbController.getAllByParent(StockPortfolioPosition.class, portfolioId)) {
			result.merge(new PositionKey(position.getSecurityId(), position.getQuantityType()),
					position.getQuantityE9(), Math::addExact);
		}
		return result;
	}

	private StockDataSource getOrCreateFinTsSource() {
		return dbController.getAll(StockDataSource.class).stream()
				.filter(source -> FINTS_SOURCE_CODE.equalsIgnoreCase(source.getSourceCode()))
				.findFirst().orElseGet(() -> {
					StockDataSource source = new StockDataSource();
					source.setSourceCode(FINTS_SOURCE_CODE);
					source.setSourceName("FinTS");
					source.setSourceType(StockDataSourceType.FINTS);
					source.setDefaultPriority(100);
					return dbController.insertOrUpdate(source);
				});
	}

	private BankAccessService bankAccessService() {
		return ServiceRegistry.getService(BankAccessService.class);
	}

	private PortfolioSummary findPortfolioSummary(int portfolioId) {
		return portfolioService.getPortfolios().stream()
				.filter(portfolio -> portfolio.portfolioId() == portfolioId)
				.findFirst().orElseThrow(() -> new GBankingException("Das Depot wurde nicht gespeichert."));
	}

	private BankAccount requireAccount(BankAccount account, AccountType type, String role) {
		return requireAccount(account != null ? account.getId() : 0, type, role);
	}

	private BankAccount requireAccount(int accountId, AccountType type, String role) {
		BankAccount stored = dbController.getById(BankAccount.class, accountId);
		if (stored == null || stored.getAccountType() != type) {
			throw new GBankingException(role + " ist kein gültiges Konto.");
		}
		return stored;
	}

	private BankAccount requireSettlementAccount(int accountId) {
		BankAccount stored = dbController.getById(BankAccount.class, accountId);
		if (stored == null || (stored.getAccountType() != AccountType.CURRENT_ACCOUNT
				&& stored.getAccountType() != AccountType.DEPOT_ACCOUNT)) {
			throw new GBankingException("Es muss ein gültiges Giro- oder Verrechnungskonto ausgewählt werden.");
		}
		return stored;
	}

	private static Konto findMatchingAccount(HBCIPassport passport, BankAccount account) {
		Konto[] accounts = passport.getAccounts();
		if (accounts == null) {
			return null;
		}
		for (Konto candidate : accounts) {
			if (accountsMatch(account, candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static Entry findMatchingEntry(Entry[] entries, BankAccount account) {
		if (entries == null) {
			return null;
		}
		for (Entry entry : entries) {
			if (entry != null && accountsMatch(account, entry.depot)) {
				return entry;
			}
		}
		return entries.length == 1 && hasNoAccountIdentifier(entries[0] != null ? entries[0].depot : null)
				? entries[0] : null;
	}

	private static boolean accountsMatch(BankAccount account, Konto konto) {
		if (account == null || konto == null) {
			return false;
		}
		return matches(account.getIban(), konto.iban)
				|| (matches(account.getNumber(), konto.number) && matchesOptional(account.getSubnumber(), konto.subnumber));
	}

	private static boolean hasNoAccountIdentifier(Konto konto) {
		return konto == null || (trimToNull(konto.iban) == null && trimToNull(konto.number) == null);
	}

	private static boolean matches(String left, String right) {
		return left != null && right != null && left.equalsIgnoreCase(right);
	}

	private static boolean matchesOptional(String left, String right) {
		return trimToNull(left) == null || trimToNull(right) == null || matches(left, right);
	}

	private static StockQuantityType quantityType(Gattung gattung) {
		return gattung.saldo_type == Entry.SALDO_TYPE_WERT ? StockQuantityType.NOMINAL : StockQuantityType.UNITS;
	}

	private static StockQuotationType quotationType(Gattung gattung) {
		return gattung.pricetype == Gattung.PRICE_TYPE_PRCT
				? StockQuotationType.PERCENT_OF_NOMINAL : StockQuotationType.ABSOLUTE;
	}

	private static StockSecurityType securityType(Gattung gattung) {
		String description = (text(gattung.wptype) + ' ' + text(gattung.name)).toUpperCase(Locale.ROOT);
		if (description.contains("ETF")) {
			return StockSecurityType.ETF;
		}
		if (description.contains("FONDS") || description.contains("FUND")) {
			return StockSecurityType.FUND;
		}
		if (description.contains("ANLEIHE") || description.contains("BOND")) {
			return StockSecurityType.BOND;
		}
		if (description.contains("AKTIE") || description.contains("STOCK")) {
			return StockSecurityType.STOCK;
		}
		return StockSecurityType.OTHER;
	}

	private static StockSubBalanceQualifier mapSubBalanceQualifier(SubSaldo source) {
		if (source.locked) {
			return StockSubBalanceQualifier.BLOCKED;
		}
		String qualifier = text(source.qualifier).toUpperCase(Locale.ROOT);
		if (qualifier.contains("AVAI") || qualifier.contains("VERF")) {
			return StockSubBalanceQualifier.AVAILABLE;
		}
		if (qualifier.contains("BLOCK") || qualifier.contains("SPERR")) {
			return StockSubBalanceQualifier.BLOCKED;
		}
		if (qualifier.contains("PLEDG") || qualifier.contains("PFAND")) {
			return StockSubBalanceQualifier.PLEDGED;
		}
		if (qualifier.contains("PEND") || qualifier.contains("LIEFER")) {
			return StockSubBalanceQualifier.PENDING_DELIVERY;
		}
		return StockSubBalanceQualifier.OTHER;
	}

	private static Currency monetaryCurrency(BigDecimalValue first, BigDecimalValue second, Currency fallback) {
		Currency firstCurrency = first != null ? requireCurrency(first.getCurr(), fallback, "Depotwert") : null;
		Currency secondCurrency = second != null ? requireCurrency(second.getCurr(), fallback, "Stückzinsen") : null;
		if (firstCurrency != null && secondCurrency != null && firstCurrency != secondCurrency) {
			throw new GBankingException("Depotwert und Stückzinsen haben unterschiedliche Währungen.");
		}
		return firstCurrency != null ? firstCurrency : secondCurrency != null ? secondCurrency : fallback;
	}

	private static Currency resolveSecurityCurrency(Gattung gattung, Currency statementCurrency, Currency accountCurrency) {
		String currencyCode = trimToNull(gattung.curr);
		if (currencyCode != null && !isSecurityQuantityUnit(currencyCode)) {
			return requireCurrency(currencyCode, null, "Wertpapierwährung");
		}
		if (gattung.price != null && gattung.pricetype != Gattung.PRICE_TYPE_PRCT
				&& trimToNull(gattung.price.getCurr()) != null) {
			currencyCode = trimToNull(gattung.price.getCurr());
		} else if (gattung.einstandspreis != null && gattung.pricetype != Gattung.PRICE_TYPE_PRCT
				&& trimToNull(gattung.einstandspreis.getCurr()) != null) {
			currencyCode = trimToNull(gattung.einstandspreis.getCurr());
		} else if (gattung.depotwert != null) {
			currencyCode = trimToNull(gattung.depotwert.getCurr());
		} else {
			currencyCode = null;
		}
		Currency fallback = statementCurrency != null ? statementCurrency
				: accountCurrency != null ? accountCurrency : Currency.EUR;
		return requireCurrency(currencyCode, fallback, "Wertpapierwährung");
	}

	private static boolean isSecurityQuantityUnit(String value) {
		return SECURITY_QUANTITY_UNITS.contains(value.trim().toUpperCase(Locale.ROOT));
	}

	private static Currency requireCurrency(String value, Currency fallback, String field) {
		String normalized = trimToNull(value);
		if (normalized == null || "%".equals(normalized)) {
			if (fallback != null) {
				return fallback;
			}
			throw new GBankingException(field + " enthält keine Währung.");
		}
		try {
			return Currency.forCode(normalized);
		} catch (GBankingException exception) {
			throw new GBankingException(field + ": " + exception.getMessage(), exception);
		}
	}

	private static long toScaled(BigDecimal value, int scale, String field) {
		if (value == null) {
			throw new GBankingException(field + " fehlt im FinTS-Ergebnis.");
		}
		try {
			return value.setScale(scale, RoundingMode.HALF_UP).movePointRight(scale).longValueExact();
		} catch (ArithmeticException exception) {
			throw new GBankingException(field + " ist zu groß.", exception);
		}
	}

	private static long toMinor(BigDecimal value, Currency currency) {
		if (value == null) {
			throw new GBankingException("Ein Geldbetrag fehlt im FinTS-Ergebnis.");
		}
		try {
			return value.setScale(currency.getMinorUnitDigits(), RoundingMode.HALF_UP)
					.movePointRight(currency.getMinorUnitDigits()).longValueExact();
		} catch (ArithmeticException exception) {
			throw new GBankingException("Ein Geldbetrag aus dem FinTS-Ergebnis ist zu groß.", exception);
		}
	}

	private static String createSnapshotFingerprint(BankAccount account, Entry entry) {
		StringBuilder canonical = new StringBuilder();
		append(canonical, account != null ? account.getId() : 0);
		append(canonical, entry.timestamp != null ? entry.timestamp.getTime() : null);
		append(canonical, decimal(entry.total));
		append(canonical, entry.total != null ? entry.total.getCurr() : null);
		for (Gattung gattung : entry.getEntries()) {
			append(canonical, gattung.isin);
			append(canonical, gattung.wkn);
			append(canonical, gattung.name);
			append(canonical, gattung.wptype);
			append(canonical, gattung.saldo_type);
			append(canonical, decimal(gattung.saldo));
			append(canonical, gattung.pricetype);
			append(canonical, decimal(gattung.price));
			append(canonical, gattung.price != null ? gattung.price.getCurr() : null);
			append(canonical, decimal(gattung.einstandspreis));
			append(canonical, gattung.einstandspreis != null ? gattung.einstandspreis.getCurr() : null);
			append(canonical, gattung.timestamp_price != null ? gattung.timestamp_price.getTime() : null);
			append(canonical, decimal(gattung.depotwert));
			append(canonical, decimal(gattung.stueckzinsbetrag));
			append(canonical, gattung.curr);
			append(canonical, gattung.xchg_cur1);
			append(canonical, gattung.xchg_cur2);
			append(canonical, gattung.xchg_kurs);
			for (SubSaldo subSaldo : gattung.getEntries()) {
				append(canonical, subSaldo.qualifier);
				append(canonical, decimal(subSaldo.saldo));
				append(canonical, subSaldo.locked);
				append(canonical, subSaldo.lockeduntil != null ? subSaldo.lockeduntil.getTime() : null);
				append(canonical, subSaldo.country);
				append(canonical, subSaldo.verwahrung);
				append(canonical, subSaldo.lager);
				append(canonical, subSaldo.comment);
			}
		}
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	private static void append(StringBuilder target, Object value) {
		String textValue = value != null ? value.toString() : "";
		target.append(textValue.length()).append(':').append(textValue).append('|');
	}

	private static String decimal(BigDecimalValue value) {
		return value != null && value.getValue() != null ? value.getValue().stripTrailingZeros().toPlainString() : null;
	}

	private static LocalDateTime toLocalDateTime(Date value, LocalDateTime fallback) {
		return value != null ? LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault()) : fallback;
	}

	private static LocalDate toLocalDate(Date value) {
		return value != null ? value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null;
	}

	private static String normalizeIdentifier(String value) {
		String normalized = trimToNull(value);
		return normalized != null ? normalized.toUpperCase(Locale.ROOT) : null;
	}

	private static String normalizeCountry(String value) {
		String normalized = trimToNull(value);
		return normalized != null && normalized.length() == 2 ? normalized.toUpperCase(Locale.ROOT) : null;
	}

	private static String normalizeMic(String value) {
		String normalized = trimToNull(value);
		return normalized != null && normalized.length() == 4 ? normalized.toUpperCase(Locale.ROOT) : null;
	}

	private static String firstNonBlank(String first, String second) {
		return trimToNull(first) != null ? first : second;
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String requireText(String value, String message) {
		String result = trimToNull(value);
		if (result == null) {
			throw new GBankingException(message);
		}
		return result;
	}

	private static String text(String value) {
		return value != null ? value : "";
	}

	public static String accountDisplayName(BankAccount account) {
		if (account == null) {
			return "";
		}
		String name = trimToNull(account.getAccountName());
		String number = trimToNull(account.getIban()) != null ? account.getIban() : account.getNumber();
		return name != null ? name + (trimToNull(number) != null ? " (" + number + ')' : "")
				: trimToNull(number) != null ? number : "Konto " + account.getId();
	}

	private record PositionKey(int securityId, StockQuantityType quantityType) {
	}

	private record MappedPosition(long quantityE9, StockQuantityType quantityType, Long priceE8,
			Currency priceCurrency, Long acquisitionPriceE8, Currency acquisitionPriceCurrency,
			StockQuotationType quotationType, StockPriceBasis priceBasis, Integer accruedInterestDays,
			Long valueMinor, Long accruedInterestMinor, Currency valueCurrency) {

		private static MappedPosition empty(StockQuantityType quantityType) {
			return new MappedPosition(0, quantityType, null, null, null, null, null, null, null, null, null, null);
		}
	}
}
