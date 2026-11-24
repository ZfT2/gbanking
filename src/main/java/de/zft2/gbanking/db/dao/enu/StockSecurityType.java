package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum StockSecurityType implements IdType, LocalizedEnumValue {

	UNKNOWN(1),
	STOCK(2),
	BOND(3, StockSecurityCategory.BOND),
	CONVERTIBLE_BOND(4, StockSecurityCategory.BOND),
	WARRANT_BOND(5, StockSecurityCategory.BOND),
	FUND(6),
	ETF(7),
	MONEY_MARKET(8),
	PARTICIPATION_CERTIFICATE(9),
	UNLISTED_PARTICIPATION(10),
	CRYPTO_ASSET(11),
	OTHER(12),
	KNOCK_OUT_WARRANT(13, StockSecurityCategory.LEVERAGED_PRODUCT),
	WARRANT(14, StockSecurityCategory.LEVERAGED_PRODUCT),
	INLINE_WARRANT(15, StockSecurityCategory.LEVERAGED_PRODUCT),
	DISCOUNT_WARRANT(16, StockSecurityCategory.LEVERAGED_PRODUCT),
	FACTOR_WARRANT(17, StockSecurityCategory.LEVERAGED_PRODUCT),
	OTHER_WARRANT(18, StockSecurityCategory.LEVERAGED_PRODUCT),
	REVERSE_CONVERTIBLE(19, StockSecurityCategory.INVESTMENT_PRODUCT),
	BONUS_CERTIFICATE(20, StockSecurityCategory.INVESTMENT_PRODUCT),
	DISCOUNT_CERTIFICATE(21, StockSecurityCategory.INVESTMENT_PRODUCT),
	ETC(22, StockSecurityCategory.INVESTMENT_PRODUCT),
	ETN(23, StockSecurityCategory.INVESTMENT_PRODUCT),
	EXPRESS_CERTIFICATE(24, StockSecurityCategory.INVESTMENT_PRODUCT),
	CAPITAL_PROTECTION_CERTIFICATE(25, StockSecurityCategory.INVESTMENT_PRODUCT),
	OUTPERFORMANCE_CERTIFICATE(26, StockSecurityCategory.INVESTMENT_PRODUCT),
	INDEX_CERTIFICATE(27, StockSecurityCategory.INVESTMENT_PRODUCT),
	CERTIFICATE(28, StockSecurityCategory.INVESTMENT_PRODUCT),
	OPTION(29, StockSecurityCategory.DERIVATIVE),
	FUTURE(30, StockSecurityCategory.DERIVATIVE);

	private final int dbStateId;
	private final StockSecurityCategory category;

	StockSecurityType(int dbStateId) {
		this(dbStateId, null);
	}

	StockSecurityType(int dbStateId, StockSecurityCategory category) {
		this.dbStateId = dbStateId;
		this.category = category;
	}

	public StockSecurityCategory getCategory() {
		return category;
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
