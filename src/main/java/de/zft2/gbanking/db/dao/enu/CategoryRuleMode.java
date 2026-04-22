package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum CategoryRuleMode implements IdType {

	AUTO("automatische Zuweisung (via Regel)", 1), 
	MANUAL("manuelle Zuweisung", 2), 
	IMPORT("Übernahme aus Datenimport", 3);

	private final String description;
	private final int dbStateId;

	private CategoryRuleMode(String translation, int dbStateId) {
		this.description = translation;
		this.dbStateId = dbStateId;
	}

	public static CategoryRuleMode forInt(int intValue) {
		return IdType.forId(CategoryRuleMode.class, intValue);
	}

	public static CategoryRuleMode forString(String strValue) {
		for (CategoryRuleMode x : values()) {
			if (x.description.equals(strValue))
				return x;
		}
		return null;
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
