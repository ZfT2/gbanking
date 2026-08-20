package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum BankAccessType implements IdType {

	HBCI(1),
	PAYPAL(2),
	ENABLEBANKING(3);

	private final int dbStateId;

	BankAccessType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static BankAccessType forInt(int intValue) {
		return IdType.forId(BankAccessType.class, intValue);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
