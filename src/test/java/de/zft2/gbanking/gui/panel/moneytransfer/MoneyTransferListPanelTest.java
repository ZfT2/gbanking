package de.zft2.gbanking.gui.panel.moneytransfer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;

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

	@Test
	void instantPaymentStatusQueryShouldRequireOnlyInstantTransfers() {
		MoneyTransfer instantTransfer = new MoneyTransfer();
		instantTransfer.setOrderType(OrderType.REALTIME_TRANSFER);
		MoneyTransfer regularTransfer = new MoneyTransfer();
		regularTransfer.setOrderType(OrderType.TRANSFER);

		assertTrue(MoneyTransferListPanel.canRetrieveInstantPaymentStatus(List.of(instantTransfer)));
		assertTrue(MoneyTransferListPanel.canRetrieveInstantPaymentStatus(List.of(instantTransfer, instantTransfer)));
		assertFalse(MoneyTransferListPanel.canRetrieveInstantPaymentStatus(List.of(instantTransfer, regularTransfer)));
		assertFalse(MoneyTransferListPanel.canRetrieveInstantPaymentStatus(List.of()));
	}
}
