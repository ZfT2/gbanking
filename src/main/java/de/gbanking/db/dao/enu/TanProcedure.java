package de.gbanking.db.dao.enu;

public enum TanProcedure {
	
	I_TAN(900),
	M_TAN(941),
	SMS_TAN(996),
	APP_TAN(999),
	APP_SECUREGO_PLUS(946),
	CHIP_TAN(922),
	PHOTO_TAN(000), // TODO
	QR_TAN(000); //TODO

	public static TanProcedure forCode(int value) {
		for (TanProcedure x : values()) {
			if (x.code == value)
				return x;
		}
		return null;
	}

	private final int code;

	private TanProcedure(int value) {
		this.code = value;
	}

	@Override
	public final String toString() {
		return String.valueOf(code);
	}

}
