package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum TanProcedure implements IdType {
	
	I_TAN(900, "i-TAN", 1),
	M_TAN(941, " mobile TAN", 2),
	SMS_TAN(996, "SMS TAN", 3),
	APP_TAN(999, "APP TAN", 4),
	APP_SECUREGO_PLUS(946, "SecureGo plus", 5),
	CHIP_TAN(922, "Chip TAN", 6),
	PHOTO_TAN(000, "Photo TAN", 7),
	QR_TAN(913, "QR-TAN", 8);

	public static TanProcedure forCode(int value) {
		for (TanProcedure x : values()) {
			if (x.code == value)
				return x;
		}
		return null;
	}

	private final int code;
	private final String description;
	private final int dbStateId;

	private TanProcedure(int code, String description, int dbStateId) {
		this.code = code;
		this.description = description;
		this.dbStateId = dbStateId;
	}

	public static TanProcedure forInt(int intValue) {
		return IdType.forId(TanProcedure.class, intValue);
	}

	public int getCode() {
		return code;
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	@Override
	public final String toString() {
		return String.valueOf(code + " - " + description);
	}

}
