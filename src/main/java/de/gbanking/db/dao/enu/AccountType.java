package de.gbanking.db.dao.enu;

import de.gbanking.db.enu.IdType;

public enum AccountType implements IdType {

	CURRENT_ACCOUNT("Girokonto", 1),
	OVERNIGHT_MONEY("Tagesgeld", 2),
	SAVINGS_ACCOUNT("Sparkonto", 3),
	SAVINGS_PLAN("Sparplan", 4),
	SAVINGS_BOOK("Sparbuch", 5),
	FIXED_DEPOSIT("Festgeld", 6),
	SAVEINGS_HOME("Bausparen", 7),
	CREDIT_CARD("Kreditkarte", 10),
	CREDIT_ACCOUNT("Kreditkonto", 11),
	SPECIAL_ACCOUNT("Sonderkonto", 15); /* z.B. Paypal.. */

	private final String description;
	private int dbStateId;

	private AccountType(String description, int dbStateId) {
		this.description = description;
		this.dbStateId = dbStateId;
	}

	public static AccountType forInt(int intValue) {
		return IdType.forId(AccountType.class, intValue);
	}

	public static AccountType forString(String strValue) {
		for (AccountType x : values()) {
			if (x.description.equals(strValue))
				return x;
		}
		return null;
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	@Override
	public final String toString() {
		return description;
	}

}
