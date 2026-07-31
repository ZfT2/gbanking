package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum StandingorderMode implements IdType, LocalizedEnumValue {

	MONTHLY(1),
	BIMONTHLY(2),
	QUARTERLY(3),
	SEMI_ANNUALLY(4),
	ANNUALLY(5);

	public static StandingorderMode forString(String strValue) {
		return LocalizedEnumValue.forString(StandingorderMode.class, strValue);
	}

	private final int dbStateId;

	private StandingorderMode(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StandingorderMode forInt(int intValue) {
		return IdType.forId(StandingorderMode.class, intValue);
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
