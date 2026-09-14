package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockPriceType implements IdType {

	MARKET(1), CLOSE(2), NAV(3), BID(4), ASK(5), INDICATIVE(6);

	private final int dbStateId;

	StockPriceType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockPriceType forInt(int value) {
		return IdType.forId(StockPriceType.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
