package de.zft2.gbanking.db.dao.enu;

import java.util.EnumSet;
import java.util.Set;

public enum StockTransactionEditField {

	TRANSACTION_TYPE(1),
	TRADE_DATE(1 << 1),
	SETTLEMENT_DATE(1 << 2),
	SECURITY(1 << 3),
	QUANTITY(1 << 4),
	PRICE_OR_AMOUNT(1 << 5),
	CURRENCY(1 << 6),
	EXCHANGE_RATE(1 << 7),
	FEES(1 << 8),
	TAXES(1 << 9),
	ACCRUED_INTEREST(1 << 10),
	NOTE(1 << 11);

	public static final int ALL_FIELDS_MASK = EnumSet.allOf(StockTransactionEditField.class).stream()
			.mapToInt(StockTransactionEditField::mask).reduce(0, (left, right) -> left | right);

	private final int mask;

	StockTransactionEditField(int mask) {
		this.mask = mask;
	}

	public int mask() {
		return mask;
	}

	public boolean isSet(int editableFieldMask) {
		return (editableFieldMask & mask) != 0;
	}

	public static int maskOf(Set<StockTransactionEditField> fields) {
		return fields.stream().mapToInt(StockTransactionEditField::mask).reduce(0, (left, right) -> left | right);
	}
}
