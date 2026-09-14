package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockDayCountConvention implements IdType {

	ACT_ACT_ICMA(1), ACT_360(2), ACT_365F(3), THIRTY_360_US(4), THIRTY_E_360(5), THIRTY_E_360_ISDA(6);

	private final int dbStateId;

	StockDayCountConvention(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockDayCountConvention forInt(int value) {
		return IdType.forId(StockDayCountConvention.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
