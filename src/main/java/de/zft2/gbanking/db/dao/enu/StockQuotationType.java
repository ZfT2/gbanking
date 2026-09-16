package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum StockQuotationType implements IdType, LocalizedEnumValue {

	ABSOLUTE(1), PERCENT_OF_NOMINAL(2), POINTS(3);

	private final int dbStateId;

	StockQuotationType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockQuotationType forInt(int value) {
		return IdType.forId(StockQuotationType.class, value);
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
