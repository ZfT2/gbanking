package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.db.enu.StateType;

public enum MoneyTransferStatus implements IdType, StateType {

	NEW("neu", 0),
	ERROR("Fehler", 2),
	SENT("gesendet", 1);

	private final String description;
	private final int dbStateId;

	private MoneyTransferStatus(String description, int dbStateId) {
		this.description = description;
		this.dbStateId = dbStateId;
	}

	public static MoneyTransferStatus forInt(int intValue) {
		return IdType.forId(MoneyTransferStatus.class, intValue);
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
