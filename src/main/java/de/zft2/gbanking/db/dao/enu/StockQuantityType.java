package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockQuantityType implements IdType {

	UNITS(1), NOMINAL(2);

	private final int dbStateId;

	StockQuantityType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockQuantityType forInt(int value) {
		return IdType.forId(StockQuantityType.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
