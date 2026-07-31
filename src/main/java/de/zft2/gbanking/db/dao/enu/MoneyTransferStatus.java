package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.db.enu.StateType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum MoneyTransferStatus implements IdType, StateType, LocalizedEnumValue {

	NEW(1),
	SENT(2),
	CHANGED(3),
	ERROR(4),
	INVENTORY(5),
	DELETED(6),
	IMPORTED(7),
	SUPERSEDED(8),
	NOT_IN_BANK_INVENTORY(9),
	DELETE_PENDING(10);

	private final int dbStateId;

	private MoneyTransferStatus(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static MoneyTransferStatus forInt(int intValue) {
		return IdType.forId(MoneyTransferStatus.class, intValue);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	public boolean isArchiveStatus() {
		return this == MoneyTransferStatus.SENT || this == MoneyTransferStatus.DELETED || this == MoneyTransferStatus.IMPORTED
				|| this == MoneyTransferStatus.SUPERSEDED || this == MoneyTransferStatus.NOT_IN_BANK_INVENTORY;
	}

	@Override
	public final String toString() {
		return getDisplayName();
	}

}
