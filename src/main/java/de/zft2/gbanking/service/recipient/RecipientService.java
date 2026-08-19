package de.zft2.gbanking.service.recipient;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.service.AbstractDbService;

public class RecipientService extends AbstractDbService {

	private static final Logger log = LogManager.getLogger(RecipientService.class);

	public Recipient saveRecipientToDB(Recipient recipient) {
		Recipient resolvedRecipient = dbController.resolveRecipient(recipient);
		synchronizeRecipientDefault(recipient, resolvedRecipient);
		log.info("saveRecipientToDB(): using Recipient with id: {}", resolvedRecipient.getId());
		return resolvedRecipient;
	}

	public Recipient findDefaultRecipientForSameAccountIdentifier(Recipient recipient) {
		List<Recipient> matchingDefaults = getDefaultRecipientsForSameAccountIdentifier(recipient);
		return matchingDefaults.isEmpty() ? null : matchingDefaults.get(0);
	}

	private void synchronizeRecipientDefault(Recipient requestedRecipient, Recipient resolvedRecipient) {
		if (requestedRecipient == null || resolvedRecipient == null || resolvedRecipient.getId() <= 0) {
			return;
		}

		boolean defaultRequested = requestedRecipient.isDefault();
		if (defaultRequested) {
			clearOtherDefaultRecipients(resolvedRecipient);
		}
		if (resolvedRecipient.isDefault() != defaultRequested) {
			dbController.updateRecipientDefault(resolvedRecipient.getId(), defaultRequested);
			resolvedRecipient.setDefault(defaultRequested);
		}
	}

	private void clearOtherDefaultRecipients(Recipient recipient) {
		for (Recipient defaultRecipient : getDefaultRecipientsForSameAccountIdentifier(recipient)) {
			if (defaultRecipient.getId() != recipient.getId()) {
				dbController.updateRecipientDefault(defaultRecipient.getId(), false);
			}
		}
	}

	private List<Recipient> getDefaultRecipientsForSameAccountIdentifier(Recipient recipient) {
		RecipientAccountIdentifier identifier = getRecipientAccountIdentifier(recipient);
		if (identifier == null) {
			return List.of();
		}

		int recipientId = recipient.getId();
		return dbController.getAll(Recipient.class).stream().filter(Recipient::isDefault).filter(candidate -> candidate.getId() != recipientId)
				.filter(identifier::matches).toList();
	}

	private RecipientAccountIdentifier getRecipientAccountIdentifier(Recipient recipient) {
		if (recipient == null) {
			return null;
		}

		String iban = normalizeIban(recipient.getIban());
		if (iban != null) {
			return new RecipientAccountIdentifier(iban, true);
		}

		String accountNumber = trimToNull(recipient.getAccountNumber());
		return accountNumber != null ? new RecipientAccountIdentifier(accountNumber, false) : null;
	}

	private static String normalizeIban(String value) {
		String normalizedValue = trimToNull(value);
		return normalizedValue != null ? normalizedValue.toUpperCase(Locale.ROOT) : null;
	}

	private record RecipientAccountIdentifier(String value, boolean iban) {

		private boolean matches(Recipient recipient) {
			if (iban) {
				return value.equals(normalizeIban(recipient.getIban()));
			}
			return value.equals(trimToNull(recipient.getAccountNumber()));
		}
	}

	public void deleteRecipientFromDB(Recipient recipient) {
		dbController.delete(recipient, null);
	}

	public boolean isRecipientEditable(Recipient recipient) {
		return dbController.isRecipientEditable(recipient);
	}

	public boolean isRecipientDeletable(Recipient recipient) {
		return dbController.isRecipientDeletable(recipient);
	}

}
