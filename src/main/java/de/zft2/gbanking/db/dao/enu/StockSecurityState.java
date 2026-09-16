package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum StockSecurityState implements IdType, LocalizedEnumValue {

	ACTIVE(1), INACTIVE(2), ARCHIVED(3);

	private final int dbStateId;

	StockSecurityState(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockSecurityState forInt(int value) {
		return IdType.forId(StockSecurityState.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	@Override
	public String toString() {
		return getDisplayName();
	}
}
