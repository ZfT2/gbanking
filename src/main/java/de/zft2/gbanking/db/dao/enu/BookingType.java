package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum BookingType implements IdType, LocalizedEnumValue {

	DEPOSIT(1),
	REMOVAL(2),
	INTEREST(3),
	INTEREST_CHARGE(4),
	REBOOKING_OUT(5),
	REBOOKING_IN(6),
	CANCEL(7);

	private int dbStateId;

	private BookingType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static BookingType forInt(int intValue) {
		return IdType.forId(BookingType.class, intValue);
	}

	public static BookingType forString(String strValue) {
		return LocalizedEnumValue.forString(BookingType.class, strValue);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	@Override
	public final String toString() {
		return getDisplayName();
	}

}
