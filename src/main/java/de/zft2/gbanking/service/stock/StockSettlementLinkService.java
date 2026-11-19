package de.zft2.gbanking.service.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.SourceGroup;
import de.zft2.gbanking.db.dao.enu.StockTransactionStatus;
import de.zft2.gbanking.db.dao.stock.StockPortfolio;
import de.zft2.gbanking.db.dao.stock.StockTransaction;
import de.zft2.gbanking.db.dao.stock.StockTransactionCashLeg;
import de.zft2.gbanking.rebooking.RebookingLinkWriter;
import de.zft2.gbanking.rebooking.RebookingRules;
import de.zft2.gbanking.service.AbstractDbService;

/**
 * Links settlement bookings with depot counter bookings, reusing existing bank bookings and
 * recognizing later imports of cash movements already covered by securities transactions.
 */
public class StockSettlementLinkService extends AbstractDbService {

	private static final Logger log = LogManager.getLogger(StockSettlementLinkService.class);

	private static final int SEARCH_WINDOW_DAYS = 6;

	private final RebookingLinkWriter linkWriter = new RebookingLinkWriter(dbController);

	/**
	 * Finds an unused settlement booking with the same amount and the nearest date within the search window.
	 */
	public Booking findCoveringSettlementBooking(BankAccount settlementAccount, LocalDate date, BigDecimal amount) {
		if (settlementAccount == null || date == null || amount == null || amount.signum() == 0) {
			return null;
		}

		LocalDate from = date.minusDays(SEARCH_WINDOW_DAYS);
		LocalDate to = date.plusDays(SEARCH_WINDOW_DAYS);
		Set<Integer> transactionBookingIds = getTransactionBookingIds(settlementAccount.getId(), from, to);
		Booking dateRangeCriteria = new Booking();
		dateRangeCriteria.setDateBooking(from);
		dateRangeCriteria.setDateValue(to);
		List<Booking> bookings = dbController.getAllByParentSpecific(dateRangeCriteria, settlementAccount.getId(),
				StatementsConfig.StatementType.SELECT_WITH_PARENT_AND_DATE_RANGE);

		return bookings.stream()
				.filter(booking -> isCoveringCandidate(booking, amount))
				.filter(booking -> !transactionBookingIds.contains(booking.getId()))
				.min(Comparator.comparingInt(booking -> dateDistance(booking.getDate(), date)))
				.orElse(null);
	}

	/**
	 * Finds an existing securities booking covering an incoming bank booking and restores its depot link.
	 */
	public Booking resolveCoveringStockBooking(Booking booking) {
		if (booking == null || booking.getAccountId() <= 0 || booking.getAmount() == null
				|| booking.getAmount().signum() == 0 || booking.isPrenotification()) {
			return null;
		}
		LocalDate date = booking.getDate();
		if (date == null) {
			return null;
		}

		LocalDate from = date.minusDays(SEARCH_WINDOW_DAYS);
		LocalDate to = date.plusDays(SEARCH_WINDOW_DAYS);
		for (StockTransactionCashLeg cashLeg : dbController.getStockCashLegsByAccountAndDateRange(booking.getAccountId(),
				from, to)) {
			Booking stockBooking = loadStockBooking(cashLeg);
			BankAccount depotAccount = getDepotAccountOfTransaction(cashLeg.getTransactionId());
			if (stockBooking == null || depotAccount == null
					|| !coversMovement(stockBooking, booking, from, to, depotAccount.getId())) {
				continue;
			}
			linkWithDepot(stockBooking, dbController.getById(BankAccount.class, stockBooking.getAccountId()),
					depotAccount);
			return stockBooking;
		}
		return null;
	}

	/**
	 * Creates a depot counter booking, or returns {@code null} when the writer rejects the link.
	 */
	public Booking linkWithDepot(Booking settlementBooking, BankAccount settlementAccount, BankAccount depotAccount) {
		Booking counterBooking = linkWriter.linkWithCounterBooking(settlementBooking, settlementAccount, depotAccount);
		if (counterBooking != null) {
			log.info("Linked booking {} of settlement account {} with depot account {}.", settlementBooking.getId(),
					settlementAccount.getId(), depotAccount.getId());
		}
		return counterBooking;
	}

	/**
	 * Deletes the depot counterpart and restores the settlement booking without deleting the bank's booking.
	 */
	public void releaseDepotLink(Booking settlementBooking) {
		if (!deleteDepotCounterpart(settlementBooking)) {
			return;
		}
		settlementBooking.setCrossBookingId(null);
		settlementBooking.setCrossAccountId(null);
		settlementBooking.setBookingType(BookingType.fromAmount(settlementBooking.getAmount()));
		dbController.insertOrUpdate(settlementBooking);
	}

	/**
	 * Deletes only a manually generated, reciprocally linked depot counterpart.
	 */
	public boolean deleteDepotCounterpart(Booking settlementBooking) {
		Integer crossBookingId = settlementBooking.getCrossBookingId();
		if (crossBookingId == null || crossBookingId <= 0) {
			return false;
		}

		Booking counterpart = dbController.getById(Booking.class, crossBookingId);
		if (counterpart == null || !isDepotAccount(counterpart.getAccountId())
				|| (counterpart.getSource() != Source.MANUELL_NEW && counterpart.getSource() != Source.MANUELL)
				|| !Integer.valueOf(settlementBooking.getId()).equals(counterpart.getCrossBookingId())) {
			return false;
		}
		dbController.delete(counterpart, StatementsConfig.StatementType.DELETE);
		return true;
	}

	public BankAccount getDepotAccount(int portfolioId) {
		StockPortfolio portfolio = dbController.getById(StockPortfolio.class, portfolioId);
		return portfolio != null ? dbController.getById(BankAccount.class, portfolio.getAccountId()) : null;
	}

	public StockTransaction getLinkedTransaction(Booking booking) {
		for (StockTransactionCashLeg leg : dbController.getAllByParent(StockTransactionCashLeg.class, booking.getId(),
				"SQL_SELECT_STOCK_CASH_LEGS_BY_BOOKING")) {
			StockTransaction transaction = dbController.getById(StockTransaction.class, leg.getTransactionId());
			BankAccount depot = transaction != null && transaction.getTransactionStatus() == StockTransactionStatus.SETTLED
					? getDepotAccount(transaction.getPortfolioId()) : null;
			if (leg.getAccountId() == booking.getAccountId() && depot != null
					&& Integer.valueOf(depot.getId()).equals(booking.getCrossAccountId())) {
				return transaction;
			}
		}
		return null;
	}

	private BankAccount getDepotAccountOfTransaction(int transactionId) {
		StockTransaction stockTransaction = dbController.getById(StockTransaction.class, transactionId);
		return stockTransaction != null ? getDepotAccount(stockTransaction.getPortfolioId()) : null;
	}

	private Booking loadStockBooking(StockTransactionCashLeg cashLeg) {
		Integer bookingId = cashLeg.getBookingId();
		return bookingId != null && bookingId > 0 ? dbController.getByIdFull(Booking.class, bookingId) : null;
	}

	private Set<Integer> getTransactionBookingIds(int accountId, LocalDate from, LocalDate to) {
		return dbController.getStockCashLegsByAccountAndDateRange(accountId, from, to).stream()
				.map(StockTransactionCashLeg::getBookingId)
				.filter(bookingId -> bookingId != null && bookingId > 0)
				.collect(Collectors.toSet());
	}

	private boolean isCoveringCandidate(Booking booking, BigDecimal amount) {
		if (booking == null || booking.getId() <= 0 || !sameAmount(booking.getAmount(), amount)) {
			return false;
		}
		if (booking.getParentBookingId() != null || RebookingRules.hasCancellationSignal(booking)
				|| booking.getForeignCurrencyDetails() != null || !isRelinkable(booking.getSource())) {
			return false;
		}
		Integer crossBookingId = booking.getCrossBookingId();
		Integer crossAccountId = booking.getCrossAccountId();
		return (crossBookingId == null || crossBookingId <= 0) && (crossAccountId == null || crossAccountId <= 0);
	}

	private boolean coversMovement(Booking stockBooking, Booking booking, LocalDate from, LocalDate to, int depotAccountId) {
		if (stockBooking.getAccountId() != booking.getAccountId() || !sameAmount(stockBooking.getAmount(),
				booking.getAmount())) {
			return false;
		}
		LocalDate bookingDate = stockBooking.getDate();
		if (bookingDate == null || bookingDate.isBefore(from) || bookingDate.isAfter(to)) {
			return false;
		}
		Integer crossAccountId = stockBooking.getCrossAccountId();
		return crossAccountId == null || crossAccountId <= 0 || crossAccountId == depotAccountId;
	}

	/**
	 * The database rejects the change of the rebooking fields of prenotifications and automatically
	 * adjusted bookings.
	 */
	private static boolean isRelinkable(Source source) {
		return source != null && !source.isPrenotification() && source.getGroup() != SourceGroup.GROUP_AUTO;
	}

	private boolean isDepotAccount(int accountId) {
		BankAccount account = dbController.getById(BankAccount.class, accountId);
		return account != null && account.getAccountType() == AccountType.DEPOT;
	}

	private static int dateDistance(LocalDate bookingDate, LocalDate date) {
		return bookingDate == null ? Integer.MAX_VALUE : (int) Math.abs(ChronoUnit.DAYS.between(bookingDate, date));
	}

	private static boolean sameAmount(BigDecimal left, BigDecimal right) {
		return left != null && right != null && left.compareTo(right) == 0;
	}
}
