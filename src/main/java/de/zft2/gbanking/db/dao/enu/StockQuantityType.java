package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum StockQuantityType implements IdType, LocalizedEnumValue {

	UNITS(1), NOMINAL(2);

	private final int dbStateId;

	StockQuantityType(int dbStateId) {
		this.dbStateId = dbStateId;
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
