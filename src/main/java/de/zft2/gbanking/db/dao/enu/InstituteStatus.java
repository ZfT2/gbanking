package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum InstituteStatus implements IdType, LocalizedEnumValue {

	ACTIVE(1),
	DUPLICATE(2),
	ARCHIVED(3);

	private final int dbStateId;

	private InstituteStatus(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static InstituteStatus forInt(int intValue) {
		return IdType.forId(InstituteStatus.class, intValue);
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
