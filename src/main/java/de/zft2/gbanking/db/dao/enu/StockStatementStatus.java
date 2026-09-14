package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockStatementStatus implements IdType {

	DRAFT(1), FINAL(2);

	private final int dbStateId;

	StockStatementStatus(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockStatementStatus forInt(int value) {
		return IdType.forId(StockStatementStatus.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
