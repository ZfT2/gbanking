package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum OrderType implements IdType {
	
	TRANSFER("Überweisung", "Überweisungen", 1),
	REALTIME_TRANSFER("Echtzeit-Überweisung", "Echtzeit-Überweisunen", 2),
	SCHEDULED_TRANSFER("Terminüberweisung", "Terminüberweisungen", 3),
	STANDING_ORDER("Dauerauftrag", "Daueraufträge", 4);



	private final String description;
	private final String plural;
	private final int dbStateId;

	private OrderType(String description, String plural, int dbStateId) {
		this.description = description;
		this.plural = plural;
		this.dbStateId = dbStateId;
	}

	public static OrderType forInt(int intValue) {
		return IdType.forId(OrderType.class, intValue);
	}

	public static OrderType forString(String strValue) {
		for (OrderType x : values()) {
			if (x.description.equals(strValue))
				return x;
		}
		return null;
	}

	public String getPlural() {
		return plural;
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
