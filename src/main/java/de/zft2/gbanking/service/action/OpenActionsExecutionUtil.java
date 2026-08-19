package de.zft2.gbanking.service.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.concurrent.CancellationSupport;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountRetrievalStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.service.account.AccountStatementAcknowledgementResult;
import de.zft2.gbanking.service.account.AccountStatementRetrievalResult;
import de.zft2.gbanking.service.account.AccountStatementService;
import de.zft2.gbanking.service.account.AccountTransactionRetrievalResult;
import de.zft2.gbanking.service.account.AccountTransactionService;
import de.zft2.gbanking.service.GBankingService;
import de.zft2.gbanking.service.moneytransfer.MoneyTransferService;
import de.zft2.gbanking.service.ServiceRegistry;

public final class OpenActionsExecutionUtil {

	private static final Logger log = LogManager.getLogger(OpenActionsExecutionUtil.class);

	private OpenActionsExecutionUtil() {
	}

	public static ExecutionSummary execute(OpenActionsSelection selection, Function<BankAccount, char[]> pinProvider) {
		return execute(selection, pinProvider, summary -> {
			// No intermediate result required by this caller.
		});
	}

	public static ExecutionSummary execute(OpenActionsSelection selection, Function<BankAccount, char[]> pinProvider,
			Consumer<ExecutionSummary> summaryConsumer) {
		ExecutionTracker tracker = new ExecutionTracker(selection, summaryConsumer);
		try {
			executeAccountUpdates(selection.accountUpdates(), pinProvider, tracker);
			executeTransfers(selection.transfers(), pinProvider, tracker);
			retrieveInventories(selection.scheduledTransferInventories(), OrderType.SCHEDULED_TRANSFER, pinProvider, tracker);
			retrieveInventories(selection.standingOrderInventories(), OrderType.STANDING_ORDER, pinProvider, tracker);
			retrieveAccountStatements(selection.accountStatements(), pinProvider, tracker);
			acknowledgeAccountStatements(selection.accountStatementReceipts(), pinProvider, tracker);
		} catch (CancellationException exception) {
			log.info("Execution of open actions was cancelled. Remaining selected actions are reported as cancelled.");
		}
		return tracker.summary();
	}

	private static void executeAccountUpdates(List<BankAccount> accounts, Function<BankAccount, char[]> pinProvider,
			ExecutionTracker tracker) {
		GBankingService gBankingService = ServiceRegistry.getService(GBankingService.class);
		AccountTransactionService accountTransactionService = ServiceRegistry.getService(AccountTransactionService.class);
		for (BankAccount account : accounts) {
			tracker.execute(() -> result(accountTransactionService.retrieveAccountTransactionsWithResult(account, copyPin(account, pinProvider))));
		}
		if (!accounts.isEmpty()) {
			checkCancellation();
			gBankingService.postRetriveActions(accounts);
		}
	}

	private static void executeTransfers(List<OpenTransferAction> transfers, Function<BankAccount, char[]> pinProvider,
			ExecutionTracker tracker) {
		MoneyTransferService moneyTransferService = ServiceRegistry.getService(MoneyTransferService.class);
		for (OpenTransferAction transfer : transfers) {
			BankAccount account = transfer.account();
			tracker.execute(() -> result(moneyTransferService.executeTransfer(transfer.moneyTransfer(), account, copyPin(account, pinProvider))));
		}
	}

	private static void retrieveInventories(List<BankAccount> accounts, OrderType orderType,
			Function<BankAccount, char[]> pinProvider, ExecutionTracker tracker) {
		MoneyTransferService moneyTransferService = ServiceRegistry.getService(MoneyTransferService.class);
		for (BankAccount account : accounts) {
			tracker.execute(() -> result(moneyTransferService.retrieveMoneyTransferInventory(account, orderType, copyPin(account, pinProvider))));
		}
	}

	private static void retrieveAccountStatements(List<BankAccount> accounts, Function<BankAccount, char[]> pinProvider,
			ExecutionTracker tracker) {
		AccountStatementService accountStatementService = ServiceRegistry.getService(AccountStatementService.class);
		for (BankAccount account : accounts) {
			tracker.execute(() -> result(accountStatementService.retrieveAccountStatementsWithResult(account, copyPin(account, pinProvider))));
		}
	}

	private static void acknowledgeAccountStatements(List<BankAccount> accounts,
			Function<BankAccount, char[]> pinProvider, ExecutionTracker tracker) {
		AccountStatementService accountStatementService = ServiceRegistry.getService(AccountStatementService.class);
		for (BankAccount account : accounts) {
			tracker.execute(() -> result(accountStatementService.acknowledgeAccountStatementsWithResult(account, copyPin(account, pinProvider))));
		}
	}

	private static ActionResult result(boolean successful) {
		return new ActionResult(status(successful), false);
	}

	private static ActionResult result(AccountTransactionRetrievalResult result) {
		if (result == null) {
			return ActionResult.FAILURE;
		}
		ActionStatus actionStatus = result.status() == AccountRetrievalStatus.CANCELLED ? ActionStatus.CANCELLED
				: status(result.successful());
		return new ActionResult(actionStatus, result.wrongPin());
	}

	private static ActionResult result(AccountStatementRetrievalResult result) {
		return result != null ? new ActionResult(status(result.successful()), result.wrongPin()) : ActionResult.FAILURE;
	}

	private static ActionResult result(AccountStatementAcknowledgementResult result) {
		return result != null ? new ActionResult(status(result.successful()), result.wrongPin()) : ActionResult.FAILURE;
	}

	private static ActionStatus status(boolean successful) {
		return successful ? ActionStatus.SUCCESSFUL : ActionStatus.FAILED;
	}

	private static char[] copyPin(BankAccount account, Function<BankAccount, char[]> pinProvider) {
		char[] pin = pinProvider.apply(account);
		return pin != null ? Arrays.copyOf(pin, pin.length) : null;
	}

	private static void checkCancellation() {
		CancellationSupport.throwIfCancellationRequested();
	}

	public enum ActionType {
		ACCOUNT_UPDATE,
		TRANSFER,
		SCHEDULED_TRANSFER,
		STANDING_ORDER,
		SCHEDULED_TRANSFER_INVENTORY,
		STANDING_ORDER_INVENTORY,
		ACCOUNT_STATEMENT,
		ACCOUNT_STATEMENT_RECEIPT
	}

	public enum ActionStatus {
		SUCCESSFUL,
		FAILED,
		SKIPPED_WRONG_PIN,
		CANCELLED
	}

	public record ActionExecution(Integer accountId, Integer bankAccessId, ActionType actionType, ActionStatus status) {
	}

	public record ExecutionSummary(List<ActionExecution> actionResults, Set<Integer> blockedBankAccessIds) {

		public ExecutionSummary {
			actionResults = actionResults != null ? List.copyOf(actionResults) : List.of();
			blockedBankAccessIds = blockedBankAccessIds != null ? Set.copyOf(blockedBankAccessIds) : Set.of();
		}

		public int successfulActions() {
			return count(ActionStatus.SUCCESSFUL);
		}

		public int failedActions() {
			return count(ActionStatus.FAILED);
		}

		public int skippedActions() {
			return count(ActionStatus.SKIPPED_WRONG_PIN);
		}

		public int cancelledActions() {
			return count(ActionStatus.CANCELLED);
		}

		public boolean hasProblems() {
			return actionResults.stream().anyMatch(action -> action.status() != ActionStatus.SUCCESSFUL);
		}

		private int count(ActionStatus status) {
			return (int) actionResults.stream().filter(action -> action.status() == status).count();
		}
	}

	private record ActionResult(ActionStatus status, boolean wrongPin) {
		private static final ActionResult FAILURE = new ActionResult(ActionStatus.FAILED, false);
	}

	private static final class ExecutionTracker {

		private final Set<Integer> blockedBankAccessIds = new LinkedHashSet<>();
		private final List<ActionExecution> actionResults = new ArrayList<>();
		private final Consumer<ExecutionSummary> summaryConsumer;
		private int nextActionIndex;

		private ExecutionTracker(OpenActionsSelection selection, Consumer<ExecutionSummary> summaryConsumer) {
			this.summaryConsumer = summaryConsumer;
			plan(selection.accountUpdates(), ActionType.ACCOUNT_UPDATE);
			for (OpenTransferAction transfer : selection.transfers()) {
				plan(transfer.account(), actionType(transfer));
			}
			plan(selection.scheduledTransferInventories(), ActionType.SCHEDULED_TRANSFER_INVENTORY);
			plan(selection.standingOrderInventories(), ActionType.STANDING_ORDER_INVENTORY);
			plan(selection.accountStatements(), ActionType.ACCOUNT_STATEMENT);
			plan(selection.accountStatementReceipts(), ActionType.ACCOUNT_STATEMENT_RECEIPT);
			publish();
		}

		private void plan(List<BankAccount> accounts, ActionType actionType) {
			for (BankAccount account : accounts) {
				plan(account, actionType);
			}
		}

		private void plan(BankAccount account, ActionType actionType) {
			actionResults.add(new ActionExecution(account != null ? account.getId() : null,
					account != null ? account.getBankAccessId() : null, actionType, ActionStatus.CANCELLED));
		}

		private static ActionType actionType(OpenTransferAction transfer) {
			OrderType orderType = transfer.moneyTransfer() != null ? transfer.moneyTransfer().getOrderType() : null;
			if (orderType == OrderType.SCHEDULED_TRANSFER) {
				return ActionType.SCHEDULED_TRANSFER;
			}
			return orderType == OrderType.STANDING_ORDER ? ActionType.STANDING_ORDER : ActionType.TRANSFER;
		}

		private void execute(Supplier<ActionResult> operation) {
			checkCancellation();
			ActionExecution action = actionResults.get(nextActionIndex++);
			Integer bankAccessId = action.bankAccessId();
			if (bankAccessId != null && blockedBankAccessIds.contains(bankAccessId)) {
				complete(action, ActionStatus.SKIPPED_WRONG_PIN);
				log.info("Skipping open action {} for account id {} because bank access id {} previously reported invalid PIN credentials.",
						action.actionType(), action.accountId(), bankAccessId);
				return;
			}

			try {
				ActionResult actionResult = operation.get();
				if (actionResult.wrongPin() && bankAccessId != null) {
					blockedBankAccessIds.add(bankAccessId);
					log.warn("Blocking remaining open actions for bank access id {} after invalid PIN credentials were reported.", bankAccessId);
				}
				ActionStatus status = actionResult.status() == ActionStatus.FAILED && Thread.currentThread().isInterrupted()
						? ActionStatus.CANCELLED : actionResult.status();
				complete(action, status);
			} catch (CancellationException exception) {
				throw exception;
			} catch (RuntimeException exception) {
				if (Thread.currentThread().isInterrupted()) {
					CancellationException cancellation = new CancellationException("Open action was cancelled");
					cancellation.initCause(exception);
					throw cancellation;
				}
				complete(action, ActionStatus.FAILED);
				throw exception;
			}
		}

		private void complete(ActionExecution action, ActionStatus status) {
			actionResults.set(nextActionIndex - 1,
					new ActionExecution(action.accountId(), action.bankAccessId(), action.actionType(), status));
			publish();
		}

		private void publish() {
			summaryConsumer.accept(summary());
		}

		private ExecutionSummary summary() {
			return new ExecutionSummary(actionResults, blockedBankAccessIds);
		}
	}
}
