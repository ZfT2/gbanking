package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockIdentifierType implements IdType {

	ISIN(1), WKN(2), TICKER(3), FIGI(4), SEDOL(5), CUSIP(6), PROVIDER(7);

	private final int dbStateId;

	StockIdentifierType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockIdentifierType forInt(int value) {
		return IdType.forId(StockIdentifierType.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
