package de.gbanking.db.dao.enu;

public enum AccountState {
	
	ACTIVE("aktiv"),
	INACTIVE("inaktiv"),
	IGNORE("für Auswetrtungen ignorieren");

	public static AccountState forString(String strValue) {
		for (AccountState x : values()) {
			if (x.description.equals(strValue))
				return x;
		}
		return null;
	}

	private final String description;

	private AccountState(String description) {
		this.description = description;
	}

	@Override
	public final String toString() {
		return description;
	}

}
