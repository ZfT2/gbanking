package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockCustodyType implements IdType {

	COLLECTIVE_SAFE_CUSTODY(1),
	WRAPPED_SAFE_CUSTODY(2),
	IN_HOUSE_COLLECTIVE_SAFE_CUSTODY(3),
	SECURITIES_ACCOUNTING(4),
	OTHER(9);

	private final int dbStateId;

	StockCustodyType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockCustodyType forInt(int value) {
		return IdType.forId(StockCustodyType.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
