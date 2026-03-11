package de.gbanking.db.dao.enu;

public enum OrderType {
	
	TRANSFER("Überweisung", "Überweisungen"),
	REALTIME_TRANSFER("Echtzeit-Überweisung", "Echtzeit-Überweisunen"), 
	SCHEDULED_TRANSFER("Terminüberweisung", "Terminüberweisungen"),
	STANDING_ORDER("Dauerauftrag", "Daueraufträge");

	public static OrderType forString(String strValue) {
		for (OrderType x : values()) {
			if (x.translation.equals(strValue))
				return x;
		}
		return null;
	}

	private final String translation;
	private final String plural;

	private OrderType(String translation, String plural) {
		this.translation = translation;
		this.plural = plural;
	}

	@Override
	public final String toString() {
		return translation;
	}

	public String getPlural() {
		return plural;
	}

}
