package de.gbanking.db.dao.enu;

import de.gbanking.db.enu.IdType;

public enum CategoryRuleType implements IdType {
	
	AMOUNT("Zuweisung nach Betrag", 1),
	PURPOSE_CONTAINS_STRING("Zuweisung nach Text im Verwendungszweck", 1),
	PURPOSE_CONTAINS_REGEX("Zuweisung nach RegEx im Verwendungszweck", 2),
	COMBINED("kombinierte Zuweisung", 3);

	private final String description;
	private int dbStateId;

	private CategoryRuleType(String description, int dbStateId) {
		this.description = description;
		this.dbStateId = dbStateId;
	}

	public static CategoryRuleType forInt(int intValue) {
		return IdType.forId(CategoryRuleType.class, intValue);
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
