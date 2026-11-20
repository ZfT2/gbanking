package de.zft2.gbanking.file.imp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.account.BookingDuplicateChecker;
import de.zft2.gbanking.service.account.BookingDuplicateChecker.Candidates;
import de.zft2.gbanking.service.account.BookingDuplicateReviewHandler;
import de.zft2.gbanking.service.account.BookingDuplicateReviewHandler.Match;
import de.zft2.gbanking.service.stock.StockSettlementLinkService;

public final class ImportedBookingPersistence {

	private static final Logger log = LogManager.getLogger(ImportedBookingPersistence.class);

	private final DBController dbController;
	private final Map<Integer, Candidates> existingBookingsByAccountId = new HashMap<>();
	private final Map<Integer, Candidates> processedBookingsByAccountId = new HashMap<>();
	// Equal bookings can still represent distinct file rows with independent review decisions.
	private final Set<Booking> excludedBookings = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<Booking, Booking> reviewedStrictMatches = new IdentityHashMap<>();
	private final Map<Booking, List<Booking>> reviewedPotentialMatches = new IdentityHashMap<>();

	public ImportedBookingPersistence(DBController dbController) {
		this.dbController = dbController;
	}

	void reset() {
		existingBookingsByAccountId.clear();
		processedBookingsByAccountId.clear();
		excludedBookings.clear();
		reviewedStrictMatches.clear();
		reviewedPotentialMatches.clear();
	}

	public void review(Collection<Booking> bookings, BookingDuplicateReviewHandler handler) {
		BookingDuplicateChecker checker = new BookingDuplicateChecker();
		Map<Integer, Candidates> processed = new HashMap<>();
		Map<Integer, String> accountNames = new HashMap<>();
		List<Match> matches = new ArrayList<>();
		for (Booking booking : bookings) {
			if (!hasDate(booking)) {
				continue;
			}
			Candidates existing = existingBookings(booking.getAccountId());
			Candidates incoming = processed.computeIfAbsent(booking.getAccountId(), id -> new Candidates(List.of()));
			Booking strictMatch = ImportedBookingMatcher.findMatchingBooking(existing, incoming, booking);
			reviewedStrictMatches.put(booking, strictMatch);
			if (strictMatch == null) {
				List<Booking> potential = checker.findPotentialDuplicates(booking, existing);
				reviewedPotentialMatches.put(booking, potential);
				if (!potential.isEmpty()) {
					booking.setAccountName(accountNames.computeIfAbsent(booking.getAccountId(),
							id -> dbController.getById(BankAccount.class, id).getAccountName()));
				}
				potential.forEach(match -> {
					matches.add(new Match(match, booking));
					excludedBookings.add(booking);
				});
			}
			incoming.add(strictMatch != null ? strictMatch : booking);
		}
		if (!matches.isEmpty()) {
			if (handler == null) {
				throw new GBankingException("Potential duplicate bookings require an explicit import review.");
			}
			handler.selectImports(List.copyOf(matches)).forEach(booking -> excludedBookings.remove(booking));
			log.info("Reviewed potential booking duplicates. pairs={}, excludedBookings={}", matches.size(), excludedBookings.size());
		}
	}

	int existingBookingCount(int accountId) {
		return existingBookings(accountId).size();
	}

	public Booking getExistingBookingForExcludedImport(Booking booking) {
		List<Booking> matches = reviewedPotentialMatches.getOrDefault(booking, List.of());
		return excludedBookings.contains(booking) && matches.size() == 1 ? matches.get(0) : null;
	}

	public Result persist(Booking booking) {
		if (excludedBookings.contains(booking)) {
			return new Result(false, true, null);
		}
		if (!hasDate(booking)) {
			// Bookings without any date are ignored. They cannot be demarcated and they would be imported
			// a second time as soon as they are delivered again with a date.
			log.warn("Skipped imported booking without booking date and value date. accountId={}, amount={}",
					booking.getAccountId(), booking.getAmount());
			return new Result(false, true, null);
		}

		int accountId = booking.getAccountId();
		Candidates processedBookings = processedBookings(accountId);
		Booking existingBooking = reviewedStrictMatches.containsKey(booking) ? reviewedStrictMatches.get(booking)
				: ImportedBookingMatcher.findMatchingBooking(existingBookings(accountId), processedBookings, booking);
		if (existingBooking != null) {
			// A repeated reference must stay excluded when its first occurrence was rejected in the review.
			if (existingBooking.getId() <= 0) {
				return new Result(false, true, null);
			}
			processedBookings.add(existingBooking);
			return new Result(true, false, existingBooking);
		}

		// A reviewed import decision takes precedence over settlement matching.
		Booking coveringStockBooking = reviewedStrictMatches.containsKey(booking) ? null
				: ServiceRegistry.getService(StockSettlementLinkService.class)
				.resolveCoveringStockBooking(booking);
		if (coveringStockBooking != null) {
			log.info("Skipped imported booking of account {} because the securities transaction {} already covers it.",
					booking.getAccountId(), coveringStockBooking.getId());
			return new Result(false, true, null);
		}

		Booking persistedBooking = dbController.insertOrUpdate(booking);
		if (persistedBooking != null) {
			processedBookings.add(persistedBooking);
		}
		return new Result(false, false, persistedBooking);
	}

	private static boolean hasDate(Booking booking) {
		return booking.getDateBooking() != null || booking.getDateValue() != null;
	}

	private Candidates existingBookings(int accountId) {
		return existingBookingsByAccountId.computeIfAbsent(accountId,
				id -> new Candidates(dbController.getAllByParentFull(Booking.class, id)));
	}

	private Candidates processedBookings(int accountId) {
		return processedBookingsByAccountId.computeIfAbsent(accountId, id -> new Candidates(List.of()));
	}

	public record Result(boolean existing, boolean skipped, Booking booking) {
	}
}
