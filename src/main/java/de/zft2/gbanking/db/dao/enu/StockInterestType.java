package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockInterestType implements IdType {

	FIXED(1), FLOATING(2), ZERO(3), STEP(4), INFLATION_LINKED(5);

	private final int dbStateId;

	StockInterestType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockInterestType forInt(int value) {
		return IdType.forId(StockInterestType.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
