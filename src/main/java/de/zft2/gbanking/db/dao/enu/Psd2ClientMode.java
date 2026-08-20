package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum Psd2ClientMode implements IdType {

	PERSONAL(1),
	CENTRAL_SERVICE(2);

	private final int dbStateId;

	Psd2ClientMode(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static Psd2ClientMode forInt(int value) {
		return IdType.forId(Psd2ClientMode.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
