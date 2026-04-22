package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum ParameterDataType implements IdType {

	BPD("Bank-Parameter-Daten (BPD)", 1),
	UPD("User-Parameter-Daten (UPD)", 2);

	private final String description;
	private final int dbStateId;

	private ParameterDataType(String description, int dbStateId) {
		this.description = description;
		this.dbStateId = dbStateId;
	}

	public static InstituteStatus forInt(int intValue) {
		return IdType.forId(InstituteStatus.class, intValue);
	}

	public static ParameterDataType forString(String strValue) {
		for (ParameterDataType x : values()) {
			if (x.description.equals(strValue))
				return x;
		}
		return null;
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
