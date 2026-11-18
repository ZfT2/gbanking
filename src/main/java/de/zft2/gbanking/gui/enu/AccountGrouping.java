package de.zft2.gbanking.gui.enu;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.util.Comparator;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountType;

public enum AccountGrouping {
	NONE, TYPE, BANK;

	@Override
	public String toString() {
		return BaseMessages.getTextStatic("UI_ACCOUNT_GROUPING_" + name());
	}

	public String label(BankAccount account) {
		if (this == BANK) {
			String identifier = bankIdentifier(account);
			if (identifier == null) {
				return BaseMessages.getTextStatic("UI_ACCOUNT_GROUPING_NO_BANK");
			}
			String bank = trimToNull(account.getBankName());
			return bank != null ? bank : identifier;
		}
		AccountType type = account.getAccountType() != null ? account.getAccountType() : AccountType.UNKNOWN_ACCOUNT;
		return switch (type) {
			case CURRENT_ACCOUNT -> BaseMessages.getTextStatic("UI_ACCOUNT_GROUPING_CURRENT_ACCOUNTS");
			case SAVINGS_ACCOUNT -> BaseMessages.getTextStatic("UI_ACCOUNT_GROUPING_SAVINGS_ACCOUNTS");
			default -> type.toString();
		};
	}

	public String key(BankAccount account) {
		String identifier = this == BANK ? bankIdentifier(account) : label(account);
		return identifier != null ? identifier : "";
	}

	private static String bankIdentifier(BankAccount account) {
		String blz = trimToNull(account.getBlz());
		return blz != null ? blz : trimToNull(account.getBic());
	}

	public Comparator<BankAccount> comparator(Comparator<BankAccount> columnComparator) {
		Comparator<BankAccount> withinGroup = columnComparator != null ? columnComparator
				: Comparator.comparing(account -> account.getAccountName(), Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER));
		return switch (this) {
			case NONE -> columnComparator;
			case TYPE -> Comparator.<BankAccount>comparingInt(account -> account.getAccountType() != null
					? account.getAccountType().getDbStateId() : AccountType.UNKNOWN_ACCOUNT.getDbStateId()).thenComparing(withinGroup);
			case BANK -> Comparator.comparing((BankAccount account) -> key(account), String.CASE_INSENSITIVE_ORDER).thenComparing(withinGroup);
		};
	}
}
