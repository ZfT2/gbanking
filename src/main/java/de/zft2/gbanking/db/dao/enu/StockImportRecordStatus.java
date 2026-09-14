package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockImportRecordStatus implements IdType {

	PREVIEW(1), IMPORTED(2), SKIPPED(3), DUPLICATE_CANDIDATE(4), ERROR(5);

	private final int dbStateId;

	StockImportRecordStatus(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockImportRecordStatus forInt(int value) {
		return IdType.forId(StockImportRecordStatus.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
