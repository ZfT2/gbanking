package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum ParameterDataType implements IdType, LocalizedEnumValue {

	BPD(1),
	UPD(2);

	private final int dbStateId;

	private ParameterDataType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static ParameterDataType forInt(int intValue) {
		return IdType.forId(ParameterDataType.class, intValue);
	}

	public static ParameterDataType forString(String strValue) {
		return LocalizedEnumValue.forString(ParameterDataType.class, strValue);
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
