package de.gbanking.db.dao.enu;

public enum CategoryRuleType {
	
	AMOUNT("Zuweisung nach Betrag"),
	PURPOSE_CONTAINS_STRING("Zuweisung nach Text im Verwendungszweck"),
	PURPOSE_CONTAINS_REGEX("Zuweisung nach RegEx im Verwendungszweck"),
	COMBINED("kombinierte Zuweisung");

	public static CategoryRuleType forString(String strValue) {
		for (CategoryRuleType x : values()) {
			if (x.description.equals(strValue))
				return x;
		}
		return null;
	}

	private final String description;

	private CategoryRuleType(String description) {
		this.description = description;
	}

	@Override
	public final String toString() {
		return description;
	}

}
