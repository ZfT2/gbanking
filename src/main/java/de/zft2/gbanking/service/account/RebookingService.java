package de.zft2.gbanking.service.account;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.core.exception.ConfigurationException;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.rebooking.DetectedRebookingPair;
import de.zft2.gbanking.rebooking.MissingRebookingCandidate;
import de.zft2.gbanking.rebooking.MissingRebookingCreationSummary;
import de.zft2.gbanking.rebooking.MissingRebookingRouteSummary;
import de.zft2.gbanking.rebooking.RebookingAccountSummary;
import de.zft2.gbanking.rebooking.RebookingAssignmentSummary;
import de.zft2.gbanking.rebooking.RebookingRules;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.importproperties.ImportPropertiesSynchronizationService;
import de.zft2.gbanking.util.AppPaths;

final class RebookingService extends AbstractDbService {

	private static final Logger log = LogManager.getLogger(RebookingService.class);
	private static final int SEARCH_WINDOW_DAYS = 6;

	int adjustRebookings(BankAccount checkedAccount) {
		if (checkedAccount == null || checkedAccount.getId() <= 0) {
			log.debug("adjustRebookings(): No account selected.");
			return 0;
		}

		List<Booking> newOnlineBookings = getAccountBookings(checkedAccount.getId()).stream()
				.filter(booking -> booking.getSource() == Source.ONLINE_NEW)
				.toList();
		return linkNewOnlineCounterBookings(newOnlineBookings);
	}

	int linkNewOnlineCounterBookings(List<Booking> newBookings) {
		RebookingAssignmentSummary summary = detectRebookingLinks(createSearchScope(newBookings));
		int linkedBookings = persistDetectedRebookingLinks(summary);
		if (linkedBookings > 0) {
			log.info("Linked {} online rebooking pair(s).", linkedBookings);
		}
		return linkedBookings;
	}

	RebookingAssignmentSummary detectRebookings(LocalDate dateFrom, LocalDate dateTo,
			Collection<BankAccount> anchorAccounts) {
		return detectRebookingLinks(createManualSearchScope(dateFrom, dateTo, anchorAccounts));
	}

	MissingRebookingCreationSummary detectMissingRebookings(LocalDate dateFrom, LocalDate dateTo,
			Collection<BankAccount> anchorAccounts) {
		RebookingDetectionContext detectionContext = detectRebookingCandidates(
				createManualSearchScope(dateFrom, dateTo, anchorAccounts), true);
		if (detectionContext.isEmpty()) {
			return MissingRebookingCreationSummary.empty();
		}
		return collectMissingRebookingCreations(detectionContext);
	}

	int createMissingRebookings(MissingRebookingCreationSummary summary) {
		if (summary == null || summary.isEmpty()) {
			return 0;
		}

		int createdBookings = 0;
		Set<Integer> processedBookingIds = new HashSet<>();
		for (MissingRebookingCandidate candidate : summary.candidates()) {
			if (processedBookingIds.add(candidate.sourceBooking().getId())
					&& createMissingCounterBooking(candidate)) {
				createdBookings++;
			}
		}
		return createdBookings;
	}

	int persistDetectedRebookingLinks(RebookingAssignmentSummary summary) {
		if (summary == null || summary.isEmpty()) {
			return 0;
		}

		int linkedBookings = 0;
		Set<String> processedPairs = new HashSet<>();
		for (DetectedRebookingPair pair : summary.pairs()) {
			Booking booking = pair.booking();
			Booking crossBooking = pair.crossBooking();
			if (canPersistRebookingPair(booking, crossBooking)
					&& processedPairs.add(pairKey(booking, crossBooking))) {
				linkRebookingPair(booking, crossBooking);
				linkedBookings++;
			}
		}
		return linkedBookings;
	}

	int releaseRebookingLinks(Collection<Booking> bookings) {
		Set<Integer> selectedBookingIds = collectPositiveBookingIds(bookings);
		if (selectedBookingIds.isEmpty()) {
			return 0;
		}

		int updatedBookings = dbController.clearBookingCrossBookingIds(selectedBookingIds);
		if (updatedBookings > 0) {
			log.info("Released {} rebooking link(s) for {} selected booking(s).", updatedBookings,
					selectedBookingIds.size());
		}
		return Math.max(updatedBookings, 0);
	}

	private RebookingAssignmentSummary detectRebookingLinks(RebookingSearchScope searchScope) {
		RebookingDetectionContext detectionContext = detectRebookingCandidates(searchScope, false);
		if (detectionContext.isEmpty()) {
			return RebookingAssignmentSummary.empty();
		}
		return collectDetectedRebookingLinks(detectionContext);
	}

	private RebookingDetectionContext detectRebookingCandidates(RebookingSearchScope searchScope,
			boolean includeAccountsWithoutBookings) {
		if (searchScope == null) {
			return RebookingDetectionContext.empty();
		}

		Map<Integer, Integer> originalCrossBookingIds = new HashMap<>();
		Map<Integer, Integer> originalCrossAccountIds = new HashMap<>();
		Map<Integer, String> originalPurposes = new HashMap<>();
		Set<Integer> anchorBookingIds = new HashSet<>(searchScope.anchorBookingIds());
		List<BankAccount> accounts = loadAccountsForRebookingCheck(searchScope, anchorBookingIds,
				originalCrossBookingIds, originalCrossAccountIds, originalPurposes, includeAccountsWithoutBookings);
		if (anchorBookingIds.isEmpty() || accounts.size() < 2) {
			return RebookingDetectionContext.empty();
		}

		List<TransferPropertiesFileSnapshot> fileSnapshots = List.of();
		try {
			ServiceRegistry.getService(ImportPropertiesSynchronizationService.class).initializeAndSynchronize();
			fileSnapshots = snapshotTransferPropertiesFiles();
			OnlineBookingProcessor.generateCrossBookingsForOnline(accounts);
		} catch (ConfigurationException | RuntimeException e) {
			log.warn("Could not initialize booking-core rebooking detection.", e);
			return RebookingDetectionContext.empty();
		} finally {
			restoreTransferPropertiesFiles(fileSnapshots);
		}
		restoreOriginalPurposes(accounts, originalPurposes);

		return new RebookingDetectionContext(accounts, anchorBookingIds, originalCrossBookingIds,
				originalCrossAccountIds);
	}

	private List<BankAccount> loadAccountsForRebookingCheck(RebookingSearchScope searchScope,
			Set<Integer> anchorBookingIds, Map<Integer, Integer> originalCrossBookingIds,
			Map<Integer, Integer> originalCrossAccountIds, Map<Integer, String> originalPurposes,
			boolean includeAccountsWithoutBookings) {
		List<BankAccount> accounts = dbController.getAll(BankAccount.class);
		if (accounts == null || accounts.isEmpty()) {
			return List.of();
		}

		return getAccountsWithCandidateBookings(searchScope, anchorBookingIds, originalCrossBookingIds,
				originalCrossAccountIds, originalPurposes, includeAccountsWithoutBookings, accounts);
	}

	private List<BankAccount> getAccountsWithCandidateBookings(RebookingSearchScope searchScope,
			Set<Integer> anchorBookingIds, Map<Integer, Integer> originalCrossBookingIds,
			Map<Integer, Integer> originalCrossAccountIds, Map<Integer, String> originalPurposes,
			boolean includeAccountsWithoutBookings, List<BankAccount> accounts) {
		List<BankAccount> candidateAccounts = new ArrayList<>();
		for (BankAccount account : accounts) {
			prepareAccountForBookingCore(account);
			List<Booking> bookings = getAccountBookings(account.getId(), searchScope.searchFrom(),
					searchScope.searchTo());
			collectAnchorBookings(searchScope, anchorBookingIds, account, bookings);
			if (bookings.isEmpty()) {
				if (includeAccountsWithoutBookings) {
					account.setBookings(bookings);
					candidateAccounts.add(account);
				}
				continue;
			}
			prepareBookingsForBookingCore(account, bookings, originalCrossBookingIds, originalCrossAccountIds,
					originalPurposes);
			candidateAccounts.add(account);
		}
		return candidateAccounts;
	}

	private static void collectAnchorBookings(RebookingSearchScope searchScope, Set<Integer> anchorBookingIds,
			BankAccount account, List<Booking> bookings) {
		if (!searchScope.anchorAccountIds().contains(account.getId())) {
			return;
		}
		if (searchScope.restrictAnchorAccountsToAnchorBookings()) {
			bookings.removeIf(booking -> !anchorBookingIds.contains(booking.getId()));
			return;
		}
		bookings.stream()
				.filter(booking -> isBookingInAnchorRange(booking, searchScope))
				.map(Booking::getId)
				.filter(bookingId -> bookingId > 0)
				.forEach(anchorBookingIds::add);
	}

	private static void prepareBookingsForBookingCore(BankAccount account, List<Booking> bookings,
			Map<Integer, Integer> originalCrossBookingIds, Map<Integer, Integer> originalCrossAccountIds,
			Map<Integer, String> originalPurposes) {
		bookings.removeIf(booking -> booking.getForeignCurrencyDetails() != null);
		for (Booking booking : bookings) {
			booking.setAccountName(account.getNamePP());
			originalCrossBookingIds.put(booking.getId(), booking.getCrossBookingId());
			originalCrossAccountIds.put(booking.getId(), booking.getCrossAccountId());
			originalPurposes.put(booking.getId(), booking.getPurpose());
			if (booking.getPurpose() == null) {
				booking.setPurpose("");
			}
		}
		account.setBookings(bookings);
	}

	private static RebookingAssignmentSummary collectDetectedRebookingLinks(
			RebookingDetectionContext detectionContext) {
		List<DetectedRebookingPair> detectedPairs = new ArrayList<>();
		Set<String> processedPairs = new HashSet<>();

		for (BankAccount account : detectionContext.accounts()) {
			for (Booking booking : account.getBookings()) {
				if (booking.getCrossBooking() instanceof Booking crossBooking
						&& shouldPersistDetectedRebooking(booking, crossBooking, detectionContext)
						&& processedPairs.add(pairKey(booking, crossBooking))) {
					detectedPairs.add(new DetectedRebookingPair(booking, crossBooking));
				}
			}
		}
		return new RebookingAssignmentSummary(detectedPairs,
				buildRebookingAccountSummaries(detectionContext.accounts(), detectedPairs));
	}

	private static MissingRebookingCreationSummary collectMissingRebookingCreations(
			RebookingDetectionContext detectionContext) {
		List<MissingRebookingCandidate> candidates = new ArrayList<>();
		Set<Integer> processedBookingIds = new HashSet<>();

		for (BankAccount account : detectionContext.accounts()) {
			for (Booking booking : account.getBookings()) {
				BankAccount targetAccount = findRebookingTargetAccount(detectionContext.accounts(), booking);
				if (shouldCreateMissingCounterBooking(booking, account, targetAccount, detectionContext)
						&& processedBookingIds.add(booking.getId())) {
					candidates.add(new MissingRebookingCandidate(booking, account, targetAccount));
				}
			}
		}

		return new MissingRebookingCreationSummary(candidates, buildMissingRebookingRouteSummaries(candidates));
	}

	private boolean createMissingCounterBooking(MissingRebookingCandidate candidate) {
		Booking sourceBooking = dbController.getByIdFull(Booking.class, candidate.sourceBooking().getId());
		if (!canPersistMissingCounterBooking(sourceBooking, candidate.targetAccount())) {
			return false;
		}

		Booking counterBooking = createCounterBooking(sourceBooking, candidate.sourceAccount(),
				candidate.targetAccount());
		Booking savedCounterBooking = dbController.insertOrUpdate(counterBooking);
		if (savedCounterBooking == null || savedCounterBooking.getId() <= 0) {
			return false;
		}

		sourceBooking.setBookingType(resolveRebookingType(sourceBooking.getAmount()));
		sourceBooking.setCrossAccountId(candidate.targetAccount().getId());
		sourceBooking.setCrossBookingId(savedCounterBooking.getId());
		dbController.insertOrUpdate(sourceBooking);
		return true;
	}

	private Booking createCounterBooking(Booking sourceBooking, BankAccount sourceAccount,
			BankAccount targetAccount) {
		BigDecimal counterAmount = sourceBooking.getAmount().negate();
		Booking counterBooking = new Booking();
		counterBooking.setAccountId(targetAccount.getId());
		counterBooking.setDateBooking(defaultDate(sourceBooking.getDateBooking(), sourceBooking.getDate(),
				LocalDate.now(ZoneId.systemDefault())));
		counterBooking.setDateValue(defaultDate(sourceBooking.getDateValue(), sourceBooking.getDate(),
				counterBooking.getDateBooking()));
		counterBooking.setDate(sourceBooking.getDate() != null ? sourceBooking.getDate()
				: counterBooking.getDateValue());
		counterBooking.setPurpose(sourceBooking.getPurpose());
		counterBooking.setAmount(counterAmount);
		counterBooking.setSource(Source.MANUELL_NEW);
		counterBooking.setBookingType(resolveRebookingType(counterAmount));
		counterBooking.setCrossAccountId(sourceBooking.getAccountId());
		counterBooking.setCrossBookingId(sourceBooking.getId());
		counterBooking.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));

		Recipient recipient = dbController.resolveRecipient(createRecipientForAccount(sourceAccount));
		if (recipient != null) {
			counterBooking.setRecipient(recipient);
			counterBooking.setRecipientId(recipient.getId());
		}
		return counterBooking;
	}

	private void linkRebookingPair(Booking booking, Booking crossBooking) {
		prepareRebookingLink(booking, crossBooking);
		prepareRebookingLink(crossBooking, booking);
		dbController.insertOrUpdate(crossBooking);
		dbController.insertOrUpdate(booking);
	}

	private List<Booking> getAccountBookings(int accountId) {
		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, accountId);
		return bookings != null ? new ArrayList<>(bookings) : new ArrayList<>();
	}

	private List<Booking> getAccountBookings(int accountId, LocalDate searchFrom, LocalDate searchTo) {
		Booking dateRangeCriteria = new Booking();
		dateRangeCriteria.setDateBooking(searchFrom);
		dateRangeCriteria.setDateValue(searchTo);
		List<Booking> bookings = dbController.getAllByParentSpecific(dateRangeCriteria, accountId,
				StatementsConfig.StatementType.SELECT_WITH_PARENT_AND_DATE_RANGE);
		return bookings != null ? new ArrayList<>(bookings) : new ArrayList<>();
	}

	private static RebookingSearchScope createManualSearchScope(LocalDate dateFrom, LocalDate dateTo,
			Collection<BankAccount> anchorAccounts) {
		if (dateFrom == null || dateTo == null || dateTo.isBefore(dateFrom)
				|| anchorAccounts == null || anchorAccounts.isEmpty()) {
			return null;
		}

		Set<Integer> anchorAccountIds = new HashSet<>();
		for (BankAccount account : anchorAccounts) {
			if (account != null && account.getId() > 0) {
				anchorAccountIds.add(account.getId());
			}
		}
		if (anchorAccountIds.isEmpty()) {
			return null;
		}

		return new RebookingSearchScope(Set.of(), anchorAccountIds, dateFrom, dateTo,
				dateFrom.minusDays(SEARCH_WINDOW_DAYS), dateTo.plusDays(SEARCH_WINDOW_DAYS), false);
	}

	private static RebookingSearchScope createSearchScope(List<Booking> newBookings) {
		if (newBookings == null || newBookings.isEmpty()) {
			return null;
		}

		Set<Integer> newBookingIds = new HashSet<>();
		Set<Integer> sourceAccountIds = new HashSet<>();
		LocalDate firstBookingDate = null;
		LocalDate lastBookingDate = null;

		for (Booking booking : newBookings) {
			if (booking.getId() > 0) {
				newBookingIds.add(booking.getId());
			}
			if (booking.getAccountId() > 0) {
				sourceAccountIds.add(booking.getAccountId());
			}

			LocalDate bookingDate = booking.getDate();
			if (bookingDate == null) {
				continue;
			}
			if (firstBookingDate == null || bookingDate.isBefore(firstBookingDate)) {
				firstBookingDate = bookingDate;
			}
			if (lastBookingDate == null || bookingDate.isAfter(lastBookingDate)) {
				lastBookingDate = bookingDate;
			}
		}
		if (newBookingIds.isEmpty() || sourceAccountIds.isEmpty()
				|| firstBookingDate == null || lastBookingDate == null) {
			return null;
		}
		return new RebookingSearchScope(newBookingIds, sourceAccountIds, firstBookingDate, lastBookingDate,
				firstBookingDate.minusDays(SEARCH_WINDOW_DAYS), lastBookingDate.plusDays(SEARCH_WINDOW_DAYS), true);
	}

	private static boolean isBookingInAnchorRange(Booking booking, RebookingSearchScope searchScope) {
		LocalDate bookingDate = booking != null ? booking.getDate() : null;
		return bookingDate != null && !bookingDate.isBefore(searchScope.anchorFrom())
				&& !bookingDate.isAfter(searchScope.anchorTo());
	}

	private static void restoreOriginalPurposes(List<BankAccount> accounts,
			Map<Integer, String> originalPurposes) {
		for (BankAccount account : accounts) {
			for (Booking booking : account.getBookings()) {
				if (originalPurposes.containsKey(booking.getId())) {
					booking.setPurpose(originalPurposes.get(booking.getId()));
				}
			}
		}
	}

	private static void prepareAccountForBookingCore(BankAccount account) {
		if (hasText(account.getNamePP())) {
			return;
		}
		if (hasText(account.getAccountName())) {
			account.setNamePP(account.getAccountName());
		} else if (hasText(account.getIban())) {
			account.setNamePP(account.getIban());
		} else if (hasText(account.getNumber())) {
			account.setNamePP(account.getNumber());
		} else {
			account.setNamePP("Konto " + account.getId());
		}
	}

	private static BankAccount findRebookingTargetAccount(List<BankAccount> accounts, Booking booking) {
		if (booking == null || !hasText(booking.getCrossAccountName())) {
			return null;
		}
		String crossAccountName = booking.getCrossAccountName();
		for (BankAccount account : accounts) {
			String accountName = account.getNamePP();
			if (hasText(accountName)
					&& (crossAccountName.equalsIgnoreCase(accountName)
							|| accountName.contains(crossAccountName))) {
				return account;
			}
		}
		return null;
	}

	private static boolean shouldCreateMissingCounterBooking(Booking booking, BankAccount sourceAccount,
			BankAccount targetAccount, RebookingDetectionContext detectionContext) {
		if (booking == null || sourceAccount == null || targetAccount == null
				|| booking.getId() <= 0 || booking.getAmount() == null) {
			return false;
		}
		if (!detectionContext.anchorBookingIds().contains(booking.getId())
				|| sourceAccount.getId() <= 0 || targetAccount.getId() <= 0
				|| sourceAccount.getId() == targetAccount.getId()) {
			return false;
		}
		if (!isRebookingType(booking.getBookingType()) || booking.getCrossBooking() != null
				|| isPrenotification(booking) || booking.getParentBookingId() != null
				|| booking.getForeignCurrencyDetails() != null
				|| sourceAccount.getBaseCurrency() != targetAccount.getBaseCurrency()) {
			return false;
		}
		Integer originalCrossBookingId = detectionContext.originalCrossBookingIds().get(booking.getId());
		if (originalCrossBookingId != null && originalCrossBookingId > 0) {
			return false;
		}
		Integer originalCrossAccountId = detectionContext.originalCrossAccountIds().get(booking.getId());
		return originalCrossAccountId == null || originalCrossAccountId <= 0
				|| originalCrossAccountId == targetAccount.getId();
	}

	private static List<MissingRebookingRouteSummary> buildMissingRebookingRouteSummaries(
			List<MissingRebookingCandidate> candidates) {
		if (candidates.isEmpty()) {
			return List.of();
		}

		Map<MissingRebookingRouteKey, Integer> countsByRoute = new HashMap<>();
		for (MissingRebookingCandidate candidate : candidates) {
			BankAccount sourceAccount = candidate.sourceAccount();
			BankAccount targetAccount = candidate.targetAccount();
			MissingRebookingRouteKey routeKey = new MissingRebookingRouteKey(sourceAccount.getId(),
					formatRebookingAccountName(sourceAccount, sourceAccount.getId()), targetAccount.getId(),
					formatRebookingAccountName(targetAccount, targetAccount.getId()));
			countsByRoute.merge(routeKey, 1, Integer::sum);
		}

		return countsByRoute.entrySet().stream()
				.map(entry -> new MissingRebookingRouteSummary(entry.getKey().sourceAccountId(),
						entry.getKey().sourceAccountName(), entry.getKey().targetAccountId(),
						entry.getKey().targetAccountName(), entry.getValue()))
				.sorted(Comparator.comparing(MissingRebookingRouteSummary::sourceAccountName,
						String.CASE_INSENSITIVE_ORDER)
						.thenComparing(MissingRebookingRouteSummary::targetAccountName,
								String.CASE_INSENSITIVE_ORDER)
						.thenComparingInt(MissingRebookingRouteSummary::targetAccountId))
				.toList();
	}

	private static Set<Integer> collectPositiveBookingIds(Collection<Booking> bookings) {
		if (bookings == null || bookings.isEmpty()) {
			return Set.of();
		}
		Set<Integer> bookingIds = new HashSet<>();
		for (Booking booking : bookings) {
			if (booking != null && booking.getId() > 0) {
				bookingIds.add(booking.getId());
			}
		}
		return bookingIds;
	}

	private static boolean canPersistMissingCounterBooking(Booking sourceBooking,
			BankAccount targetAccount) {
		if (sourceBooking == null || targetAccount == null || sourceBooking.getId() <= 0
				|| sourceBooking.getAmount() == null || targetAccount.getId() <= 0
				|| sourceBooking.getAccountId() == targetAccount.getId()) {
			return false;
		}
		if (sourceBooking.getParentBookingId() != null || isPrenotification(sourceBooking)
				|| sourceBooking.getForeignCurrencyDetails() != null) {
			return false;
		}
		Integer crossBookingId = sourceBooking.getCrossBookingId();
		if (crossBookingId != null && crossBookingId > 0) {
			return false;
		}
		Integer crossAccountId = sourceBooking.getCrossAccountId();
		return crossAccountId == null || crossAccountId <= 0
				|| crossAccountId == targetAccount.getId();
	}

	private static Recipient createRecipientForAccount(BankAccount account) {
		Recipient recipient = new Recipient();
		recipient.setName(formatRebookingAccountName(account, account.getId()));
		recipient.setIban(account.getIban());
		recipient.setBic(account.getBic());
		recipient.setAccountNumber(account.getNumber());
		recipient.setBlz(account.getBlz());
		recipient.setBank(account.getBankName());
		recipient.setSource(Source.MANUELL);
		return recipient;
	}

	private static LocalDate defaultDate(LocalDate value, LocalDate fallback, LocalDate defaultValue) {
		if (value != null) {
			return value;
		}
		return fallback != null ? fallback : defaultValue;
	}

	private static boolean canPersistRebookingPair(Booking booking, Booking crossBooking) {
		return booking != null && crossBooking != null && booking.getId() > 0
				&& crossBooking.getId() > 0 && booking.getId() != crossBooking.getId()
				&& booking.getForeignCurrencyDetails() == null
				&& crossBooking.getForeignCurrencyDetails() == null
				&& !RebookingRules.isForbiddenSameAccountRebooking(booking.getAccountId(),
						crossBooking.getAccountId(),
						hasCancellationSignal(booking) || hasCancellationSignal(crossBooking));
	}

	private static List<RebookingAccountSummary> buildRebookingAccountSummaries(
			List<BankAccount> accounts, List<DetectedRebookingPair> detectedPairs) {
		if (detectedPairs.isEmpty()) {
			return List.of();
		}

		Map<Integer, BankAccount> accountById = new HashMap<>();
		for (BankAccount account : accounts) {
			accountById.put(account.getId(), account);
		}

		Map<Integer, Integer> rebookingCountsByAccountId = new HashMap<>();
		for (DetectedRebookingPair pair : detectedPairs) {
			addRebookingCount(rebookingCountsByAccountId, pair.booking().getAccountId());
			addRebookingCount(rebookingCountsByAccountId, pair.crossBooking().getAccountId());
		}

		return rebookingCountsByAccountId.entrySet().stream()
				.map(entry -> new RebookingAccountSummary(entry.getKey(),
						formatRebookingAccountName(accountById.get(entry.getKey()), entry.getKey()),
						entry.getValue()))
				.sorted(Comparator.comparing(RebookingAccountSummary::accountName,
						String.CASE_INSENSITIVE_ORDER)
						.thenComparingInt(RebookingAccountSummary::accountId))
				.toList();
	}

	private static void addRebookingCount(Map<Integer, Integer> rebookingCountsByAccountId,
			int accountId) {
		if (accountId > 0) {
			rebookingCountsByAccountId.merge(accountId, 1, Integer::sum);
		}
	}

	private static String formatRebookingAccountName(BankAccount account, int accountId) {
		if (account != null) {
			if (hasText(account.getNamePP())) {
				return account.getNamePP();
			}
			if (hasText(account.getAccountName())) {
				return account.getAccountName();
			}
			if (hasText(account.getIban())) {
				return account.getIban();
			}
			if (hasText(account.getNumber())) {
				return account.getNumber();
			}
		}
		return "Konto " + accountId;
	}

	private static boolean shouldPersistDetectedRebooking(Booking booking, Booking crossBooking,
			RebookingDetectionContext detectionContext) {
		if (booking == null || crossBooking == null || booking.getId() <= 0
				|| crossBooking.getId() <= 0 || booking.getId() == crossBooking.getId()) {
			return false;
		}
		if (!detectionContext.anchorBookingIds().contains(booking.getId())
				&& !detectionContext.anchorBookingIds().contains(crossBooking.getId())) {
			return false;
		}
		if (isPrenotification(booking) || isPrenotification(crossBooking)) {
			return false;
		}
		if (!hasSameBaseCurrency(booking, crossBooking, detectionContext.accounts())) {
			return false;
		}
		if (RebookingRules.isForbiddenSameAccountRebooking(booking.getAccountId(),
				crossBooking.getAccountId(),
				hasCancellationSignal(booking) || hasCancellationSignal(crossBooking))) {
			return false;
		}
		if (isAlreadyLinked(booking, crossBooking, detectionContext.originalCrossBookingIds())) {
			return false;
		}
		return hasNoConflictingLink(booking, crossBooking, detectionContext)
				&& hasNoConflictingLink(crossBooking, booking, detectionContext);
	}

	private static boolean hasSameBaseCurrency(Booking booking, Booking crossBooking,
			List<BankAccount> accounts) {
		BankAccount bookingAccount = findAccount(accounts, booking.getAccountId());
		BankAccount crossBookingAccount = findAccount(accounts, crossBooking.getAccountId());
		return bookingAccount != null && crossBookingAccount != null
				&& bookingAccount.getBaseCurrency() == crossBookingAccount.getBaseCurrency();
	}

	private static BankAccount findAccount(List<BankAccount> accounts, int accountId) {
		for (BankAccount account : accounts) {
			if (account.getId() == accountId) {
				return account;
			}
		}
		return null;
	}

	private static boolean isAlreadyLinked(Booking booking, Booking crossBooking,
			Map<Integer, Integer> originalCrossBookingIds) {
		return Integer.valueOf(crossBooking.getId()).equals(originalCrossBookingIds.get(booking.getId()))
				&& Integer.valueOf(booking.getId()).equals(originalCrossBookingIds.get(crossBooking.getId()));
	}

	private static boolean hasNoConflictingLink(Booking baseBooking, Booking counterpartBooking,
			RebookingDetectionContext detectionContext) {
		Integer originalCrossBookingId = detectionContext.originalCrossBookingIds().get(baseBooking.getId());
		if (originalCrossBookingId != null
				&& originalCrossBookingId != counterpartBooking.getId()) {
			return false;
		}

		Integer originalCrossAccountId = detectionContext.originalCrossAccountIds().get(baseBooking.getId());
		return originalCrossAccountId == null || originalCrossAccountId <= 0
				|| originalCrossAccountId == counterpartBooking.getAccountId();
	}

	private static void prepareRebookingLink(Booking baseBooking, Booking counterpartBooking) {
		Objects.requireNonNull(baseBooking, "baseBooking");
		Objects.requireNonNull(counterpartBooking, "counterpartBooking");

		if (!isCancelBooking(baseBooking)) {
			baseBooking.setBookingType(resolveRebookingType(baseBooking.getAmount()));
		}
		baseBooking.setCrossAccountId(counterpartBooking.getAccountId());
		baseBooking.setCrossBookingId(counterpartBooking.getId());
	}

	private static BookingType resolveRebookingType(BigDecimal amount) {
		return amount != null && amount.signum() < 0
				? BookingType.REBOOKING_OUT
				: BookingType.REBOOKING_IN;
	}

	private static boolean isRebookingType(BookingType bookingType) {
		return bookingType == BookingType.REBOOKING_IN || bookingType == BookingType.REBOOKING_OUT;
	}

	private static boolean hasCancellationSignal(Booking booking) {
		BookingAdditionalDetails details = booking != null ? booking.getAdditionalDetails() : null;
		return isCancelBooking(booking) || (details != null && Boolean.TRUE.equals(details.getStorno()));
	}

	private static boolean isCancelBooking(Booking booking) {
		return booking != null && booking.getBookingType() == BookingType.CANCEL;
	}

	private static boolean isPrenotification(Booking booking) {
		return booking != null && booking.getSource() != null && booking.getSource().isPrenotification();
	}

	private static String pairKey(Booking booking, Booking crossBooking) {
		int left = Math.min(booking.getId(), crossBooking.getId());
		int right = Math.max(booking.getId(), crossBooking.getId());
		return left + ":" + right;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static List<TransferPropertiesFileSnapshot> snapshotTransferPropertiesFiles() {
		Path propertiesDirectory = AppPaths.getImportPropertiesDirectory();
		List<TransferPropertiesFileSnapshot> snapshots = new ArrayList<>();
		addSnapshot(snapshots, propertiesDirectory.resolve("accountTransfer.properties"));
		addSnapshot(snapshots, propertiesDirectory.resolve("intern").resolve("accountTransfer.properties"));
		return snapshots;
	}

	private static void addSnapshot(List<TransferPropertiesFileSnapshot> snapshots, Path path) {
		TransferPropertiesFileSnapshot snapshot = snapshotTransferPropertiesFile(path);
		if (snapshot != null) {
			snapshots.add(snapshot);
		}
	}

	private static TransferPropertiesFileSnapshot snapshotTransferPropertiesFile(Path path) {
		try {
			return Files.exists(path)
					? new TransferPropertiesFileSnapshot(path, true, Files.readAllBytes(path))
					: new TransferPropertiesFileSnapshot(path, false, null);
		} catch (IOException e) {
			log.warn("Could not snapshot booking-core transfer properties file {}.", path, e);
			return null;
		}
	}

	private static void restoreTransferPropertiesFiles(
			List<TransferPropertiesFileSnapshot> snapshots) {
		for (TransferPropertiesFileSnapshot snapshot : snapshots) {
			try {
				if (snapshot.existed()) {
					Files.write(snapshot.path(), snapshot.content());
				} else {
					Files.deleteIfExists(snapshot.path());
				}
			} catch (IOException e) {
				log.warn("Could not restore booking-core transfer properties file {}.", snapshot.path(), e);
			}
		}
	}

	private record TransferPropertiesFileSnapshot(Path path, boolean existed, byte[] content) {
	}

	private record RebookingSearchScope(Set<Integer> anchorBookingIds, Set<Integer> anchorAccountIds,
			LocalDate anchorFrom, LocalDate anchorTo, LocalDate searchFrom, LocalDate searchTo,
			boolean restrictAnchorAccountsToAnchorBookings) {
	}

	private record RebookingDetectionContext(List<BankAccount> accounts, Set<Integer> anchorBookingIds,
			Map<Integer, Integer> originalCrossBookingIds,
			Map<Integer, Integer> originalCrossAccountIds) {

		static RebookingDetectionContext empty() {
			return new RebookingDetectionContext(List.of(), Set.of(), Map.of(), Map.of());
		}

		boolean isEmpty() {
			return accounts.isEmpty() || anchorBookingIds.isEmpty();
		}
	}

	private record MissingRebookingRouteKey(int sourceAccountId, String sourceAccountName,
			int targetAccountId, String targetAccountName) {
	}
}
