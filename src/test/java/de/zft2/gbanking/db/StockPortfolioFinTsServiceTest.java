package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRWPDepotList;
import org.kapott.hbci.GV_Result.GVRWPDepotList.Entry;
import org.kapott.hbci.GV_Result.GVRWPDepotList.Entry.Gattung;
import org.kapott.hbci.GV_Result.GVRWPDepotList.Entry.Gattung.SubSaldo;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.BigDecimalValue;
import org.kapott.hbci.structures.Konto;
import org.mockito.MockedConstruction;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockImportStatus;
import de.zft2.gbanking.db.dao.enu.StockStatementStatus;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.db.dao.stock.StockImportBatch;
import de.zft2.gbanking.db.dao.stock.StockPortfolioStatement;
import de.zft2.gbanking.db.dao.stock.StockPortfolioStatementPosition;
import de.zft2.gbanking.db.dao.stock.StockPortfolioStatementSubBalance;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.db.dao.stock.StockSecurityIdentifier;
import de.zft2.gbanking.db.dao.stock.StockTransactionSecurityLeg;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.service.bankaccess.BankMessageService;
import de.zft2.gbanking.service.stock.StockPortfolioFinTsRetrievalResult;
import de.zft2.gbanking.service.stock.StockPortfolioFinTsService;
import de.zft2.gbanking.service.stock.StockPortfolioSettings;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.TradeRequest;
import de.zft2.gbanking.testdata.TestDataFactory;

class StockPortfolioFinTsServiceTest extends DBControllerIntegrationBaseTest {

	private static final LocalDate STATEMENT_DATE = LocalDate.of(2026, 9, 15);

	@Test
	void shouldConfigurePortfolioAndReconcileFinTsStatementsIdempotently() {
		BankAccount depotAccount = createAccount("FinTS-Depot", AccountType.DEPOT);
		BankAccount settlementAccount = createAccount("Girokonto", AccountType.CURRENT_ACCOUNT);
		StockPortfolioFinTsService service = new StockPortfolioFinTsService();
		PortfolioSummary portfolio = service.ensurePortfolio(depotAccount.getId(), settlementAccount.getId());

		StockPortfolioFinTsRetrievalResult firstResult = service.persistPortfolioStatement(
				portfolio.portfolioId(), depotAccount, createStatement(new BigDecimal("10"), STATEMENT_DATE));

		assertTrue(firstResult.successful());
		assertEquals(1, firstResult.positionCount());
		assertEquals(1, firstResult.adjustmentCount());
		var position = new StockPortfolioService().getPositions(portfolio.portfolioId()).get(0);
		assertEquals(new BigDecimal("1E+1"), position.quantity());
		assertEquals(new BigDecimal("25"), position.unitPrice());
		assertEquals(new BigDecimal("2E+1"), position.acquisitionPrice());
		assertEquals(new BigDecimal("25"), position.performancePercent());
		assertEquals(1, db.getAll(StockSecurity.class).size());
		assertEquals(2, db.getAll(StockSecurityIdentifier.class).size());
		assertEquals(1, db.getAll(StockPortfolioStatementPosition.class).size());
		assertEquals(1, db.getAll(StockPortfolioStatementSubBalance.class).size());
		StockPortfolioStatement statement = db.getAll(StockPortfolioStatement.class).get(0);
		assertEquals(StockStatementStatus.FINAL, statement.getStatementStatus());
		assertEquals(123L, statement.getReportedAccruedInterestMinor());
		assertEquals(StockImportStatus.IMPORTED, db.getAll(StockImportBatch.class).get(0).getImportStatus());
		assertEquals(StockTransactionType.RECONCILIATION_ADJUSTMENT,
				new StockPortfolioService().getTransactions(portfolio.portfolioId()).get(0).transactionType());
		assertEquals(15, db.getAll(StockTransactionSecurityLeg.class).get(0).getAccruedInterestDays());
		assertEquals(2_000_000_000L,
				db.getAll(StockPortfolioStatementPosition.class).get(0).getReportedAcquisitionPriceE8());

		StockPortfolioFinTsRetrievalResult duplicateResult = service.persistPortfolioStatement(
				portfolio.portfolioId(), depotAccount, createStatement(new BigDecimal("10"), STATEMENT_DATE));

		assertTrue(duplicateResult.unchanged());
		assertEquals(1, db.getAll(StockPortfolioStatement.class).size());

		StockPortfolioFinTsRetrievalResult changedResult = service.persistPortfolioStatement(
				portfolio.portfolioId(), depotAccount, createStatement(new BigDecimal("7"), STATEMENT_DATE.plusDays(1)));

		assertEquals(1, changedResult.adjustmentCount());
		assertEquals(new BigDecimal("7"), new StockPortfolioService().getPositions(portfolio.portfolioId()).get(0).quantity());
		assertEquals(2, db.getAll(StockPortfolioStatement.class).size());
	}

	@Test
	void missingFinTsAcquisitionPriceShouldRemainUnknownWithoutPurchases() {
		BankAccount depotAccount = createAccount("FinTS-Depot", AccountType.DEPOT);
		BankAccount settlementAccount = createAccount("Girokonto", AccountType.CURRENT_ACCOUNT);
		StockPortfolioFinTsService finTsService = new StockPortfolioFinTsService();
		PortfolioSummary portfolio = finTsService.ensurePortfolio(depotAccount.getId(), settlementAccount.getId());
		Entry statement = createStatement(new BigDecimal("10"), STATEMENT_DATE);
		statement.getEntries()[0].einstandspreis = null;
		finTsService.persistPortfolioStatement(portfolio.portfolioId(), depotAccount, statement);
		StockPortfolioService portfolioService = new StockPortfolioService();
		var position = portfolioService.getPositions(portfolio.portfolioId()).get(0);

		assertNull(position.acquisitionPrice());
	}

	@Test
	void reconciliationPrefillShouldUseReportedQuantityMinusActualTransactions() {
		BankAccount depotAccount = createAccount("FinTS-Depot", AccountType.DEPOT);
		BankAccount settlementAccount = createAccount("Girokonto", AccountType.CURRENT_ACCOUNT);
		StockPortfolioFinTsService finTsService = new StockPortfolioFinTsService();
		PortfolioSummary portfolio = finTsService.ensurePortfolio(depotAccount.getId(), settlementAccount.getId());
		finTsService.persistPortfolioStatement(portfolio.portfolioId(), depotAccount,
				createStatement(new BigDecimal("10"), STATEMENT_DATE));
		StockPortfolioService portfolioService = new StockPortfolioService();

		var initialPrefill = portfolioService.getReconciliationPrefills(portfolio.portfolioId()).get(0);
		assertEquals(StockTransactionType.BUY, initialPrefill.transactionType());
		assertEquals(new BigDecimal("1E+1"), initialPrefill.quantity());

		var position = portfolioService.getPositions(portfolio.portfolioId()).get(0);
		portfolioService.recordTrade(portfolio, position, new TradeRequest(StockTransactionType.SELL,
				STATEMENT_DATE.minusDays(5), STATEMENT_DATE.minusDays(3), new BigDecimal("5"),
				new BigDecimal("20"), Currency.EUR, BigDecimal.ONE, null, null, null));

		var updatedPrefill = portfolioService.getReconciliationPrefills(portfolio.portfolioId()).get(0);
		assertEquals(StockTransactionType.BUY, updatedPrefill.transactionType());
		assertEquals(new BigDecimal("15"), updatedPrefill.quantity());
		assertEquals(new BigDecimal("1E+1"), portfolioService.getPositions(portfolio.portfolioId()).get(0).quantity());

		portfolioService.recordTrade(portfolio, portfolioService.getPositions(portfolio.portfolioId()).get(0),
				new TradeRequest(StockTransactionType.BUY, STATEMENT_DATE.minusDays(4), STATEMENT_DATE.minusDays(2),
						new BigDecimal("11"), new BigDecimal("20"), Currency.EUR, BigDecimal.ONE,
						null, null, null));
		assertEquals(new BigDecimal("4"),
				portfolioService.getReconciliationPrefills(portfolio.portfolioId()).get(0).quantity());
		assertEquals(new BigDecimal("1E+1"), portfolioService.getPositions(portfolio.portfolioId()).get(0).quantity());

		portfolioService.recordTrade(portfolio, portfolioService.getPositions(portfolio.portfolioId()).get(0),
				new TradeRequest(StockTransactionType.BUY, STATEMENT_DATE.minusDays(3), STATEMENT_DATE.minusDays(1),
						new BigDecimal("4"), new BigDecimal("20"), Currency.EUR, BigDecimal.ONE,
						null, null, null));
		assertTrue(portfolioService.getReconciliationPrefills(portfolio.portfolioId()).isEmpty());
		assertEquals(new BigDecimal("1E+1"), portfolioService.getPositions(portfolio.portfolioId()).get(0).quantity());
	}

	@Test
	void suppliedFinTsAcquisitionPriceShouldBeUsed() {
		BankAccount depotAccount = createAccount("FinTS-Depot", AccountType.DEPOT);
		BankAccount settlementAccount = createAccount("Girokonto", AccountType.CURRENT_ACCOUNT);
		StockPortfolioFinTsService finTsService = new StockPortfolioFinTsService();
		PortfolioSummary portfolio = finTsService.ensurePortfolio(depotAccount.getId(), settlementAccount.getId());
		finTsService.persistPortfolioStatement(portfolio.portfolioId(), depotAccount,
				createStatement(new BigDecimal("10"), STATEMENT_DATE));
		StockPortfolioService portfolioService = new StockPortfolioService();
		var position = portfolioService.getPositions(portfolio.portfolioId()).get(0);

		assertEquals(new BigDecimal("2E+1"), position.acquisitionPrice());
	}

	@Test
	void emptyFinTsStatementShouldClearExistingCalculatedPosition() {
		BankAccount depotAccount = createAccount("FinTS-Depot", AccountType.DEPOT);
		BankAccount settlementAccount = createAccount("Verrechnung", AccountType.DEPOT_ACCOUNT);
		StockPortfolioFinTsService service = new StockPortfolioFinTsService();
		PortfolioSummary portfolio = service.ensurePortfolio(depotAccount.getId(), settlementAccount.getId());
		service.persistPortfolioStatement(portfolio.portfolioId(), depotAccount,
				createStatement(BigDecimal.ONE, STATEMENT_DATE));

		Entry emptyStatement = new Entry();
		emptyStatement.timestamp = toDate(STATEMENT_DATE.plusDays(1));
		emptyStatement.total = new BigDecimalValue(BigDecimal.ZERO, Currency.EUR.name());
		StockPortfolioFinTsRetrievalResult result = service.persistPortfolioStatement(
				portfolio.portfolioId(), depotAccount, emptyStatement);

		assertEquals(1, result.adjustmentCount());
		assertTrue(new StockPortfolioService().getPositions(portfolio.portfolioId()).isEmpty());
		assertEquals(settlementAccount.getId(), portfolio.settlementAccountId());
	}

	@Test
	void repeatedFinTsSecurityWithoutIsinOrWknShouldReuseProviderIdentifier() {
		BankAccount depotAccount = createAccount("FinTS-Depot", AccountType.DEPOT);
		BankAccount settlementAccount = createAccount("Girokonto", AccountType.CURRENT_ACCOUNT);
		StockPortfolioFinTsService service = new StockPortfolioFinTsService();
		PortfolioSummary portfolio = service.ensurePortfolio(depotAccount.getId(), settlementAccount.getId());
		Entry firstStatement = createStatement(new BigDecimal("3"), STATEMENT_DATE);
		firstStatement.getEntries()[0].isin = null;
		firstStatement.getEntries()[0].wkn = null;
		Entry secondStatement = createStatement(new BigDecimal("3"), STATEMENT_DATE.plusDays(1));
		secondStatement.getEntries()[0].isin = null;
		secondStatement.getEntries()[0].wkn = null;

		service.persistPortfolioStatement(portfolio.portfolioId(), depotAccount, firstStatement);
		StockPortfolioFinTsRetrievalResult result = service.persistPortfolioStatement(
				portfolio.portfolioId(), depotAccount, secondStatement);

		assertEquals(0, result.adjustmentCount());
		assertEquals(1, db.getAll(StockSecurity.class).size());
		assertEquals(1, db.getAll(StockSecurityIdentifier.class).size());
	}

	@Test
	void pieceUnitShouldUseReportedPriceCurrencyInsteadOfBeingParsedAsCurrency() {
		BankAccount depotAccount = createAccount("FinTS-Depot", AccountType.DEPOT);
		BankAccount settlementAccount = createAccount("Girokonto", AccountType.CURRENT_ACCOUNT);
		StockPortfolioFinTsService service = new StockPortfolioFinTsService();
		PortfolioSummary portfolio = service.ensurePortfolio(depotAccount.getId(), settlementAccount.getId());
		Entry statement = createStatement(new BigDecimal("10"), STATEMENT_DATE);
		Gattung securityData = statement.getEntries()[0];
		securityData.curr = "STK";
		securityData.price = new BigDecimalValue(new BigDecimal("25"), Currency.USD.name());

		StockPortfolioFinTsRetrievalResult result = service.persistPortfolioStatement(
				portfolio.portfolioId(), depotAccount, statement);

		assertTrue(result.successful());
		StockSecurity security = db.getAll(StockSecurity.class).get(0);
		assertEquals(Currency.USD, security.getDefaultQuoteCurrency());
		assertEquals(Currency.USD, db.getAll(StockPortfolioStatementPosition.class).get(0).getPriceCurrency());
	}

	@Test
	void shouldReturnConfiguredPortfoliosForSelectedBankAccess() {
		BankAccess firstAccess = db.insertOrUpdate(TestDataFactory.createSampleBankAccess("10020030"));
		BankAccess secondAccess = db.insertOrUpdate(TestDataFactory.createSampleBankAccess("40050060"));
		BankAccount firstDepot = createAccount("Depot A", AccountType.DEPOT, firstAccess.getId());
		BankAccount firstSettlement = createAccount("Giro A", AccountType.CURRENT_ACCOUNT, firstAccess.getId());
		BankAccount secondDepot = createAccount("Depot B", AccountType.DEPOT, secondAccess.getId());
		BankAccount secondSettlement = createAccount("Giro B", AccountType.CURRENT_ACCOUNT, secondAccess.getId());
		StockPortfolioFinTsService service = new StockPortfolioFinTsService();
		PortfolioSummary firstPortfolio = service.ensurePortfolio(firstDepot.getId(), firstSettlement.getId());
		service.ensurePortfolio(secondDepot.getId(), secondSettlement.getId());

		List<PortfolioSummary> portfolios = service.getPortfoliosForBankAccess(firstAccess.getId());

		assertEquals(1, portfolios.size());
		assertEquals(firstPortfolio.portfolioId(), portfolios.get(0).portfolioId());
	}

	@Test
	void selectedPortfolioShouldAddItsSettlementAccountToAccountUpdateByDefault() {
		BankAccount depotAccount = createAccount("Depot", AccountType.DEPOT);
		BankAccount settlementAccount = createAccount("Girokonto", AccountType.CURRENT_ACCOUNT);
		StockPortfolioFinTsService service = new StockPortfolioFinTsService();
		service.ensurePortfolio(depotAccount.getId(), settlementAccount.getId());

		List<BankAccount> accounts = service.resolveAccountsForUpdate(List.of(depotAccount));

		assertEquals(List.of(depotAccount.getId(), settlementAccount.getId()),
				accounts.stream().map(BankAccount::getId).toList());
	}

	@Test
	void disabledSettlementAccountRetrievalShouldKeepOriginalSelection() {
		BankAccount depotAccount = createAccount("Depot", AccountType.DEPOT);
		BankAccount settlementAccount = createAccount("Girokonto", AccountType.CURRENT_ACCOUNT);
		StockPortfolioFinTsService service = new StockPortfolioFinTsService();
		service.ensurePortfolio(depotAccount.getId(), settlementAccount.getId());
		StockPortfolioSettings.ensureSettingsExist();
		Setting setting = db.getAll(Setting.class).stream()
				.filter(candidate -> StockPortfolioSettings.SETTING_RETRIEVE_SETTLEMENT_ACCOUNTS.equals(candidate.getAttribute()))
				.findFirst().orElseThrow();
		setting.setValue(Boolean.FALSE.toString());
		db.insertOrUpdate(setting);

		List<BankAccount> accounts = service.resolveAccountsForUpdate(List.of(depotAccount));

		assertEquals(List.of(depotAccount.getId()), accounts.stream().map(BankAccount::getId).toList());
	}

	@Test
	void portfolioRetrievalShouldSaveSelectedTanProcedureAfterExecution() {
		BankAccess bankAccess = db.insertOrUpdate(TestDataFactory.createSampleBankAccess("10020030"));
		BankAccount depotAccount = createAccount("Depot", AccountType.DEPOT, bankAccess.getId());
		BankAccount settlementAccount = createAccount("Girokonto", AccountType.CURRENT_ACCOUNT, bankAccess.getId());
		StockPortfolioFinTsService service = new StockPortfolioFinTsService();
		PortfolioSummary portfolio = service.ensurePortfolio(depotAccount.getId(), settlementAccount.getId());

		BankAccessService bankAccessService = mock(BankAccessService.class);
		BankMessageService bankMessageService = mock(BankMessageService.class);
		HBCIPassport passport = mock(HBCIPassport.class);
		HBCIHandler handler = mock(HBCIHandler.class);
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		@SuppressWarnings("unchecked")
		HBCIJob<GVRWPDepotList> job = mock(HBCIJob.class);
		Konto providerAccount = new Konto();
		providerAccount.iban = depotAccount.getIban();

		when(passport.getAccounts()).thenReturn(new Konto[] { providerAccount });
		when(status.isOK()).thenReturn(false);
		when(status.getErrorString()).thenReturn("FinTS-Fehler");
		when(handler.execute()).thenReturn(status);
		when(bankAccessService.initBankAccess(any(BankAccount.class), any(char[].class))).thenReturn(bankAccess);
		doReturn(passport).when(bankAccessService).initBankConnection(eq(bankAccess), any(GBankingHBCICallback.class));
		doReturn(handler).when(bankAccessService).createHBCIHandler(eq(BaseMessagesDb.getVersion().getId()), same(passport));
		doReturn(job).when(bankAccessService).newHbciJob(handler, "WPDepotList");
		ServiceRegistry.setService(BankAccessService.class, bankAccessService);
		ServiceRegistry.setService(BankMessageService.class, bankMessageService);

		try (MockedConstruction<GBankingHBCICallback> ignored = mockConstruction(GBankingHBCICallback.class)) {
			StockPortfolioFinTsRetrievalResult result = service.retrievePortfolio(portfolio, "12345".toCharArray());

			assertFalse(result.successful());
			verify(passport).saveChanges();
		} finally {
			ServiceRegistry.removeService(BankAccessService.class);
			ServiceRegistry.removeService(BankMessageService.class);
		}
	}

	private BankAccount createAccount(String name, AccountType accountType) {
		return createAccount(name, accountType, null);
	}

	private BankAccount createAccount(String name, AccountType accountType, Integer bankAccessId) {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setBankAccessId(bankAccessId);
		account.setAccountName(name);
		account.setAccountType(accountType);
		return db.insertOrUpdate(account);
	}

	private Entry createStatement(BigDecimal quantity, LocalDate date) {
		Entry entry = new Entry();
		entry.timestamp = toDate(date);
		entry.total = new BigDecimalValue(quantity.multiply(new BigDecimal("25")), Currency.EUR.name());
		Gattung security = new Gattung();
		security.isin = "DE000TEST001";
		security.wkn = "TEST01";
		security.name = "Test AG Aktie";
		security.wptype = "Aktie";
		security.curr = Currency.EUR.name();
		security.saldo_type = Entry.SALDO_TYPE_STCK;
		security.saldo = new BigDecimalValue(quantity, "");
		security.pricetype = Gattung.PRICE_TYPE_VALUE;
		security.pricequalifier = Gattung.PRICE_QUALIF_MRKT;
		security.price = new BigDecimalValue(new BigDecimal("25"), Currency.EUR.name());
		security.einstandspreis = new BigDecimalValue(new BigDecimal("20"), Currency.EUR.name());
		security.timestamp_price = toDate(date);
		security.depotwert = entry.total;
		security.stueckzinsbetrag = new BigDecimalValue(new BigDecimal("1.23"), Currency.EUR.name());
		security.days = 15;
		security.source_comment = "XETR";
		SubSaldo available = new SubSaldo();
		available.qualifier = "AVAILABLE";
		available.saldo = new BigDecimalValue(quantity, "");
		security.addSubSaldo(available);
		entry.addEntry(security);
		return entry;
	}

	private Date toDate(LocalDate date) {
		return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}
}
