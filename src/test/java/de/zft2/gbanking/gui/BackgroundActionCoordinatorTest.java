package de.zft2.gbanking.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.zft2.gbanking.gui.BackgroundActionCoordinator.ActionScope;
import de.zft2.gbanking.gui.BackgroundActionCoordinator.QuiesceMode;
import de.zft2.gbanking.gui.BackgroundActionCoordinator.QuiesceResult;
import javafx.application.Platform;
import javafx.concurrent.Task;

class BackgroundActionCoordinatorTest {

	private BackgroundActionCoordinator coordinator;

	@BeforeEach
	void setUp() {
		coordinator = BackgroundActionCoordinator.createForTest(Runnable::run);
	}

	@AfterEach
	void tearDown() {
		coordinator.closeForTest();
	}

	@ParameterizedTest
	@ValueSource(strings = { "success", "failure", "cancel" })
	void completionShouldRunOnceOnFxBeforeLocalTerminalHandlers(String outcome) throws Exception {
		List<String> calls = new ArrayList<>();
		CountDownLatch terminal = new CountDownLatch(1);
		AtomicBoolean finishedOnFx = new AtomicBoolean();
		BackgroundActionCoordinator starter = spy(coordinator);
		doAnswer(invocation -> coordinator.start(invocation.getArgument(0), invocation.getArgument(1), ActionScope.INDEPENDENT))
				.when(starter).start(any(), anyString());
		JavaFxTestSupport.runFx(() -> {
			Task<String> task = BackgroundActionCoordinator.newTask(() -> {
				if ("failure".equals(outcome)) {
					throw new IllegalStateException("Work failed");
				}
				if ("cancel".equals(outcome)) {
					new CountDownLatch(1).await();
				}
				return "done";
			});
			Consumer<String> handler = state -> {
				calls.add(state);
				terminal.countDown();
			};
			task.setOnSucceeded(event -> handler.accept("success"));
			task.setOnFailed(event -> handler.accept("failure"));
			task.setOnCancelled(event -> handler.accept("cancel"));
			assertTrue(starter.startWithCompletion(task, "completion-test", () -> {
				calls.add("finished");
				finishedOnFx.set(Platform.isFxApplicationThread());
			}));
			if ("cancel".equals(outcome)) {
				task.cancel(true);
			}
		});

		assertTrue(terminal.await(2, TimeUnit.SECONDS));
		assertEquals(List.of("finished", outcome), calls);
		assertTrue(finishedOnFx.get());
		assertTrue(coordinator.quiesce(QuiesceMode.WAIT, Duration.ofSeconds(2)).get(3, TimeUnit.SECONDS).completed());
	}

	@Test
	void rejectedStartShouldFinishOnceBeforeCancellationHandler() {
		coordinator.stopAcceptingActions();
		JavaFxTestSupport.runFx(() -> {
			List<String> calls = new ArrayList<>();
			Task<Void> task = BackgroundActionCoordinator.newTask(() -> null);
			task.setOnCancelled(event -> calls.add("cancelled"));

			assertFalse(coordinator.startWithCompletion(task, "rejected", () -> calls.add("finished")));

			assertEquals(List.of("finished", "cancelled"), calls);
		});
	}

	@Test
	void failedStartShouldFinishAndRethrowTheOriginalFailure() {
		JavaFxTestSupport.runFx(() -> {
			AtomicBoolean finished = new AtomicBoolean();
			BackgroundActionCoordinator starter = spy(coordinator);
			Task<Void> task = BackgroundActionCoordinator.newTask(() -> null);
			IllegalStateException failure = new IllegalStateException("Start failed");
			doThrow(failure).when(starter).start(task, "broken");
			Runnable onFinished = () -> finished.set(true);

			assertSame(failure, assertThrows(IllegalStateException.class,
					() -> starter.startWithCompletion(task, "broken", onFinished)));
			assertTrue(finished.get());
		});
	}

	@Test
	void quiesceShouldWaitForActualWorkerAndRejectNewActions() throws Exception {
		CountDownLatch workerEntered = new CountDownLatch(1);
		CountDownLatch releaseWorker = new CountDownLatch(1);
		AtomicBoolean rejectedActionCancelled = new AtomicBoolean();

		assertTrue(coordinator.startOperation(() -> {
			workerEntered.countDown();
			awaitUninterruptibly(releaseWorker);
		}, () -> { }, "import", ActionScope.INDEPENDENT));
		assertTrue(workerEntered.await(2, TimeUnit.SECONDS));

		QuiesceResult timedOut = coordinator.quiesce(QuiesceMode.WAIT, Duration.ofMillis(50)).get(2, TimeUnit.SECONDS);
		assertFalse(timedOut.completed());
		assertEquals(java.util.List.of("import"), timedOut.activeActionNames());
		assertFalse(coordinator.startOperation(() -> { }, () -> rejectedActionCancelled.set(true), "late", ActionScope.INDEPENDENT));
		assertTrue(rejectedActionCancelled.get());

		releaseWorker.countDown();
		assertTrue(coordinator.quiesce(QuiesceMode.WAIT, Duration.ofSeconds(2)).get(3, TimeUnit.SECONDS).completed());
		assertFalse(coordinator.hasActiveActions());
	}

	@Test
	void cancelShouldSignalActionAndCompleteOnlyAfterWorkerStopped() throws Exception {
		CountDownLatch workerEntered = new CountDownLatch(1);
		CountDownLatch keepWorkerAlive = new CountDownLatch(1);
		AtomicBoolean cancellationActionCalled = new AtomicBoolean();
		AtomicBoolean workerFinished = new AtomicBoolean();

		coordinator.startOperation(() -> {
			workerEntered.countDown();
			try {
				keepWorkerAlive.await();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			} finally {
				workerFinished.set(true);
			}
		}, () -> cancellationActionCalled.set(true), "retrieval", ActionScope.INDEPENDENT);
		assertTrue(workerEntered.await(2, TimeUnit.SECONDS));

		QuiesceResult result = coordinator.quiesce(QuiesceMode.CANCEL, Duration.ofSeconds(2)).get(3, TimeUnit.SECONDS);

		assertTrue(result.completed());
		assertTrue(cancellationActionCalled.get());
		assertTrue(workerFinished.get());
	}

	@Test
	void resumeShouldAllowActionsAfterAbortedTransition() throws Exception {
		CountDownLatch workerEntered = new CountDownLatch(1);
		CountDownLatch releaseWorker = new CountDownLatch(1);
		coordinator.startOperation(() -> {
			workerEntered.countDown();
			awaitUninterruptibly(releaseWorker);
		}, () -> { }, "export", ActionScope.INDEPENDENT);
		assertTrue(workerEntered.await(2, TimeUnit.SECONDS));
		assertFalse(coordinator.quiesce(QuiesceMode.WAIT, Duration.ofMillis(50)).get(2, TimeUnit.SECONDS).completed());

		coordinator.resume();
		CountDownLatch secondActionFinished = new CountDownLatch(1);
		assertTrue(coordinator.startOperation(secondActionFinished::countDown, () -> { }, "second", ActionScope.INDEPENDENT));
		assertTrue(secondActionFinished.await(2, TimeUnit.SECONDS));

		releaseWorker.countDown();
		assertTrue(coordinator.quiesce(QuiesceMode.WAIT, Duration.ofSeconds(2)).get(3, TimeUnit.SECONDS).completed());
	}

	@Test
	void stoppedCoordinatorShouldStillWaitForAlreadyRunningAction() throws Exception {
		CountDownLatch workerEntered = new CountDownLatch(1);
		CountDownLatch releaseWorker = new CountDownLatch(1);
		coordinator.startOperation(() -> {
			workerEntered.countDown();
			awaitUninterruptibly(releaseWorker);
		}, () -> { }, "write", ActionScope.INDEPENDENT);
		assertTrue(workerEntered.await(2, TimeUnit.SECONDS));

		coordinator.stopAcceptingActions();
		assertFalse(coordinator.quiesce(QuiesceMode.WAIT, Duration.ofMillis(50)).get(2, TimeUnit.SECONDS).completed());

		releaseWorker.countDown();
		assertTrue(coordinator.quiesce(QuiesceMode.WAIT, Duration.ofSeconds(2)).get(3, TimeUnit.SECONDS).completed());
	}

	private static void awaitUninterruptibly(CountDownLatch latch) {
		boolean interrupted = false;
		while (true) {
			try {
				latch.await();
				break;
			} catch (InterruptedException exception) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}
}
