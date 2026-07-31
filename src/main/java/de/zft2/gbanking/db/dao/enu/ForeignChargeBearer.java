package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum ForeignChargeBearer implements IdType, LocalizedEnumValue {

	SHARED(1),
	SENDER(2),
	RECIPIENT(3);

	private final int dbStateId;

	private ForeignChargeBearer(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static ForeignChargeBearer forInt(int intValue) {
		return IdType.forId(ForeignChargeBearer.class, intValue);
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
