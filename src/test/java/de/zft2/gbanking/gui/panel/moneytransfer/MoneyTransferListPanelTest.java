package de.zft2.gbanking.gui.panel.moneytransfer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;

class MoneyTransferListPanelTest {

	@Test
	void archiveStatusShouldContainCompletedAndHistoricalOrders() {
		assertTrue(MoneyTransferStatus.SENT.isArchiveStatus());
		assertTrue(MoneyTransferStatus.DELETED.isArchiveStatus());
		assertTrue(MoneyTransferStatus.SUPERSEDED.isArchiveStatus());
		assertTrue(MoneyTransferStatus.NOT_IN_BANK_INVENTORY.isArchiveStatus());

		assertFalse(MoneyTransferStatus.NEW.isArchiveStatus());
		assertFalse(MoneyTransferStatus.CHANGED.isArchiveStatus());
		assertFalse(MoneyTransferStatus.ERROR.isArchiveStatus());
		assertFalse(MoneyTransferStatus.INVENTORY.isArchiveStatus());
		assertFalse(MoneyTransferStatus.DELETE_PENDING.isArchiveStatus());
	}
}
