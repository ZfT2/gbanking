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

	default void handleAccountReselection(BankAccount account) {
		// Only account overviews restore their details when the selected row is clicked again.
	}
}
