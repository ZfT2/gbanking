package de.gbanking.db.dao.enu;

import de.gbanking.db.enu.IdType;

public enum CategoryRuleMode implements IdType {

	AUTO("automatische Zuweisung (via Regel)", 1), 
	MANUAL("manuelle Zuweisung", 2), 
	IMPORT("Übernahme aus Datenimport", 3);

	public static CategoryRuleMode forString(String strValue) {
		for (CategoryRuleMode x : values()) {
			if (x.translation.equals(strValue))
				return x;
		}
		return null;
	}
	
	public static IdType forInt(int intValue) {
		for (CategoryRuleMode x : values()) {
			if (x.dbStateId == intValue)
				return x;
		}
		return null;
	}

	private final String translation;
	private final int dbStateId;

	private CategoryRuleMode(String translation, int dbStateId) {
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
