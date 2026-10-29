package de.zft2.gbanking.gui.panel.transaction;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingForeignCurrencyDetails;
import de.zft2.gbanking.db.dao.BookingNoteDetails;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.util.Ranges;

final class TransactionFilter {

	static final int UNCATEGORIZED_ID = -1;

	private static final Pattern GERMAN_GROUPED_AMOUNT = Pattern.compile("[-+]?\\d{1,3}(\\.\\d{3})+");
	private static final String NUMERIC_SEARCH_CHARACTERS = "0123456789.,+-";

	private TransactionFilter() {
	}

	static boolean matches(Booking booking, Criteria criteria) {
		if (booking == null) {
			return false;
		}
		Criteria effectiveCriteria = criteria != null ? criteria : Criteria.empty();
		return matchesTextSearch(booking, effectiveCriteria.searchText()) && matchesDateRange(booking, effectiveCriteria)
				&& matchesAmountRange(booking, effectiveCriteria) && matchesCategory(booking, effectiveCriteria.categoryId())
				&& matchesBookingState(booking, effectiveCriteria.bookingState());
	}

	static BigDecimal parseGermanAmount(String value) {
		String normalized = trimToNull(value);
		if (normalized == null) {
			return null;
		}
		String compact = normalized.replace(" ", "").replace("\u00a0", "");
		if (compact.contains(",")) {
			return new BigDecimal(compact.replace(".", "").replace(',', '.'));
		}
		if (GERMAN_GROUPED_AMOUNT.matcher(compact).matches()) {
			return new BigDecimal(compact.replace(".", ""));
		}
		return new BigDecimal(compact);
	}

	static String formatGermanAmount(BigDecimal amount) {
		if (amount == null) {
			return "";
		}
		// Formatted manually: runs per booking while filtering, DecimalFormat is costly to create and not thread-safe.
		String plain = amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
		StringBuilder formatted = new StringBuilder(plain.replace('.', ','));
		int firstDigit = plain.startsWith("-") ? 1 : 0;
		for (int groupStart = plain.length() - 6; groupStart > firstDigit; groupStart -= 3) {
			formatted.insert(groupStart, '.');
		}
		return formatted.toString();
	}

	private static boolean matchesTextSearch(Booking booking, String filter) {
		if (filter.isBlank()) {
			return true;
		}
		String categoryName = booking.getCategory() != null ? booking.getCategory().getFullName() : null;
		String sourceDescription = booking.getSource() != null ? booking.getSource().toString() : null;
		String sourceSymbol = booking.getSource() != null ? booking.getSource().getSymbol() : null;
		BookingNoteDetails noteDetails = booking.getNoteDetails();
		String note = noteDetails != null ? noteDetails.getNote() : null;
		BookingForeignCurrencyDetails foreign = booking.getForeignCurrencyDetails();
		String foreignCurrency = foreign != null && foreign.getForeignCurrency() != null ? foreign.getForeignCurrency().name() : null;
		return containsAny(filter, booking.getPurpose(), note, categoryName, foreignCurrency, booking.getCrossAccountName(), booking.getAccountName(),
				sourceDescription, sourceSymbol) || matchesRecipientSearch(booking.getRecipient(), filter) || matchesNumericSearch(booking, filter);
	}

	private static boolean matchesNumericSearch(Booking booking, String filter) {
		return isNumericSearch(filter) && (matchesAmountSearch(booking.getAmount(), filter) || matchesDateSearch(booking.getDateBooking(), filter)
				|| matchesDateSearch(booking.getDateValue(), filter) || matchesDateSearch(booking.getDate(), filter));
	}

	// Amounts and dates consist of digits and separators only, a search with other characters can never match them.
	private static boolean isNumericSearch(String filter) {
		for (int i = 0; i < filter.length(); i++) {
			if (NUMERIC_SEARCH_CHARACTERS.indexOf(filter.charAt(i)) < 0) {
				return false;
			}
		}
		return true;
	}

	private static boolean matchesRecipientSearch(Recipient recipient, String filter) {
		return recipient != null && containsAny(filter, recipient.getName(), recipient.getIban(), recipient.getAccountNumber(), recipient.getBic(),
				recipient.getBlz(), recipient.getBank(), recipient.getNote());
	}

	private static boolean matchesAmountSearch(BigDecimal amount, String filter) {
		if (amount == null) {
			return false;
		}
		String plain = amount.toPlainString();
		String normalizedPlain = amount.stripTrailingZeros().toPlainString();
		return containsAny(filter, plain, normalizedPlain, plain.replace('.', ','), normalizedPlain.replace('.', ','), formatGermanAmount(amount));
	}

	private static boolean matchesDateSearch(LocalDate date, String filter) {
		return date != null && containsAny(filter, date.toString(), DateFormatUtils.formatShort(date), DateFormatUtils.formatLong(date));
	}

	private static boolean matchesDateRange(Booking booking, Criteria criteria) {
		LocalDate bookingDate = booking.getDateBooking() != null ? booking.getDateBooking() : booking.getDate();
		if (criteria.dateFrom() != null && (bookingDate == null || bookingDate.isBefore(criteria.dateFrom()))) {
			return false;
		}
		return criteria.dateTo() == null || bookingDate != null && !bookingDate.isAfter(criteria.dateTo());
	}

	private static boolean matchesAmountRange(Booking booking, Criteria criteria) {
		BigDecimal amount = booking.getAmount();
		if (criteria.amountFrom() != null && (amount == null || amount.compareTo(criteria.amountFrom()) < 0)) {
			return false;
		}
		return criteria.amountTo() == null || amount != null && amount.compareTo(criteria.amountTo()) <= 0;
	}

	private static boolean matchesCategory(Booking booking, Integer categoryId) {
		if (categoryId == null) {
			return true;
		}
		if (categoryId == UNCATEGORIZED_ID) {
			return booking.getCategory() == null;
		}
		return booking.getCategory() != null && booking.getCategory().getId() == categoryId;
	}

	private static boolean matchesBookingState(Booking booking, BookingState bookingState) {
		boolean prenotification = booking.getSource() != null && booking.getSource().isPrenotification();
		return switch (bookingState) {
		case ALL -> true;
		case BOOKED -> !prenotification;
		case PRENOTIFICATION -> prenotification;
		};
	}

	private static boolean containsAny(String filter, String... values) {
		for (String value : values) {
			if (value != null && normalize(value).contains(filter)) {
				return true;
			}
		}
		return false;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	enum BookingState {
		ALL,
		BOOKED,
		PRENOTIFICATION
	}

	record Criteria(String searchText, LocalDate dateFrom, LocalDate dateTo, BigDecimal amountFrom, BigDecimal amountTo, Integer categoryId,
			BookingState bookingState) {

		Criteria {
			searchText = normalize(searchText);
			bookingState = bookingState != null ? bookingState : BookingState.ALL;
			// Reversed ranges are swapped, consistent with the turnover analysis.
			if (Ranges.isReversed(dateFrom, dateTo)) {
				LocalDate from = dateFrom;
				dateFrom = dateTo;
				dateTo = from;
			}
			if (Ranges.isReversed(amountFrom, amountTo)) {
				BigDecimal from = amountFrom;
				amountFrom = amountTo;
				amountTo = from;
			}
		}

		static Criteria empty() {
			return new Criteria("", null, null, null, null, null, BookingState.ALL);
		}
	}
}
