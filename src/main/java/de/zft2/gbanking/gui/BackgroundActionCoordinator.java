package de.zft2.gbanking.gui;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import de.zft2.gbanking.concurrent.CancellationSupport;
import de.zft2.gbanking.db.DbRuntimeContext;
import javafx.application.Platform;
import javafx.concurrent.Task;

public final class BackgroundActionCoordinator {

	public enum ActionScope {
		DATABASE,
		INDEPENDENT
	}

	public enum QuiesceMode {
		WAIT,
		CANCEL
	}

	public record QuiesceResult(boolean completed, List<String> activeActionNames) {

		public QuiesceResult {
			activeActionNames = List.copyOf(activeActionNames);
		}
	}

	private enum State {
		RUNNING,
		QUIESCING,
		STOPPED
	}

	private static final BackgroundActionCoordinator INSTANCE = new BackgroundActionCoordinator(Platform::runLater);

	private final Object monitor = new Object();
	private final Consumer<Runnable> completionScheduler;
	private final ExecutorService completionWaiter;
	private final Map<Long, ManagedAction> activeActions = new LinkedHashMap<>();
	private long nextActionId;
	private State state = State.RUNNING;

	private BackgroundActionCoordinator(Consumer<Runnable> completionScheduler) {
		this.completionScheduler = Objects.requireNonNull(completionScheduler, "completionScheduler");
		ThreadFactory threadFactory = operation -> {
			Thread thread = new Thread(operation, "background-action-completion");
			thread.setDaemon(true);
			return thread;
		};
		completionWaiter = Executors.newCachedThreadPool(threadFactory);
	}

	static BackgroundActionCoordinator createForTest(Consumer<Runnable> completionScheduler) {
		return new BackgroundActionCoordinator(completionScheduler);
	}

	public static BackgroundActionCoordinator getInstance() {
		return INSTANCE;
	}

	public boolean start(Task<?> task, String actionName) {
		return start(task, actionName, ActionScope.DATABASE);
	}

	public boolean start(Task<?> task, String actionName, ActionScope scope) {
		Objects.requireNonNull(task, "task");
		return startOperation(task, () -> task.cancel(true), actionName, scope);
	}

	boolean startOperation(Runnable operation, Runnable cancellationAction, String actionName, ActionScope scope) {
		Objects.requireNonNull(operation, "operation");
		Objects.requireNonNull(cancellationAction, "cancellationAction");
		Objects.requireNonNull(scope, "scope");
		String effectiveActionName = actionName == null || actionName.isBlank() ? "background-action" : actionName;

		synchronized (monitor) {
			if (state != State.RUNNING) {
				try {
					cancellationAction.run();
				} catch (RuntimeException ignored) {
					// The action was never started and must remain rejected.
				}
				return false;
			}
			long actionId = ++nextActionId;
			ManagedAction action = new ManagedAction(effectiveActionName, cancellationAction);
			activeActions.put(actionId, action);
			boolean started = false;
			try {
				Runnable managedOperation = () -> {
					try {
						CancellationSupport.runWithCancellation(action.cancellationRequested::get, operation);
					} catch (CancellationException ignored) {
						// Cancellation is an expected terminal state; the worker is still joined below.
					}
				};
				action.workerThread = startWorker(managedOperation, effectiveActionName, scope);
				completionWaiter.execute(() -> awaitCompletion(actionId, action));
				started = true;
				return true;
			} catch (RuntimeException failure) {
				action.completion.completeExceptionally(failure);
				throw failure;
			} finally {
				if (!started) {
					activeActions.remove(actionId);
					action.completion.completeExceptionally(new IllegalStateException("Background action could not be started"));
				}
			}
		}
	}

	public CompletableFuture<QuiesceResult> quiesce(QuiesceMode mode, Duration timeout) {
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(timeout, "timeout");
		List<ManagedAction> actions;
		synchronized (monitor) {
			if (state != State.STOPPED) {
				state = State.QUIESCING;
			}
			actions = new ArrayList<>(activeActions.values());
		}
		if (mode == QuiesceMode.CANCEL) {
			actions.forEach(ManagedAction::requestCancellation);
		}
		CompletableFuture<?>[] completions = actions.stream().map(action -> action.completion).toArray(CompletableFuture[]::new);
		CompletableFuture<Void> allCompleted = CompletableFuture.allOf(completions);
		return allCompleted.completeOnTimeout(null, timeout.toMillis(), TimeUnit.MILLISECONDS)
			.thenApply(ignored -> currentQuiesceResult());
	}

	public void resume() {
		synchronized (monitor) {
			if (state == State.QUIESCING) {
				state = State.RUNNING;
			}
		}
	}

	public void stopAcceptingActions() {
		synchronized (monitor) {
			state = State.STOPPED;
		}
	}

	public boolean hasActiveActions() {
		synchronized (monitor) {
			return !activeActions.isEmpty();
		}
	}

	public List<String> getActiveActionNames() {
		synchronized (monitor) {
			return activeActions.values().stream().map(action -> action.name).toList();
		}
	}

	void closeForTest() {
		completionWaiter.shutdownNow();
	}

	private Thread startWorker(Runnable operation, String actionName, ActionScope scope) {
		if (scope == ActionScope.DATABASE) {
			return DbRuntimeContext.startBackgroundThread(operation, actionName, completionScheduler);
		}
		Thread thread = new Thread(operation, actionName);
		thread.setDaemon(true);
		thread.start();
		return thread;
	}

	private void awaitCompletion(long actionId, ManagedAction action) {
		joinWorker(action.workerThread);
		Runnable completion = () -> {
			synchronized (monitor) {
				activeActions.remove(actionId);
			}
			action.completion.complete(null);
		};
		boolean scheduled = false;
		try {
			completionScheduler.accept(completion);
			scheduled = true;
		} catch (RuntimeException failure) {
			completion.run();
			scheduled = true;
		} finally {
			if (!scheduled) {
				completion.run();
			}
		}
	}

	private void joinWorker(Thread workerThread) {
		try {
			workerThread.join();
		} catch (InterruptedException exception) {
			joinWorker(workerThread);
			Thread.currentThread().interrupt();
		}
	}

	private QuiesceResult currentQuiesceResult() {
		List<String> actionNames = getActiveActionNames();
		return new QuiesceResult(actionNames.isEmpty(), actionNames);
	}

	private static final class ManagedAction {

		private final String name;
		private final Runnable cancellationAction;
		private final AtomicBoolean cancellationRequested = new AtomicBoolean();
		private final CompletableFuture<Void> completion = new CompletableFuture<>();
		private Thread workerThread;

		private ManagedAction(String name, Runnable cancellationAction) {
			this.name = name;
			this.cancellationAction = cancellationAction;
		}

		private void requestCancellation() {
			if (cancellationRequested.compareAndSet(false, true)) {
				try {
					cancellationAction.run();
				} catch (RuntimeException ignored) {
					// The action remains registered until its worker thread has really stopped.
				}
				workerThread.interrupt();
			}
		}
	}
}
