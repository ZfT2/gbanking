package de.zft2.gbanking.db.dao.enu;

import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum StockSecurityCategory implements LocalizedEnumValue {

	BOND(null),
	DERIVATIVE(null),
	LEVERAGED_PRODUCT(DERIVATIVE),
	INVESTMENT_PRODUCT(DERIVATIVE);

	private final StockSecurityCategory parent;

	StockSecurityCategory(StockSecurityCategory parent) {
		this.parent = parent;
	}

	public StockSecurityCategory getParent() {
		return parent;
	}

	@Override
	public String toString() {
		return getDisplayName();
	}
}
