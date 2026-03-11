package de.gbanking.db.dao.enu;

public enum AccountType {
	
	CURRENT_ACCOUNT("Girokonto"),
	OVERNIGHT_MONEY("Tagesgeld"), 
	SAVINGS_ACCOUNT("Sparkonto"),
	SAVINGS_PLAN("Sparplan"),
	SAVINGS_BOOK("Sparbuch"),
	FIXED_DEPOSIT("Festgeld"),
	CREDIT_ACCOUNT("Kreditkonto");

	public static AccountType forString(String strValue) {
		for (AccountType x : values()) {
			if (x.description.equals(strValue))
				return x;
		}
		return null;
	}

	private final String description;

	private AccountType(String description) {
		this.description = description;
	}

	@Override
	public final String toString() {
		return description;
	}

}
