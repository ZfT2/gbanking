package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum HbciEncodingFilterType implements IdType {

	BASE64("Base64", 1),
	NONE("keine", 0);

	private final String description;
	private int dbStateId;

	private HbciEncodingFilterType(String description, int dbStateId) {
		this.description = description;
		this.dbStateId = dbStateId;
	}

	public static HbciEncodingFilterType forString(String strValue) {
		for (HbciEncodingFilterType x : values()) {
			if (x.description.equals(strValue))
				return x;
		}
		return null;
	}

	public static HbciEncodingFilterType forInt(int intValue) {
		return IdType.forId(HbciEncodingFilterType.class, intValue);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	public String getDescription() {
		return description;
	}

	@Override
	public final String toString() {
		return description;
	}

}
