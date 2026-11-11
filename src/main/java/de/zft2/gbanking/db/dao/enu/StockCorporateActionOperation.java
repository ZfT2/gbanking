package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum StockCorporateActionOperation implements IdType, LocalizedEnumValue {
	RIGHTS_ALLOCATION(1), RIGHTS_BUY(2), RIGHTS_SALE(3), SUBSCRIPTION(4), RIGHTS_EXPIRY(5),
	CONSOLIDATION(6), NOMINAL_REDUCTION(7), CANCELLATION(8), CAPITAL_RETURN(9), FRACTIONAL_SALE(10);

	private final int id;
	StockCorporateActionOperation(int id) { this.id = id; }
	@Override public int getDbStateId() { return id; }
	@Override public String toString() { return getDisplayName(); }

	public boolean isSale() {
		return this == RIGHTS_SALE || this == FRACTIONAL_SALE || this == CANCELLATION;
	}

	public boolean isCashOperation() {
		return this == RIGHTS_BUY || this == SUBSCRIPTION || isSale() || this == CAPITAL_RETURN;
	}

	public boolean isPermitted(StockCorporateActionType type) {
		return switch (type) {
		case RIGHTS_ISSUE -> this == RIGHTS_ALLOCATION || this == RIGHTS_BUY || this == RIGHTS_SALE
				|| this == SUBSCRIPTION || this == RIGHTS_EXPIRY;
		case CONSOLIDATION -> this == CONSOLIDATION || this == FRACTIONAL_SALE;
		case NOMINAL_REDUCTION -> this == NOMINAL_REDUCTION;
		case CAPITAL_REDUCTION -> this == CANCELLATION || this == CAPITAL_RETURN || this == FRACTIONAL_SALE;
		};
	}
}
