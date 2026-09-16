package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum StockPriceBasis implements IdType, LocalizedEnumValue {

	CLEAN(1), DIRTY(2);

	private final int dbStateId;

	StockPriceBasis(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockPriceBasis forInt(int value) {
		return IdType.forId(StockPriceBasis.class, value);
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
