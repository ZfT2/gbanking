package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockFactorType implements IdType {

	SPLIT(1), NOMINAL(2), INDEX(3);

	private final int dbStateId;

	StockFactorType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockFactorType forInt(int value) {
		return IdType.forId(StockFactorType.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
