package de.zft2.gbanking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import de.zft2.gbanking.concurrent.CancellationSupport;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.service.account.AccountStatementAcknowledgementResult;
import de.zft2.gbanking.service.account.AccountStatementRetrievalResult;
import de.zft2.gbanking.service.account.AccountTransactionRetrievalResult;
import de.zft2.gbanking.service.action.OpenActionsExecutionService;
import de.zft2.gbanking.service.action.OpenActionsExecutionService.ActionStatus;
import de.zft2.gbanking.service.action.OpenActionsExecutionService.ActionType;
import de.zft2.gbanking.service.action.OpenActionsExecutionService.ExecutionSummary;
import de.zft2.gbanking.service.action.OpenActionsSelection;
import de.zft2.gbanking.service.action.OpenTransferAction;

class OpenActionsExecutionServiceTest {

	@Test
	void shouldExecuteEverySelectedActionInDisplayOrder() {
		GBankingBean bean = mock(GBankingBean.class);
		BankAccount accountUpdates = account(1);
		BankAccount transferAccount = account(2);
		BankAccount scheduledInventory = account(3);
		BankAccount standingInventory = account(4);
		BankAccount statements = account(5);
		BankAccount receipts = account(6);
		MoneyTransfer transfer = new MoneyTransfer();
		OpenActionsSelection selection = new OpenActionsSelection(
				List.of(accountUpdates),
				List.of(new OpenTransferAction(transferAccount, transfer)),
				List.of(scheduledInventory),
				List.of(standingInventory),
				List.of(statements),
				List.of(receipts));
		when(bean.retrieveAccountTransactionsWithResult(eq(accountUpdates), any(char[].class)))
				.thenReturn(AccountTransactionRetrievalResult.success());
		when(bean.executeTransfer(eq(transfer), eq(transferAccount), any(char[].class))).thenReturn(true);
		when(bean.retrieveMoneyTransferInventory(eq(scheduledInventory), eq(OrderType.SCHEDULED_TRANSFER), any(char[].class))).thenReturn(true);
		when(bean.retrieveMoneyTransferInventory(eq(standingInventory), eq(OrderType.STANDING_ORDER), any(char[].class))).thenReturn(true);
		when(bean.retrieveAccountStatementsWithResult(eq(statements), any(char[].class)))
				.thenReturn(AccountStatementRetrievalResult.success(List.of()));
		when(bean.acknowledgeAccountStatementsWithResult(eq(receipts), any(char[].class)))
				.thenReturn(AccountStatementAcknowledgementResult.success(1));

		ExecutionSummary summary = OpenActionsExecutionService.execute(bean, selection, account -> "1234".toCharArray());

		InOrder order = inOrder(bean);
		order.verify(bean).retrieveAccountTransactionsWithResult(eq(accountUpdates), any(char[].class));
		order.verify(bean).postRetriveActions(List.of(accountUpdates));
		order.verify(bean).executeTransfer(eq(transfer), eq(transferAccount), any(char[].class));
		order.verify(bean).retrieveMoneyTransferInventory(eq(scheduledInventory), eq(OrderType.SCHEDULED_TRANSFER), any(char[].class));
		order.verify(bean).retrieveMoneyTransferInventory(eq(standingInventory), eq(OrderType.STANDING_ORDER), any(char[].class));
		order.verify(bean).retrieveAccountStatementsWithResult(eq(statements), any(char[].class));
		order.verify(bean).acknowledgeAccountStatementsWithResult(eq(receipts), any(char[].class));
		order.verifyNoMoreInteractions();
		assertEquals(6, summary.successfulActions());
		assertEquals(0, summary.failedActions());
		assertEquals(0, summary.skippedActions());
		assertEquals(0, summary.cancelledActions());
		assertTrue(summary.blockedBankAccessIds().isEmpty());
		assertEquals(List.of(ActionType.ACCOUNT_UPDATE, ActionType.TRANSFER, ActionType.SCHEDULED_TRANSFER_INVENTORY,
				ActionType.STANDING_ORDER_INVENTORY, ActionType.ACCOUNT_STATEMENT, ActionType.ACCOUNT_STATEMENT_RECEIPT),
				summary.actionResults().stream().map(action -> action.actionType()).toList());
		assertTrue(summary.actionResults().stream().allMatch(action -> action.status() == ActionStatus.SUCCESSFUL));
	}

	@Test
	void shouldBlockRemainingActionsOfWrongPinBankAccessAndContinueOtherBankAccesses() {
		GBankingBean bean = mock(GBankingBean.class);
		BankAccount wrongPinAccount = account(1, 10);
		BankAccount blockedAccount = account(2, 10);
		BankAccount otherBankAccount = account(3, 20);
		MoneyTransfer blockedTransfer = new MoneyTransfer();
		OpenActionsSelection selection = new OpenActionsSelection(List.of(wrongPinAccount, blockedAccount, otherBankAccount),
				List.of(new OpenTransferAction(blockedAccount, blockedTransfer)), List.of(otherBankAccount), List.of(blockedAccount),
				List.of(blockedAccount), List.of(otherBankAccount));
		when(bean.retrieveAccountTransactionsWithResult(eq(wrongPinAccount), any(char[].class)))
				.thenReturn(AccountTransactionRetrievalResult.wrongPinFailure());
		when(bean.retrieveAccountTransactionsWithResult(eq(otherBankAccount), any(char[].class)))
				.thenReturn(AccountTransactionRetrievalResult.success());
		when(bean.retrieveMoneyTransferInventory(eq(otherBankAccount), eq(OrderType.SCHEDULED_TRANSFER), any(char[].class))).thenReturn(true);
		when(bean.acknowledgeAccountStatementsWithResult(eq(otherBankAccount), any(char[].class)))
				.thenReturn(AccountStatementAcknowledgementResult.success(1));

		ExecutionSummary summary = OpenActionsExecutionService.execute(bean, selection, account -> "1234".toCharArray());

		verify(bean, never()).retrieveAccountTransactionsWithResult(eq(blockedAccount), any(char[].class));
		verify(bean, never()).executeTransfer(eq(blockedTransfer), eq(blockedAccount), any(char[].class));
		verify(bean, never()).retrieveMoneyTransferInventory(eq(blockedAccount), eq(OrderType.STANDING_ORDER), any(char[].class));
		verify(bean, never()).retrieveAccountStatementsWithResult(eq(blockedAccount), any(char[].class));
		verify(bean).retrieveAccountTransactionsWithResult(eq(otherBankAccount), any(char[].class));
		verify(bean).retrieveMoneyTransferInventory(eq(otherBankAccount), eq(OrderType.SCHEDULED_TRANSFER), any(char[].class));
		verify(bean).acknowledgeAccountStatementsWithResult(eq(otherBankAccount), any(char[].class));
		assertEquals(3, summary.successfulActions());
		assertEquals(1, summary.failedActions());
		assertEquals(4, summary.skippedActions());
		assertEquals(0, summary.cancelledActions());
		assertEquals(Set.of(10), summary.blockedBankAccessIds());
		assertEquals(List.of(ActionStatus.FAILED, ActionStatus.SKIPPED_WRONG_PIN, ActionStatus.SUCCESSFUL,
				ActionStatus.SKIPPED_WRONG_PIN, ActionStatus.SUCCESSFUL, ActionStatus.SKIPPED_WRONG_PIN,
				ActionStatus.SKIPPED_WRONG_PIN, ActionStatus.SUCCESSFUL),
				summary.actionResults().stream().map(action -> action.status()).toList());
	}

	@Test
	void shouldBlockReceiptAfterWrongPinFromStatementAndContinueOtherBankAccess() {
		GBankingBean bean = mock(GBankingBean.class);
		BankAccount wrongPinAccount = account(1, 10);
		BankAccount sameBankAccess = account(2, 10);
		BankAccount otherBankAccess = account(3, 20);
		OpenActionsSelection selection = new OpenActionsSelection(List.of(), List.of(), List.of(), List.of(), List.of(wrongPinAccount),
				List.of(sameBankAccess, otherBankAccess));
		when(bean.retrieveAccountStatementsWithResult(eq(wrongPinAccount), any(char[].class)))
				.thenReturn(AccountStatementRetrievalResult.wrongPinFailure());
		when(bean.acknowledgeAccountStatementsWithResult(eq(otherBankAccess), any(char[].class)))
				.thenReturn(AccountStatementAcknowledgementResult.success(1));

		ExecutionSummary summary = OpenActionsExecutionService.execute(bean, selection, account -> "1234".toCharArray());

		verify(bean, never()).acknowledgeAccountStatementsWithResult(eq(sameBankAccess), any(char[].class));
		verify(bean).acknowledgeAccountStatementsWithResult(eq(otherBankAccess), any(char[].class));
		assertEquals(1, summary.successfulActions());
		assertEquals(1, summary.failedActions());
		assertEquals(1, summary.skippedActions());
		assertEquals(0, summary.cancelledActions());
		assertEquals(Set.of(10), summary.blockedBankAccessIds());
	}

	@Test
	void shouldSummarizeOrdinaryFailuresWithoutBlockingBankAccess() {
		GBankingBean bean = mock(GBankingBean.class);
		BankAccount account = account(1, 10);
		MoneyTransfer transfer = new MoneyTransfer();
		OpenActionsSelection selection = new OpenActionsSelection(List.of(), List.of(new OpenTransferAction(account, transfer)), List.of(account),
				List.of(), List.of(), List.of());
		when(bean.executeTransfer(eq(transfer), eq(account), any(char[].class))).thenReturn(false);
		when(bean.retrieveMoneyTransferInventory(eq(account), eq(OrderType.SCHEDULED_TRANSFER), any(char[].class))).thenReturn(true);

		ExecutionSummary summary = OpenActionsExecutionService.execute(bean, selection, selected -> "1234".toCharArray());

		verify(bean).executeTransfer(eq(transfer), eq(account), any(char[].class));
		verify(bean).retrieveMoneyTransferInventory(eq(account), eq(OrderType.SCHEDULED_TRANSFER), any(char[].class));
		assertEquals(1, summary.successfulActions());
		assertEquals(1, summary.failedActions());
		assertEquals(0, summary.skippedActions());
		assertEquals(0, summary.cancelledActions());
		assertTrue(summary.blockedBankAccessIds().isEmpty());
	}

	@Test
	void shouldReportUnstartedActionsAsCancelled() {
		GBankingBean bean = mock(GBankingBean.class);
		BankAccount completedAccount = account(1, 10);
		BankAccount cancelledAccount = account(2, 20);
		MoneyTransfer cancelledTransfer = new MoneyTransfer();
		OpenActionsSelection selection = new OpenActionsSelection(List.of(completedAccount, cancelledAccount),
				List.of(new OpenTransferAction(cancelledAccount, cancelledTransfer)), List.of(), List.of(), List.of(), List.of());
		AtomicBoolean cancellationRequested = new AtomicBoolean();
		when(bean.retrieveAccountTransactionsWithResult(eq(completedAccount), any(char[].class))).thenAnswer(invocation -> {
			cancellationRequested.set(true);
			return AccountTransactionRetrievalResult.success();
		});
		AtomicReference<ExecutionSummary> summaryReference = new AtomicReference<>();

		assertThrows(CancellationException.class,
				() -> CancellationSupport.runWithCancellation(() -> cancellationRequested.get(),
						() -> summaryReference.set(OpenActionsExecutionService.execute(bean, selection, account -> "1234".toCharArray()))));

		verify(bean).retrieveAccountTransactionsWithResult(eq(completedAccount), any(char[].class));
		verify(bean, never()).retrieveAccountTransactionsWithResult(eq(cancelledAccount), any(char[].class));
		verify(bean, never()).executeTransfer(eq(cancelledTransfer), eq(cancelledAccount), any(char[].class));
		verify(bean, never()).postRetriveActions(List.of(completedAccount, cancelledAccount));
		ExecutionSummary summary = summaryReference.get();
		assertEquals(1, summary.successfulActions());
		assertEquals(0, summary.failedActions());
		assertEquals(0, summary.skippedActions());
		assertEquals(2, summary.cancelledActions());
		assertEquals(List.of(ActionStatus.SUCCESSFUL, ActionStatus.CANCELLED, ActionStatus.CANCELLED),
				summary.actionResults().stream().map(action -> action.status()).toList());
	}

	private BankAccount account(int id) {
		return account(id, id);
	}

	private BankAccount account(int id, int bankAccessId) {
		BankAccount account = new BankAccount();
		account.setId(id);
		account.setBankAccessId(bankAccessId);
		return account;
	}
}
