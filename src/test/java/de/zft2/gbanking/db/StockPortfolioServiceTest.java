package de.zft2.gbanking.db;

import static de.zft2.gbanking.testdata.TestDataFactory.createSecurityIdentifier;
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
import java.time.Month;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kapott.hbci.GV_Result.GVRWPDepotList.Entry;
import org.kapott.hbci.GV_Result.GVRWPDepotList.Entry.Gattung;
import org.kapott.hbci.structures.BigDecimalValue;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.StockDataSourceType;
import de.zft2.gbanking.db.dao.enu.StockIdentifierType;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockPriceType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;
import de.zft2.gbanking.db.dao.enu.StockTransactionEditField;
import de.zft2.gbanking.db.dao.enu.StockTransactionStatus;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockExchangeRate;
import de.zft2.gbanking.db.dao.stock.StockPortfolio;
import de.zft2.gbanking.db.dao.stock.StockPortfolioSettlementAccount;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.db.dao.stock.StockSecurityPrice;
import de.zft2.gbanking.db.dao.stock.StockSecurityPriceSource;
import de.zft2.gbanking.db.dao.stock.StockTransaction;
import de.zft2.gbanking.db.dao.stock.StockTransactionSecurityLeg;
import de.zft2.gbanking.db.dao.stock.StockTransferLink;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioFinTsService;
import de.zft2.gbanking.service.stock.StockPortfolioTransferService;
import de.zft2.gbanking.service.stock.PortfolioPerformanceImportService;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.service.stock.StockPortfolioService.IncomeRequest;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.SecurityExchangeRequest;
import de.zft2.gbanking.service.stock.StockPortfolioService.TradeRequest;
import de.zft2.gbanking.service.stock.StockPortfolioService.TransactionEditRequest;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService;
import de.zft2.gbanking.testdata.TestDataFactory;

class StockPortfolioServiceTest extends DBControllerIntegrationBaseTest {

	private static final LocalDate TRADE_DATE = LocalDate.of(2026, Month.SEPTEMBER, 10);
	private static final LocalDate SETTLEMENT_DATE = LocalDate.of(2026, Month.SEPTEMBER, 14);

	@TempDir
	Path files;

	@Test
	void identifierSelectionShouldKeepTheFirstValuesWithoutDateFiltering() {
		int securityId = createSecurity("Identified").getId();
		createSecurity("Missing");
		LocalDate today = LocalDate.now(ZoneId.systemDefault());
		db.insertOrUpdate(createSecurityIdentifier(securityId, StockIdentifierType.ISIN, "DE0000000001",
				today.minusDays(2), today.minusDays(1)));
		db.insertOrUpdate(createSecurityIdentifier(securityId, StockIdentifierType.ISIN, "DE0000000002", null, null));
		db.insertOrUpdate(createSecurityIdentifier(securityId, StockIdentifierType.WKN, "ABC123", today.plusDays(1), null));

		var securities = new StockPortfolioService().getTransactionSecurities();

		assertEquals("DE0000000001", securities.get(0).isin());
		assertEquals("ABC123", securities.get(0).wkn());
		assertNull(securities.get(1).isin());
		assertNull(securities.get(1).wkn());
	}

	@Test
	void securityDeletionShouldRejectHoldingsAndHistoricalReferences() throws Exception {
		PortfolioSummary portfolio = createPortfolio(100, 200, "Depot", "Verrechnung", BigDecimal.ZERO);
		importDeliveries(portfolio, deliveryRow("2026-09-10", "Einlieferung", 50, 5000));
		StockPortfolioService portfolios = new StockPortfolioService();
		int securityId = portfolios.getPositions(portfolio.portfolioId()).get(0).securityId();
		StockSecurityAdministrationService securities = new StockSecurityAdministrationService();
		assertFalse(securities.canDelete(securityId));
		assertThrows(GBankingException.class, () -> securities.delete(securityId));
		importDeliveries(portfolio, deliveryRow("2026-09-16", "Auslieferung", 50, 5000));
		assertTrue(portfolios.getPositions(portfolio.portfolioId()).isEmpty());
		assertFalse(securities.canDelete(securityId));
		assertThrows(GBankingException.class, () -> securities.delete(securityId));
		assertNotNull(db.getById(StockSecurity.class, securityId));
		assertEquals(2, portfolios.getTransactions(portfolio.portfolioId()).size());
	}

	@Test
	void securityUsageShouldDistinguishHistoricalAndCurrentHoldingsPerPortfolio() throws Exception {
		PortfolioSummary first = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary second = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		importDeliveries(first, deliveryRow("2026-09-10", "Einlieferung", 50, 50)
				+ deliveryRow("2026-09-16", "Auslieferung", 50, 50));
		importDeliveries(second, deliveryRow("2026-09-10", "Einlieferung", 1, 1)
				.replace("DE0000000001", "DE0000000002"));
		int unused = createSecurity("Unused").getId();
		StockPortfolioService portfolios = new StockPortfolioService();
		int historical = portfolios.getTransactions(first.portfolioId()).get(0).securityId();
		int current = portfolios.getPositions(second.portfolioId()).get(0).securityId();
		var usage = new StockSecurityAdministrationService().getSecurityUsage();
		assertTrue(usage.referenced().containsAll(List.of(historical, current)));
		assertFalse(usage.referenced().contains(unused));
		assertFalse(usage.holdingsByPortfolio().containsKey(first.portfolioId()));
		assertEquals(java.util.Set.of(current), usage.holdingsByPortfolio().get(second.portfolioId()));
	}

	@ParameterizedTest
	@ValueSource(strings = { "outgoing matched", "incoming matched", "outgoing missing", "incoming missing" })
	void singleDeliveryShouldBecomeALinkedTransferWithoutDuplicatingExistingMovements(String scenario) throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		boolean outgoing = scenario.startsWith("outgoing");
		boolean matched = scenario.endsWith("matched");
		importDeliveries(source, deliveryRow("2026-09-10", "Einlieferung", 200, 20000)
				+ (outgoing || matched ? deliveryRow("2026-09-16", "Auslieferung", 50, 5000) : ""));
		if (!outgoing || matched) {
			importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 50, 3750));
		}
		PortfolioSummary selected = outgoing ? source : target;
		PortfolioSummary other = outgoing ? target : source;
		StockTransaction original = db.getAllByParent(StockTransaction.class, selected.portfolioId()).stream()
				.filter(transaction -> transaction.getTransactionType() == (outgoing
						? StockTransactionType.DELIVERY_OUT : StockTransactionType.DELIVERY_IN)).findFirst().orElseThrow();
		int count = db.getAll(StockTransaction.class).size();
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		var plan = transfers.preparePositionTransfer(selected, other, original.getId());
		assertEquals(matched, plan.counterpartId() != null);
		assertEquals(0, new BigDecimal("50").compareTo(plan.position().quantity()));
		transfers.convertPositionDelivery(selected, other, plan);
		assertEquals(count + (matched ? 0 : 1), db.getAll(StockTransaction.class).size());
		StockTransferLink link = db.getAll(StockTransferLink.class).get(0);
		assertEquals(StockTransactionType.TRANSFER_OUT,
				db.getById(StockTransaction.class, link.getOutgoingTransactionId()).getTransactionType());
		assertEquals(StockTransactionType.TRANSFER_IN,
				db.getById(StockTransaction.class, link.getIncomingTransactionId()).getTransactionType());
		StockTransaction stored = db.getById(StockTransaction.class, original.getId());
		assertEquals(original.getSettledAt(), stored.getSettledAt());
		assertEquals(original.getFingerprint(), stored.getFingerprint());
		assertEquals(original.getImportRecordId(), stored.getImportRecordId());
		assertEquals(original.getId(), outgoing ? link.getOutgoingTransactionId() : link.getIncomingTransactionId());
		if (matched) {
			assertEquals(plan.counterpartId(), outgoing ? link.getIncomingTransactionId() : link.getOutgoingTransactionId());
		}
		StockPortfolioService portfolios = new StockPortfolioService();
		assertEquals(0, new BigDecimal("150").compareTo(portfolios.getPositions(source.portfolioId()).get(0).quantity()));
		assertEquals(0, new BigDecimal("50").compareTo(portfolios.getPositions(target.portfolioId()).get(0).quantity()));
		assertTrue(db.getAll(Booking.class).isEmpty());
		assertThrows(GBankingException.class, () -> transfers.convertPositionDelivery(selected, other, plan));
	}

	@Test
	void singleDeliveryShouldRejectAmbiguousCounterpartsAndStalePreviews() throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		importDeliveries(source, deliveryRow("2026-09-10", "Einlieferung", 200, 20000)
				+ deliveryRow("2026-09-16", "Auslieferung", 50, 5000));
		StockTransaction original = db.getAllByParent(StockTransaction.class, source.portfolioId()).stream()
				.filter(transaction -> transaction.getTransactionType() == StockTransactionType.DELIVERY_OUT).findFirst().orElseThrow();
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		importDeliveries(target, deliveryRow("2026-09-17", "Einlieferung", 50, 3750)
				+ deliveryRow("2026-09-16", "Einlieferung", 49, 3675));
		var plan = transfers.preparePositionTransfer(source, target, original.getId());
		assertNull(plan.counterpartId());
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 50, 3750));
		assertThrows(GBankingException.class, () -> transfers.convertPositionDelivery(source, target, plan));
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 50, 4000));
		int originalId = original.getId();
		assertThrows(GBankingException.class, () -> transfers.preparePositionTransfer(source, target, originalId));
		assertEquals(StockTransactionType.DELIVERY_OUT, db.getById(StockTransaction.class, original.getId()).getTransactionType());
		assertTrue(db.getAll(StockTransferLink.class).isEmpty());
	}

	@Test
	void incomingDeliveryMustNotCreateAnUnfundedOutgoingTransfer() throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 50, 3750));
		StockTransaction original = db.getAllByParent(StockTransaction.class, target.portfolioId()).get(0);
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		var plan = transfers.preparePositionTransfer(target, source, original.getId());
		int count = db.getAll(StockTransaction.class).size();
		assertThrows(GBankingException.class, () -> transfers.convertPositionDelivery(target, source, plan));
		assertEquals(count, db.getAll(StockTransaction.class).size());
		assertEquals(StockTransactionType.DELIVERY_IN, db.getById(StockTransaction.class, original.getId()).getTransactionType());
		assertTrue(db.getAll(StockTransferLink.class).isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = { "both", "outgoing", "incoming", "split outgoing", "split dates" })
	void portfolioTransferShouldReuseCompleteImportedDeliveriesAndPreserveLots(String sides) throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		String outgoing = sides.equals("split dates")
				? deliveryRow("2026-09-15", "Auslieferung", 50, 5000) + deliveryRow("2026-09-16", "Auslieferung", 150, 15000)
				: sides.equals("split outgoing")
				? deliveryRow("2026-09-15", "Auslieferung", 50, 5000) + deliveryRow("2026-09-15", "Auslieferung", 150, 15000)
				: deliveryRow("2026-09-15", "Auslieferung", 200, 20000);
		importDeliveries(source, deliveryRow("2026-09-10", "Einlieferung", 200, 20000)
				+ (sides.equals("incoming") ? "" : outgoing));
		if (!sides.equals("outgoing")) {
			importDeliveries(target, sides.equals("split outgoing")
					? deliveryRow("2026-09-16", "Einlieferung", 200, 16250)
					: deliveryRow("2026-09-16", "Einlieferung", 50, 5000)
							+ deliveryRow(sides.equals("split dates") ? "2026-09-17" : "2026-09-16", "Einlieferung", 150, 11250));
		}
		List<StockTransaction> originals = db.getAll(StockTransaction.class);
		List<StockTransactionSecurityLeg> lots = db.getAll(StockTransactionSecurityLeg.class);
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		var plan = transfers.findExistingDeliveries(source, target, SETTLEMENT_DATE.plusDays(10));
		assertNotNull(plan);
		assertEquals(originals.size(), db.getAll(StockTransaction.class).size());
		transfers.transferExistingDeliveries(source, target, plan, null, BigDecimal.ONE);

		StockPortfolioService portfolios = new StockPortfolioService();
		assertTrue(portfolios.getPositions(source.portfolioId()).isEmpty());
		PositionSummary holding = portfolios.getPositions(target.portfolioId()).get(0);
		assertEquals(0, new BigDecimal("200").compareTo(holding.quantity()));
		assertEquals(0, new BigDecimal(sides.equals("outgoing") ? "100" : "81.25").compareTo(holding.acquisitionPrice()));
		assertEquals(originals.size() + (sides.equals("incoming") || sides.equals("outgoing") ? 1 : 0),
				db.getAll(StockTransaction.class).size());
		for (StockTransaction original : originals) {
			StockTransaction stored = db.getById(StockTransaction.class, original.getId());
			assertEquals(original.getFingerprint(), stored.getFingerprint());
			assertEquals(original.getImportRecordId(), stored.getImportRecordId());
			assertEquals(original.getSettledAt(), stored.getSettledAt());
			if (plan.outgoingIds().contains(original.getId()) || plan.incomingIds().contains(original.getId())) {
				assertEquals(original.getPortfolioId() == source.portfolioId()
						? StockTransactionType.TRANSFER_OUT : StockTransactionType.TRANSFER_IN, stored.getTransactionType());
			}
		}
		for (StockTransactionSecurityLeg lot : lots) {
			StockTransactionSecurityLeg stored = db.getById(StockTransactionSecurityLeg.class, lot.getId());
			assertEquals(lot.getQuantityE9(), stored.getQuantityE9());
			assertEquals(lot.getPriceE8(), stored.getPriceE8());
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void portfolioTransferShouldRevalidateAndRollBackAnInvalidOverrideDate(boolean changeHoldings) throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		importDeliveries(source, deliveryRow("2026-09-10", "Einlieferung", 200, 20000));
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 50, 5000)
				+ deliveryRow("2026-09-16", "Einlieferung", 150, 11250));
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		var plan = transfers.findExistingDeliveries(source, target, null);
		if (changeHoldings) {
			importDeliveries(source, deliveryRow("2026-09-15", "Einlieferung", 1, 100));
		}
		int count = db.getAll(StockTransaction.class).size();
		LocalDate overrideDate = changeHoldings ? null : TRADE_DATE.minusDays(1);
		assertThrows(GBankingException.class, () -> transfers.transferExistingDeliveries(source, target, plan,
				overrideDate, BigDecimal.ONE));
		assertEquals(count, db.getAll(StockTransaction.class).size());
		assertTrue(db.getAllByParent(StockTransaction.class, target.portfolioId()).stream()
				.allMatch(transaction -> transaction.getTransactionType() == StockTransactionType.DELIVERY_IN));
	}

	@Test
	void portfolioTransferShouldRequireCompleteQuantitiesAndAllowExplicitDateOverride() throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		importDeliveries(source, deliveryRow("2026-09-10", "Einlieferung", 200, 20000));
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 199, 19900));
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		assertNull(transfers.findExistingDeliveries(source, target, null));
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 1, 100));
		var plan = transfers.findExistingDeliveries(source, target, null);
		LocalDate date = SETTLEMENT_DATE.plusDays(5);
		transfers.transferExistingDeliveries(source, target, plan, date, BigDecimal.ONE);
		assertTrue(db.getAllByParent(StockTransaction.class, target.portfolioId()).stream()
				.allMatch(transaction -> transaction.getSettledAt().toLocalDate().equals(date)
						&& transaction.getTransactionType() == StockTransactionType.TRANSFER_IN));
	}

	private void importDeliveries(PortfolioSummary portfolio, String rows) throws Exception {
		if (db.getAll(StockDataSource.class).stream().noneMatch(source -> source.getSourceCode().equals("PORTFOLIO_PERFORMANCE"))) {
			StockDataSource source = new StockDataSource();
			source.setSourceCode("PORTFOLIO_PERFORMANCE");
			source.setSourceName("Portfolio Performance");
			source.setSourceType(StockDataSourceType.FILE);
			db.insertOrUpdate(source);
		}
		Path csv = Files.createTempFile(files, "deliveries-", ".csv");
		Files.writeString(csv, "Datum;Typ;Wert;Buchungswährung;Bruttobetrag;Währung Bruttobetrag;Wechselkurs;"
				+ "Gebühren;Steuern;Stück;ISIN;WKN;Ticker-Symbol;Wertpapiername;Notiz\n" + rows);
		new PortfolioPerformanceImportService().importFile(csv, ExportType.STOCK_PP_TRANSACTIONS_CSV, portfolio);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void portfolioTransferShouldRejectIncompleteHoldingsAndPreferOldestIncomingDeliveries(boolean incomplete) throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		importDeliveries(source, deliveryRow("2026-09-10", "Einlieferung", 200, 20000)
				+ (incomplete ? deliveryRow("2026-09-10", "Einlieferung", 1, 100).replace("DE0000000001", "DE0000000002") : "")
				+ deliveryRow("2026-09-15", "Auslieferung", 200, 20000));
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 200, 20000)
				+ (incomplete ? "" : deliveryRow("2026-09-17", "Einlieferung", 200, 20000)));
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		if (incomplete) {
			assertNull(transfers.findExistingDeliveries(source, target, null));
		} else {
			assertEquals(List.of(LocalDate.of(2026, Month.SEPTEMBER, 15), LocalDate.of(2026, Month.SEPTEMBER, 16)),
					transfers.findExistingDeliveries(source, target, null).dates());
			assertNotNull(transfers.findExistingDeliveries(source, target, LocalDate.of(2026, Month.SEPTEMBER, 16)));
			assertTrue(transfers.findExistingDeliveries(source, target, LocalDate.of(2026, Month.SEPTEMBER, 17))
					.dates().contains(LocalDate.of(2026, Month.SEPTEMBER, 17)));
		}
	}

	private static String deliveryRow(String date, String type, int quantity, int value) {
		return date + "T12:00;" + type + ";" + value + ",00;EUR;;;;;;" + quantity + ";DE0000000001;;;Aktie;\n";
	}

	@ParameterizedTest
	@ValueSource(strings = { "single", "split lots", "split dates" })
	void incomingOnlyTransferShouldReuseOldestLotsAndIgnoreLaterMatchingDeliveries(String lots) throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		importDeliveries(source, deliveryRow("2010-10-30", "Einlieferung", 430, 43));
		boolean splitDates = lots.equals("split dates");
		importDeliveries(target, (lots.equals("single") ? deliveryRow("2010-10-31", "Einlieferung", 430, 43)
				: deliveryRow("2010-10-31", "Einlieferung", 50, 5)
						+ deliveryRow(splitDates ? "2010-11-01" : "2010-10-31", "Einlieferung", 380, 19))
				+ deliveryRow("2015-10-31", "Einlieferung", 430, 43));
		StockPortfolioService portfolios = new StockPortfolioService();
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		var plan = transfers.findExistingDeliveries(source, target, null);
		assertNotNull(plan);
		assertTrue(plan.outgoingIds().isEmpty());
		assertEquals(lots.equals("single") ? 1 : 2, plan.incomingIds().size());
		assertEquals(LocalDate.of(2010, splitDates ? Month.NOVEMBER : Month.OCTOBER, splitDates ? 1 : 31), plan.cutoff());
		var comparison = transfers.compareDeliveries(source, target, null);
		assertEquals(plan.cutoff(), comparison.cutoff());
		assertEquals(0, new BigDecimal("430").compareTo(comparison.differences().get(0).incoming()));
		int count = db.getAll(StockTransaction.class).size();
		transfers.transferExistingDeliveries(source, target, plan, null, BigDecimal.ONE);
		assertTrue(portfolios.getPositions(source.portfolioId()).isEmpty());
		assertEquals(0, new BigDecimal("860").compareTo(portfolios.getPositions(target.portfolioId()).get(0).quantity()));
		assertEquals(count + 1, db.getAll(StockTransaction.class).size());
		assertEquals(StockTransactionType.DELIVERY_IN, portfolios.getTransactions(target.portfolioId()).stream()
				.filter(transaction -> transaction.date().getYear() == 2015).findFirst().orElseThrow().transactionType());
	}

	@Test
	void incomingOnlyTransferMustNotSkipAnIncompleteInitialDelivery() throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		importDeliveries(source, deliveryRow("2026-09-10", "Einlieferung", 430, 43));
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 429, 43)
				+ deliveryRow("2026-09-20", "Einlieferung", 430, 43));
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		assertNull(transfers.findExistingDeliveries(source, target, null));
		var comparison = transfers.compareDeliveries(source, target, null);
		assertEquals(LocalDate.of(2026, Month.SEPTEMBER, 16), comparison.cutoff());
		assertEquals(0, new BigDecimal("429").compareTo(comparison.differences().get(0).incoming()));
		assertNotNull(transfers.findExistingDeliveries(source, target, LocalDate.of(2026, Month.SEPTEMBER, 20)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "automatic", "explicit", "incomplete", "same day" })
	void historicalOutgoingDeliveriesMustNotReplaceTheSourceHoldingsAtTheTransferDate(String scenario) throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		String historicalDate = scenario.equals("same day") ? "2009-12-28" : "2008-01-01";
		importDeliveries(source, (deliveryRow("2007-01-01", "Einlieferung", 50, 50)
				+ deliveryRow(historicalDate, "Auslieferung", 50, 50)).replace("DE0000000001", "DE0000000002")
				+ deliveryRow("2009-12-28", "Einlieferung", 1, 1));
		boolean incomplete = scenario.equals("incomplete");
		importDeliveries(target, deliveryRow("2010-10-31", "Einlieferung", incomplete ? 2 : 1, 1));
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		LocalDate cutoff = LocalDate.of(2010, Month.OCTOBER, 31);
		LocalDate requested = scenario.equals("explicit") ? cutoff : null;
		var comparison = transfers.compareDeliveries(source, target, requested);
		assertEquals(cutoff, comparison.cutoff());
		assertEquals(1, comparison.differences().size());
		var difference = comparison.differences().get(0);
		assertEquals(0, BigDecimal.ONE.compareTo(difference.expected()));
		assertEquals(!incomplete, difference.quantitiesMatch());
		var plan = transfers.findExistingDeliveries(source, target, requested);
		if (incomplete) {
			assertNull(plan);
			return;
		}
		assertNotNull(plan);
		assertTrue(plan.outgoingIds().isEmpty());
		int count = db.getAll(StockTransaction.class).size();
		transfers.transferExistingDeliveries(source, target, plan, null, BigDecimal.ONE);
		assertEquals(count + 1, db.getAll(StockTransaction.class).size());
		assertTrue(new StockPortfolioService().getPositions(source.portfolioId()).isEmpty());
		assertEquals(1, db.getAllByParent(StockTransaction.class, source.portfolioId()).stream()
				.filter(transaction -> transaction.getTransactionType() == StockTransactionType.DELIVERY_OUT).count());
	}

	@Test
	void incomingOnlyTransferMustRecheckTheOldestDeliveriesBeforeSaving() throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		importDeliveries(source, deliveryRow("2026-09-10", "Einlieferung", 430, 43));
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 430, 43));
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		var plan = transfers.findExistingDeliveries(source, target, null);
		importDeliveries(target, deliveryRow("2026-09-15", "Einlieferung", 1, 1));
		int count = db.getAll(StockTransaction.class).size();
		assertThrows(GBankingException.class, () -> transfers.transferExistingDeliveries(source, target, plan, null, BigDecimal.ONE));
		assertEquals(count, db.getAll(StockTransaction.class).size());
		assertTrue(db.getAll(StockTransaction.class).stream()
				.allMatch(transaction -> transaction.getTransactionType() == StockTransactionType.DELIVERY_IN));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void portfolioTransferShouldCompareAtTheCutoffAndIgnoreLaterMovements(boolean explicitDate) throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		importDeliveries(source, deliveryRow("2026-09-10", "Einlieferung", 200, 20000)
				+ deliveryRow("2026-09-20", "Einlieferung", 10, 1000));
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 200, 20000)
				+ deliveryRow("2026-09-20", "Auslieferung", 50, 5000)
				+ deliveryRow("2026-09-21", "Einlieferung", 25, 2500));
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		LocalDate cutoff = LocalDate.of(2026, Month.SEPTEMBER, 16);
		var plan = transfers.findExistingDeliveries(source, target, explicitDate ? cutoff : null);
		assertNotNull(plan);
		assertEquals(cutoff, plan.cutoff());
		assertEquals(0, new BigDecimal("200").compareTo(plan.positions().get(0).quantity()));
		importDeliveries(target, deliveryRow("2026-09-22", "Einlieferung", 200, 20000));
		transfers.transferExistingDeliveries(source, target, plan, null, BigDecimal.ONE);
		StockPortfolioService portfolios = new StockPortfolioService();
		assertEquals(0, BigDecimal.TEN.compareTo(portfolios.getPositions(source.portfolioId()).get(0).quantity()));
		assertEquals(0, new BigDecimal("375").compareTo(portfolios.getPositions(target.portfolioId()).get(0).quantity()));
		assertEquals(3, portfolios.getTransactions(target.portfolioId()).stream()
				.filter(transaction -> transaction.date().isAfter(cutoff))
				.filter(transaction -> transaction.transactionType() != StockTransactionType.TRANSFER_IN).count());
	}

	@Test
	void partialOutgoingDeliveriesMustNotMakePartialIncomingDeliveriesLookComplete() throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		importDeliveries(source, deliveryRow("2026-09-10", "Einlieferung", 200, 20000)
				+ deliveryRow("2026-09-15", "Auslieferung", 100, 10000));
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 100, 10000));
		assertNull(new StockPortfolioTransferService().findExistingDeliveries(source, target, LocalDate.of(2026, Month.SEPTEMBER, 16)));
	}

	@Test
	void manualPortfolioTransferShouldUseSourceHoldingsAtTheCutoff() throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		importDeliveries(source, deliveryRow("2026-09-10", "Einlieferung", 200, 20000)
				+ deliveryRow("2026-09-20", "Einlieferung", 10, 1000));
		new StockPortfolioTransferService().transferManually(source, target, LocalDate.of(2026, Month.SEPTEMBER, 16), BigDecimal.ONE);
		StockPortfolioService portfolios = new StockPortfolioService();
		assertEquals(0, BigDecimal.TEN.compareTo(portfolios.getPositions(source.portfolioId()).get(0).quantity()));
		assertEquals(0, new BigDecimal("200").compareTo(portfolios.getPositions(target.portfolioId()).get(0).quantity()));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void laterPurchasesMustNotPreventMatchingButDifferentSecurityRecordsMust(boolean sameSecurity) throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		StockPortfolioService portfolios = new StockPortfolioService();
		StockSecurity old;
		if (sameSecurity) {
			importDeliveries(source, deliveryRow("2026-09-10", "Einlieferung", 430, 43)
					.replace("DE0000000001", "SG1N28909355").replace("Aktie", "Ocean Sky International"));
			old = db.getById(StockSecurity.class, portfolios.getPositions(source.portfolioId()).get(0).securityId());
		} else {
			old = createSecurity("$ Ocean Sky Intl");
			portfolios.recordTrade(source, position(source, old), new TradeRequest(StockTransactionType.BUY,
					TRADE_DATE, SETTLEMENT_DATE, new BigDecimal("430"), new BigDecimal("0.05"),
					Currency.EUR, BigDecimal.ONE, null, null, null));
		}
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 430, 43)
				.replace("DE0000000001", "SG1N28909355").replace("Aktie", "Ocean Sky International"));
		var incoming = portfolios.getPositions(target.portfolioId()).get(0);
		portfolios.recordTrade(target, incoming, new TradeRequest(StockTransactionType.BUY,
				LocalDate.of(2026, Month.SEPTEMBER, 20), LocalDate.of(2026, Month.SEPTEMBER, 20), new BigDecimal("4570"),
				new BigDecimal("0.01"), Currency.EUR, BigDecimal.ONE, null, null, null));
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		LocalDate cutoff = LocalDate.of(2026, Month.SEPTEMBER, 16);
		if (sameSecurity) {
			var plan = transfers.findExistingDeliveries(source, target, null);
			assertNotNull(plan);
			assertEquals(0, new BigDecimal("430").compareTo(plan.positions().get(0).quantity()));
			transfers.transferExistingDeliveries(source, target, plan, null, BigDecimal.ONE);
			assertTrue(portfolios.getPositions(source.portfolioId()).isEmpty());
			assertEquals(0, new BigDecimal("5000").compareTo(portfolios.getPositions(target.portfolioId()).get(0).quantity()));
			return;
		}
		assertNull(transfers.findExistingDeliveries(source, target, cutoff));
		var differences = transfers.compareDeliveries(source, target, cutoff).differences();
		assertEquals(2, differences.size());
		assertTrue(differences.stream().anyMatch(row -> row.securityId() == old.getId() && row.isin().isEmpty()
				&& row.expected().compareTo(new BigDecimal("430")) == 0 && row.incoming().signum() == 0));
		assertTrue(differences.stream().anyMatch(row -> row.securityId() == incoming.securityId()
				&& row.isin().equals("SG1N28909355") && row.expected().signum() == 0
				&& row.incoming().compareTo(new BigDecimal("430")) == 0));
	}

	@Test
	void deliveryDifferencesShouldIncludeMissingAndExcessQuantitiesOnlyThroughTheCutoff() throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		importDeliveries(source, deliveryRow("2026-09-10", "Einlieferung", 200, 20000)
				+ deliveryRow("2026-09-15", "Auslieferung", 200, 20000));
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 199, 19900)
				+ deliveryRow("2026-09-20", "Einlieferung", 1, 100));
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		assertNull(transfers.findExistingDeliveries(source, target, LocalDate.of(2026, Month.SEPTEMBER, 16)));
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 5, 500)
				.replace("DE0000000001", "DE0000000002").replace("Aktie", "Zusatzaktie"));
		var differences = transfers.compareDeliveries(source, target, LocalDate.of(2026, Month.SEPTEMBER, 16)).differences();
		assertEquals(2, differences.size(), differences.toString());
		assertTrue(differences.stream().anyMatch(row -> row.expected().compareTo(new BigDecimal("200")) == 0
				&& row.outgoingDifference().signum() == 0 && row.incomingDifference().compareTo(BigDecimal.ONE.negate()) == 0));
		assertTrue(differences.stream().anyMatch(row -> row.expected().signum() == 0
				&& row.incomingDifference().compareTo(new BigDecimal("5")) == 0));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void portfolioTransferShouldIncludeBankOnlyPositionsAndQuantities(boolean sameSecurity) throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", BigDecimal.ZERO);
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", BigDecimal.ZERO);
		importDeliveries(source, deliveryRow("2026-09-10", "Einlieferung", 200, 20000));
		Entry statement = new Entry();
		statement.timestamp = Date.from(TRADE_DATE.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
		statement.total = new BigDecimalValue(new BigDecimal("20100"), "EUR");
		for (String isin : sameSecurity ? List.of("DE0000000001") : List.of("DE0000000001", "DE0000000002")) {
			Gattung security = new Gattung();
			security.isin = isin;
			security.name = isin;
			security.curr = "EUR";
			security.saldo_type = Entry.SALDO_TYPE_STCK;
			long quantity = isin.endsWith("1") ? 200 : 1;
			security.saldo = new BigDecimalValue(BigDecimal.valueOf(sameSecurity ? quantity + 1 : quantity), "");
			security.einstandspreis = new BigDecimalValue(new BigDecimal("100"), "EUR");
			statement.addEntry(security);
		}
		new StockPortfolioFinTsService().persistPortfolioStatement(source.portfolioId(),
				db.getById(BankAccount.class, db.getById(StockPortfolio.class, source.portfolioId()).getAccountId()), statement);
		importDeliveries(source, deliveryRow("2026-09-15", "Auslieferung", 200, 20000));
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 200, 20000));
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		assertNull(transfers.findExistingDeliveries(source, target, null));
		importDeliveries(source, deliveryRow("2026-09-15", "Auslieferung", 1, 100)
				.replace("DE0000000001", sameSecurity ? "DE0000000001" : "DE0000000002"));
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 1, 100)
				.replace("DE0000000001", sameSecurity ? "DE0000000001" : "DE0000000002"));
		var plan = transfers.findExistingDeliveries(source, target, null);
		assertNotNull(plan);
		transfers.transferExistingDeliveries(source, target, plan, null, BigDecimal.ONE);
		assertTrue(new StockPortfolioService().getPositions(source.portfolioId()).isEmpty());
	}

	@Test
	void replacementShouldCarryBasisAndHistoryAcrossMultipleSecuritiesWithoutCashFlow() {
		PortfolioSummary portfolio = createPortfolio(100, 200, "Depot A", "Verrechnung A", new BigDecimal("1000.00"));
		StockSecurity original = createSecurity("Original AG");
		StockSecurity successor = createSecurity("Nachfolger AG");
		StockSecurity current = createSecurity("Aktuell AG");
		StockPortfolioService service = new StockPortfolioService();
		service.recordTrade(portfolio, position(portfolio, original), new TradeRequest(StockTransactionType.BUY,
				TRADE_DATE, SETTLEMENT_DATE, new BigDecimal("10"), new BigDecimal("20"),
				Currency.EUR, BigDecimal.ONE, null, null, null));
		service.recordIncome(portfolio, service.getPositions(portfolio.portfolioId()).get(0),
				new IncomeRequest(StockTransactionType.DIVIDEND, SETTLEMENT_DATE, new BigDecimal("5"), null, Currency.EUR, BigDecimal.ONE));
		BigDecimal balance = db.getById(BankAccount.class, portfolio.settlementAccountId()).getBalance();
		int bookings = db.getAll(Booking.class).size();

		SecurityExchangeRequest partialExchange = new SecurityExchangeRequest(original.getId(), successor.getId(),
				new BigDecimal("5"), SETTLEMENT_DATE.plusDays(1), null);
		assertThrows(GBankingException.class, () -> service.recordSecurityExchange(portfolio, partialExchange));
		service.recordSecurityExchange(portfolio, new SecurityExchangeRequest(original.getId(), successor.getId(),
				new BigDecimal("10"), SETTLEMENT_DATE.plusDays(1), "Umtauschbeleg"));
		service.recordSecurityExchange(portfolio, new SecurityExchangeRequest(successor.getId(), current.getId(),
				new BigDecimal("10"), SETTLEMENT_DATE.plusDays(2), null));
		service.savePrice(current.getId(), null, SETTLEMENT_DATE.plusDays(3), new BigDecimal("30"), Currency.EUR);

		PositionSummary holding = service.getPositions(portfolio.portfolioId()).get(0);
		assertEquals(current.getId(), holding.securityId());
		assertEquals(0, new BigDecimal("200").compareTo(holding.acquisitionValue()));
		assertEquals(0, new BigDecimal("50").compareTo(holding.performancePercent()));
		assertEquals(balance, db.getById(BankAccount.class, portfolio.settlementAccountId()).getBalance());
		assertEquals(bookings, db.getAll(Booking.class).size());
		assertEquals(List.of("Aktuell AG", "Nachfolger AG", "Original AG"),
				service.getPositionHistory(portfolio.portfolioId(), current.getId(), true).securityNames());
		assertEquals(6, service.getPositionHistory(portfolio.portfolioId(), current.getId(), true).transactions().size());
		assertEquals(1, service.getPositionHistory(portfolio.portfolioId(), current.getId(), false).transactions().size());
		assertTrue(service.getPositionHistory(portfolio.portfolioId(), current.getId(), true).transactions().stream()
				.anyMatch(transaction -> transaction.date().equals(TRADE_DATE) && transaction.securityId() == original.getId()));
		int transactionCount = db.getAll(StockTransaction.class).size();
		SecurityExchangeRequest cyclicExchange = new SecurityExchangeRequest(current.getId(), original.getId(),
				new BigDecimal("10"), SETTLEMENT_DATE.plusDays(4), null);
		assertThrows(GBankingException.class, () -> service.recordSecurityExchange(portfolio, cyclicExchange));
		assertEquals(transactionCount, db.getAll(StockTransaction.class).size());
		var firstExchange = service.getTransactions(portfolio.portfolioId()).stream()
				.filter(transaction -> transaction.transactionType() == StockTransactionType.CORPORATE_ACTION
						&& transaction.securityId() == original.getId()).findFirst().orElseThrow();
		assertTrue(firstExchange.quantity().signum() < 0);
		int firstExchangeId = firstExchange.transactionId();
		assertThrows(GBankingException.class, () -> service.deleteTransaction(firstExchangeId));
		assertThrows(GBankingException.class, () -> service.unlinkSecurityExchange(firstExchangeId));
		service.deleteTransaction(service.getTransactions(portfolio.portfolioId()).stream()
				.filter(transaction -> transaction.securityId() == current.getId()
						&& transaction.transactionType() == StockTransactionType.CORPORATE_ACTION).findFirst().orElseThrow().transactionId());
		assertEquals(successor.getId(), service.getPositions(portfolio.portfolioId()).get(0).securityId());
		assertEquals(0, new BigDecimal("200").compareTo(service.getPositions(portfolio.portfolioId()).get(0).acquisitionValue()));
	}

	@Test
	void replacementShouldMergeBasisWithExistingSuccessorHoldingsAndLaterPurchases() {
		PortfolioSummary portfolio = createPortfolio(100, 200, "Depot A", "Verrechnung A", new BigDecimal("1000.00"));
		StockSecurity original = createSecurity("Alt AG");
		StockSecurity successor = createSecurity("Neu AG");
		StockPortfolioService service = new StockPortfolioService();
		for (StockSecurity security : List.of(original, successor)) {
			service.recordTrade(portfolio, position(portfolio, security), new TradeRequest(StockTransactionType.BUY,
					TRADE_DATE, SETTLEMENT_DATE, new BigDecimal("10"),
					new BigDecimal(security == original ? "10" : "30"),
					Currency.EUR, BigDecimal.ONE, null, null, null));
		}
		service.recordSecurityExchange(portfolio, new SecurityExchangeRequest(original.getId(), successor.getId(),
				new BigDecimal("10"), SETTLEMENT_DATE, null));
		service.recordTrade(portfolio, service.getPositions(portfolio.portfolioId()).get(0),
				new TradeRequest(StockTransactionType.BUY, SETTLEMENT_DATE.plusDays(2), SETTLEMENT_DATE.plusDays(2),
						new BigDecimal("10"), new BigDecimal("50"), Currency.EUR, BigDecimal.ONE, null, null, null));

		PositionSummary holding = service.getPositions(portfolio.portfolioId()).get(0);
		assertEquals(0, new BigDecimal("30").compareTo(holding.quantity()));
		assertEquals(0, new BigDecimal("30").compareTo(holding.acquisitionPrice()));
	}

	@Test
	void tradeShouldCreatePositionCashComponentsAndSettlementBooking() {
		PortfolioSummary portfolio = createPortfolio(100, 200, "Depot A", "Verrechnung A", new BigDecimal("1000.00"));
		StockSecurity security = createSecurity("Test AG");
		StockPortfolioService service = new StockPortfolioService();

		service.recordTrade(portfolio, position(portfolio, security), new TradeRequest(StockTransactionType.BUY, TRADE_DATE, SETTLEMENT_DATE,
				new BigDecimal("10"), new BigDecimal("12.50"), Currency.EUR, BigDecimal.ONE,
				new BigDecimal("1.00"), new BigDecimal("2.00"), new BigDecimal("0.50")));

		assertEquals(new BigDecimal("1E+1"), service.getPositions(portfolio.portfolioId()).get(0).quantity());
		assertEquals(new BigDecimal("12.5"), service.getPositions(portfolio.portfolioId()).get(0).acquisitionPrice());
		assertEquals(new BigDecimal("125"), service.getPositions(portfolio.portfolioId()).get(0).acquisitionValue());
		List<Booking> bookings = service.getAccountTransactions(portfolio.settlementAccountId());
		assertEquals(1, bookings.size());
		assertEquals(new BigDecimal("-128.50"), bookings.get(0).getAmount());
		assertEquals(new BigDecimal("871.50"), db.getById(BankAccount.class, portfolio.settlementAccountId()).getBalance());
		assertEquals(4, db.getAllByParent(de.zft2.gbanking.db.dao.stock.StockTransactionCashLeg.class,
				service.getTransactions(portfolio.portfolioId()).get(0).transactionId()).size());
	}

	@Test
	void manualTransactionShouldBeEditableAndDeletable() {
		PortfolioSummary portfolio = createPortfolio(100, 200, "Depot A", "Verrechnung A", new BigDecimal("1000.00"));
		StockSecurity security = createSecurity("Editierbar AG");
		StockPortfolioService service = new StockPortfolioService();
		service.recordTrade(portfolio, position(portfolio, security), new TradeRequest(StockTransactionType.BUY,
				TRADE_DATE, SETTLEMENT_DATE, new BigDecimal("10"), new BigDecimal("10"), Currency.EUR,
				BigDecimal.ONE, BigDecimal.ONE, null, null));
		var original = service.getTransactions(portfolio.portfolioId()).get(0);

		assertEquals(StockTransactionEditField.ALL_FIELDS_MASK, original.editableFieldMask());
		service.saveTransaction(portfolio, new TransactionEditRequest(original.transactionId(), StockTransactionType.BUY,
				TRADE_DATE, SETTLEMENT_DATE, security.getId(), new BigDecimal("5"), new BigDecimal("20"),
				Currency.EUR, BigDecimal.ONE, new BigDecimal("2"), null, null, "korrigiert"));

		assertEquals(new BigDecimal("5"), service.getPositions(portfolio.portfolioId()).get(0).quantity());
		assertEquals(new BigDecimal("2E+1"), service.getPositions(portfolio.portfolioId()).get(0).acquisitionPrice());
		assertEquals(new BigDecimal("898.00"), db.getById(BankAccount.class, portfolio.settlementAccountId()).getBalance());
		assertEquals(StockTransactionStatus.CANCELLED,
				db.getById(StockTransaction.class, original.transactionId()).getTransactionStatus());

		int replacementId = service.getTransactions(portfolio.portfolioId()).get(0).transactionId();
		service.deleteTransaction(replacementId);

		assertEquals(List.of(), service.getPositions(portfolio.portfolioId()));
		assertEquals(List.of(), service.getTransactions(portfolio.portfolioId()));
		assertEquals(new BigDecimal("1000.00"), db.getById(BankAccount.class, portfolio.settlementAccountId()).getBalance());
	}

	@Test
	void acquisitionPriceShouldBeWeightedAndRemainUnchangedAfterPartialSale() {
		PortfolioSummary portfolio = createPortfolio(100, 200, "Depot A", "Verrechnung A", new BigDecimal("1000.00"));
		StockSecurity security = createSecurity("Einstand AG");
		StockPortfolioService service = new StockPortfolioService();

		service.recordTrade(portfolio, position(portfolio, security), new TradeRequest(StockTransactionType.BUY,
				TRADE_DATE, SETTLEMENT_DATE, new BigDecimal("10"), new BigDecimal("10"), Currency.EUR,
				BigDecimal.ONE, null, null, null));
		PositionSummary holding = service.getPositions(portfolio.portfolioId()).get(0);
		service.recordTrade(portfolio, holding, new TradeRequest(StockTransactionType.BUY,
				TRADE_DATE.plusDays(1), SETTLEMENT_DATE.plusDays(1), new BigDecimal("10"), new BigDecimal("20"),
				Currency.EUR, BigDecimal.ONE, null, null, null));
		holding = service.getPositions(portfolio.portfolioId()).get(0);
		service.recordTrade(portfolio, holding, new TradeRequest(StockTransactionType.SELL,
				TRADE_DATE.plusDays(2), SETTLEMENT_DATE.plusDays(2), new BigDecimal("5"), new BigDecimal("30"),
				Currency.EUR, BigDecimal.ONE, null, null, null));

		PositionSummary remainingPosition = service.getPositions(portfolio.portfolioId()).get(0);
		assertEquals(new BigDecimal("15"), remainingPosition.quantity());
		assertEquals(new BigDecimal("15"), remainingPosition.acquisitionPrice());
	}

	@Test
	void calculatedAcquisitionPriceShouldDeterminePerformance() {
		PortfolioSummary portfolio = createPortfolio(100, 200, "Depot A", "Verrechnung A", new BigDecimal("1000.00"));
		StockSecurity security = createSecurity("Manueller Einstand AG");
		StockPortfolioService service = new StockPortfolioService();
		service.recordTrade(portfolio, position(portfolio, security), new TradeRequest(StockTransactionType.BUY,
				TRADE_DATE, SETTLEMENT_DATE, new BigDecimal("10"), new BigDecimal("12.50"), Currency.EUR,
				BigDecimal.ONE, null, null, null));
		service.savePrice(security.getId(), null, SETTLEMENT_DATE, new BigDecimal("15"), Currency.EUR);
		PositionSummary position = service.getPositions(portfolio.portfolioId()).get(0);

		assertEquals(new BigDecimal("12.5"), position.acquisitionPrice());
		assertEquals(new BigDecimal("125"), position.acquisitionValue());
		assertEquals(new BigDecimal("1.5E+2"), position.totalValue());
		assertEquals(new BigDecimal("2E+1"), position.performancePercent());
	}

	@Test
	void incomeShouldCreditGrossAmountLessTaxesWithoutChangingPosition() {
		PortfolioSummary portfolio = createPortfolio(100, 200, "Depot A", "Verrechnung A", new BigDecimal("1000.00"));
		StockSecurity security = createSecurity("Dividenden AG");
		StockPortfolioService service = new StockPortfolioService();
		service.recordTrade(portfolio, position(portfolio, security), new TradeRequest(StockTransactionType.BUY,
				TRADE_DATE, SETTLEMENT_DATE, BigDecimal.ONE, BigDecimal.ONE, Currency.EUR, BigDecimal.ONE, null, null, null));
		PositionSummary holding = service.getPositions(portfolio.portfolioId()).get(0);

		service.recordIncome(portfolio, holding, new IncomeRequest(StockTransactionType.DIVIDEND,
				SETTLEMENT_DATE.plusDays(1), new BigDecimal("20.00"), new BigDecimal("5.00"), Currency.EUR, BigDecimal.ONE));

		assertEquals(BigDecimal.ONE, service.getPositions(portfolio.portfolioId()).get(0).quantity());
		assertEquals(new BigDecimal("15.00"), service.getAccountTransactions(portfolio.settlementAccountId()).get(0).getAmount());
		assertEquals(new BigDecimal("1014.00"), db.getById(BankAccount.class, portfolio.settlementAccountId()).getBalance());
	}

	@Test
	void priceCorrectionShouldReplaceEffectivePriceAndAllowDateCorrection() {
		StockSecurity security = createSecurity("Kurs AG");
		StockPortfolioService service = new StockPortfolioService();
		StockSecurityPrice original = service.savePrice(security.getId(), null, TRADE_DATE, new BigDecimal("10.25"), Currency.EUR);

		StockSecurityPrice correction = service.savePrice(security.getId(), original.getId(), SETTLEMENT_DATE,
				new BigDecimal("11.75"), Currency.EUR);

		var prices = service.getPrices(security.getId());
		assertEquals(1, prices.size());
		assertEquals(correction.getId(), prices.get(0).priceId());
		assertEquals(original.getId(), correction.getSupersedesPriceId());
		assertEquals(SETTLEMENT_DATE, correction.getQuotedAt().toLocalDate());
		int securityId = security.getId();
		int originalId = original.getId();
		GBankingException error = assertThrows(GBankingException.class,
				() -> service.deletePrice(securityId, originalId, false));
		assertTrue(error.getMessage().startsWith("Der Wertpapierkurs wurde nicht gefunden oder bereits ersetzt"));
	}

	@Test
	void positionShouldUseNewestPriceBeforeSourcePriority() {
		PortfolioSummary portfolio = createPortfolio(100, 200, "Depot A", "Verrechnung A", new BigDecimal("1000.00"));
		StockSecurity security = createSecurity("Kurswahl AG");
		StockPortfolioService service = new StockPortfolioService();
		service.recordTrade(portfolio, position(portfolio, security), new TradeRequest(StockTransactionType.BUY,
				TRADE_DATE, SETTLEMENT_DATE, BigDecimal.ONE, BigDecimal.ONE, Currency.EUR, BigDecimal.ONE, null, null, null));
		service.savePrice(security.getId(), null, TRADE_DATE, new BigDecimal("10.00"), Currency.EUR);

		createImportedPrice(security, SETTLEMENT_DATE, 1_100_000_000L);

		assertEquals(new BigDecimal("11"), service.getPositions(portfolio.portfolioId()).get(0).unitPrice());
	}

	@Test
	void manualCorrectionShouldSupersedePriceFromAnotherSource() {
		PortfolioSummary portfolio = createPortfolio(100, 200, "Depot A", "Verrechnung A", new BigDecimal("1000.00"));
		StockSecurity security = createSecurity("Kurskorrektur AG");
		StockPortfolioService service = new StockPortfolioService();
		service.recordTrade(portfolio, position(portfolio, security), new TradeRequest(StockTransactionType.BUY,
				TRADE_DATE, SETTLEMENT_DATE, BigDecimal.ONE, BigDecimal.ONE, Currency.EUR, BigDecimal.ONE, null, null, null));
		StockSecurityPrice importedPrice = createImportedPrice(security, SETTLEMENT_DATE, 1_100_000_000L);

		int securityId = security.getId();
		int priceId = importedPrice.getId();
		BigDecimal replacementPrice = new BigDecimal("12.00");
		assertThrows(GBankingException.class, () -> service.savePrice(securityId, priceId, TRADE_DATE,
				replacementPrice, Currency.EUR));
		StockSecurityPrice correction = service.savePrice(security.getId(), importedPrice.getId(), TRADE_DATE,
				new BigDecimal("12.00"), Currency.EUR, true);

		assertEquals(correction.getId(), service.getPrices(security.getId()).get(0).priceId());
		assertEquals(new BigDecimal("12"), service.getPositions(portfolio.portfolioId()).get(0).unitPrice());
	}

	@Test
	void fileAndFinTsPricesShouldRequireConfirmationBeforeCorrection() {
		StockPortfolioService service = new StockPortfolioService();
		for (StockDataSourceType sourceType : List.of(StockDataSourceType.FILE, StockDataSourceType.FINTS)) {
			StockSecurity security = createSecurity(sourceType.name());
			StockSecurityPrice importedPrice = createImportedPrice(security, TRADE_DATE, 1_100_000_000L, sourceType);

			int securityId = security.getId();
			int priceId = importedPrice.getId();
			BigDecimal replacementPrice = new BigDecimal("12.00");
			assertThrows(GBankingException.class, () -> service.savePrice(securityId, priceId,
					SETTLEMENT_DATE, replacementPrice, Currency.EUR));
			StockSecurityPrice correction = service.savePrice(security.getId(), importedPrice.getId(),
					SETTLEMENT_DATE, new BigDecimal("12.00"), Currency.EUR, true);

			assertEquals(importedPrice.getId(), correction.getSupersedesPriceId());
			assertFalse(correction.isDeleted());
		}
	}

	@Test
	void importedPriceDeletionShouldRequireConfirmationAndCreateTombstone() {
		StockSecurity security = createSecurity("Import-Kurs");
		StockPortfolioService service = new StockPortfolioService();
		StockSecurityPrice importedPrice = createImportedPrice(security, TRADE_DATE, 1_100_000_000L,
				StockDataSourceType.FILE);

		int securityId = security.getId();
		int priceId = importedPrice.getId();
		assertThrows(GBankingException.class, () -> service.deletePrice(securityId, priceId, false));
		service.deletePrice(security.getId(), importedPrice.getId(), true);

		assertEquals(List.of(), service.getPrices(security.getId()));
		StockSecurityPrice tombstone = db.getAll(StockSecurityPrice.class).stream()
				.filter(StockSecurityPrice::isDeleted).findFirst().orElseThrow();
		assertEquals(importedPrice.getId(), tombstone.getSupersedesPriceId());
		assertTrue(tombstone.isDeleted());
	}

	@Test
	void manualPriceShouldBeDeletableWithoutExternalConfirmation() {
		StockSecurity security = createSecurity("Manueller Kurs");
		StockPortfolioService service = new StockPortfolioService();
		StockSecurityPrice price = service.savePrice(security.getId(), null, TRADE_DATE,
				new BigDecimal("10.00"), Currency.EUR);

		service.deletePrice(security.getId(), price.getId(), false);

		assertEquals(List.of(), service.getPrices(security.getId()));
	}

	@Test
	void securitiesWithoutCurrentHoldingsShouldBeFilterable() {
		PortfolioSummary portfolio = createPortfolio(100, 200, "Depot A", "Verrechnung A", new BigDecimal("1000.00"));
		StockSecurity heldSecurity = createSecurity("Aktueller Bestand");
		StockSecurity historicalSecurity = createSecurity("Historischer Bestand");
		StockPortfolioService portfolioService = new StockPortfolioService();
		portfolioService.recordTrade(portfolio, position(portfolio, heldSecurity), new TradeRequest(StockTransactionType.BUY,
				TRADE_DATE, SETTLEMENT_DATE, new BigDecimal("10"), BigDecimal.ONE, Currency.EUR,
				BigDecimal.ONE, null, null, null));
		portfolioService.recordTrade(portfolio, position(portfolio, historicalSecurity), new TradeRequest(StockTransactionType.BUY,
				TRADE_DATE, SETTLEMENT_DATE, new BigDecimal("5"), BigDecimal.ONE, Currency.EUR,
				BigDecimal.ONE, null, null, null));
		PositionSummary historicalPosition = portfolioService.getPositions(portfolio.portfolioId()).stream()
				.filter(position -> position.securityId() == historicalSecurity.getId()).findFirst().orElseThrow();
		portfolioService.recordTrade(portfolio, historicalPosition, new TradeRequest(StockTransactionType.SELL,
				TRADE_DATE.plusDays(1), SETTLEMENT_DATE.plusDays(1), new BigDecimal("5"), BigDecimal.ONE, Currency.EUR,
				BigDecimal.ONE, null, null, null));

		var filtered = new StockSecurityAdministrationService().getSecurities(true);

		assertEquals(List.of(heldSecurity.getId()), filtered.stream().map(security -> security.securityId()).toList());
		PortfolioSummary second = createPortfolio(101, 201, "Depot B", "Verrechnung B", new BigDecimal("1000"));
		portfolioService.recordTrade(second, position(second, heldSecurity), new TradeRequest(StockTransactionType.BUY,
				TRADE_DATE, SETTLEMENT_DATE, new BigDecimal("0.123456789"), BigDecimal.ONE, Currency.EUR,
				BigDecimal.ONE, null, null, null));
		var holdings = portfolioService.getSecurityHoldings(heldSecurity.getId());
		assertEquals(List.of(portfolio.portfolioId(), second.portfolioId()),
				holdings.stream().map(row -> row.portfolio().portfolioId()).toList());
		assertEquals(0, new BigDecimal("10").compareTo(holdings.get(0).quantity()));
		assertEquals(0, new BigDecimal("0.123456789").compareTo(holdings.get(1).quantity()));
		assertTrue(portfolioService.getSecurityHoldings(historicalSecurity.getId()).isEmpty());
	}

	@Test
	void portfolioTransferShouldMoveAllPositionsAndSettlementBalance() {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", new BigDecimal("1000.00"));
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", new BigDecimal("100.00"));
		BankAccount targetSettlementAccount = db.getById(BankAccount.class, target.settlementAccountId());
		targetSettlementAccount.setBaseCurrency(Currency.USD);
		db.insertOrUpdate(targetSettlementAccount);
		StockSecurity security = createSecurity("Transfer AG");
		StockPortfolioService service = new StockPortfolioService();
		service.recordTrade(source, position(source, security), new TradeRequest(StockTransactionType.BUY, TRADE_DATE, SETTLEMENT_DATE,
				new BigDecimal("10"), BigDecimal.ONE, Currency.EUR, BigDecimal.ONE, null, null, null));

		List<PortfolioSummary> reloaded = service.getPortfolios();
		source = findPortfolio(reloaded, source.portfolioId());
		target = findPortfolio(reloaded, target.portfolioId());
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		assertNull(transfers.findExistingDeliveries(source, target, null));
		transfers.transferManually(source, target, SETTLEMENT_DATE.plusDays(1), new BigDecimal("1.10"));

		assertEquals(List.of(), service.getPositions(source.portfolioId()));
		assertEquals(new BigDecimal("1E+1"), service.getPositions(target.portfolioId()).get(0).quantity());
		assertEquals(BigDecimal.ONE, service.getPositions(target.portfolioId()).get(0).acquisitionPrice());
		assertEquals(new BigDecimal("0.00"), db.getById(BankAccount.class, source.settlementAccountId()).getBalance());
		assertEquals(new BigDecimal("1189.00"), db.getById(BankAccount.class, target.settlementAccountId()).getBalance());
		assertEquals(1, db.getAll(StockExchangeRate.class).size());

		List<Booking> sourceBookings = service.getAccountTransactions(source.settlementAccountId());
		List<Booking> targetBookings = service.getAccountTransactions(target.settlementAccountId());
		Booking outgoing = sourceBookings.stream().filter(booking -> booking.getCrossBookingId() != null).findFirst().orElseThrow();
		Booking incoming = targetBookings.get(0);
		assertEquals(incoming.getId(), outgoing.getCrossBookingId());
		assertEquals(outgoing.getId(), incoming.getCrossBookingId());
		assertNotNull(incoming.getCrossAccountId());
		assertEquals(Currency.EUR, incoming.getForeignCurrencyDetails().getForeignCurrency());
		assertEquals(new BigDecimal("1.1"), incoming.getForeignCurrencyDetails().getExchangeRateToBaseCurrency());
		assertEquals(new BigDecimal("-1000.00"), outgoing.getBalance());
		assertEquals(new BigDecimal("1089.00"), incoming.getBalance());
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void settlementRunningBalancesShouldStartAtZeroAndUseBookingDatesWithoutPendingAmounts(boolean knownBalance) {
		PortfolioSummary portfolio = createPortfolio(100, 200, "Depot", "Verrechnung",
				knownBalance ? new BigDecimal("999") : null);
		int accountId = portfolio.settlementAccountId();
		Booking first = new Booking(accountId, TRADE_DATE, TRADE_DATE.plusDays(5), "Deposit",
				new BigDecimal("100"), BookingType.DEPOSIT, 0);
		Booking pending = new Booking(accountId, TRADE_DATE.plusDays(1), TRADE_DATE.plusDays(1), "Pending",
				new BigDecimal("-80"), BookingType.REMOVAL, 0);
		Booking last = new Booking(accountId, TRADE_DATE.plusDays(2), TRADE_DATE.plusDays(2), "Removal",
				new BigDecimal("-20"), BookingType.REMOVAL, 0);
		first.setSource(Source.MANUELL_NEW);
		pending.setSource(Source.ONLINE_PRENO);
		last.setSource(Source.MANUELL_NEW);
		for (Booking booking : List.of(last, pending, first)) {
			booking.setCrossAccountId(null);
			db.insertOrUpdate(booking);
		}
		List<Booking> bookings = new StockPortfolioService().getAccountTransactions(accountId);
		assertEquals(first.getId(), bookings.get(0).getId());
		Map<Integer, BigDecimal> balances = Map.of(first.getId(), new BigDecimal("100.00"), last.getId(), new BigDecimal("80.00"));
		for (Booking booking : bookings) {
			assertEquals(balances.get(booking.getId()), booking.getBalance());
		}
		assertEquals(knownBalance ? new BigDecimal("999.00") : null,
				db.getById(BankAccount.class, accountId).getBalance());
	}

	@ParameterizedTest
	@ValueSource(strings = { "existing", "unknown target balance", "remaining balance", "different target", "opposite direction" })
	void recognizedPortfolioTransferShouldPreserveExistingSettlementTransfers(String scenario) throws Exception {
		PortfolioSummary source = createPortfolio(100, 200, "Depot A", "Verrechnung A", new BigDecimal("100"));
		PortfolioSummary target = createPortfolio(101, 201, "Depot B", "Verrechnung B", new BigDecimal("100"));
		importDeliveries(source, deliveryRow("2026-09-10", "Einlieferung", 1, 1));
		importDeliveries(target, deliveryRow("2026-09-16", "Einlieferung", 1, 1));
		boolean remaining = scenario.equals("remaining balance");
		boolean unrelated = scenario.equals("different target") || scenario.equals("opposite direction");
		Booking existing = new Booking(source.settlementAccountId(), TRADE_DATE, TRADE_DATE, "Existing transfer",
				new BigDecimal(scenario.equals("opposite direction") ? "100" : "-100"),
				scenario.equals("opposite direction") ? BookingType.REBOOKING_IN : BookingType.REBOOKING_OUT,
				scenario.equals("different target") ? source.settlementAccountId() : target.settlementAccountId());
		existing.setSource(Source.MANUELL_NEW);
		db.insertOrUpdate(existing);
		BankAccount sourceAccount = db.getById(BankAccount.class, source.settlementAccountId());
		sourceAccount.setBalance(remaining ? BigDecimal.TEN : BigDecimal.ZERO);
		db.insertOrUpdate(sourceAccount);
		BankAccount targetAccount = db.getById(BankAccount.class, target.settlementAccountId());
		boolean unknown = scenario.equals("unknown target balance") || unrelated;
		targetAccount.setBalance(unknown ? null : new BigDecimal("100"));
		db.insertOrUpdate(targetAccount);
		StockPortfolioTransferService transfers = new StockPortfolioTransferService();
		var plan = transfers.findExistingDeliveries(source, target, null);
		if (unrelated) {
			assertThrows(GBankingException.class, () -> transfers.transferExistingDeliveries(source, target, plan, null, BigDecimal.ONE));
		} else {
			transfers.transferExistingDeliveries(source, target, plan, null, BigDecimal.ONE);
			assertTrue(new StockPortfolioService().getPositions(source.portfolioId()).isEmpty());
		}
		assertEquals(remaining ? 3 : 1, db.getAll(Booking.class).size());
		assertEquals(0, db.getById(BankAccount.class, source.settlementAccountId()).getBalance().signum());
		assertEquals(unknown ? null : new BigDecimal(remaining ? "110.00" : "100.00"),
				db.getById(BankAccount.class, target.settlementAccountId()).getBalance());
		assertEquals(0, existing.getAmount().compareTo(db.getById(Booking.class, existing.getId()).getAmount()));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void settlementAccountChangeShouldKeepAssignmentHistory(boolean sameStart) {
		PortfolioSummary portfolio = createPortfolio(100, 200, "Depot A", "Verrechnung A", new BigDecimal("1000.00"));
		BankAccount targetAccount = createAccount("Verrechnung B", AccountType.CURRENT_ACCOUNT, new BigDecimal("500.00"));
		LocalDate validFrom = sameStart ? portfolio.openedAt() : LocalDate.of(2026, Month.SEPTEMBER, 15);
		StockPortfolioService service = new StockPortfolioService();

		service.changeSettlementAccount(portfolio, targetAccount, validFrom);

		StockPortfolio storedPortfolio = db.getById(StockPortfolio.class, portfolio.portfolioId());
		StockPortfolioSettlementAccount previous = db.getById(StockPortfolioSettlementAccount.class, 200);
		StockPortfolioSettlementAccount current = db.getById(StockPortfolioSettlementAccount.class,
				storedPortfolio.getCurrentSettlementRelationId());
		assertEquals(sameStart ? null : validFrom, previous.getValidTo());
		assertEquals(sameStart ? 1 : 2,
				db.getAllByParent(StockPortfolioSettlementAccount.class, portfolio.portfolioId()).size());
		assertEquals(targetAccount.getId(), current.getAccountId());
		assertEquals(validFrom, current.getValidFrom());
		assertEquals(targetAccount.getId(), service.getPortfolios().get(0).settlementAccountId());
	}

	private PortfolioSummary createPortfolio(int portfolioId, int relationId, String portfolioName,
			String settlementName, BigDecimal settlementBalance) {
		BankAccount portfolioAccount = createAccount(portfolioName, AccountType.DEPOT, null);
		BankAccount settlementAccount = createAccount(settlementName, AccountType.CURRENT_ACCOUNT, settlementBalance);
		StockPortfolio portfolio = new StockPortfolio();
		portfolio.setId(portfolioId);
		portfolio.setAccountId(portfolioAccount.getId());
		portfolio.setCurrentSettlementRelationId(relationId);
		portfolio.setOpenedAt(LocalDate.of(2026, Month.JANUARY, 1));
		StockPortfolioSettlementAccount relation = new StockPortfolioSettlementAccount();
		relation.setId(relationId);
		relation.setPortfolioId(portfolioId);
		relation.setAccountId(settlementAccount.getId());
		relation.setValidFrom(portfolio.getOpenedAt());
		db.executeInTransaction(() -> {
			db.insertOrUpdate(portfolio);
			db.insertOrUpdate(relation);
			return null;
		});
		return new PortfolioSummary(portfolioId, portfolioName, portfolioAccount.getBankName(), portfolioAccount.getIban(),
				portfolio.getOpenedAt(), settlementAccount.getId(), settlementName, settlementAccount.getBankName(),
				settlementAccount.getIban(), settlementAccount.getBaseCurrency(), settlementAccount.getBalance());
	}

	private BankAccount createAccount(String name, AccountType type, BigDecimal balance) {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setAccountName(name);
		account.setAccountType(type);
		account.setBalance(balance);
		return db.insertOrUpdate(account);
	}

	private StockSecurity createSecurity(String name) {
		StockSecurity security = new StockSecurity();
		security.setSecurityType(StockSecurityType.STOCK);
		security.setName(name);
		security.setDefaultQuantityType(StockQuantityType.UNITS);
		security.setDefaultQuoteCurrency(Currency.EUR);
		security.setDefaultQuotationType(StockQuotationType.ABSOLUTE);
		return db.insertOrUpdate(security);
	}

	private StockSecurityPrice createImportedPrice(StockSecurity security, LocalDate date, long priceE8) {
		return createImportedPrice(security, date, priceE8, StockDataSourceType.FINTS);
	}

	private StockSecurityPrice createImportedPrice(StockSecurity security, LocalDate date, long priceE8,
			StockDataSourceType sourceType) {
		StockDataSource source = new StockDataSource();
		source.setSourceCode(sourceType.name() + "_TEST_" + security.getId());
		source.setSourceName(sourceType.name() + "-Test");
		source.setSourceType(sourceType);
		source = db.insertOrUpdate(source);
		StockSecurityPriceSource priceSource = new StockSecurityPriceSource();
		priceSource.setSecurityId(security.getId());
		priceSource.setSourceId(source.getId());
		priceSource.setPriority(100);
		priceSource = db.insertOrUpdate(priceSource);
		StockSecurityPrice price = new StockSecurityPrice();
		price.setPriceSourceId(priceSource.getId());
		price.setQuotedAt(date.atStartOfDay());
		price.setPriceE8(priceE8);
		price.setQuoteCurrency(Currency.EUR);
		price.setQuotationType(StockQuotationType.ABSOLUTE);
		price.setPriceType(StockPriceType.CLOSE);
		return db.insertOrUpdate(price);
	}

	private PositionSummary position(PortfolioSummary portfolio, StockSecurity security) {
		return new PositionSummary(portfolio.portfolioId(), security.getId(), security.getName(),
				null, null, security.getDefaultQuantityType(), BigDecimal.ZERO, null, null, null, null,
				security.getDefaultQuoteCurrency(), null);
	}

	private PortfolioSummary findPortfolio(List<PortfolioSummary> portfolios, int portfolioId) {
		return portfolios.stream().filter(portfolio -> portfolio.portfolioId() == portfolioId).findFirst().orElseThrow();
	}
}
