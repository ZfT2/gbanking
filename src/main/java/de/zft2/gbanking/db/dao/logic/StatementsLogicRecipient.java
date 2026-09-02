package de.zft2.gbanking.db.dao.logic;

import java.util.List;
import java.util.Objects;

import de.zft2.gbanking.cache.InstituteLookupCache;
import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.SourceGroup;

public class StatementsLogicRecipient extends StatementsLogicDefault<Recipient> implements StatementsLogic<Recipient> {
	
	@Override
	public SqlParameter getSqlParameter(Recipient rp) {
		return new SqlParameter(String.valueOf(rp.getId()),rp.getNote());
	}

	@Override
	public Recipient insertOrUpdateSingle(Recipient recipient) {
		if (recipient.getId() > 0 && isEditable(recipient)) {
			completeMissingBankName(recipient);
			return executeInsertUpdateStatement(StatementType.UPDATE, recipient);
		}

		boolean indexedAccountIdentifierLookup = canUseIndexedAccountIdentifierLookup(recipient);
		List<Recipient> matchingCandidates = findMatchingRecipientCandidates(recipient, indexedAccountIdentifierLookup);

		Recipient matchingRecipient = findMatchingRecipient(recipient, matchingCandidates);
		if (matchingRecipient != null) {
			return updateMatchingRecipient(matchingRecipient, recipient);
		}

		Recipient unreferencedRecipient = findUnreferencedRecipient(recipient, matchingCandidates, indexedAccountIdentifierLookup);
		if (unreferencedRecipient != null) {
			return updateUnreferencedRecipient(unreferencedRecipient, recipient);
		}

		completeMissingBankName(recipient);
		recipient.setId(0);
		return executeInsertUpdateStatement(StatementType.INSERT, recipient);
	}

	private Recipient findMatchingRecipient(Recipient recipient, List<Recipient> candidates) {
		Recipient matchingRecipient = null;
		for (Recipient existingRecipient : candidates) {
			if (!matchesRecipient(existingRecipient, recipient)) {
				continue;
			}
			if (matchingRecipient == null || readabilityScore(existingRecipient) > readabilityScore(matchingRecipient)) {
				matchingRecipient = existingRecipient;
			}
		}
		return matchingRecipient;
	}

	private boolean matchesRecipient(Recipient existingRecipient, Recipient incomingRecipient) {
		return existingRecipient.equals(incomingRecipient)
				|| (isImportRecipient(incomingRecipient) && existingRecipient.hasSameDataWithCompatibleBank(incomingRecipient));
	}

	private Recipient findUnreferencedRecipient(Recipient recipient, List<Recipient> matchingCandidates, boolean indexedAccountIdentifierLookup) {
		if (!recipient.hasAccountIdentifier()) {
			return null;
		}
		if (indexedAccountIdentifierLookup && matchingCandidates.isEmpty()) {
			return null;
		}

		List<Recipient> candidates = indexedAccountIdentifierLookup
				? findRecipientsByAccountIdentifier(DaoSqlStatements.SQL_SELECT_UNREFERENCED_RECIPIENT_CANDIDATES_BY_ACCOUNT_IDENTIFIER, recipient)
				: matchingCandidates;
		for (Recipient existingRecipient : candidates) {
			if (existingRecipient.hasSameAccountIdentifier(recipient) && (indexedAccountIdentifierLookup || isUnreferenced(existingRecipient))) {
				return existingRecipient;
			}
		}
		return null;
	}

	private List<Recipient> findMatchingRecipientCandidates(Recipient recipient, boolean indexedAccountIdentifierLookup) {
		if (!indexedAccountIdentifierLookup) {
			return getAll(Recipient.class);
		}
		return findRecipientsByAccountIdentifier(DaoSqlStatements.SQL_SELECT_RECIPIENT_CANDIDATES_BY_ACCOUNT_IDENTIFIER, recipient);
	}

	private List<Recipient> findRecipientsByAccountIdentifier(String sql, Recipient recipient) {
		return executeSqlSelectStatementForList(sql, Recipient.class,
				java.util.Arrays.asList(recipient.getIban(), recipient.getIban(), recipient.getAccountNumber(), recipient.getAccountNumber()));
	}

	private boolean canUseIndexedAccountIdentifierLookup(Recipient recipient) {
		return hasText(recipient.getAccountNumber()) || hasAsciiText(recipient.getIban());
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private boolean hasAsciiText(String value) {
		if (!hasText(value)) {
			return false;
		}
		return value.trim().chars().allMatch(character -> character <= 0x7f);
	}

	private Recipient updateMatchingRecipient(Recipient existingRecipient, Recipient incomingRecipient) {
		if (!hasText(existingRecipient.getBank())) {
			completeMissingBankName(incomingRecipient);
		}
		boolean updateRequired = false;
		if (hasBetterReadability(incomingRecipient.getName(), existingRecipient.getName())) {
			existingRecipient.setName(incomingRecipient.getName());
			updateRequired = true;
		}
		if (shouldUseIncomingBank(incomingRecipient.getBank(), existingRecipient.getBank())) {
			existingRecipient.setBank(incomingRecipient.getBank());
			updateRequired = true;
		}
		if (incomingRecipient.getNote() != null && !Objects.equals(incomingRecipient.getNote(), existingRecipient.getNote())) {
			existingRecipient.setNote(incomingRecipient.getNote());
			updateRequired = true;
		}
		return updateRequired ? executeInsertUpdateStatement(StatementType.UPDATE, existingRecipient) : existingRecipient;
	}

	private Recipient updateUnreferencedRecipient(Recipient existingRecipient, Recipient incomingRecipient) {
		if (!hasText(incomingRecipient.getBank())) {
			if (hasText(existingRecipient.getBank())) {
				incomingRecipient.setBank(existingRecipient.getBank());
			} else {
				completeMissingBankName(incomingRecipient);
			}
		}
		incomingRecipient.setId(existingRecipient.getId());
		incomingRecipient.setSource(existingRecipient.getSource());
		if (incomingRecipient.getNote() == null) {
			incomingRecipient.setNote(existingRecipient.getNote());
		}
		return executeInsertUpdateStatement(StatementType.UPDATE, incomingRecipient);
	}

	private void completeMissingBankName(Recipient recipient) {
		if (!isImportRecipient(recipient) || hasText(recipient.getBank())) {
			return;
		}
		String lookupBlz = hasText(recipient.getBlz())
				? recipient.getBlz()
				: InstituteLookupCache.extractGermanBlzFromIban(recipient.getIban());
		String bankName = InstituteLookupCache.findBankNameForBankData(recipient.getBic(), lookupBlz).orElse(null);
		if (bankName != null) {
			recipient.setBank(bankName);
		}
	}

	private boolean isImportRecipient(Recipient recipient) {
		return recipient.getSource() != null && recipient.getSource().getGroup() == SourceGroup.GROUP_IMPORT;
	}

	private boolean shouldUseIncomingBank(String candidateBank, String currentBank) {
		return hasText(candidateBank) && (!hasText(currentBank) || hasBetterReadability(candidateBank, currentBank));
	}

	private boolean hasBetterReadability(String candidateText, String currentText) {
		return readabilityScore(candidateText) > readabilityScore(currentText);
	}

	private int readabilityScore(Recipient recipient) {
		return readabilityScore(recipient.getName()) + readabilityScore(recipient.getBank());
	}

	private int readabilityScore(String value) {
		boolean upperCaseLetter = false;
		boolean lowerCaseLetter = false;
		if (value != null) {
			for (int index = 0; index < value.length(); index++) {
				char character = value.charAt(index);
				upperCaseLetter |= Character.isUpperCase(character);
				lowerCaseLetter |= Character.isLowerCase(character);
			}
		}
		final int lower = lowerCaseLetter ? 1 : 0;
		return upperCaseLetter && lowerCaseLetter ? 2 : lower;
	}

	private boolean isUnreferenced(Recipient recipient) {
		return matchesSelector(recipient, DaoSqlStatements.SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED);
	}

	private boolean isEditable(Recipient recipient) {
		return matchesSelector(recipient, DaoSqlStatements.SQL_SELECT_RECIPIENT_BY_ID_IF_EDITABLE);
	}

	private boolean matchesSelector(Recipient recipient, String sql) {
		return recipient.getId() > 0
				&& executeSelectId(sql,
						List.of(sqlParameterValue(recipient.getId(), java.sql.Types.INTEGER))) > 0;
	}
}
