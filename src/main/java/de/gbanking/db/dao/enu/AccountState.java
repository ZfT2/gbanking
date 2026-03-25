package de.gbanking.db.dao.enu;

import de.gbanking.db.enu.IdType;

public enum AccountState implements IdType {
	
	ACTIVE("aktiv", 1),
	INACTIVE("inaktiv", 0),
	IGNORE("für Auswetrtungen ignorieren", 2);

	private final String description;
	private final int dbStateId;

	private AccountState(String description, int dbStateId) {
		this.description = description;
		this.dbStateId = dbStateId;
	}

	public static AccountState forInt(int intValue) {
		return IdType.forId(AccountState.class, intValue);
	}

	public static AccountState forString(String strValue) {
		for (AccountState x : values()) {
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
