package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockDataSourceType implements IdType {

	MANUAL(1), FINTS(2), FILE(3), MARKET_DATA(4), OTHER(5);

	private final int dbStateId;

	StockDataSourceType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockDataSourceType forInt(int value) {
		return IdType.forId(StockDataSourceType.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
