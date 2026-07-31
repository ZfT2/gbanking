package de.zft2.gbanking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.service.action.OpenActionsSelection;
import de.zft2.gbanking.service.action.OpenTransferAction;

class OpenActionsSelectionTest {

	@Test
	void shouldNormalizeNullListsAndDetectEmptySelection() {
		OpenActionsSelection selection = new OpenActionsSelection(null, null, null, null, null, null);

		assertTrue(selection.isEmpty());
		assertTrue(selection.accountsRequiringAuthentication().isEmpty());
	}

	@Test
	void shouldReturnEveryRequiredAccountOnlyOnceInActionOrder() {
		BankAccount first = account(1);
		BankAccount second = account(2);
		MoneyTransfer transfer = new MoneyTransfer();
		OpenActionsSelection selection = new OpenActionsSelection(
				List.of(first),
				List.of(new OpenTransferAction(second, transfer)),
				List.of(first),
				List.of(second),
				List.of(first),
				List.of(second));

		assertFalse(selection.isEmpty());
		assertEquals(List.of(first, second), selection.accountsRequiringAuthentication());
	}

	private BankAccount account(int id) {
		BankAccount account = new BankAccount();
		account.setId(id);
		return account;
	}
}
