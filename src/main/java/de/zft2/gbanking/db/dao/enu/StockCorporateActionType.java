package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum StockCorporateActionType implements IdType, LocalizedEnumValue {
	RIGHTS_ISSUE(1), CONSOLIDATION(2), NOMINAL_REDUCTION(3), CAPITAL_REDUCTION(4);

	private final int id;
	StockCorporateActionType(int id) { this.id = id; }
	@Override public int getDbStateId() { return id; }
	@Override public String toString() { return getDisplayName(); }
}
