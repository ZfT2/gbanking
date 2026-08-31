package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum SepaCancellationCode implements IdType {

	REVERSAL(1),
	REVOCATION(2),
	DELETE(3),
	RECALL(4);

	private final int dbStateId;

	SepaCancellationCode(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static SepaCancellationCode forInt(int intValue) {
		return IdType.forId(SepaCancellationCode.class, intValue);
	}

	public static SepaCancellationCode forCode(String code) {
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
}
