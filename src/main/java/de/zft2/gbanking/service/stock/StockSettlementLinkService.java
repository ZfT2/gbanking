package de.zft2.gbanking.service.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import de.zft2.gbanking.db.dao.stock.StockPortfolioSettlementAccount;
import de.zft2.gbanking.db.dao.stock.StockTransaction;
import de.zft2.gbanking.db.dao.stock.StockTransactionCashLeg;
import de.zft2.gbanking.rebooking.RebookingAssignmentSummary.StockSettlementAssignment;
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

	private final RebookingLinkWriter linkWriter = new RebookingLinkWriter();

	public List<StockSettlementAssignment> detectAssignments(LocalDate from, LocalDate to,
			Collection<BankAccount> anchorAccounts, Set<Integer> reservedBookingIds) {
		Set<Integer> anchors = anchorAccounts.stream().filter(account -> account != null)
				.map(account -> account.getId()).collect(Collectors.toSet());
		Map<Integer, StockTransaction> transactions = dbController.getAll(StockTransaction.class).stream()
				.collect(Collectors.toMap(transaction -> transaction.getId(), transaction -> transaction));
		List<StockSettlementAssignment> candidates = collectAssignmentCandidates(transactions, reservedBookingIds);
		Map<Integer, Long> bookingCounts = candidates.stream()
				.collect(Collectors.groupingBy(candidate -> candidate.bookingId(), Collectors.counting()));
		Map<Integer, Long> transactionCounts = candidates.stream()
				.collect(Collectors.groupingBy(candidate -> candidate.transactionId(), Collectors.counting()));
		// Check uniqueness in both directions, including competing transactions outside the selected period.
		return candidates.stream()
				.filter(candidate -> bookingCounts.get(candidate.bookingId()) == 1
						&& transactionCounts.get(candidate.transactionId()) == 1)
				.filter(candidate -> anchors.contains(candidate.depotAccountId()) || anchors.contains(candidate.settlementAccountId()))
				.filter(candidate -> {
					LocalDate date = cashDate(transactions.get(candidate.transactionId()));
					return !date.isBefore(from) && !date.isAfter(to);
				}).toList();
	}

	public int persistAssignments(List<StockSettlementAssignment> assignments) {
		if (assignments.isEmpty()) {
			return 0;
		}
		return dbController.executeInTransaction(() -> {
			Set<StockSettlementAssignment> eligible = Set.copyOf(detectAssignments(LocalDate.MIN, LocalDate.MAX,
					dbController.getAll(BankAccount.class), Set.of()));
			int linked = 0;
			for (StockSettlementAssignment assignment : Set.copyOf(assignments)) {
				if (!eligible.contains(assignment)) {
					log.info("Skipped changed or ambiguous settlement assignment for transaction {}.", assignment.transactionId());
					continue;
				}
				Booking booking = dbController.getByIdFull(Booking.class, assignment.bookingId());
				if (linkWithDepot(booking, dbController.getById(BankAccount.class, assignment.settlementAccountId()),
						dbController.getById(BankAccount.class, assignment.depotAccountId())) == null) {
					throw new IllegalStateException("Could not link settlement booking " + assignment.bookingId());
				}
				for (StockTransactionCashLeg leg : dbController.getAllByParent(StockTransactionCashLeg.class, assignment.transactionId())) {
					leg.setBookingId(booking.getId());
					dbController.insertOrUpdate(leg);
				}
				linked++;
			}
			return linked;
		});
	}

	private List<StockSettlementAssignment> collectAssignmentCandidates(Map<Integer, StockTransaction> transactions,
			Set<Integer> reservedBookingIds) {
		List<StockTransactionCashLeg> cashLegs = dbController.getAll(StockTransactionCashLeg.class);
		Map<Integer, List<StockTransactionCashLeg>> legsByTransaction = cashLegs.stream()
				.collect(Collectors.groupingBy(leg -> leg.getTransactionId()));
		Set<Integer> usedBookingIds = cashLegs.stream().map(leg -> leg.getBookingId())
				.filter(id -> id != null).collect(Collectors.toSet());
		usedBookingIds.addAll(reservedBookingIds);
		Set<Integer> reversedIds = transactions.values().stream().map(transaction -> transaction.getReversalOfTransactionId())
				.filter(id -> id != null).collect(Collectors.toSet());
		Map<Integer, List<StockTransaction>> transactionsByPortfolio = transactions.values().stream()
				.filter(transaction -> transaction.getTransactionStatus() == StockTransactionStatus.SETTLED
						&& cashDate(transaction) != null && transaction.getReversalOfTransactionId() == null
						&& !reversedIds.contains(transaction.getId()))
				.collect(Collectors.groupingBy(transaction -> transaction.getPortfolioId()));
		Map<Integer, Map<BigDecimal, List<Booking>>> bookingsByAccount = new HashMap<>();
		List<StockSettlementAssignment> candidates = new ArrayList<>();
		for (StockPortfolio portfolio : dbController.getAll(StockPortfolio.class).stream()
				.filter(item -> transactionsByPortfolio.containsKey(item.getId())).toList()) {
			candidates.addAll(findPortfolioAssignmentCandidates(portfolio, transactionsByPortfolio.get(portfolio.getId()),
					legsByTransaction, usedBookingIds, bookingsByAccount));
		}
		return candidates;
	}

	private List<StockSettlementAssignment> findPortfolioAssignmentCandidates(StockPortfolio portfolio,
			List<StockTransaction> transactions, Map<Integer, List<StockTransactionCashLeg>> legsByTransaction,
			Set<Integer> usedBookingIds, Map<Integer, Map<BigDecimal, List<Booking>>> bookingsByAccount) {
		StockPortfolioSettlementAccount relation = dbController.getById(StockPortfolioSettlementAccount.class,
				portfolio.getCurrentSettlementRelationId());
		if (relation == null || relation.getPortfolioId() != portfolio.getId()) {
			return List.of();
		}
		BankAccount account = dbController.getById(BankAccount.class, relation.getAccountId());
		BankAccount depot = dbController.getById(BankAccount.class, portfolio.getAccountId());
		if (account == null || depot == null || account.getBaseCurrency() == null) {
			return List.of();
		}
		Map<BigDecimal, List<Booking>> bookings = bookingsByAccount.computeIfAbsent(account.getId(),
				id -> dbController.getAllByParentFull(Booking.class, id).stream()
						.filter(booking -> booking.getAmount() != null)
						.collect(Collectors.groupingBy(booking -> booking.getAmount().stripTrailingZeros())));
		List<StockSettlementAssignment> candidates = new ArrayList<>();
		for (StockTransaction transaction : transactions) {
			candidates.addAll(findAssignmentCandidates(transaction, legsByTransaction.getOrDefault(transaction.getId(), List.of()),
					account, depot, bookings, usedBookingIds));
		}
		return candidates;
	}

	private List<StockSettlementAssignment> findAssignmentCandidates(StockTransaction transaction,
			List<StockTransactionCashLeg> legs, BankAccount account, BankAccount depot,
			Map<BigDecimal, List<Booking>> bookings, Set<Integer> usedIds) {
		if (legs.isEmpty() || legs.stream().anyMatch(leg -> leg.getBookingId() != null
				|| leg.getAccountId() != account.getId() || leg.getCurrency() != account.getBaseCurrency())) {
			return List.of();
		}
		long minor = legs.stream().mapToLong(leg -> leg.getAmountMinor()).reduce(0L, Math::addExact);
		if (minor == 0) {
			return List.of();
		}
		BigDecimal amount = StockNumericValues.fromScaled(minor, account.getBaseCurrency().getMinorUnitDigits());
		return bookings.getOrDefault(amount, List.of()).stream()
				.filter(booking -> !usedIds.contains(booking.getId()) && isCoveringCandidate(booking, amount))
				.filter(booking -> dateDistance(booking.getDate(), cashDate(transaction)) <= SEARCH_WINDOW_DAYS)
				.filter(booking -> RebookingLinkWriter.canLinkWithCounterBooking(booking, account, depot))
				.map(booking -> new StockSettlementAssignment(transaction.getId(), booking.getId(), depot.getId(), account.getId()))
				.toList();
	}

	private static LocalDate cashDate(StockTransaction transaction) {
		var date = transaction.getCashValueAt() != null ? transaction.getCashValueAt() : transaction.getSettledAt();
		return date != null ? date.toLocalDate() : null;
	}

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
