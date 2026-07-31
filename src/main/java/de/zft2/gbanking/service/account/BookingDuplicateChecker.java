package de.zft2.gbanking.service.account;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.Source;

final class BookingDuplicateChecker {

	private static final Set<Source> IMPORT_ONLINE_SOURCES = EnumSet.of(Source.ONLINE, Source.ONLINE_NEW, Source.IMPORT,
			Source.IMPORT_NEW, Source.IMPORT_INITIAL, Source.IMPORT_INITIAL_NEW);
	private static final Set<Source> MANUAL_SOURCES = EnumSet.of(Source.MANUELL, Source.MANUELL_NEW);
	private static final Set<String> GENERIC_BANK_REFERENCES = Set.of("NONREF", "NOTPROVIDED", "EREF+NONREF");
	private static final Pattern MONEYPLEX_REFERENCE_VERSION_SUFFIX = Pattern.compile("[ \\t]{2,}V\\d{5}$",
			Pattern.CASE_INSENSITIVE);

	boolean isDuplicate(Booking newBooking, List<Booking> existingBookings, List<Booking> processedIncomingBookings) {
		if (hasStableReferenceMatch(newBooking, existingBookings)
				|| hasStableReferenceMatch(newBooking, processedIncomingBookings)) {
			return true;
		}

		int existingFingerprintMatches = countFingerprintMatches(newBooking, existingBookings);
		if (existingFingerprintMatches == 0) {
			return false;
		}
		return countFingerprintMatches(newBooking, processedIncomingBookings) < existingFingerprintMatches;
	}

	private boolean hasStableReferenceMatch(Booking newBooking, List<Booking> existingBookings) {
		return existingBookings.stream().anyMatch(existingBooking -> IMPORT_ONLINE_SOURCES.contains(existingBooking.getSource())
				&& hasSameStableReference(newBooking, existingBooking));
	}

	private int countFingerprintMatches(Booking newBooking, List<Booking> existingBookings) {
		int count = 0;
		for (Booking existingBooking : existingBookings) {
			if (hasFingerprintDuplicateMatch(newBooking, existingBooking)) {
				count++;
			}
		}
		return count;
	}

	private boolean hasFingerprintDuplicateMatch(Booking newBooking, Booking existingBooking) {
		Source existingSource = existingBooking.getSource();
		if (IMPORT_ONLINE_SOURCES.contains(existingSource)) {
			return hasSameBookingFingerprint(newBooking, existingBooking)
					&& hasNoConflictingRecipients(newBooking.getRecipient(), existingBooking.getRecipient());
		}
		if (MANUAL_SOURCES.contains(existingSource)) {
			return hasSameBookingFingerprint(newBooking, existingBooking)
					&& hasMatchingRecipient(newBooking.getRecipient(), existingBooking.getRecipient());
		}
		return false;
	}

	private boolean hasSameStableReference(Booking newBooking, Booking existingBooking) {
		return sameAmount(newBooking.getAmount(), existingBooking.getAmount())
				&& hasCompatibleBookingDate(newBooking, existingBooking)
				&& (sameMeaningfulReference(getInstituteReference(newBooking), getInstituteReference(existingBooking))
						|| sameMeaningfulReference(newBooking.getSepaEndToEnd(), existingBooking.getSepaEndToEnd()));
	}

	private String getInstituteReference(Booking booking) {
		BookingAdditionalDetails details = booking.getAdditionalDetails();
		return details != null ? details.getInstref() : null;
	}

	private boolean hasSameBookingFingerprint(Booking newBooking, Booking existingBooking) {
		return newBooking.getAccountId() == existingBooking.getAccountId()
				&& sameAmount(newBooking.getAmount(), existingBooking.getAmount())
				&& sameDate(newBooking.getDateBooking(), existingBooking.getDateBooking())
				&& sameOptionalDate(newBooking.getDateValue(), existingBooking.getDateValue())
				&& samePurpose(newBooking.getPurpose(), existingBooking.getPurpose())
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

	private boolean samePurpose(String left, String right) {
		String normalizedLeft = normalizePurposeForFingerprint(left);
		String normalizedRight = normalizePurposeForFingerprint(right);
		if (normalizedLeft == null || normalizedRight == null) {
			return normalizedLeft == null && normalizedRight == null;
		}
		return normalizedLeft.equalsIgnoreCase(normalizedRight);
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

	private boolean sameAmount(BigDecimal left, BigDecimal right) {
		return normalizeAmount(left).compareTo(normalizeAmount(right)) == 0;
	}

	private BigDecimal normalizeAmount(BigDecimal amount) {
		return amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
	}
}
