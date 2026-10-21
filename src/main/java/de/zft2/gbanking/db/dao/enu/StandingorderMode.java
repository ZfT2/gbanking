package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum StandingorderMode implements IdType, LocalizedEnumValue {

	MONTHLY(1, 1),
	BIMONTHLY(2, 2),
	QUARTERLY(3, 3),
	SEMI_ANNUALLY(4, 6),
	ANNUALLY(5, 12);

	public static StandingorderMode forString(String strValue) {
		return LocalizedEnumValue.forString(StandingorderMode.class, strValue);
	}

	private final int dbStateId;
	private final int intervalMonths;

	private StandingorderMode(int dbStateId, int intervalMonths) {
		this.dbStateId = dbStateId;
		this.intervalMonths = intervalMonths;
	}

	public static StandingorderMode forInt(int intValue) {
		return IdType.forId(StandingorderMode.class, intValue);
	}

	/**
	 * Returns the interval of the standing order in months.
	 *
	 * @return interval in months
	 */
	public int getIntervalMonths() {
		return intervalMonths;
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
