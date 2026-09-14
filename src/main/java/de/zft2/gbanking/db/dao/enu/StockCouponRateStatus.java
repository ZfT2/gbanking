package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockCouponRateStatus implements IdType {

	CONFIRMED(1), ESTIMATED(2);

	private final int dbStateId;

	StockCouponRateStatus(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockCouponRateStatus forInt(int value) {
		return IdType.forId(StockCouponRateStatus.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
