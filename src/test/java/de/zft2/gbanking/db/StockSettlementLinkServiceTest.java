package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.StockCashLegRole;
import de.zft2.gbanking.db.dao.enu.StockDataSourceType;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.db.dao.enu.StockTransactionStatus;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockPortfolio;
import de.zft2.gbanking.db.dao.stock.StockPortfolioSettlementAccount;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.db.dao.stock.StockTransaction;
import de.zft2.gbanking.db.dao.stock.StockTransactionCashLeg;
import de.zft2.gbanking.file.imp.ImportedBookingPersistence;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.account.AccountTransactionService;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.TradeRequest;
import de.zft2.gbanking.service.stock.StockSettlementLinkService;
import de.zft2.gbanking.testdata.TestDataFactory;

class StockSettlementLinkServiceTest extends DBControllerIntegrationBaseTest {

	private static final LocalDate TRANSACTION_DATE = LocalDate.of(2026, Month.OCTOBER, 19);
	private static final LocalDate BOOKING_DATE = LocalDate.of(2026, Month.OCTOBER, 20);
	private static final BigDecimal AMOUNT = new BigDecimal("-72.00");

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void assignmentToolShouldLinkExistingTransactionsOnlyAfterConfirmation(boolean selectDepot) {
		PortfolioSummary portfolio = createPortfolio();
		StockTransaction transaction = createUnlinkedTrade(portfolio, Currency.EUR);
		BankAccount account = db.getById(BankAccount.class, portfolio.settlementAccountId());
		Booking booking = createBankBooking(account, selectDepot ? TRANSACTION_DATE.plusDays(6) : BOOKING_DATE, AMOUNT, Source.ONLINE);
		BankAccount other = createAccount("Anderes Konto", AccountType.CURRENT_ACCOUNT, BigDecimal.ZERO);
		createBankBooking(other, BOOKING_DATE, AMOUNT, Source.ONLINE);
		createBankBooking(account, TRANSACTION_DATE.plusDays(7), AMOUNT, Source.ONLINE);
		AccountTransactionService tool = new AccountTransactionService();
		BankAccount selected = selectDepot ? new StockSettlementLinkService().getDepotAccount(portfolio.portfolioId()) : account;
		var summary = tool.detectRebookings(TRANSACTION_DATE, TRANSACTION_DATE,
				List.of(selected));
		assertEquals(1, summary.pairCount());
		assertEquals(2, summary.accountSummaries().size());
		assertNull(db.getById(Booking.class, booking.getId()).getCrossBookingId());
		assertTrue(db.getAll(StockTransactionCashLeg.class).stream().allMatch(leg -> leg.getBookingId() == null));

		assertEquals(1, tool.persistDetectedRebookingLinks(summary));
		assertEquals(0, tool.persistDetectedRebookingLinks(summary));
		Booking linked = db.getByIdFull(Booking.class, booking.getId());
		assertEquals(transaction.getId(), new StockSettlementLinkService().getLinkedTransaction(linked).getId());
		assertEquals(booking.getId(), db.getById(Booking.class, linked.getCrossBookingId()).getCrossBookingId());
		assertEquals(2, db.getAllByParent(Booking.class, account.getId()).size());
		List<StockTransactionCashLeg> legs = db.getAllByParent(StockTransactionCashLeg.class, transaction.getId());
		assertTrue(legs.stream().allMatch(leg -> Integer.valueOf(booking.getId()).equals(leg.getBookingId())));
		assertEquals(-7200, legs.stream().mapToLong(leg -> leg.getAmountMinor()).sum());
		StockTransactionCashLeg leg = legs.get(0);
		leg.setAmountMinor(-1);
		assertThrows(GBankingException.class, () -> db.insertOrUpdate(leg));
		StockTransactionCashLeg linkedLeg = db.getById(StockTransactionCashLeg.class, leg.getId());
		linkedLeg.setBookingId(null);
		assertThrows(GBankingException.class, () -> db.insertOrUpdate(linkedLeg));
	}

	@ParameterizedTest
	@ValueSource(strings = { "two bookings", "two transactions", "wrong account", "wrong currency", "wrong selection", "prenotification" })
	void assignmentToolShouldSkipAmbiguousOrIneligibleMatches(String scenario) {
		PortfolioSummary portfolio = createPortfolio();
		createUnlinkedTrade(portfolio, scenario.equals("wrong currency") ? Currency.USD : Currency.EUR);
		BankAccount account = db.getById(BankAccount.class, portfolio.settlementAccountId());
		BankAccount other = createAccount("Anderes Konto", AccountType.CURRENT_ACCOUNT, BigDecimal.ZERO);
		createBankBooking(scenario.equals("wrong account") ? other : account, BOOKING_DATE, AMOUNT,
				scenario.equals("prenotification") ? Source.ONLINE_PRENO : Source.ONLINE);
		if (scenario.equals("two bookings")) {
			createBankBooking(account, BOOKING_DATE.plusDays(1), AMOUNT, Source.ONLINE);
		}
		if (scenario.equals("two transactions")) {
			createUnlinkedTrade(portfolio, Currency.EUR);
		}
		var summary = new AccountTransactionService().detectRebookings(TRANSACTION_DATE, TRANSACTION_DATE,
				List.of(scenario.equals("wrong selection") ? other : account));
		assertTrue(summary.isEmpty());
		assertTrue(db.getAll(StockTransactionCashLeg.class).stream().allMatch(leg -> leg.getBookingId() == null));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void assignmentToolShouldRecheckAmbiguityAndSettlementAccountBeforeSaving(boolean changeAccount) {
		PortfolioSummary portfolio = createPortfolio();
		createUnlinkedTrade(portfolio, Currency.EUR);
		BankAccount account = db.getById(BankAccount.class, portfolio.settlementAccountId());
		Booking booking = createBankBooking(account, BOOKING_DATE, AMOUNT, Source.ONLINE);
		AccountTransactionService tool = new AccountTransactionService();
		var summary = tool.detectRebookings(TRANSACTION_DATE, TRANSACTION_DATE, List.of(account));
		assertEquals(1, summary.pairCount());
		if (changeAccount) {
			new StockPortfolioService().changeSettlementAccount(portfolio,
					createAccount("Neues Konto", AccountType.CURRENT_ACCOUNT, BigDecimal.ZERO), BOOKING_DATE.plusDays(1));
		} else {
			createBankBooking(account, BOOKING_DATE.plusDays(1), AMOUNT, Source.ONLINE);
		}
		assertEquals(0, tool.persistDetectedRebookingLinks(summary));
		assertNull(db.getById(Booking.class, booking.getId()).getCrossBookingId());
	}

	private StockTransaction createUnlinkedTrade(PortfolioSummary portfolio, Currency currency) {
		StockDataSource source = new StockDataSource();
		source.setSourceCode("TEST_" + db.getAll(StockTransaction.class).size());
		source.setSourceName("Test");
		source.setSourceType(StockDataSourceType.MANUAL);
		source = db.insertOrUpdate(source);
		StockTransaction transaction = new StockTransaction();
		transaction.setPortfolioId(portfolio.portfolioId());
		transaction.setSourceId(source.getId());
		transaction.setTransactionType(StockTransactionType.BUY);
		transaction.setTransactionStatus(StockTransactionStatus.PENDING);
		transaction.setTradeAt(TRANSACTION_DATE.atStartOfDay());
		transaction.setCashValueAt(transaction.getTradeAt());
		transaction = db.insertOrUpdate(transaction);
		for (int number = 1; number <= 2; number++) {
			StockTransactionCashLeg leg = new StockTransactionCashLeg();
			leg.setTransactionId(transaction.getId());
			leg.setLegNumber(number);
			leg.setAccountId(portfolio.settlementAccountId());
			leg.setCurrency(currency);
			leg.setAmountMinor(number == 1 ? -7000 : -200);
			leg.setLegRole(number == 1 ? StockCashLegRole.TRADE_VALUE : StockCashLegRole.FEE);
			leg.setValueAt(transaction.getTradeAt());
			db.insertOrUpdate(leg);
		}
		transaction.setTransactionStatus(StockTransactionStatus.SETTLED);
		transaction.setSettledAt(transaction.getTradeAt());
		return db.insertOrUpdate(transaction);
	}

	@Test
	void existingSettlementBookingShouldBeReusedAndLinkedOnlyOnce() {
		PortfolioSummary portfolio = createPortfolio();
		BankAccount settlementAccount = db.getById(BankAccount.class, portfolio.settlementAccountId());
		Booking bankBooking = createBankBooking(settlementAccount, BOOKING_DATE, AMOUNT, Source.IMPORT);
		StockSettlementLinkService service = new StockSettlementLinkService();

		Booking coveringBooking = service.findCoveringSettlementBooking(settlementAccount, TRANSACTION_DATE, AMOUNT);

		assertNotNull(coveringBooking);
		assertEquals(bankBooking.getId(), coveringBooking.getId());
		BankAccount depotAccount = service.getDepotAccount(portfolio.portfolioId());
		Booking counterBooking = service.linkWithDepot(coveringBooking, settlementAccount, depotAccount);
		assertNotNull(counterBooking);

		Booking reloaded = db.getByIdFull(Booking.class, bankBooking.getId());
		assertEquals(BookingType.REBOOKING_OUT, reloaded.getBookingType());
		assertEquals(counterBooking.getId(), reloaded.getCrossBookingId());
		assertEquals(counterBooking.getAccountId(), reloaded.getCrossAccountId());
		assertEquals(1, db.getAllByParentFull(Booking.class, settlementAccount.getId()).size());
		assertEquals(new BigDecimal("72.00"), counterBooking.getAmount());
		assertEquals(bankBooking.getId(), counterBooking.getCrossBookingId());
		assertEquals(settlementAccount.getId(), counterBooking.getCrossAccountId());
		assertNull(service.linkWithDepot(coveringBooking, settlementAccount, depotAccount));
		assertEquals(1, db.getAllByParentFull(Booking.class, depotAccount.getId()).size());
	}

	@Test
	void laterDeliveredBookingShouldBeRecognizedAsCoveredBySecuritiesTransaction() {
		PortfolioSummary portfolio = createPortfolio();
		StockSecurity security = createSecurity();
		StockPortfolioService portfolioService = new StockPortfolioService();
		portfolioService.recordTrade(portfolio, position(portfolio, security), new TradeRequest(
				StockTransactionType.BUY, TRANSACTION_DATE, TRANSACTION_DATE,
				new BigDecimal("6"), new BigDecimal("11.50"), Currency.EUR, BigDecimal.ONE,
				new BigDecimal("2"), BigDecimal.ONE, null));
		BankAccount settlementAccount = db.getById(BankAccount.class, portfolio.settlementAccountId());
		StockSettlementLinkService service = new StockSettlementLinkService();

		Booking deliveredBooking = createDeliveredBooking(settlementAccount, BOOKING_DATE, AMOUNT, Source.IMPORT);
		Booking coveringBooking = service.resolveCoveringStockBooking(deliveredBooking);

		assertNotNull(coveringBooking);
		assertEquals(settlementAccount.getId(), coveringBooking.getAccountId());
		assertEquals(AMOUNT, coveringBooking.getAmount());
		var linkedTransaction = service.getLinkedTransaction(coveringBooking);
		assertNotNull(linkedTransaction);
		assertEquals(portfolio.portfolioId(), linkedTransaction.getPortfolioId());
		assertEquals(linkedTransaction.getId(), db.getAll(StockTransactionCashLeg.class).get(0).getTransactionId());
		BankAccount newSettlement = createAccount("Neues Verrechnungskonto", AccountType.CURRENT_ACCOUNT, BigDecimal.ZERO);
		portfolioService.changeSettlementAccount(portfolio, newSettlement, BOOKING_DATE.plusDays(1));
		List<Booking> referenced = portfolioService.getSettlementBookings(portfolio.portfolioId()).get(linkedTransaction.getId());
		assertEquals(1, referenced.size());
		assertEquals(coveringBooking.getId(), referenced.get(0).getId());
		assertEquals(settlementAccount.getAccountName(), referenced.get(0).getAccountName());
		assertTrue(portfolioService.getSettlementBookings(999).isEmpty());
		coveringBooking.setCrossAccountId(settlementAccount.getId());
		assertNull(service.getLinkedTransaction(coveringBooking));
		assertEquals(1, db.getAllByParentFull(Booking.class, settlementAccount.getId()).size());
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void settlementMatchingShouldRespectTheImportReviewDecision(boolean accept) {
		PortfolioSummary portfolio = createPortfolio();
		StockSecurity security = createSecurity();
		new StockPortfolioService().recordTrade(portfolio, position(portfolio, security), new TradeRequest(
				StockTransactionType.BUY, TRANSACTION_DATE, TRANSACTION_DATE,
				new BigDecimal("6"), new BigDecimal("12"), Currency.EUR, BigDecimal.ONE, null, null, null));
		BankAccount account = db.getById(BankAccount.class, portfolio.settlementAccountId());
		Booking incoming = createDeliveredBooking(account, TRANSACTION_DATE, AMOUNT, Source.IMPORT);
		incoming.setPurpose("Different bank reference");
		ImportedBookingPersistence persistence = new ImportedBookingPersistence(db);
		persistence.review(List.of(incoming), matches -> {
			assertEquals(1, matches.size());
			return accept ? Set.of(incoming) : Set.of();
		});

		assertEquals(!accept, persistence.persist(incoming).skipped());
		assertEquals(accept ? 2 : 1, db.getAllByParentFull(Booking.class, account.getId()).size());
	}

	@Test
	void prenotificationShouldNotBeUsedAsCoveringBooking() {
		PortfolioSummary portfolio = createPortfolio();
		BankAccount settlementAccount = db.getById(BankAccount.class, portfolio.settlementAccountId());
		createBankBooking(settlementAccount, BOOKING_DATE, AMOUNT, Source.ONLINE_PRENO);
		StockSettlementLinkService service = new StockSettlementLinkService();

		assertNull(service.findCoveringSettlementBooking(settlementAccount, TRANSACTION_DATE, AMOUNT));
	}

	@Test
	void releasedDepotLinkShouldKeepTheBankBookingAndDeleteTheDepotCounterpart() {
		PortfolioSummary portfolio = createPortfolio();
		BankAccount settlementAccount = db.getById(BankAccount.class, portfolio.settlementAccountId());
		Booking bankBooking = createBankBooking(settlementAccount, BOOKING_DATE, AMOUNT, Source.IMPORT);
		StockSettlementLinkService service = new StockSettlementLinkService();
		BankAccount depotAccount = service.getDepotAccount(portfolio.portfolioId());
		Booking counterBooking = service.linkWithDepot(bankBooking, settlementAccount, depotAccount);
		assertNotNull(counterBooking);

		service.releaseDepotLink(bankBooking);

		Booking reloaded = db.getByIdFull(Booking.class, bankBooking.getId());
		assertNotNull(reloaded);
		assertNull(reloaded.getCrossBookingId());
		assertNull(reloaded.getCrossAccountId());
		assertEquals(BookingType.REMOVAL, reloaded.getBookingType());
		assertTrue(db.getAllByParentFull(Booking.class, depotAccount.getId()).isEmpty());
	}

	private PortfolioSummary createPortfolio() {
		BankAccount depotAccount = createAccount("Depot A", AccountType.DEPOT, null);
		BankAccount settlementAccount = createAccount("Verrechnung A", AccountType.CURRENT_ACCOUNT, new BigDecimal("1000.00"));
		StockPortfolio portfolio = new StockPortfolio();
		portfolio.setId(100);
		portfolio.setAccountId(depotAccount.getId());
		portfolio.setCurrentSettlementRelationId(200);
		portfolio.setOpenedAt(LocalDate.of(2026, Month.JANUARY, 1));
		StockPortfolioSettlementAccount relation = new StockPortfolioSettlementAccount();
		relation.setId(200);
		relation.setPortfolioId(portfolio.getId());
		relation.setAccountId(settlementAccount.getId());
		relation.setValidFrom(portfolio.getOpenedAt());
		db.executeInTransaction(() -> {
			db.insertOrUpdate(portfolio);
			db.insertOrUpdate(relation);
			return null;
		});
		return new PortfolioSummary(portfolio.getId(), depotAccount.getAccountName(), depotAccount.getBankName(),
				depotAccount.getIban(), portfolio.getOpenedAt(), settlementAccount.getId(),
				settlementAccount.getAccountName(), settlementAccount.getBankName(), settlementAccount.getIban(),
				settlementAccount.getBaseCurrency(), settlementAccount.getBalance());
	}

	private BankAccount createAccount(String name, AccountType type, BigDecimal balance) {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setAccountName(name);
		account.setAccountType(type);
		account.setBalance(balance);
		return db.insertOrUpdate(account);
	}

	private Booking createBankBooking(BankAccount account, LocalDate date, BigDecimal amount, Source source) {
		return db.insertOrUpdate(createDeliveredBooking(account, date, amount, source));
	}

	private Booking createDeliveredBooking(BankAccount account, LocalDate date, BigDecimal amount, Source source) {
		Booking booking = new Booking();
		booking.setAccountId(account.getId());
		booking.setDateBooking(date);
		booking.setDateValue(date);
		booking.setPurpose("Kauf Wertpapiere");
		booking.setAmount(amount);
		booking.setBookingType(BookingType.fromAmount(amount));
		booking.setSource(source);
		return booking;
	}

	private StockSecurity createSecurity() {
		StockSecurity security = new StockSecurity();
		security.setSecurityType(StockSecurityType.STOCK);
		security.setName("Test AG");
		security.setDefaultQuantityType(StockQuantityType.UNITS);
		security.setDefaultQuoteCurrency(Currency.EUR);
		security.setDefaultQuotationType(StockQuotationType.ABSOLUTE);
		return db.insertOrUpdate(security);
	}

	private PositionSummary position(PortfolioSummary portfolio, StockSecurity security) {
		return new PositionSummary(portfolio.portfolioId(), security.getId(), security.getName(),
				null, null, security.getDefaultQuantityType(), BigDecimal.ZERO, null, null, null, null,
				security.getDefaultQuoteCurrency(), null);
	}

}
