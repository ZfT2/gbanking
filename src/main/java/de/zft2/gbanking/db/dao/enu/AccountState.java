package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum AccountState implements IdType, LocalizedEnumValue {
	
	ACTIVE(1),
	INACTIVE(2),
	IGNORE(3);

	private final int dbStateId;

	private AccountState(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static AccountState forInt(int intValue) {
		return IdType.forId(AccountState.class, intValue);
	}

	public static AccountState forString(String strValue) {
		return LocalizedEnumValue.forString(AccountState.class, strValue);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	@Override
	public final String toString() {
		return getDisplayName();
	}

}
