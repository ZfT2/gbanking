package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockTransactionStatus implements IdType {

	PENDING(1), SETTLED(2), CANCELLED(3), REJECTED(4);

	private final int dbStateId;

	StockTransactionStatus(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
