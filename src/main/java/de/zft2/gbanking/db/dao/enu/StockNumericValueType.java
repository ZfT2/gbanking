package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockNumericValueType implements IdType {

	QUANTITY(1), PRICE(2), RATE(3), FACTOR(4);

	private final int dbStateId;

	StockNumericValueType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockNumericValueType forInt(int value) {
		return IdType.forId(StockNumericValueType.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
