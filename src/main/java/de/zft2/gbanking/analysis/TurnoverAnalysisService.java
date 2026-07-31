package de.zft2.gbanking.analysis;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.enu.AccountState;

public final class TurnoverAnalysisService {

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

	public enum AccountSelectionMode {
		ALL,
		ACTIVE,
		ONLINE,
		CUSTOM
	}

	public enum PeriodType {
		CURRENT_MONTH,
		LAST_30_DAYS,
		LAST_3_MONTHS,
		CURRENT_YEAR,
		LAST_365_DAYS,
		ALL_TIME,
		SINCE,
		CUSTOM_RANGE
	}

	public enum FlowDirection {
		INCOME,
		EXPENSE
	}

	public record AnalysisConfiguration(
			AccountSelectionMode accountSelectionMode,
			List<Integer> selectedAccountIds,
			PeriodType periodType,
			LocalDate dateFrom,
			LocalDate dateTo) {

		public AnalysisConfiguration {
			accountSelectionMode = accountSelectionMode != null ? accountSelectionMode : AccountSelectionMode.ALL;
			selectedAccountIds = selectedAccountIds != null ? List.copyOf(selectedAccountIds) : List.of();
			periodType = periodType != null ? periodType : PeriodType.CURRENT_MONTH;
		}

		public static AnalysisConfiguration defaultConfiguration() {
			return new AnalysisConfiguration(AccountSelectionMode.ALL, List.of(), PeriodType.CURRENT_MONTH, null, null);
		}
	}

	public record DateRange(LocalDate from, LocalDate to) {

		public boolean contains(LocalDate date) {
			if (date == null) {
				return from == null && to == null;
			}
			return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
		}
	}

	public record Summary(
			BigDecimal income,
			BigDecimal expenses,
			BigDecimal netExternalFlow,
			BigDecimal turnover,
			BigDecimal neutralTransferAmount,
			BigDecimal selectedBalanceChange,
			int accountCount,
			int bookingCount,
			int neutralBookingCount,
			int neutralTransferCount) {
	}

	public record BalancePoint(LocalDate date, BigDecimal balance) {
	}

	public record CategorySlice(FlowDirection direction, String categoryName, BigDecimal amount) {
	}

	public record AnalysisResult(
			List<BankAccount> selectedAccounts,
			DateRange dateRange,
			List<Booking> periodBookings,
			Summary summary,
			List<BalancePoint> balancePoints,
			List<CategorySlice> categorySlices) {
	}

	private record CategoryKey(FlowDirection direction, String categoryName) {
	}

	public AnalysisResult analyze(List<BankAccount> accounts, List<Booking> allBookings, AnalysisConfiguration configuration, LocalDate today) {
		List<Booking> bookingList = nullSafeBookings(allBookings);
		List<BankAccount> eligibleAccounts = getEligibleAccounts(accounts, bookingList);
		AnalysisConfiguration safeConfiguration = configuration != null ? configuration : AnalysisConfiguration.defaultConfiguration();
		List<BankAccount> selectedAccounts = resolveSelectedAccounts(eligibleAccounts, safeConfiguration);
		Set<Integer> selectedAccountIds = accountIds(selectedAccounts);
		List<Booking> selectedBookings = bookingList.stream()
				.filter(booking -> booking != null && selectedAccountIds.contains(booking.getAccountId()))
				.filter(booking -> !isPrenotification(booking))
				.toList();
		DateRange dateRange = resolveDateRange(safeConfiguration, selectedBookings, today != null ? today : LocalDate.now(ZoneId.systemDefault()));
		List<Booking> periodBookings = selectedBookings.stream()
				.filter(booking -> dateRange.contains(relevantDate(booking)))
				.toList();

		Summary summary = buildSummary(selectedAccounts, periodBookings);
		List<BalancePoint> balancePoints = buildBalancePoints(selectedBookings, dateRange);
		List<CategorySlice> categorySlices = buildCategorySlices(periodBookings);

		return new AnalysisResult(List.copyOf(selectedAccounts), dateRange, List.copyOf(periodBookings), summary, balancePoints, categorySlices);
	}

	public List<BankAccount> getEligibleAccounts(List<BankAccount> accounts, List<Booking> allBookings) {
		Set<Integer> accountIdsWithBookings = nullSafeBookings(allBookings).stream()
				.filter(booking -> booking != null && booking.getAccountId() > 0)
				.map(Booking::getAccountId)
				.collect(Collectors.toSet());

		return nullSafeAccounts(accounts).stream()
				.filter(account -> account != null && account.getId() > 0)
				.filter(account -> account.getAccountState() != AccountState.IGNORE)
				.filter(account -> accountIdsWithBookings.contains(account.getId()))
				.sorted(Comparator.comparing(this::accountDisplayName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public List<BankAccount> resolveSelectedAccounts(List<BankAccount> eligibleAccounts, AnalysisConfiguration configuration) {
		List<BankAccount> accounts = nullSafeAccounts(eligibleAccounts);
		AnalysisConfiguration safeConfiguration = configuration != null ? configuration : AnalysisConfiguration.defaultConfiguration();
		return switch (safeConfiguration.accountSelectionMode()) {
		case ALL -> accounts;
		case ACTIVE -> accounts.stream()
				.filter(account -> account.getAccountState() == AccountState.ACTIVE)
				.toList();
		case ONLINE -> accounts.stream()
				.filter(this::hasBankAccess)
				.toList();
		case CUSTOM -> {
			Set<Integer> selectedIds = new HashSet<>(safeConfiguration.selectedAccountIds());
			yield accounts.stream()
					.filter(account -> selectedIds.contains(account.getId()))
					.toList();
		}
		};
	}

	public DateRange resolveDateRange(AnalysisConfiguration configuration, List<Booking> selectedBookings, LocalDate today) {
		AnalysisConfiguration safeConfiguration = configuration != null ? configuration : AnalysisConfiguration.defaultConfiguration();
		LocalDate referenceDate = today != null ? today : LocalDate.now(ZoneId.systemDefault());
		return switch (safeConfiguration.periodType()) {
		case CURRENT_MONTH -> new DateRange(referenceDate.withDayOfMonth(1), referenceDate.with(TemporalAdjusters.lastDayOfMonth()));
		case LAST_30_DAYS -> new DateRange(referenceDate.minusDays(29), referenceDate);
		case LAST_3_MONTHS -> new DateRange(referenceDate.minusMonths(3).plusDays(1), referenceDate);
		case CURRENT_YEAR -> new DateRange(referenceDate.withDayOfYear(1), referenceDate.with(TemporalAdjusters.lastDayOfYear()));
		case LAST_365_DAYS -> new DateRange(referenceDate.minusDays(364), referenceDate);
		case ALL_TIME -> resolveAllTimeRange(selectedBookings);
		case SINCE -> new DateRange(safeConfiguration.dateFrom() != null ? safeConfiguration.dateFrom() : referenceDate.withDayOfYear(1), referenceDate);
		case CUSTOM_RANGE -> normalizeRange(
				safeConfiguration.dateFrom() != null ? safeConfiguration.dateFrom() : referenceDate.withDayOfMonth(1),
				safeConfiguration.dateTo() != null ? safeConfiguration.dateTo() : referenceDate);
		};
	}

	public boolean isConcreteLinkedRebooking(Booking booking) {
		return booking != null
				&& booking.getCrossAccountId() != null
				&& booking.getCrossAccountId() > 0
				&& booking.getCrossBookingId() != null
				&& booking.getCrossBookingId() > 0
				&& booking.getCrossBookingId() != booking.getId();
	}

	private Summary buildSummary(List<BankAccount> selectedAccounts, List<Booking> periodBookings) {
		BigDecimal income = ZERO;
		BigDecimal expenses = ZERO;
		BigDecimal selectedBalanceChange = ZERO;
		int neutralBookingCount = 0;
		Map<String, BigDecimal> neutralTransfersByPair = new HashMap<>();

		for (Booking booking : periodBookings) {
			BigDecimal amount = amountOf(booking);
			selectedBalanceChange = selectedBalanceChange.add(amount);
			if (isConcreteLinkedRebooking(booking)) {
				neutralBookingCount++;
				neutralTransfersByPair.merge(pairKey(booking), amount.abs(), BigDecimal::max);
				continue;
			}

			if (amount.signum() > 0) {
				income = income.add(amount);
			} else if (amount.signum() < 0) {
				expenses = expenses.add(amount.abs());
			}
		}

		BigDecimal neutralTransferAmount = neutralTransfersByPair.values().stream().reduce(ZERO, BigDecimal::add);
		BigDecimal netExternalFlow = income.subtract(expenses);
		return new Summary(
				money(income),
				money(expenses),
				money(netExternalFlow),
				money(income.add(expenses)),
				money(neutralTransferAmount),
				money(selectedBalanceChange),
				selectedAccounts.size(),
				periodBookings.size(),
				neutralBookingCount,
				neutralTransfersByPair.size());
	}

	private List<BalancePoint> buildBalancePoints(List<Booking> selectedBookings, DateRange range) {
		TreeMap<LocalDate, BigDecimal> deltaByDate = new TreeMap<>();
		for (Booking booking : selectedBookings) {
			LocalDate date = relevantDate(booking);
			if (date != null) {
				deltaByDate.merge(date, amountOf(booking), BigDecimal::add);
			}
		}
		if (deltaByDate.isEmpty()) {
			return List.of();
		}

		BigDecimal balance = ZERO;
		List<BalancePoint> points = new ArrayList<>();
		LocalDate from = range != null ? range.from() : null;
		LocalDate to = range != null ? range.to() : null;

		boolean startPointAdded = calculateBalance(points, deltaByDate, from, to, balance);

		if (!startPointAdded && from != null) {
			addOrReplacePoint(points, from, balance);
		}
		if (to != null && (points.isEmpty() || points.get(points.size() - 1).date().isBefore(to))) {
			addOrReplacePoint(points, to, balance);
		}
		return List.copyOf(points);
	}

	private boolean calculateBalance(List<BalancePoint> points, TreeMap<LocalDate, BigDecimal> deltaByDate, LocalDate from, LocalDate to, BigDecimal balance) {
		boolean startPointAdded = false;
		for (Map.Entry<LocalDate, BigDecimal> entry : deltaByDate.entrySet()) {
			LocalDate date = entry.getKey();

			if (from != null && date.isBefore(from)) {
				balance = balance.add(entry.getValue());
			} else {
				if (to != null && date.isAfter(to)) {
					break;
				}
				if (!startPointAdded && from != null) {
					addOrReplacePoint(points, from, balance);
					startPointAdded = true;
				}
				balance = balance.add(entry.getValue());
				addOrReplacePoint(points, date, balance);
			}
		}
		return startPointAdded;
	}

	private List<CategorySlice> buildCategorySlices(List<Booking> periodBookings) {
		Map<CategoryKey, BigDecimal> amountByCategory = new HashMap<>();
		for (Booking booking : periodBookings) {
			BigDecimal amount = amountOf(booking);
			if (booking == null || isConcreteLinkedRebooking(booking) || amount.signum() == 0) {
				continue;
			}
			FlowDirection direction = amount.signum() > 0 ? FlowDirection.INCOME : FlowDirection.EXPENSE;
			CategoryKey key = new CategoryKey(direction, categoryName(booking.getCategory()));
			amountByCategory.merge(key, amount.abs(), BigDecimal::add);
		}

		return amountByCategory.entrySet().stream()
				.map(entry -> new CategorySlice(entry.getKey().direction(), entry.getKey().categoryName(), money(entry.getValue())))
				.sorted(Comparator.comparing(CategorySlice::amount).reversed()
						.thenComparing(slice -> slice.direction().name())
						.thenComparing(slice -> slice.categoryName() != null ? slice.categoryName() : "", String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	private DateRange resolveAllTimeRange(List<Booking> selectedBookings) {
		List<LocalDate> dates = nullSafeBookings(selectedBookings).stream()
				.map(this::relevantDate)
				.filter(Objects::nonNull)
				.sorted()
				.toList();
		if (dates.isEmpty()) {
			return new DateRange(null, null);
		}
		return new DateRange(dates.get(0), dates.get(dates.size() - 1));
	}

	private DateRange normalizeRange(LocalDate from, LocalDate to) {
		if (from != null && to != null && to.isBefore(from)) {
			return new DateRange(to, from);
		}
		return new DateRange(from, to);
	}

	private void addOrReplacePoint(List<BalancePoint> points, LocalDate date, BigDecimal balance) {
		if (!points.isEmpty() && points.get(points.size() - 1).date().equals(date)) {
			points.set(points.size() - 1, new BalancePoint(date, money(balance)));
			return;
		}
		points.add(new BalancePoint(date, money(balance)));
	}

	private Set<Integer> accountIds(List<BankAccount> accounts) {
		return nullSafeAccounts(accounts).stream()
				.filter(account -> account != null && account.getId() > 0)
				.map(BankAccount::getId)
				.collect(Collectors.toSet());
	}

	private List<BankAccount> nullSafeAccounts(List<BankAccount> accounts) {
		return accounts != null ? accounts : List.of();
	}

	private List<Booking> nullSafeBookings(List<Booking> bookings) {
		return bookings != null ? bookings : List.of();
	}

	private boolean hasBankAccess(BankAccount account) {
		return account != null && account.getBankAccessId() != null && account.getBankAccessId() > 0;
	}

	private boolean isPrenotification(Booking booking) {
		return booking != null && booking.getSource() != null && booking.getSource().isPrenotification();
	}

	private LocalDate relevantDate(Booking booking) {
		return booking != null ? booking.getDate() : null;
	}

	private BigDecimal amountOf(Booking booking) {
		return booking != null && booking.getAmount() != null ? booking.getAmount() : ZERO;
	}

	private BigDecimal money(BigDecimal amount) {
		return (amount != null ? amount : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
	}

	private String categoryName(Category category) {
		return category != null ? trimToNull(category.getFullName()) : null;
	}

	private String accountDisplayName(BankAccount account) {
		if (account == null) {
			return "";
		}
		if (account.getAccountName() != null && !account.getAccountName().isBlank()) {
			return account.getAccountName();
		}
		if (account.getIban() != null && !account.getIban().isBlank()) {
			return account.getIban();
		}
		return account.getNumber() != null ? account.getNumber() : "";
	}

	private String pairKey(Booking booking) {
		int left = Math.min(booking.getId(), booking.getCrossBookingId());
		int right = Math.max(booking.getId(), booking.getCrossBookingId());
		return left + ":" + right;
	}
}
