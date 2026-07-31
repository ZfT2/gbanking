package de.zft2.gbanking.db.dao.logic;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.util.TypeConverter;

public class StatementsLogicCategoryRule extends StatementsLogicDefault<CategoryRule> implements StatementsLogic<CategoryRule> {

	private static final int MAX_NAME_LENGTH = 40;
	private static final String RULE_PREFIX = "Regel:";

	@Override
	public CategoryRule insertOrUpdateSingle(CategoryRule categoryRule) {
		applyGeneratedNameIfBlank(categoryRule);
		CategoryRule savedRule = super.insertOrUpdateSingle(categoryRule);
		syncBankAccounts(savedRule);
		return savedRule;
	}

	@Override
	public void addOneToOneRelations(CategoryRule categoryRule) {
		categoryRule.setBankAccountList(executeSqlSelectStatementForList(
				DaoSqlStatements.SQL_SELECT_BANKACCOUNTS_BY_CATEGORYRULE, BankAccount.class, List.of(categoryRule.getId())));
	}

	private void syncBankAccounts(CategoryRule categoryRule) {
		executeSqlDeleteStatement(DaoSqlStatements.SQL_DELETE_CATEGORYRULE_BANKACCOUNT, categoryRule);

		Set<Integer> accountIds = getAccountIds(categoryRule);
		if (!accountIds.isEmpty()) {
			executeStatementList(DaoSqlStatements.SQL_INSERT_CATEGORYRULE_BANKACCOUNT, accountIds, categoryRule, MnDao.class);
		}
	}

	private Set<Integer> getAccountIds(CategoryRule categoryRule) {
		if (categoryRule.getBankAccountList() == null) {
			return Set.of();
		}
		return categoryRule.getBankAccountList().stream()
				.filter(account -> account != null && account.getId() > 0)
				.map(BankAccount::getId)
				.collect(Collectors.toCollection(HashSet::new));
	}

	private void applyGeneratedNameIfBlank(CategoryRule categoryRule) {
		if (categoryRule == null || hasText(categoryRule.getName())) {
			return;
		}

		categoryRule.setName(createUniqueGeneratedName(categoryRule));
	}

	private String createUniqueGeneratedName(CategoryRule categoryRule) {
		String generatedName = buildGeneratedName(categoryRule);
		String candidate = abbreviate(generatedName, MAX_NAME_LENGTH);
		int suffixCounter = 2;
		while (isNameUsedByAnotherRule(candidate, categoryRule.getId())) {
			String suffix = " (" + suffixCounter + ")";
			candidate = abbreviate(generatedName, MAX_NAME_LENGTH - suffix.length()) + suffix;
			suffixCounter++;
		}
		return candidate;
	}

	private String buildGeneratedName(CategoryRule categoryRule) {
		List<String> parts = new ArrayList<>();
		addDatePart(categoryRule, parts);
		addAmountPart(categoryRule, parts);
		addTextPart(parts, "Name", categoryRule.getFilterRecipientName());
		addTextPart(parts, "IBAN", categoryRule.getFilterRecipientIban());
		addTextPart(parts, "Kto.", categoryRule.getFilterRecipientAccountNumber());
		addTextPart(parts, "Zweck", categoryRule.getFilterPurpose());
		if (categoryRule.getCategory() != null) {
			addTextPart(parts, "Kat.", categoryRule.getCategory().getFullName());
		}

		if (parts.isEmpty()) {
			return RULE_PREFIX;
		}
		return RULE_PREFIX + " " + String.join(" ", parts);
	}

	private void addDatePart(CategoryRule categoryRule, List<String> parts) {
		if (categoryRule.getFilterDateFrom() == null && categoryRule.getFilterDateTo() == null) {
			return;
		}
		parts.add("Datum " + formatDate(categoryRule.getFilterDateFrom()) + " " + formatDate(categoryRule.getFilterDateTo()));
	}

	private void addAmountPart(CategoryRule categoryRule, List<String> parts) {
		if (categoryRule.getFilterAmountFrom() == null && categoryRule.getFilterAmountTo() == null) {
			return;
		}
		parts.add("Betrag " + formatAmount(categoryRule.getFilterAmountFrom()) + " " + formatAmount(categoryRule.getFilterAmountTo()));
	}

	private void addTextPart(List<String> parts, String label, String value) {
		if (hasText(value)) {
			parts.add(label + " " + value.trim());
		}
	}

	private String formatDate(java.time.LocalDate date) {
		return date != null ? TypeConverter.toDateStringLong(date) : "";
	}

	private String formatAmount(BigDecimal amount) {
		return amount != null ? new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(amount) : "";
	}

	private boolean isNameUsedByAnotherRule(String name, int currentRuleId) {
		for (CategoryRule existingRule : getAllFull(CategoryRule.class)) {
			if (existingRule != null && existingRule.getId() != currentRuleId && name.equals(existingRule.getName())) {
				return true;
			}
		}
		return false;
	}

	private String abbreviate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		if (maxLength <= 3) {
			return value.substring(0, maxLength);
		}
		return value.substring(0, maxLength - 3) + "...";
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
