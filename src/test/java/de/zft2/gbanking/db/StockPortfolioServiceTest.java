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
import java.time.Month;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

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
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockDataSourceType;
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
			importDeliveries(source, deliveryRow("2026-09-17", "Einlieferung", 1, 100));
		}
		int count = db.getAll(StockTransaction.class).size();
		assertThrows(GBankingException.class, () -> transfers.transferExistingDeliveries(source, target, plan,
				changeHoldings ? null : TRADE_DATE.minusDays(1), BigDecimal.ONE));
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
	void portfolioTransferShouldRejectIncompleteHoldingsAndRequireAnUnambiguousDate(boolean incomplete) throws Exception {
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
			assertThrows(GBankingException.class, () -> transfers.findExistingDeliveries(source, target, null));
			assertNotNull(transfers.findExistingDeliveries(source, target, LocalDate.of(2026, 9, 16)));
		}
	}

	private static String deliveryRow(String date, String type, int quantity, int value) {
		return date + "T12:00;" + type + ";" + value + ",00;EUR;;;;;;" + quantity + ";DE0000000001;;;Aktie;\n";
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
		assertEquals(new BigDecimal("0.00"), outgoing.getBalance());
		assertEquals(new BigDecimal("1189.00"), incoming.getBalance());
	}

	@Test
	void settlementAccountChangeShouldKeepAssignmentHistory() {
		PortfolioSummary portfolio = createPortfolio(100, 200, "Depot A", "Verrechnung A", new BigDecimal("1000.00"));
		BankAccount targetAccount = createAccount("Verrechnung B", AccountType.CURRENT_ACCOUNT, new BigDecimal("500.00"));
		LocalDate validFrom = LocalDate.of(2026, Month.SEPTEMBER, 15);
		StockPortfolioService service = new StockPortfolioService();

		service.changeSettlementAccount(portfolio, targetAccount, validFrom);

		StockPortfolio storedPortfolio = db.getById(StockPortfolio.class, portfolio.portfolioId());
		StockPortfolioSettlementAccount previous = db.getById(StockPortfolioSettlementAccount.class, 200);
		StockPortfolioSettlementAccount current = db.getById(StockPortfolioSettlementAccount.class,
				storedPortfolio.getCurrentSettlementRelationId());
		assertEquals(validFrom, previous.getValidTo());
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
