package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockCashLegRole implements IdType {

	TRADE_VALUE(1), FEE(2), TAX(3), ACCRUED_INTEREST(4), DIVIDEND(5), INTEREST(6), REDEMPTION(7), FX(8), OTHER(9);

	private final int dbStateId;

	StockCashLegRole(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockCashLegRole forInt(int value) {
		return IdType.forId(StockCashLegRole.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
