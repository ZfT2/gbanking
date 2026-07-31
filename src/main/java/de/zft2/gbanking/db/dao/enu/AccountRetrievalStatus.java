package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum AccountRetrievalStatus implements IdType, LocalizedEnumValue {

	SUCCESS(1),
	FAILED(2),
	WRONG_PIN(3),
	CANCELLED(4);

	private final int dbStateId;

	AccountRetrievalStatus(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static AccountRetrievalStatus forInt(int intValue) {
		return IdType.forId(AccountRetrievalStatus.class, intValue);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	@Override
	public String toString() {
		return getDisplayName();
	}
}
