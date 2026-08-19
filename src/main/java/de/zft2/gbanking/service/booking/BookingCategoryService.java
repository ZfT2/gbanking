package de.zft2.gbanking.service.booking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.service.AbstractDbService;

public class BookingCategoryService extends AbstractDbService {

	private static final Logger log = LogManager.getLogger(BookingCategoryService.class);

	public int clearCategoryFromBookings(List<Booking> bookings) {
		if (bookings == null || bookings.isEmpty()) {
			return 0;
		}

		Set<Integer> bookingIds = bookings.stream().filter(booking -> booking != null && booking.getId() > 0 && getBookingCategoryId(booking) > 0)
				.map(Booking::getId).collect(java.util.stream.Collectors.toSet());
		if (bookingIds.isEmpty()) {
			return 0;
		}

		int updatedBookingCount = dbController.clearBookingCategories(bookingIds);
		if (updatedBookingCount <= 0) {
			return 0;
		}

		for (Booking booking : bookings) {
			if (booking != null && bookingIds.contains(booking.getId())) {
				booking.setCategory(null);
				booking.setCategoryId(0);
				booking.setCategoryRuleId(null);
				booking.setCategoryRuleName(null);
			}
		}
		log.info("Removed category assignment from {} bookings.", updatedBookingCount);
		return updatedBookingCount;
	}

	public int applyCategoryRulesToBookings(List<Booking> bookings, boolean overwriteExistingCategories) {
		if (bookings == null || bookings.isEmpty()) {
			return 0;
		}

		Set<Integer> bookingIds = bookings.stream().filter(booking -> booking != null && booking.getId() > 0).map(Booking::getId)
				.collect(java.util.stream.Collectors.toSet());
		if (bookingIds.isEmpty()) {
			return 0;
		}

		List<Booking> candidateBookings = dbController.getAllFull(Booking.class).stream().filter(booking -> bookingIds.contains(booking.getId())).toList();
		return applyCategoryRulesToCandidateBookings(candidateBookings, overwriteExistingCategories);
	}

	private int applyCategoryRulesToCandidateBookings(List<Booking> candidateBookings, boolean overwriteExistingCategories) {
		if (candidateBookings == null || candidateBookings.isEmpty()) {
			return 0;
		}

		Set<Integer> candidateAccountIds = candidateBookings.stream().map(Booking::getAccountId).collect(java.util.stream.Collectors.toSet());
		Set<Integer> updatedBookingIds = new HashSet<>();
		for (CategoryRule categoryRule : dbController.getAllFull(CategoryRule.class)) {
			if (appliesToAnyCheckedAccount(categoryRule, candidateAccountIds)) {
				updatedBookingIds.addAll(applyCategoryRule(categoryRule, candidateBookings, overwriteExistingCategories));
			}
		}
		log.info("Applied category rules to {} bookings. overwriteExistingCategories={}", updatedBookingIds.size(), overwriteExistingCategories);
		return updatedBookingIds.size();
	}

	public void applyCategoryRule(CategoryRule categoryRule) {
		applyCategoryRule(categoryRule, dbController.getAllFull(Booking.class), true);
	}

	public int assignCategoryToBookings(Category category, List<Booking> bookings) {
		if (category == null || category.getId() <= 0 || bookings == null || bookings.isEmpty()) {
			return 0;
		}

		Set<Integer> bookingIds = bookings.stream().filter(booking -> booking != null && booking.getId() > 0).map(Booking::getId)
				.collect(java.util.stream.Collectors.toSet());
		if (bookingIds.isEmpty()) {
			return 0;
		}

		Map<Category, Set<Integer>> categoryBookingMap = new HashMap<>();
		categoryBookingMap.put(category, bookingIds);
		if (!dbController.updateBookingsWithCategories(categoryBookingMap)) {
			return 0;
		}

		for (Booking booking : bookings) {
			if (booking != null && bookingIds.contains(booking.getId())) {
				booking.setCategory(category);
				booking.setCategoryId(category.getId());
				booking.setCategoryRuleId(null);
				booking.setCategoryRuleName(null);
			}
		}
		log.info("Assigned category '{}' to {} bookings.", category.getFullName(), bookingIds.size());
		return bookingIds.size();
	}

	public void applyCategoryRules(List<BankAccount> checkedAccounts) {
		if (checkedAccounts == null || checkedAccounts.isEmpty()) {
			return;
		}

		Set<Integer> checkedAccountIds = checkedAccounts.stream().map(BankAccount::getId).collect(java.util.stream.Collectors.toSet());
		List<Booking> candidateBookings = dbController.getAllFull(Booking.class).stream().filter(booking -> checkedAccountIds.contains(booking.getAccountId()))
				.toList();
		applyCategoryRulesToCandidateBookings(candidateBookings, true);
	}

	private Set<Integer> applyCategoryRule(CategoryRule categoryRule, List<Booking> candidateBookings, boolean overwriteExistingCategories) {
		if (categoryRule == null || categoryRule.getCategory() == null || categoryRule.getCategory().getId() <= 0 || candidateBookings == null
				|| candidateBookings.isEmpty()) {
			return Set.of();
		}

		List<Predicate<Booking>> filters = buildCategoryRuleFilters(categoryRule);
		Predicate<Booking> matchesRule = combineCategoryRuleFilters(categoryRule, filters);

		Set<Integer> allowedAccountIds = getAllowedAccountIds(categoryRule);
		List<Booking> bookingListToCategorize = candidateBookings.stream().filter(Objects::nonNull)
				.filter(booking -> allowedAccountIds.isEmpty() || allowedAccountIds.contains(booking.getAccountId())).filter(matchesRule)
				.filter(booking -> shouldApplyCategory(booking, categoryRule.getCategory(), overwriteExistingCategories)).toList();

		if (bookingListToCategorize.isEmpty()) {
			log.debug("Category rule id {} matched no bookings to update.", categoryRule.getId());
			return Set.of();
		}

		Set<Integer> bookingIdSet = bookingListToCategorize.stream().map(Booking::getId).collect(java.util.stream.Collectors.toSet());
		if (!updateBookingsWithCategoryRule(categoryRule, bookingIdSet)) {
			return Set.of();
		}

		for (Booking booking : bookingListToCategorize) {
			booking.setCategory(categoryRule.getCategory());
			booking.setCategoryId(categoryRule.getCategory().getId());
			booking.setCategoryRuleId(categoryRule.getId() > 0 ? categoryRule.getId() : null);
			booking.setCategoryRuleName(categoryRule.getId() > 0 ? categoryRule.getName() : null);
		}
		log.info("Applied category rule id {} to {} bookings.", categoryRule.getId(), bookingListToCategorize.size());
		return bookingIdSet;
	}

	private List<Predicate<Booking>> buildCategoryRuleFilters(CategoryRule categoryRule) {
		List<Predicate<Booking>> filters = new ArrayList<>();
		addAmountFilters(categoryRule, filters);
		addDateFilters(categoryRule, filters);
		addTextFilters(categoryRule, filters);
		return filters;
	}

	private void addAmountFilters(CategoryRule categoryRule, List<Predicate<Booking>> filters) {
		if (categoryRule.getFilterAmountFrom() != null) {
			filters.add(booking -> booking.getAmount() != null && booking.getAmount().compareTo(categoryRule.getFilterAmountFrom()) >= 0);
		}
		if (categoryRule.getFilterAmountTo() != null) {
			filters.add(booking -> booking.getAmount() != null && booking.getAmount().compareTo(categoryRule.getFilterAmountTo()) <= 0);
		}
	}

	private void addDateFilters(CategoryRule categoryRule, List<Predicate<Booking>> filters) {
		if (categoryRule.getFilterDateFrom() != null) {
			filters.add(booking -> booking.getDateBooking() != null && !booking.getDateBooking().isBefore(categoryRule.getFilterDateFrom()));
		}
		if (categoryRule.getFilterDateTo() != null) {
			filters.add(booking -> booking.getDateBooking() != null && !booking.getDateBooking().isAfter(categoryRule.getFilterDateTo()));
		}
	}

	private void addTextFilters(CategoryRule categoryRule, List<Predicate<Booking>> filters) {
		if (categoryRule.getFilterPurpose() != null) {
			filters.add(booking -> matchesTextFilter(booking.getPurpose(), categoryRule.getFilterPurpose(), categoryRule.isFilterPurposeIsRegex()));
		}
		if (categoryRule.getFilterRecipientName() != null) {
			filters.add(booking -> matchesTextFilter(booking.getRecipient() != null ? booking.getRecipient().getName() : null,
					categoryRule.getFilterRecipientName(), categoryRule.isFilterRecipientIsRegex()));
		}
		if (categoryRule.getFilterRecipientIban() != null) {
			filters.add(booking -> matchesTextFilter(booking.getRecipient() != null ? booking.getRecipient().getIban() : null,
					categoryRule.getFilterRecipientIban(), false));
		}
		if (categoryRule.getFilterRecipientAccountNumber() != null) {
			filters.add(booking -> matchesTextFilter(booking.getRecipient() != null ? booking.getRecipient().getAccountNumber() : null,
					categoryRule.getFilterRecipientAccountNumber(), false));
		}
	}

	private boolean matchesTextFilter(String value, String filter, boolean regex) {
		if (filter == null) {
			return true;
		}
		if (value == null) {
			return false;
		}
		if (regex) {
			try {
				return Pattern.compile(filter, Pattern.CASE_INSENSITIVE).matcher(value).find();
			} catch (PatternSyntaxException ex) {
				log.warn("Invalid regex for category rule filter: {}", filter, ex);
				return false;
			}
		}
		return value.toLowerCase().contains(filter.toLowerCase());
	}

	private boolean updateBookingsWithCategoryRule(CategoryRule categoryRule, Set<Integer> bookingIdSet) {
		if (categoryRule.getId() > 0) {
			return dbController.updateBookingsWithCategoryRule(categoryRule, bookingIdSet);
		}

		Map<Category, Set<Integer>> categoryBookingMap = new HashMap<>();
		categoryBookingMap.put(categoryRule.getCategory(), bookingIdSet);
		return dbController.updateBookingsWithCategories(categoryBookingMap);
	}

	private boolean shouldApplyCategory(Booking booking, Category category, boolean overwriteExistingCategories) {
		if (booking == null || category == null || category.getId() <= 0) {
			return false;
		}

		int existingCategoryId = getBookingCategoryId(booking);
		return existingCategoryId <= 0 || overwriteExistingCategories && existingCategoryId != category.getId();
	}

	public int getBookingCategoryId(Booking booking) {
		if (booking.getCategory() != null && booking.getCategory().getId() > 0) {
			return booking.getCategory().getId();
		}
		return booking.getCategoryId();
	}

	private Predicate<Booking> combineCategoryRuleFilters(CategoryRule categoryRule, List<Predicate<Booking>> filters) {
		if (filters.isEmpty()) {
			return booking -> true;
		}

		if (categoryRule.getJoinType() == CategoryRule.JoinType.AND) {
			return booking -> filters.stream().allMatch(filter -> filter.test(booking));
		}

		return booking -> filters.stream().anyMatch(filter -> filter.test(booking));
	}

	private boolean appliesToAnyCheckedAccount(CategoryRule categoryRule, Set<Integer> checkedAccountIds) {
		Set<Integer> ruleAccountIds = getAllowedAccountIds(categoryRule);
		return ruleAccountIds.isEmpty() || ruleAccountIds.stream().anyMatch(checkedAccountIds::contains);
	}

	private Set<Integer> getAllowedAccountIds(CategoryRule categoryRule) {
		if (categoryRule.getBankAccountList() == null || categoryRule.getBankAccountList().isEmpty()) {
			return Set.of();
		}

		Set<Integer> accountIds = new HashSet<>();
		for (BankAccount account : categoryRule.getBankAccountList()) {
			if (account != null && account.getId() > 0) {
				accountIds.add(account.getId());
			}
		}
		return accountIds;
	}

}
