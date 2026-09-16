package de.zft2.gbanking.gui.enu;

import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum PageContext implements LocalizedEnumValue {

	ACCOUNTS_TRANSACTIONS,
	STOCK_PORTFOLIOS,
	ALL_STOCK_PORTFOLIOS,
	ALL_SECURITIES,
	ALL_ACCOUNTS,
	ALL_TRANSACTIONS,
	ANALYSIS,
	CATEGORY_ANALYSIS,
	ACCOUNTS_MONEYTRANSFERS,
	OPEN_ACTIONS,
	BANKACCESS,
	INSTITUTES,
	RECIPIENTS,
	CATEGORIES;

	public static PageContext forString(String strValue) {
		return LocalizedEnumValue.forString(PageContext.class, strValue);
	}

	@Override
	public final String toString() {
		return getDisplayName();
	}

}
