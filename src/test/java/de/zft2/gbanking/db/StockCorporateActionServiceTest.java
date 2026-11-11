package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.commons.csv.CSVFormat;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.StockDataSourceType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockCorporateActionMemberRole;
import de.zft2.gbanking.db.dao.enu.StockCorporateActionOperation;
import de.zft2.gbanking.db.dao.enu.StockCorporateActionType;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.db.dao.enu.StockTransactionStatus;
import de.zft2.gbanking.db.dao.enu.StockStatementStatus;
import de.zft2.gbanking.db.dao.enu.StockSecurityLegRole;
import de.zft2.gbanking.db.dao.enu.StockImportStatus;
import de.zft2.gbanking.db.dao.enu.StockImportRecordStatus;
import de.zft2.gbanking.db.dao.stock.StockImportBatch;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockImportRecord;
import de.zft2.gbanking.db.dao.stock.StockPortfolioStatement;
import de.zft2.gbanking.db.dao.stock.StockPortfolioStatementPosition;
import de.zft2.gbanking.db.dao.stock.StockTransactionSecurityLeg;
import de.zft2.gbanking.db.dao.stock.StockTransactionCashLeg;
import de.zft2.gbanking.db.dao.stock.StockCorporateAction;
import de.zft2.gbanking.db.dao.stock.StockCorporateActionMember;
import de.zft2.gbanking.db.dao.stock.StockCorporateActionTransaction;
import de.zft2.gbanking.db.dao.stock.StockPortfolio;
import de.zft2.gbanking.db.dao.stock.StockPortfolioSettlementAccount;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.db.dao.stock.StockTransaction;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.service.stock.PortfolioPerformanceExportService;
import de.zft2.gbanking.service.stock.StockCorporateActionService;
import de.zft2.gbanking.service.stock.StockCorporateActionService.OperationRequest;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.TradeRequest;
import de.zft2.gbanking.testdata.TestDataFactory;

class StockCorporateActionServiceTest extends DBControllerIntegrationBaseTest {
	private static final LocalDate DATE = LocalDate.of(2026, 9, 15);
	private final StockPortfolioService portfolios = new StockPortfolioService();
	private final StockCorporateActionService service = new StockCorporateActionService();

	@Test
	void topUpAndSubscriptionShouldCarryAllBasisWithoutSellingOriginalShares() {
		PortfolioSummary portfolio = portfolio();
		StockSecurity stock = security("Altaktie"), rights = security("Bezugsrecht"), young = security("Junge Aktie");
		buy(portfolio, stock, "103", "10");
		StockCorporateAction action = definition(stock, rights, young, StockCorporateActionType.RIGHTS_ISSUE);
		record(portfolio, action, StockCorporateActionOperation.RIGHTS_ALLOCATION, "103", null, "103", DATE);
		record(portfolio, action, StockCorporateActionOperation.RIGHTS_BUY, "7", "1", null, DATE);
		record(portfolio, action, StockCorporateActionOperation.SUBSCRIPTION, "110", null, null, DATE.plusDays(1));

		amount("103", holding(portfolio, stock).quantity());
		amount("927", holding(portfolio, stock).acquisitionValue());
		amount("11", holding(portfolio, young).quantity());
		amount("220", holding(portfolio, young).acquisitionValue());
		assertEquals(2, portfolios.getPositions(portfolio.portfolioId()).size());
		assertEquals(3, db.getAll(Booking.class).size());
		amount("8853", db.getById(BankAccount.class, portfolio.settlementAccountId()).getBalance());
		assertNull(service.getResults(portfolio.portfolioId()).get(2).realizedResult());
		assertThrows(GBankingException.class, () -> service.unlink(service.getResults(portfolio.portfolioId()).get(0).associationId()));
		assertEquals(3, db.getAll(StockCorporateActionTransaction.class).size());
		assertEquals(3, db.getAll(StockCorporateActionMember.class).size());
	}

	@Test
	void remainderSaleShouldHaveOwnClassificationAndSeparateProceedsFromProfit() {
		PortfolioSummary portfolio = portfolio();
		StockSecurity stock = security("Altaktie"), rights = security("Bezugsrecht");
		buy(portfolio, stock, "103", "10");
		StockCorporateAction action = definition(stock, rights, stock, StockCorporateActionType.RIGHTS_ISSUE);
		record(portfolio, action, StockCorporateActionOperation.RIGHTS_ALLOCATION, "103", null, "103", DATE);
		service.record(portfolio, new OperationRequest(action.getId(), StockCorporateActionOperation.RIGHTS_SALE,
				DATE.plusDays(1), n("3"), n("2"), n("1"), null, null, null));
		record(portfolio, action, StockCorporateActionOperation.SUBSCRIPTION, "100", null, null, DATE.plusDays(2));

		var sale = service.getResults(portfolio.portfolioId()).get(1);
		assertEquals(StockCorporateActionOperation.RIGHTS_SALE, sale.operation());
		assertEquals(stock.getId(), sale.underlyingSecurityId());
		amount("6", sale.gross());
		amount("5", sale.netCash());
		amount("3", sale.disposedBasis());
		amount("2", sale.realizedResult());
		amount("113", holding(portfolio, stock).quantity());
		amount("1127", holding(portfolio, stock).acquisitionValue());
		assertEquals(1, portfolios.getPositions(portfolio.portfolioId()).size());
	}

	@Test
	void zeroAndUnknownAllocationBasisMustRemainDifferentAndExpiryRealizesOnlyKnownBasis() {
		PortfolioSummary portfolio = portfolio();
		StockSecurity stock = security("Aktie"), rights = security("Recht");
		buy(portfolio, stock, "103", "10");
		StockCorporateAction action = definition(stock, rights, stock, StockCorporateActionType.RIGHTS_ISSUE);
		record(portfolio, action, StockCorporateActionOperation.RIGHTS_ALLOCATION, "103", null, "0", DATE);
		amount("0", holding(portfolio, rights).acquisitionValue());
		assertThrows(GBankingException.class, () -> record(portfolio, action,
				StockCorporateActionOperation.RIGHTS_ALLOCATION, "103", null, "0", DATE));
		record(portfolio, action, StockCorporateActionOperation.RIGHTS_EXPIRY, "103", null, null, DATE.plusDays(20));
		amount("0", service.getResults(portfolio.portfolioId()).get(1).realizedResult());
		StockSecurity rights2 = security("Weiteres Recht");
		StockCorporateAction unknown = definition(stock, rights2, stock, StockCorporateActionType.RIGHTS_ISSUE);
		record(portfolio, unknown, StockCorporateActionOperation.RIGHTS_ALLOCATION, "103", null, null, DATE);
		assertNull(holding(portfolio, rights2).acquisitionPrice());
		assertNull(holding(portfolio, stock).acquisitionPrice());
		record(portfolio, unknown, StockCorporateActionOperation.RIGHTS_SALE, "1", "2", null, DATE.plusDays(1));
		var sale = service.getResults(portfolio.portfolioId()).stream()
				.filter(result -> result.operation() == StockCorporateActionOperation.RIGHTS_SALE).findFirst().orElseThrow();
		amount("2", sale.gross());
		assertNull(sale.realizedResult());
	}

	@Test
	void reverseSplitAndFractionSaleShouldPreserveCostAndExactFractionalQuantities() {
		PortfolioSummary portfolio = portfolio();
		StockSecurity stock = security("Aktie");
		buy(portfolio, stock, "103", "10");
		portfolios.savePrice(stock.getId(), null, DATE.minusDays(1), n("10"), Currency.EUR);
		StockCorporateAction action = definition(stock, null, stock, StockCorporateActionType.CONSOLIDATION);
		record(portfolio, action, StockCorporateActionOperation.CONSOLIDATION, "103", null, null, DATE);
		amount("10.3", holding(portfolio, stock).quantity());
		amount("1030", holding(portfolio, stock).acquisitionValue());
		assertNull(holding(portfolio, stock).totalValue());
		record(portfolio, action, StockCorporateActionOperation.FRACTIONAL_SALE, "0.3", "105", null, DATE.plusDays(1));
		amount("10", holding(portfolio, stock).quantity());
		amount("1000", holding(portfolio, stock).acquisitionValue());
		var sale = service.getResults(portfolio.portfolioId()).get(1);
		amount("1.5", sale.realizedResult());
		portfolios.savePrice(stock.getId(), null, DATE.plusDays(1), n("105"), Currency.EUR);
		amount("1050", holding(portfolio, stock).totalValue());
		assertEquals(StockCorporateActionOperation.FRACTIONAL_SALE, sale.operation());
		assertThrows(GBankingException.class, () -> record(portfolio, action,
				StockCorporateActionOperation.CONSOLIDATION, "1", null, null, DATE));
	}

	@Test
	void nominalReductionAndCapitalReturnShouldNotBecomeDividendsOrArbitraryLosses() {
		PortfolioSummary portfolio = portfolio();
		StockSecurity stock = security("Aktie");
		buy(portfolio, stock, "100", "10");
		StockCorporateAction nominal = definition(stock, null, stock, StockCorporateActionType.NOMINAL_REDUCTION);
		record(portfolio, nominal, StockCorporateActionOperation.NOMINAL_REDUCTION, null, null, null, DATE);
		amount("1000", holding(portfolio, stock).acquisitionValue());
		assertEquals(1, db.getAll(Booking.class).size());
		assertEquals(100_000_000L, db.getById(StockCorporateAction.class, nominal.getId()).getNewNominalE8());
		StockCorporateAction reduction = definition(stock, null, stock, StockCorporateActionType.CAPITAL_REDUCTION);
		record(portfolio, reduction, StockCorporateActionOperation.CAPITAL_RETURN, null, "70", "50", DATE.plusDays(1));
		amount("950", holding(portfolio, stock).acquisitionValue());
		var repayment = service.getResults(portfolio.portfolioId()).get(1);
		amount("20", repayment.realizedResult());
		assertEquals(StockCorporateActionOperation.CAPITAL_RETURN, repayment.operation());
		record(portfolio, reduction, StockCorporateActionOperation.CANCELLATION, "10", "80", "50", DATE.plusDays(2));
		amount("90", holding(portfolio, stock).quantity());
		assertTrue(holding(portfolio, stock).acquisitionValue().subtract(n("900")).abs().compareTo(n("0.000001")) < 0);
		assertThrows(GBankingException.class, () -> record(portfolio, reduction,
				StockCorporateActionOperation.CAPITAL_RETURN, null, "2000", "2000", DATE.plusDays(3)));
		assertThrows(GBankingException.class, () -> record(portfolio, reduction,
				StockCorporateActionOperation.CANCELLATION, "90", "800", "899", DATE.plusDays(3)));
		record(portfolio, reduction, StockCorporateActionOperation.CANCELLATION, "90", "800", "900", DATE.plusDays(3));
		assertTrue(portfolios.getPositions(portfolio.portfolioId()).isEmpty());
		amount("-100", service.getResults(portfolio.portfolioId()).get(3).realizedResult());
	}

	@Test
	void deletingLatestManualOperationShouldRestoreBasisCashAndAssociationAtomically() {
		PortfolioSummary portfolio = portfolio();
		StockSecurity stock = security("Aktie"), rights = security("Recht");
		buy(portfolio, stock, "103", "10");
		StockCorporateAction action = definition(stock, rights, stock, StockCorporateActionType.RIGHTS_ISSUE);
		record(portfolio, action, StockCorporateActionOperation.RIGHTS_ALLOCATION, "103", null, "103", DATE);
		record(portfolio, action, StockCorporateActionOperation.RIGHTS_SALE, "3", "2", null, DATE.plusDays(1));
		service.unlink(service.getResults(portfolio.portfolioId()).get(1).associationId());
		amount("103", holding(portfolio, rights).quantity());
		amount("8970", db.getById(BankAccount.class, portfolio.settlementAccountId()).getBalance());
		assertEquals(1, db.getAll(Booking.class).size());
		assertEquals(1, db.getAll(StockCorporateActionTransaction.class).size());
		int transactionId = db.getAll(StockCorporateActionMember.class).get(0).getTransactionId();
		assertThrows(GBankingException.class, () -> portfolios.deleteTransaction(transactionId));
		StockTransaction transaction = db.getById(StockTransaction.class, transactionId);
		assertThrows(GBankingException.class, () -> db.insertOrUpdate(transaction));
		StockTransactionSecurityLeg leg = db.getAllByParent(StockTransactionSecurityLeg.class, transactionId).get(0);
		assertThrows(GBankingException.class, () -> db.insertOrUpdate(leg));
		assertThrows(GBankingException.class, () -> db.delete(leg, StatementsConfig.StatementType.DELETE));
	}

	@Test
	void reusedBankBookingMustSurviveCancellationAndBeReusableWithoutCashDuplication() {
		PortfolioSummary portfolio = portfolio();
		StockSecurity stock = security("Aktie"), rights = security("Recht");
		buy(portfolio, stock, "103", "10");
		StockCorporateAction action = definition(stock, rights, stock, StockCorporateActionType.RIGHTS_ISSUE);
		record(portfolio, action, StockCorporateActionOperation.RIGHTS_ALLOCATION, "103", null, "103", DATE);
		Booking booking = new Booking(portfolio.settlementAccountId(), DATE.plusDays(1), DATE.plusDays(1),
				"Originalbankumsatz", n("5"), BookingType.DEPOSIT, 0);
		booking.setSource(Source.ONLINE);
		db.insertOrUpdate(booking);
		OperationRequest request = new OperationRequest(action.getId(), StockCorporateActionOperation.RIGHTS_SALE,
				DATE.plusDays(1), n("3"), n("2"), n("1.000"), null, null, booking.getId());
		service.record(portfolio, request);
		int saleId = db.getAll(StockCorporateActionMember.class).get(1).getTransactionId();
		StockTransactionCashLeg cash = db.getAllByParent(StockTransactionCashLeg.class, saleId).get(0);
		assertThrows(GBankingException.class, () -> db.insertOrUpdate(cash));
		service.unlink(service.getResults(portfolio.portfolioId()).get(1).associationId());
		service.record(portfolio, request);
		assertEquals(2, db.getAll(Booking.class).size());
		amount("8970", db.getById(BankAccount.class, portfolio.settlementAccountId()).getBalance());
		assertEquals("Originalbankumsatz", db.getById(Booking.class, booking.getId()).getPurpose());
		amount("2", service.getResults(portfolio.portfolioId()).get(1).realizedResult());
	}

	@Test
	void importedAllocationAndExerciseShouldLinkOriginalLegsWithoutDuplicatingPositions(@TempDir Path directory) throws Exception {
		PortfolioSummary portfolio = portfolio();
		StockSecurity stock = security("Alt"), rights = security("Recht"), young = security("Jung");
		buy(portfolio, stock, "103", "10");
		StockCorporateAction action = definition(stock, rights, young, StockCorporateActionType.RIGHTS_ISSUE);
		int grant = imported(portfolio, rights, "103", StockTransactionType.DELIVERY_IN, DATE);
		OperationRequest allocation = new OperationRequest(action.getId(), StockCorporateActionOperation.RIGHTS_ALLOCATION,
				DATE, n("103"), null, null, null, n("103"), null);
		service.link(portfolio, allocation, grant, null);
		Path export = directory.resolve("actions.csv");
		PortfolioPerformanceExportService exporter = new PortfolioPerformanceExportService();
		assertTrue(exporter.losesCorporateActionAssociations(portfolio));
		exporter.exportFile(export, ExportType.STOCK_PP_TRANSACTIONS_CSV, portfolio);
		assertEquals(3, Files.readAllLines(export).size());
		assertTrue(Files.readString(export).contains(";Einlieferung;;EUR"));
		assertThrows(GBankingException.class, () -> service.link(portfolio, allocation, grant, null));
		int outgoing = imported(portfolio, rights, "-100", StockTransactionType.DELIVERY_OUT, DATE.plusDays(1));
		int incoming = imported(portfolio, young, "10", StockTransactionType.DELIVERY_IN, DATE.plusDays(1));
		Booking payment = new Booking(portfolio.settlementAccountId(), DATE.plusDays(1), DATE.plusDays(1),
				"Bezug", n("-100"), BookingType.REMOVAL, 0);
		payment.setSource(Source.ONLINE);
		db.insertOrUpdate(payment);
		service.link(portfolio, new OperationRequest(action.getId(), StockCorporateActionOperation.SUBSCRIPTION,
				DATE.plusDays(1), n("100"), null, null, null, null, payment.getId()), incoming, outgoing);
		int associationId = service.getResults(portfolio.portfolioId()).get(1).associationId();
		var members = db.getAllByParent(StockCorporateActionMember.class, associationId);
		assertEquals(2, members.size());
		assertEquals(incoming, members.stream().filter(member -> member.getRole() == StockCorporateActionMemberRole.PRIMARY)
				.findFirst().orElseThrow().getTransactionId());
		assertEquals(outgoing, members.stream().filter(member -> member.getRole() == StockCorporateActionMemberRole.RELATED)
				.findFirst().orElseThrow().getTransactionId());
		amount("927", holding(portfolio, stock).acquisitionValue());
		amount("200", holding(portfolio, young).acquisitionValue());
		amount("3", holding(portfolio, rights).quantity());
		assertEquals(4, db.getAll(StockTransaction.class).size());
		assertEquals(2, db.getAll(Booking.class).size());
		assertTrue(portfolios.getTransactions(portfolio.portfolioId()).stream()
				.anyMatch(row -> row.transactionId() == outgoing && row.corporateOperation() == StockCorporateActionOperation.SUBSCRIPTION));
		service.unlink(service.getResults(portfolio.portfolioId()).get(1).associationId());
		assertTrue(db.getAllByParent(StockCorporateActionMember.class, associationId).isEmpty());
		assertEquals(StockTransactionStatus.SETTLED, db.getById(StockTransaction.class, outgoing).getTransactionStatus());
		assertNotNull(db.getById(Booking.class, payment.getId()));
		assertEquals(4, db.getAll(StockTransaction.class).size());
	}

	@Test
	void corporateMembershipAndSecurityExchangeMustExcludeEachOtherInBothDirections() {
		PortfolioSummary portfolio = portfolio();
		StockSecurity stock = security("Alt"), rights = security("Recht");
		buy(portfolio, stock, "10", "10");
		StockCorporateAction action = definition(stock, rights, stock, StockCorporateActionType.RIGHTS_ISSUE);
		int incoming = imported(portfolio, rights, "10", StockTransactionType.DELIVERY_IN, DATE);
		OperationRequest allocation = new OperationRequest(action.getId(), StockCorporateActionOperation.RIGHTS_ALLOCATION,
				DATE, n("10"), null, null, null, n("0"), null);
		service.link(portfolio, allocation, incoming, null);
		int outgoing = imported(portfolio, stock, "-10", StockTransactionType.DELIVERY_OUT, DATE);
		assertThrows(GBankingException.class, () -> portfolios.linkSecurityExchange(portfolio, outgoing, incoming));
		service.unlink(db.getAll(StockCorporateActionTransaction.class).get(0).getId());
		portfolios.linkSecurityExchange(portfolio, outgoing, incoming);
		int related = imported(portfolio, rights, "5", StockTransactionType.DELIVERY_IN, DATE);
		OperationRequest pair = new OperationRequest(action.getId(), StockCorporateActionOperation.RIGHTS_ALLOCATION,
				DATE, n("15"), null, null, null, n("0"), null);
		assertThrows(GBankingException.class, () -> service.link(portfolio, pair, incoming, related));
		assertTrue(db.getAll(StockCorporateActionTransaction.class).isEmpty());
		assertTrue(db.getAll(StockCorporateActionMember.class).isEmpty());
		assertEquals(4, db.getAll(StockTransaction.class).size());
	}

	@Test
	void bankOnlyOpeningHoldingMustSeedEconomicBasisBeforeTheAction() {
		PortfolioSummary portfolio = portfolio();
		StockSecurity stock = security("Bankbestand"), rights = security("Recht");
		StockPortfolioStatement statement = new StockPortfolioStatement();
		statement.setPortfolioId(portfolio.portfolioId());
		statement.setSourceId(source(StockDataSourceType.FINTS));
		statement.setStatementAt(DATE.minusDays(1).atStartOfDay());
		statement.setStatementStatus(StockStatementStatus.DRAFT);
		db.insertOrUpdate(statement);
		StockPortfolioStatementPosition position = new StockPortfolioStatementPosition();
		position.setStatementId(statement.getId());
		position.setSecurityId(stock.getId());
		position.setQuantityType(StockQuantityType.UNITS);
		position.setQuantityE9(103_000_000_000L);
		position.setReportedAcquisitionPriceE8(1_000_000_000L);
		position.setAcquisitionPriceCurrency(Currency.EUR);
		db.insertOrUpdate(position);
		statement.setStatementStatus(StockStatementStatus.FINAL);
		db.insertOrUpdate(statement);
		StockTransaction opening = new StockTransaction();
		opening.setPortfolioId(portfolio.portfolioId());
		opening.setSourceId(source(StockDataSourceType.MANUAL));
		opening.setTransactionType(StockTransactionType.RECONCILIATION_ADJUSTMENT);
		opening.setReconciliationStatementId(statement.getId());
		opening.setTransactionStatus(StockTransactionStatus.PENDING);
		opening.setTradeAt(statement.getStatementAt());
		db.insertOrUpdate(opening);
		StockTransactionSecurityLeg leg = new StockTransactionSecurityLeg();
		leg.setTransactionId(opening.getId());
		leg.setSecurityId(stock.getId());
		leg.setLegNumber(1);
		leg.setLegRole(StockSecurityLegRole.POSITION);
		leg.setQuantityE9(position.getQuantityE9());
		leg.setQuantityType(StockQuantityType.UNITS);
		db.insertOrUpdate(leg);
		opening.setTransactionStatus(StockTransactionStatus.SETTLED);
		opening.setSettledAt(statement.getStatementAt());
		db.insertOrUpdate(opening);
		StockCorporateAction action = definition(stock, rights, stock, StockCorporateActionType.RIGHTS_ISSUE);
		record(portfolio, action, StockCorporateActionOperation.RIGHTS_ALLOCATION, "103", null, "103", DATE);
		amount("103", holding(portfolio, stock).quantity());
		amount("927", holding(portfolio, stock).acquisitionValue());
		amount("103", holding(portfolio, rights).acquisitionValue());
		amount("10", portfolios.getBankReportedAcquisitionPrice(portfolio.portfolioId(), stock.getId(), Currency.EUR));
	}

	@Test
	void partialExerciseShouldMergeExistingYoungSharesAndRejectInsufficientRightsAtomically() {
		PortfolioSummary portfolio = portfolio();
		StockSecurity stock = security("Alt"), rights = security("Recht"), young = security("Jung");
		buy(portfolio, stock, "103", "10");
		buy(portfolio, young, "2", "30");
		StockCorporateAction action = definition(stock, rights, young, StockCorporateActionType.RIGHTS_ISSUE);
		record(portfolio, action, StockCorporateActionOperation.RIGHTS_ALLOCATION, "103", null, "103", DATE);
		record(portfolio, action, StockCorporateActionOperation.SUBSCRIPTION, "50", null, null, DATE.plusDays(1));
		amount("7", holding(portfolio, young).quantity());
		amount("160", holding(portfolio, young).acquisitionValue());
		assertThrows(GBankingException.class, () -> record(portfolio, action,
				StockCorporateActionOperation.SUBSCRIPTION, "60", null, null, DATE.plusDays(2)));
		amount("53", holding(portfolio, rights).quantity());
		assertEquals(2, db.getAll(StockCorporateActionTransaction.class).size());
		assertEquals(3, db.getAll(Booking.class).size());
		assertThrows(GBankingException.class, () -> StockCorporateActionService.ratio(n("1"), 3, 1));
	}

	@Test
	void roundedUnitPricesMustNotLoseTransferredTotalBasis() {
		PortfolioSummary portfolio = portfolio();
		StockSecurity stock = security("Alt"), rights = security("Recht"), young = security("Jung");
		buy(portfolio, stock, "3", "1");
		StockCorporateAction action = definition(stock, rights, young, StockCorporateActionType.RIGHTS_ISSUE);
		record(portfolio, action, StockCorporateActionOperation.RIGHTS_ALLOCATION, "3", null, "1", DATE);
		assertEquals(0, n("2").compareTo(holding(portfolio, stock).acquisitionValue()));
		assertEquals(0, n("1").compareTo(holding(portfolio, rights).acquisitionValue()));
		record(portfolio, action, StockCorporateActionOperation.SUBSCRIPTION, "3", null, null, DATE.plusDays(1));
		assertEquals(0, n("4").compareTo(holding(portfolio, young).acquisitionValue()));
		StockSecurity successor = security("Nachfolger");
		portfolios.recordSecurityExchange(portfolio, new StockPortfolioService.SecurityExchangeRequest(
				young.getId(), successor.getId(), n("0.3"), DATE.plusDays(2), null));
		assertEquals(0, n("4").compareTo(holding(portfolio, successor).acquisitionValue()));
		assertTrue(portfolios.getCorporateActionHistory(portfolio.portfolioId(), action.getId()).stream()
				.anyMatch(row -> row.securityId() == successor.getId()));
		assertThrows(GBankingException.class, () -> service.unlink(service.getResults(portfolio.portfolioId()).get(1).associationId()));
		portfolios.recordTrade(portfolio, holding(portfolio, successor), new TradeRequest(StockTransactionType.SELL,
				DATE.plusDays(3), DATE.plusDays(3), n("0.1"), n("10"), Currency.EUR, BigDecimal.ONE, null, null, null));
		StockCorporateAction reduction = definition(successor, null, successor, StockCorporateActionType.CAPITAL_REDUCTION);
		record(portfolio, reduction, StockCorporateActionOperation.CANCELLATION, "0.2", "0", "2.66666667", DATE.plusDays(4));
		assertFalse(portfolios.getPositions(portfolio.portfolioId()).stream().anyMatch(row -> row.securityId() == successor.getId()));
	}

	@Test
	void sameSecurityImportedConsolidationMustContainBothGrossMovementsNotJustTheirNet() {
		PortfolioSummary portfolio = portfolio();
		StockSecurity stock = security("Aktie");
		buy(portfolio, stock, "103", "10");
		StockCorporateAction action = definition(stock, null, stock, StockCorporateActionType.CONSOLIDATION);
		OperationRequest request = new OperationRequest(action.getId(), StockCorporateActionOperation.CONSOLIDATION,
				DATE, n("103"), null, null, null, null, null);
		int netOnly = imported(portfolio, stock, "-92.7", StockTransactionType.DELIVERY_OUT, DATE);
		assertThrows(GBankingException.class, () -> service.link(portfolio, request, netOnly, null));
		portfolios.deleteTransaction(netOnly);
		int outgoing = imported(portfolio, stock, "-103", StockTransactionType.DELIVERY_OUT, DATE);
		int incoming = imported(portfolio, stock, "10.3", StockTransactionType.DELIVERY_IN, DATE);
		service.link(portfolio, request, incoming, outgoing);
		amount("10.3", holding(portfolio, stock).quantity());
		amount("1030", holding(portfolio, stock).acquisitionValue());
	}

	@Test
	void subscriptionExportShouldKeepBothMovementsAndChargeCashFeesAndTaxesOnlyOnce(@TempDir Path directory) throws Exception {
		PortfolioSummary portfolio = portfolio();
		StockSecurity stock = security("Alt"), rights = security("Recht"), young = security("Jung");
		buy(portfolio, stock, "10", "10");
		StockCorporateAction action = definition(stock, rights, young, StockCorporateActionType.RIGHTS_ISSUE);
		record(portfolio, action, StockCorporateActionOperation.RIGHTS_ALLOCATION, "10", null, "0", DATE);
		service.record(portfolio, new OperationRequest(action.getId(), StockCorporateActionOperation.SUBSCRIPTION,
				DATE.plusDays(1), n("10"), null, n("1"), n("2"), null, null));
		Path file = directory.resolve("subscription.csv");
		new PortfolioPerformanceExportService().exportFile(file, ExportType.STOCK_PP_TRANSACTIONS_CSV, portfolio);
		try (var reader = Files.newBufferedReader(file);
				var parser = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
			var rows = parser.getRecords();
			assertEquals(4, rows.size());
			var outgoing = rows.stream().filter(row -> row.get("Typ").equals("Auslieferung")).findFirst().orElseThrow();
			assertEquals("Recht", outgoing.get("Wertpapiername"));
			assertEquals("10", outgoing.get("Stück"));
			assertEquals("", outgoing.get("Wert"));
			assertEquals("", outgoing.get("Gebühren"));
			assertEquals("", outgoing.get("Steuern"));
			var incoming = rows.stream().filter(row -> row.get("Wertpapiername").equals("Jung")).findFirst().orElseThrow();
			assertEquals("Kauf", incoming.get("Typ"));
			assertEquals("13,00", incoming.get("Wert"));
			assertEquals("1,00", incoming.get("Gebühren"));
			assertEquals("2,00", incoming.get("Steuern"));
		}
		assertEquals(2, db.getAll(StockCorporateActionTransaction.class).size());
	}

	private int imported(PortfolioSummary portfolio, StockSecurity security, String quantity, StockTransactionType type, LocalDate date) {
		int sourceId = source(StockDataSourceType.FILE);
		StockImportBatch batch = new StockImportBatch();
		batch.setSourceId(sourceId);
		batch.setImporterKey("test");
		batch.setFormatType("test");
		batch.setImporterVersion("1");
		batch.setContentHash(security.getId() + quantity);
		batch.setStartedAt(date.atStartOfDay());
		batch.setImportStatus(StockImportStatus.RUNNING);
		db.insertOrUpdate(batch);
		StockImportRecord record = new StockImportRecord();
		record.setImportBatchId(batch.getId());
		record.setRecordNumber(1);
		record.setRecordType("transaction");
		record.setFingerprint(batch.getContentHash());
		record.setRecordStatus(StockImportRecordStatus.IMPORTED);
		db.insertOrUpdate(record);
		StockTransaction transaction = new StockTransaction();
		transaction.setPortfolioId(portfolio.portfolioId());
		transaction.setSourceId(sourceId);
		transaction.setImportRecordId(record.getId());
		transaction.setFingerprint(record.getFingerprint());
		transaction.setTransactionType(type);
		transaction.setTransactionStatus(StockTransactionStatus.PENDING);
		transaction.setTradeAt(date.atStartOfDay());
		db.insertOrUpdate(transaction);
		StockTransactionSecurityLeg leg = new StockTransactionSecurityLeg();
		leg.setTransactionId(transaction.getId());
		leg.setLegNumber(1);
		leg.setSecurityId(security.getId());
		leg.setLegRole(StockSecurityLegRole.POSITION);
		leg.setQuantityType(StockQuantityType.UNITS);
		leg.setQuantityE9(StockCorporateActionService.scaled(n(quantity), 9));
		db.insertOrUpdate(leg);
		transaction.setTransactionStatus(StockTransactionStatus.SETTLED);
		transaction.setSettledAt(date.atTime(12, 0));
		db.insertOrUpdate(transaction);
		batch.setImportStatus(StockImportStatus.IMPORTED);
		batch.setCompletedAt(date.atTime(13, 0));
		db.insertOrUpdate(batch);
		return transaction.getId();
	}

	private int source(StockDataSourceType type) {
		return db.getAll(StockDataSource.class).stream().filter(row -> row.getSourceType() == type).findFirst().orElseGet(() -> {
			StockDataSource source = new StockDataSource();
			source.setSourceCode(type.name());
			source.setSourceName(type.name());
			source.setSourceType(type);
			return db.insertOrUpdate(source);
		}).getId();
	}

	private void record(PortfolioSummary portfolio, StockCorporateAction action, StockCorporateActionOperation operation,
			String quantity, String price, String basis, LocalDate date) {
		service.record(portfolio, new OperationRequest(action.getId(), operation, date, n(quantity), n(price), null, null, n(basis), null));
	}
	private StockCorporateAction definition(StockSecurity stock, StockSecurity rights, StockSecurity target, StockCorporateActionType type) {
		StockCorporateAction action = new StockCorporateAction();
		action.setName("Kapitalmaßnahme " + stock.getName());
		action.setActionType(type);
		action.setUnderlyingSecurityId(stock.getId());
		action.setRightsSecurityId(rights != null ? rights.getId() : null);
		action.setTargetSecurityId(target.getId());
		action.setAllocationFromE9(1_000_000_000L);
		action.setAllocationToE9(1_000_000_000L);
		action.setRatioFromE9(10_000_000_000L);
		action.setRatioToE9(1_000_000_000L);
		action.setSubscriptionPriceE8(1_000_000_000L);
		action.setEffectiveDate(DATE);
		action.setEndDate(DATE.plusDays(20));
		action.setOldNominalE8(500_000_000L);
		action.setNewNominalE8(100_000_000L);
		return service.createAction(action);
	}
	private void buy(PortfolioSummary portfolio, StockSecurity security, String quantity, String price) {
		PositionSummary position = new PositionSummary(portfolio.portfolioId(), security.getId(), security.getName(), null, null,
				StockQuantityType.UNITS, BigDecimal.ZERO, null, null, null, null, Currency.EUR, null);
		portfolios.recordTrade(portfolio, position, new TradeRequest(StockTransactionType.BUY, DATE.minusDays(1), DATE.minusDays(1),
				n(quantity), n(price), Currency.EUR, BigDecimal.ONE, null, null, null));
	}
	private PositionSummary holding(PortfolioSummary portfolio, StockSecurity security) {
		return portfolios.getPositions(portfolio.portfolioId()).stream().filter(row -> row.securityId() == security.getId()).findFirst().orElseThrow();
	}
	private StockSecurity security(String name) {
		StockSecurity security = new StockSecurity();
		security.setSecurityType(StockSecurityType.STOCK);
		security.setName(name);
		security.setDefaultQuantityType(StockQuantityType.UNITS);
		security.setDefaultQuotationType(StockQuotationType.ABSOLUTE);
		security.setDefaultQuoteCurrency(Currency.EUR);
		return db.insertOrUpdate(security);
	}
	private PortfolioSummary portfolio() {
		BankAccount depot = TestDataFactory.createSampleAccount(null);
		depot.setAccountType(AccountType.DEPOT);
		db.insertOrUpdate(depot);
		BankAccount cash = TestDataFactory.createSampleAccount(null);
		cash.setAccountType(AccountType.CURRENT_ACCOUNT);
		cash.setBalance(n("10000"));
		db.insertOrUpdate(cash);
		StockPortfolio portfolio = new StockPortfolio();
		portfolio.setId(100);
		portfolio.setAccountId(depot.getId());
		portfolio.setCurrentSettlementRelationId(200);
		portfolio.setOpenedAt(DATE.minusDays(30));
		StockPortfolioSettlementAccount relation = new StockPortfolioSettlementAccount();
		relation.setId(200);
		relation.setPortfolioId(100);
		relation.setAccountId(cash.getId());
		relation.setValidFrom(portfolio.getOpenedAt());
		db.executeInTransaction(() -> { db.insertOrUpdate(portfolio); db.insertOrUpdate(relation); return null; });
		return portfolios.getPortfolios().get(0);
	}
	private static BigDecimal n(String value) { return value != null ? new BigDecimal(value) : null; }
	private static void amount(String expected, BigDecimal actual) {
		assertNotNull(actual);
		assertTrue(actual.subtract(n(expected)).abs().compareTo(n("0.000001")) < 0, () -> "Expected " + expected + ", got " + actual);
	}
}
