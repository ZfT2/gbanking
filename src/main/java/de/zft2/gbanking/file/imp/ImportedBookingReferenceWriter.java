package de.zft2.gbanking.file.imp;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.Recipient;

final class ImportedBookingReferenceWriter {

	private final DBController dbController;

	ImportedBookingReferenceWriter(DBController dbController) {
		this.dbController = dbController;
	}

	void writeRecipients(Collection<Booking> bookings) {
		List<Booking> recipientBookings = bookings.stream()
				.filter(booking -> booking.getRecipient() != null && booking.getRecipientId() <= 0)
				.toList();
		assignBankNamesByValidity(recipientBookings);
		Map<RecipientIdentityKey, RecipientReferenceGroup> recipientGroups = new LinkedHashMap<>();

		for (Booking booking : recipientBookings) {
			Recipient recipient = booking.getRecipient();
			recipientGroups.computeIfAbsent(RecipientIdentityKey.from(recipient), ignored -> new RecipientReferenceGroup(recipient)).add(booking);
		}

		for (RecipientReferenceGroup recipientGroup : recipientGroups.values()) {
			Recipient recipient = dbController.resolveRecipient(recipientGroup.recipient());
			if (recipient == null) {
				continue;
			}

			recipientGroup.bookings().forEach(booking -> booking.setRecipient(recipient));
			dbController.updateBookingsWithRecipients(Map.of(recipient, recipientGroup.bookingIds()));
		}
	}

	private static void assignBankNamesByValidity(List<Booking> bookings) {
		Map<RecipientIdentityKey, List<Booking>> timelines = new LinkedHashMap<>();
		for (Booking booking : bookings) {
			timelines.computeIfAbsent(RecipientIdentityKey.from(booking.getRecipient()).withoutBank(), ignored -> new ArrayList<>()).add(booking);
		}

		for (List<Booking> timeline : timelines.values()) {
			timeline.sort(Comparator.comparing((Booking booking) -> booking.getDate(),
					Comparator.nullsLast(Comparator.naturalOrder())));
			assignBankNamesByValidityForTimeline(timeline);
		}
	}

	private static void assignBankNamesByValidityForTimeline(List<Booking> timeline) {
		String currentBankName = timeline.stream()
				.map(booking -> booking.getRecipient().getBank())
				.filter(bankName -> trimToNull(bankName) != null)
				.findFirst()
				.orElse(null);
		if (currentBankName == null) {
			return;
		}

		for (Booking booking : timeline) {
			Recipient recipient = booking.getRecipient();
			if (trimToNull(recipient.getBank()) != null) {
				currentBankName = recipient.getBank();
			} else {
				recipient.setBank(currentBankName);
			}
		}
	}

	void writeCategories(Collection<Booking> bookings) {
		Map<Category, Set<Integer>> categoryBookingMap = new HashMap<>();

		for (Booking booking : bookings) {
			Category category = booking.getCategory();
			if (category != null) {
				categoryBookingMap.computeIfAbsent(category, ignored -> new HashSet<>()).add(booking.getId());
			}
		}

		dbController.insertAll(categoryBookingMap.keySet());
		dbController.updateBookingsWithCategories(categoryBookingMap);
	}

	private record RecipientIdentityKey(String name, String iban, String bic, String accountNumber, String blz, String bank) {

		private static RecipientIdentityKey from(Recipient recipient) {
			return new RecipientIdentityKey(caseInsensitiveValue(recipient.getName()), caseInsensitiveValue(recipient.getIban()),
					caseInsensitiveValue(recipient.getBic()), trimToNull(recipient.getAccountNumber()), trimToNull(recipient.getBlz()),
					caseInsensitiveValue(recipient.getBank()));
		}

		private RecipientIdentityKey withoutBank() {
			return new RecipientIdentityKey(name, iban, bic, accountNumber, blz, null);
		}
	}

	private static final class RecipientReferenceGroup {

		private final Recipient recipient;
		private final List<Booking> bookings = new ArrayList<>();
		private final Set<Integer> bookingIds = new HashSet<>();

		private RecipientReferenceGroup(Recipient recipient) {
			this.recipient = recipient;
		}

		private void add(Booking booking) {
			Recipient incomingRecipient = booking.getRecipient();
			if (hasBetterReadability(incomingRecipient.getName(), recipient.getName())) {
				recipient.setName(incomingRecipient.getName());
			}
			if (hasBetterReadability(incomingRecipient.getBank(), recipient.getBank())) {
				recipient.setBank(incomingRecipient.getBank());
			}
			if (incomingRecipient.getNote() != null) {
				recipient.setNote(incomingRecipient.getNote());
			}
			bookings.add(booking);
			bookingIds.add(booking.getId());
		}

		private Recipient recipient() {
			return recipient;
		}

		private List<Booking> bookings() {
			return bookings;
		}

		private Set<Integer> bookingIds() {
			return bookingIds;
		}

		private static boolean hasBetterReadability(String candidateText, String currentText) {
			return readabilityScore(candidateText) > readabilityScore(currentText);
		}

		private static int readabilityScore(String value) {
			boolean upperCaseLetter = false;
			boolean lowerCaseLetter = false;
			if (value != null) {
				for (int index = 0; index < value.length(); index++) {
					char character = value.charAt(index);
					upperCaseLetter |= Character.isUpperCase(character);
					lowerCaseLetter |= Character.isLowerCase(character);
				}
			}
			int lower = lowerCaseLetter ? 1 : 0;
			return upperCaseLetter && lowerCaseLetter ? 2 : lower;
		}
	}



	private static String caseInsensitiveValue(String value) {
		String normalizedValue = trimToNull(value);
		return normalizedValue != null ? normalizedValue.toLowerCase(Locale.ROOT) : null;
	}

}
