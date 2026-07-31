package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum AccountType implements IdType, LocalizedEnumValue {

	CURRENT_ACCOUNT(1),
	OVERNIGHT_MONEY(2),
	SAVINGS_ACCOUNT(3),
	SAVINGS_PLAN(4),
	SAVINGS_BOOK(5),
	FIXED_DEPOSIT(6),
	SAVEINGS_HOME(7),
	CREDIT_CARD(10),
	CREDIT_ACCOUNT(11),
	DEPOT_ACCOUNT(12),
	CASH_ACCOUNT(13),
	SPECIAL_ACCOUNT(15), /* z.B. Paypal.. */
	DEPOT(16),
	UNKNOWN_ACCOUNT(20);

	private int dbStateId;

	private AccountType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static AccountType forInt(int intValue) {
		return IdType.forId(AccountType.class, intValue);
	}

	public static AccountType forString(String strValue) {
		return LocalizedEnumValue.forString(AccountType.class, strValue);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	@Override
	public final String toString() {
		return getDisplayName();
	}

}
