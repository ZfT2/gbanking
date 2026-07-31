package de.zft2.gbanking.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.BackgroundActionCoordinator.ActionScope;
import de.zft2.gbanking.gui.BackgroundActionCoordinator.QuiesceMode;
import de.zft2.gbanking.gui.BackgroundActionCoordinator.QuiesceResult;

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
