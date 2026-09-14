package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;

public enum StockTransactionType implements IdType {

	BUY(1),
	SELL(2),
	TRANSFER_IN(3),
	TRANSFER_OUT(4),
	DELIVERY_IN(5),
	DELIVERY_OUT(6),
	OPENING_BALANCE(7),
	RECONCILIATION_ADJUSTMENT(8),
	CORPORATE_ACTION(9),
	NOMINAL_ADJUSTMENT(10),
	PARTIAL_REDEMPTION(11),
	REDEMPTION(12),
	EXPIRY(13),
	DIVIDEND(14),
	INTEREST(15),
	FEE(16),
	TAX(17),
	REVERSAL(18);

	private final int dbStateId;

	StockTransactionType(int dbStateId) {
		this.dbStateId = dbStateId;
	}

	public static StockTransactionType forInt(int value) {
		return IdType.forId(StockTransactionType.class, value);
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}
}
