package de.gbanking.db.dao.enu;

import de.gbanking.db.enu.IdType;

public enum InstituteStatus implements IdType {

	ACTIVE("aktiv", 1), 
	DUPLICATE("Dublette", 2), 
	ARCHIVED("archiviert", 0);

	public static InstituteStatus forString(String strValue) {
		for (InstituteStatus x : values()) {
			if (x.translation.equals(strValue))
				return x;
		}
		return null;
	}
	
	public static IdType forInt(int intValue) {
		for (InstituteStatus x : values()) {
			if (x.dbStateId == intValue)
				return x;
		}
		return null;
	}

	private final String translation;
	private final int dbStateId;

	private InstituteStatus(String translation, int dbStateId) {
		this.translation = translation;
		this.dbStateId = dbStateId;
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	@Override
	public final String toString() {
		return translation;
	}

}
