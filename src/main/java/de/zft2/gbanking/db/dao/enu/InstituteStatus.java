package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum InstituteStatus implements IdType {

	ACTIVE("aktiv", 1), 
	DUPLICATE("Dublette", 2), 
	ARCHIVED("archiviert", 0);

	private final String description;
	private final int dbStateId;

	private InstituteStatus(String description, int dbStateId) {
		this.description = description;
		this.dbStateId = dbStateId;
	}

	public static InstituteStatus forInt(int intValue) {
		return IdType.forId(InstituteStatus.class, intValue);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	@Override
	public final String toString() {
		return description;
	}

}
