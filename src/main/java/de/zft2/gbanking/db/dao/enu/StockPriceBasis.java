package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockPriceBasis implements IdType {

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
}
