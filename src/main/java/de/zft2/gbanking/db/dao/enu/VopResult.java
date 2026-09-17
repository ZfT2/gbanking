package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum VopResult implements IdType, LocalizedEnumValue {

	MATCH(1),
	CLOSE_MATCH(2),
	NO_MATCH(3),
	OPT_OUT(4);

	private final int dbStateId;

	VopResult(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static VopResult forInt(int intValue) {
		return IdType.forId(VopResult.class, intValue);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	@Override
	public String toString() {
		return getDisplayName();
	}
}
