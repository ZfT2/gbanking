package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum BookingType implements IdType {

	DEPOSIT("Einnahme", 1),
	REMOVAL("Ausgabe", 2),
	INTEREST("Zinsen", 3),
	INTEREST_CHARGE("Zinsbelastung", 4),
	REBOOKING_OUT("Umbuchung (Ausgang)", 5),
	REBOOKING_IN("Umbuchung (Eingang)", 6);

	private final String description;
	private int dbStateId;

	private BookingType(String description, int dbStateId) {
		this.description = description;
		this.dbStateId = dbStateId;
	}

	public static BookingType forInt(int intValue) {
		return IdType.forId(BookingType.class, intValue);
	}

	public static BookingType forString(String strValue) {
		for (BookingType x : values()) {
			if (x.name().equals(strValue))
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
