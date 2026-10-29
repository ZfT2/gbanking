package de.zft2.gbanking.service.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.util.Amounts;

public final class BookingDuplicateChecker {

	private static final Set<Source> IMPORT_ONLINE_SOURCES = EnumSet.of(Source.ONLINE, Source.ONLINE_NEW, Source.IMPORT,
			Source.IMPORT_NEW, Source.IMPORT_INITIAL, Source.IMPORT_INITIAL_NEW);
	private static final Set<Source> MANUAL_SOURCES = EnumSet.of(Source.MANUELL, Source.MANUELL_NEW);
	private static final Set<String> GENERIC_BANK_REFERENCES = Set.of("NONREF", "NOTPROVIDED", "EREF+NONREF");
	private static final Pattern MONEYPLEX_REFERENCE_VERSION_SUFFIX = Pattern.compile("[ \\t]{2,}V\\d{5}$",
			Pattern.CASE_INSENSITIVE);
	/** Moneyplex reference suffix of a single purpose line, also on a line that only holds the suffix. */
	private static final Pattern MONEYPLEX_PURPOSE_LINE_SUFFIX = Pattern.compile("(?:[ \\t]{2,}|\\A)V\\d{5}$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");
	private static final String NORMALIZED_PURPOSE_SEPARATOR = " ";

	boolean isDuplicate(Booking newBooking, Candidates existingBookings, Candidates processedIncomingBookings) {
		return findDuplicate(newBooking, existingBookings, processedIncomingBookings) != null;
	}

	public Booking findDuplicate(Booking newBooking, Candidates existingBookings, Candidates processedIncomingBookings) {
		if (newBooking == null) {
			return null;
		}
		List<Booking> existing = existingBookings.withSameAmount(newBooking);
		List<Booking> processed = processedIncomingBookings.withSameAmount(newBooking);
		Booking stableReferenceMatch = findStableReferenceMatch(newBooking, existing);
		if (stableReferenceMatch != null) {
			return stableReferenceMatch;
		}
		stableReferenceMatch = findStableReferenceMatch(newBooking, processed);
		return stableReferenceMatch != null ? stableReferenceMatch
				: findFingerprintMatch(newBooking, existing, countFingerprintMatches(newBooking, processed));
	}

	private Booking findStableReferenceMatch(Booking newBooking, Collection<Booking> existingBookings) {
		for (Booking existingBooking : existingBookings) {
			if (isStableReferenceDuplicate(newBooking, existingBooking)) {
				return existingBooking;
			}
		}
		return null;
	}

	private boolean isStableReferenceDuplicate(Booking newBooking, Booking existingBooking) {
		return IMPORT_ONLINE_SOURCES.contains(existingBooking.getSource()) && hasSameStableReference(newBooking, existingBooking);
	}

	private int countFingerprintMatches(Booking newBooking, Collection<Booking> existingBookings) {
		int count = 0;
		for (Booking existingBooking : existingBookings) {
			if (hasFingerprintDuplicateMatch(newBooking, existingBooking)) {
				count++;
			}
		}
		return count;
	}

	private Booking findFingerprintMatch(Booking newBooking, Collection<Booking> existingBookings, int matchesToSkip) {
		for (Booking existingBooking : existingBookings) {
			if (hasFingerprintDuplicateMatch(newBooking, existingBooking)) {
				if (matchesToSkip == 0) {
					return existingBooking;
				}
				matchesToSkip--;
			}
		}
		return null;
	}

	private boolean hasFingerprintDuplicateMatch(Booking newBooking, Booking existingBooking) {
		Source existingSource = existingBooking.getSource();
		if (IMPORT_ONLINE_SOURCES.contains(existingSource)) {
			return hasSameBookingFingerprint(newBooking, existingBooking, purposeComparator(newBooking, existingBooking))
					&& hasNoConflictingRecipients(newBooking.getRecipient(), existingBooking.getRecipient());
		}
		if (MANUAL_SOURCES.contains(existingSource)) {
			return hasSameBookingFingerprint(newBooking, existingBooking, BookingDuplicateChecker::hasEqualPurpose)
					&& hasMatchingRecipient(newBooking.getRecipient(), existingBooking.getRecipient());
		}
		return false;
	}

	/**
	 * Returns the purpose comparison for the given pair of bookings. The tolerant comparison is only
	 * used between imported and online bookings because only there the same transaction can be stored
	 * with a different level of detail.
	 *
	 * @param newBooking     the booking that is checked
	 * @param existingBooking the booking that is already stored
	 * @return the comparison of the two purposes
	 */
	private static BiPredicate<String, String> purposeComparator(Booking newBooking, Booking existingBooking) {
		boolean bothImportOrOnline = IMPORT_ONLINE_SOURCES.contains(newBooking.getSource())
				&& IMPORT_ONLINE_SOURCES.contains(existingBooking.getSource());
		return bothImportOrOnline ? BookingDuplicateChecker::hasSamePurposeIncludingDetails
				: BookingDuplicateChecker::hasEqualPurpose;
	}

	private boolean hasSameStableReference(Booking newBooking, Booking existingBooking) {
		return Amounts.sameRoundedAmount(newBooking.getAmount(), existingBooking.getAmount())
				&& hasCompatibleBookingDate(newBooking, existingBooking)
				&& (sameMeaningfulReference(getInstituteReference(newBooking), getInstituteReference(existingBooking))
						|| sameMeaningfulReference(newBooking.getSepaEndToEnd(), existingBooking.getSepaEndToEnd()));
	}

	private String getInstituteReference(Booking booking) {
		BookingAdditionalDetails details = booking.getAdditionalDetails();
		return details != null ? details.getInstref() : null;
	}

	private boolean hasSameBookingFingerprint(Booking newBooking, Booking existingBooking,
			BiPredicate<String, String> purposeComparator) {
		return newBooking.getAccountId() == existingBooking.getAccountId()
				&& Amounts.sameRoundedAmount(newBooking.getAmount(), existingBooking.getAmount())
				&& sameDate(newBooking.getDateBooking(), existingBooking.getDateBooking())
				&& sameOptionalDate(newBooking.getDateValue(), existingBooking.getDateValue())
				&& purposeComparator.test(newBooking.getPurpose(), existingBooking.getPurpose())
				&& sameOptionalInteger(newBooking.getCrossAccountId(), existingBooking.getCrossAccountId());
	}

	private boolean hasCompatibleBookingDate(Booking newBooking, Booking existingBooking) {
		return sameDate(newBooking.getDateBooking(), existingBooking.getDateBooking())
				|| sameDate(newBooking.getDateValue(), existingBooking.getDateValue());
	}

	private boolean hasMatchingRecipient(Recipient newRecipient, Recipient existingRecipient) {
		return newRecipient != null && existingRecipient != null
				&& hasSharedRecipientIdentifier(newRecipient, existingRecipient)
				&& hasNoConflictingRecipientData(newRecipient, existingRecipient);
	}

	private boolean hasNoConflictingRecipients(Recipient newRecipient, Recipient existingRecipient) {
		return newRecipient == null || existingRecipient == null
				|| hasNoConflictingRecipientData(newRecipient, existingRecipient);
	}

	private boolean hasSharedRecipientIdentifier(Recipient newRecipient, Recipient existingRecipient) {
		return sameNonBlankText(newRecipient.getIban(), existingRecipient.getIban())
				|| (sameNonBlankText(newRecipient.getAccountNumber(), existingRecipient.getAccountNumber())
						&& (sameNonBlankText(newRecipient.getBlz(), existingRecipient.getBlz())
								|| sameNonBlankText(newRecipient.getBic(), existingRecipient.getBic())));
	}

	private boolean hasNoConflictingRecipientData(Recipient newRecipient, Recipient existingRecipient) {
		return hasNoTextConflict(newRecipient.getName(), existingRecipient.getName())
				&& hasNoTextConflict(newRecipient.getIban(), existingRecipient.getIban())
				&& hasNoTextConflict(newRecipient.getBic(), existingRecipient.getBic())
				&& hasNoTextConflict(newRecipient.getAccountNumber(), existingRecipient.getAccountNumber())
				&& hasNoTextConflict(newRecipient.getBlz(), existingRecipient.getBlz());
	}

	private boolean sameDate(LocalDate left, LocalDate right) {
		return left != null && left.equals(right);
	}

	private boolean sameOptionalDate(LocalDate left, LocalDate right) {
		return left == null || right == null || left.equals(right);
	}

	private boolean sameOptionalInteger(Integer left, Integer right) {
		return left == null || right == null || left.equals(right);
	}

	/**
	 * Compares the purposes of two bookings. The comparison ignores line breaks and the case because an
	 * imported booking usually stores the purpose in a single line while the online booking of the same
	 * transaction contains the same text in several lines.
	 *
	 * @param left  the purpose of the first booking
	 * @param right the purpose of the second booking
	 * @return true if both purposes describe the same text
	 */
	private static boolean hasEqualPurpose(String left, String right) {
		return hasEqualFlattenedPurpose(left, right, NORMALIZED_PURPOSE_SEPARATOR)
				|| hasEqualFlattenedPurpose(left, right, "");
	}

	private static boolean hasEqualFlattenedPurpose(String left, String right, String lineSeparator) {
		String flattenedLeft = flattenPurpose(left, lineSeparator);
		String flattenedRight = flattenPurpose(right, lineSeparator);
		if (flattenedLeft.isEmpty() || flattenedRight.isEmpty()) {
			return flattenedLeft.isEmpty() && flattenedRight.isEmpty();
		}
		return flattenedLeft.equalsIgnoreCase(flattenedRight);
	}

	/**
	 * Compares the purposes of an imported and an online booking. The imported booking contains only the
	 * core of the purpose while the online booking contains the same core plus additional detail lines of
	 * the bank, for example the camt usage lines. Both bookings describe the same transaction if the
	 * shorter purpose is contained in the longer one.
	 *
	 * @param left  the purpose of the first booking
	 * @param right the purpose of the second booking
	 * @return true if both purposes describe the same transaction
	 */
	private static boolean hasSamePurposeIncludingDetails(String left, String right) {
		return hasSameFlattenedPurposeIncludingDetails(left, right, NORMALIZED_PURPOSE_SEPARATOR)
				|| hasSameFlattenedPurposeIncludingDetails(left, right, "");
	}

	private static boolean hasSameFlattenedPurposeIncludingDetails(String left, String right, String lineSeparator) {
		String flattenedLeft = flattenPurpose(left, lineSeparator);
		String flattenedRight = flattenPurpose(right, lineSeparator);
		if (flattenedLeft.isEmpty() || flattenedRight.isEmpty()) {
			return flattenedLeft.isEmpty() && flattenedRight.isEmpty();
		}
		if (flattenedLeft.length() <= flattenedRight.length()) {
			return containsDelimited(flattenedRight, flattenedLeft);
		}
		return containsDelimited(flattenedLeft, flattenedRight);
	}

	/**
	 * Returns the purpose as a single line without line breaks, without repeated whitespace and without
	 * the moneyplex reference suffix of the single lines. The given separator replaces a line break.
	 *
	 * @param purpose       the purpose of a booking
	 * @param lineSeparator the replacement for line breaks
	 * @return the flattened purpose, empty if there is no purpose
	 */
	private static String flattenPurpose(String purpose, String lineSeparator) {
		String normalized = normalizeText(purpose);
		if (normalized == null) {
			return "";
		}
		StringBuilder flattened = new StringBuilder();
		for (String line : normalized.split("\n")) {
			String cleanedLine = MONEYPLEX_PURPOSE_LINE_SUFFIX.matcher(line.trim()).replaceFirst("").trim();
			cleanedLine = WHITESPACE_RUN.matcher(cleanedLine).replaceAll(NORMALIZED_PURPOSE_SEPARATOR).trim();
			if (cleanedLine.isEmpty()) {
				continue;
			}
			if (flattened.length() > 0) {
				flattened.append(lineSeparator);
			}
			flattened.append(cleanedLine);
		}
		return flattened.toString();
	}

	/**
	 * Checks whether the shorter purpose is contained in the longer one. The match must be delimited by a
	 * non alphanumeric character so that a purpose is not matched inside a word.
	 *
	 * @param longer  the flattened purpose of the booking with more text
	 * @param shorter the flattened purpose of the booking with less text
	 * @return true if the shorter purpose is contained in the longer one
	 */
	private static boolean containsDelimited(String longer, String shorter) {
		String haystack = longer.toLowerCase(Locale.ROOT);
		String needle = shorter.toLowerCase(Locale.ROOT);
		int index = haystack.indexOf(needle);
		while (index >= 0) {
			int end = index + needle.length();
			if (isDelimiting(haystack, index - 1) && isDelimiting(haystack, end)) {
				return true;
			}
			index = haystack.indexOf(needle, index + 1);
		}
		return false;
	}

	private static boolean isDelimiting(String text, int index) {
		return index < 0 || index >= text.length() || !Character.isLetterOrDigit(text.charAt(index));
	}

	private boolean sameNonBlankText(String left, String right) {
		String normalizedLeft = normalizeText(left);
		String normalizedRight = normalizeText(right);
		return normalizedLeft != null && normalizedLeft.equalsIgnoreCase(normalizedRight);
	}

	private boolean hasNoTextConflict(String left, String right) {
		String normalizedLeft = normalizeText(left);
		String normalizedRight = normalizeText(right);
		return normalizedLeft == null || normalizedRight == null || normalizedLeft.equalsIgnoreCase(normalizedRight);
	}

	private boolean sameMeaningfulReference(String left, String right) {
		String normalizedLeft = normalizeReference(left);
		String normalizedRight = normalizeReference(right);
		return normalizedLeft != null && normalizedLeft.equals(normalizedRight);
	}

	static String normalizeReference(String value) {
		String normalizedValue = normalizeText(value);
		if (normalizedValue == null) {
			return null;
		}
		String upperCaseValue = normalizedValue.toUpperCase(Locale.ROOT);
		return GENERIC_BANK_REFERENCES.contains(upperCaseValue) ? null : upperCaseValue;
	}

	static String normalizeText(String value) {
		if (value == null) {
			return null;
		}
		String normalizedValue = value.replace("\r\n", "\n").replace('\r', '\n').trim();
		return normalizedValue.isEmpty() ? null : normalizedValue;
	}

	static String normalizePurposeForFingerprint(String value) {
		String normalizedValue = normalizeText(value);
		if (normalizedValue == null) {
			return null;
		}
		String normalizedPurpose = MONEYPLEX_REFERENCE_VERSION_SUFFIX.matcher(normalizedValue).replaceFirst("").trim();
		return normalizedPurpose.isEmpty() ? null : normalizedPurpose;
	}

	/**
	 * Bookings grouped by their rounded amount. Every duplicate criterion requires the same rounded amount, so a
	 * booking only has to be compared with the bookings of its own group. The order is kept within a group because
	 * the multiplicity check depends on it.
	 */
	public static final class Candidates {
		// compareTo based keys, so a missing amount and 0.00 share a group like in Amounts.sameRoundedAmount
		private final Map<BigDecimal, List<Booking>> bookingsByAmount = new TreeMap<>();
		private int size;

		public Candidates(Collection<Booking> bookings) {
			bookings.forEach(booking -> add(booking));
		}

		public void add(Booking booking) {
			bookingsByAmount.computeIfAbsent(Amounts.normalizeAmount(booking.getAmount()), amount -> new ArrayList<>()).add(booking);
			size++;
		}

		public List<Booking> withSameAmount(Booking booking) {
			return bookingsByAmount.getOrDefault(Amounts.normalizeAmount(booking.getAmount()), List.of());
		}

		public int size() {
			return size;
		}
	}
}
