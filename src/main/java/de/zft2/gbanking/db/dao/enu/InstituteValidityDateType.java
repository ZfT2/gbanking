package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum InstituteValidityDateType implements IdType, LocalizedEnumValue {

	SOURCE_DATE(1),
	FIRST_SEEN(2),
	FIRST_MISSING(3),
	FILE_MONTH(4);

	private final int dbStateId;

	InstituteValidityDateType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static InstituteValidityDateType forInt(int value) {
		return IdType.forId(InstituteValidityDateType.class, value);
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
