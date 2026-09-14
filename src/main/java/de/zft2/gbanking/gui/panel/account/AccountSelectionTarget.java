package de.zft2.gbanking.gui.panel.account;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.enu.PageContext;

public interface AccountSelectionTarget {

	PageContext getPageContext();

	default boolean canChangeAccountSelection() {
		return true;
	}

	default void handleAccountSelection(BankAccount account) {
		// Some account lists use row selection only as a visual focus indicator.
	}
}
