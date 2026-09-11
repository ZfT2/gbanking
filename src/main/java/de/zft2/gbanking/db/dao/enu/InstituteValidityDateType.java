package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum InstituteValidityDateType implements LocalizedEnumValue {

	SOURCE_DATE,
	FIRST_SEEN,
	FIRST_MISSING,
	FILE_MONTH;

	public static InstituteValidityDateType forString(String value) {
		return value == null ? null : valueOf(value);
	}

	@Override
	public String toString() {
		return getDisplayName();
	}
}
