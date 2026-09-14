package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockSubBalanceQualifier implements IdType {

	AVAILABLE(1), BLOCKED(2), PLEDGED(3), PENDING_DELIVERY(4), OTHER(5);

	private final int dbStateId;

	StockSubBalanceQualifier(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockSubBalanceQualifier forInt(int value) {
		return IdType.forId(StockSubBalanceQualifier.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
