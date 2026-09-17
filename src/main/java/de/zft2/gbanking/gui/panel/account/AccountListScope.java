package de.zft2.gbanking.gui.panel.account;

public enum AccountListScope {

	FOLLOW_VIEW_SETTING,
	ONLINE_ONLY,
	ALL;

	boolean usesOnlineFilter(boolean onlyOnlineAccountsVisible) {
		return this == ONLINE_ONLY || (this == FOLLOW_VIEW_SETTING && onlyOnlineAccountsVisible);
	}
}
