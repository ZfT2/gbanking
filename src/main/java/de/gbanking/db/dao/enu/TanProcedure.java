package de.gbanking.db.dao.enu;

public enum TanProcedure {
	
	I_TAN(900, "i-TAN"),
	M_TAN(941, " mobile TAN"),
	SMS_TAN(996, "SMS TAN"),
	APP_TAN(999, "APP TAN"),
	APP_SECUREGO_PLUS(946, "SecureGo plus"),
	CHIP_TAN(922, "Chip TAN"),
	PHOTO_TAN(000, "Photo TAN"),
	QR_TAN(913, "QR-TAN");

	public static TanProcedure forCode(int value) {
		for (TanProcedure x : values()) {
			if (x.code == value)
				return x;
		}
		return null;
	}

	private final int code;
	private final String name;

	private TanProcedure(int value, String name) {
		this.code = value;
		this.name = name;
	}

	public int getCode() {
		return code;
	}

	@Override
	public final String toString() {
		return String.valueOf(code + " - " + name);
	}

}
