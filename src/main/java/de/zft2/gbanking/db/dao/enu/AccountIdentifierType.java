package de.zft2.gbanking.db.dao.enu;

import java.util.Arrays;

import de.zft2.gbanking.db.enu.IdType;

public enum AccountIdentifierType implements IdType {

	ACCOUNT("account", 1),
	ACCOUNT_TRANSFER("accountTransfer", 2);

	private final String propertyValue;
	private final int dbStateId;

	AccountIdentifierType(String propertyValue, int dbStateId) {
		this.propertyValue = propertyValue;
		this.dbStateId = dbStateId;
	}

	public String getPropertyValue() {
		return propertyValue;
	}

	public String getFileName() {
		return propertyValue + ".properties";
	}

	public static AccountIdentifierType forInt(int intValue) {
		return IdType.forId(AccountIdentifierType.class, intValue);
	}

	public static AccountIdentifierType forPropertyValue(String propertyValue) {
		return Arrays.stream(values()).filter(type -> type.propertyValue.equals(propertyValue)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown account identifier property type: " + propertyValue));
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
