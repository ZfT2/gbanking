package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum StockSecurityType implements IdType, LocalizedEnumValue {

	STOCK(1),
	BOND(2),
	FUND(3),
	ETF(4),
	ETC(5),
	ETN(6),
	CERTIFICATE(7),
	WARRANT(8),
	OPTION(9),
	FUTURE(10),
	MONEY_MARKET(11),
	CRYPTO_ASSET(12),
	UNLISTED_PARTICIPATION(13),
	OTHER(14);

	private final int dbStateId;

	StockSecurityType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockSecurityType forInt(int value) {
		return IdType.forId(StockSecurityType.class, value);
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
