package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockImportStatus implements IdType {

	PREVIEW(1), RUNNING(2), IMPORTED(3), FAILED(4), REJECTED(5);

	private final int dbStateId;

	StockImportStatus(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockImportStatus forInt(int value) {
		return IdType.forId(StockImportStatus.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
