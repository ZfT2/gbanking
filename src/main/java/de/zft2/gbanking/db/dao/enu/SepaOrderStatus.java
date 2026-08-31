package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum SepaOrderStatus implements IdType, LocalizedEnumValue {

	SCHEDULED(1),
	REJECTED_BY_FIRST_AGENT(2),
	PROCESSING(3),
	PROCESSED_BY_CREDITOR_AGENT(4),
	RETURN_INITIATED(5),
	FAILED(6),
	COMPLETED(7),
	REJECTED_BY_DEBTOR_AGENT(8),
	REJECTED_BY_CREDITOR_AGENT(9);

	private final int dbStateId;

	SepaOrderStatus(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static SepaOrderStatus forInt(int intValue) {
		return IdType.forId(SepaOrderStatus.class, intValue);
	}

	public static SepaOrderStatus forCode(String code) {
		try {
			return code != null ? forInt(Integer.parseInt(code)) : null;
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	public boolean isFinal() {
		return this == REJECTED_BY_FIRST_AGENT || this == FAILED || this == COMPLETED || this == REJECTED_BY_DEBTOR_AGENT
				|| this == REJECTED_BY_CREDITOR_AGENT;
	}

	@Override
	public String toString() {
		return getDisplayName();
	}
}
