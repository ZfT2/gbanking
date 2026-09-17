package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum MoneyTransferProtocolResultStatus implements IdType, LocalizedEnumValue {

	SUCCESS(1),
	ERROR_INVALID_PIN(2),
	ERROR_LIMIT_INSUFFICIENT(3),
	ERROR_INSUFFICIENT_FUNDS(4),
	ERROR_INVALID_ORDER_DATA(5),
	ERROR_NOT_AUTHORIZED(6),
	ERROR_DUPLICATE_ORDER(7),
	ERROR_AUTHENTICATION(8),
	ERROR_SERVICE_UNAVAILABLE(9),
	ERROR_REJECTED(10),
	ERROR_CANCELLED(11),
	ERROR_TECHNICAL(12),
	UNKNOWN(13),
	ERROR_UNKNOWN(14);

	private final int dbStateId;

	MoneyTransferProtocolResultStatus(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static MoneyTransferProtocolResultStatus forInt(int intValue) {
		return IdType.forId(MoneyTransferProtocolResultStatus.class, intValue);
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
