package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StandingorderMode implements IdType {

	MONTHLY("monatlich", 1),
	BIMONTHLY("zweimonatlich", 2),
	QUARTERLY("quartalsweise", 3),
	SEMI_ANNUALLY("halbjährlich", 4),
	ANNUALLY("jährlich", 5);

	public static StandingorderMode forString(String strValue) {
		for (StandingorderMode x : values()) {
			if (x.description.equals(strValue))
				return x;
		}
		return null;
	}

	private final String description;
	private final int dbStateId;

	private StandingorderMode(String description, int dbStateId) {
		this.description = description;
		this.dbStateId = dbStateId;
	}

	public static StandingorderMode forInt(int intValue) {
		return IdType.forId(StandingorderMode.class, intValue);
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
