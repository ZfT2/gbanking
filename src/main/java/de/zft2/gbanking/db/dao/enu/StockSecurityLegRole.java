package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockSecurityLegRole implements IdType {

	POSITION(1), REFERENCE(2), CORPORATE_ACTION_SOURCE(3), CORPORATE_ACTION_TARGET(4);

	private final int dbStateId;

	StockSecurityLegRole(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
