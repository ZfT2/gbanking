package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockNumericValueType implements IdType {

	QUANTITY(1, 9), PRICE(2, 8), RATE(3, 9), FACTOR(4, 12);

	private final int dbStateId;
	private final int scaleDigits;
	private final long scaleFactor;

	StockNumericValueType(int dbStateId, int scaleDigits) {
		this.dbStateId = dbStateId;
		this.scaleDigits = scaleDigits;
		this.scaleFactor = powerOfTen(scaleDigits);
	}

	public static StockNumericValueType forInt(int value) {
		return IdType.forId(StockNumericValueType.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	public int getScaleDigits() {
		return scaleDigits;
	}

	public long getScaleFactor() {
		return scaleFactor;
	}

	private static long powerOfTen(int exponent) {
		long result = 1L;
		for (int i = 0; i < exponent; i++) {
			result = Math.multiplyExact(result, 10L);
		}
		return result;
	}
}
