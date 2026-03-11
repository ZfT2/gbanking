package de.gbanking.db.dao.enu;

public enum BookingType {
	
	DEPOSIT("Einnahme"),
	REMOVAL("Ausgabe"), 
	INTEREST("Zinsen"),
	INTEREST_CHARGE("Zinsbelastung"),
	REBOOKING_OUT("Umbuchung (Ausgang)"),
	REBOOKING_IN("Umbuchung (Eingang)");

	public static BookingType forString(String strValue) {
		for (BookingType x : values()) {
			if (x.name().equals(strValue))
				return x;
		}
		return null;
	}

	private final String translation;

	private BookingType(String translation) {
		this.translation = translation;
	}

	@Override
	public final String toString() {
		return translation;
	}

}
