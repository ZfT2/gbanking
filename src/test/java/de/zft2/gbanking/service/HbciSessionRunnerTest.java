package de.zft2.gbanking.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.TestData;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.service.bankaccess.BankAccessService;

class HbciSessionRunnerTest {

	private static final List<Class<? extends Service>> SERVICES_TO_STUB = List.of(BankAccessService.class);

	@BeforeEach
	void setUp() throws Exception {
		ServiceStubbingUtil.initStubbedServicesInContext(SERVICES_TO_STUB);
	}

	@AfterEach
	void tearDown() throws Exception {
		ServiceStubbingUtil.unloadStubbedServicesInContext(SERVICES_TO_STUB);
	}

	@Test
	void run_shouldSerializeHbciOperationsForSameBankAccess() throws Exception {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		HbciSessionRunner runner = new HbciSessionRunner(ignored -> mock(GBankingHBCICallback.class));
		BankAccess bankAccess = TestData.createSampleBankAccess("10020030");
		CountDownLatch firstOperationStarted = new CountDownLatch(1);
		CountDownLatch releaseFirstOperation = new CountDownLatch(1);
		AtomicInteger activeOperations = new AtomicInteger();
		AtomicBoolean overlapped = new AtomicBoolean();
		AtomicBoolean holdOperation = new AtomicBoolean(true);

		doAnswer(invocation -> mock(HBCIPassport.class)).when(hbciSupport).initBankConnection(eq(bankAccess), any(GBankingHBCICallback.class));
		doAnswer(invocation -> mock(HBCIHandler.class)).when(hbciSupport).createHBCIHandler(eq(BaseMessagesDb.getVersion().getId()), any(HBCIPassport.class));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Boolean> first = executor.submit(() -> runTrackedOperation(runner, bankAccess, activeOperations, overlapped,
					firstOperationStarted, releaseFirstOperation, holdOperation));
			assertTrue(firstOperationStarted.await(1, TimeUnit.SECONDS));

			CountDownLatch secondOperationStarted = new CountDownLatch(1);
			Future<Boolean> second = executor.submit(() -> runTrackedOperation(runner, bankAccess, activeOperations, overlapped,
					secondOperationStarted, new CountDownLatch(0), new AtomicBoolean(false)));

			assertFalse(secondOperationStarted.await(100, TimeUnit.MILLISECONDS));
			assertFalse(second.isDone());
			releaseFirstOperation.countDown();
			assertTrue(first.get(1, TimeUnit.SECONDS));
			assertTrue(second.get(1, TimeUnit.SECONDS));
			assertFalse(overlapped.get());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void run_shouldSerializeHbciOperationsAcrossDifferentBankAccesses() throws Exception {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		HbciSessionRunner runner = new HbciSessionRunner(ignored -> mock(GBankingHBCICallback.class));
		BankAccess firstBankAccess = TestData.createSampleBankAccess("10020030");
		BankAccess secondBankAccess = TestData.createSampleBankAccess("40050060");
		CountDownLatch firstOperationStarted = new CountDownLatch(1);
		CountDownLatch releaseFirstOperation = new CountDownLatch(1);
		AtomicInteger activeOperations = new AtomicInteger();
		AtomicBoolean overlapped = new AtomicBoolean();

		doAnswer(invocation -> mock(HBCIPassport.class)).when(hbciSupport).initBankConnection(any(BankAccess.class), any(GBankingHBCICallback.class));
		doAnswer(invocation -> mock(HBCIHandler.class)).when(hbciSupport).createHBCIHandler(eq(BaseMessagesDb.getVersion().getId()), any(HBCIPassport.class));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Boolean> first = executor.submit(() -> runTrackedOperation(runner, firstBankAccess, activeOperations, overlapped,
					firstOperationStarted, releaseFirstOperation, new AtomicBoolean(true)));
			assertTrue(firstOperationStarted.await(1, TimeUnit.SECONDS));

			CountDownLatch secondOperationStarted = new CountDownLatch(1);
			Future<Boolean> second = executor.submit(() -> runTrackedOperation(runner, secondBankAccess, activeOperations, overlapped,
					secondOperationStarted, new CountDownLatch(0), new AtomicBoolean(false)));

			assertFalse(secondOperationStarted.await(100, TimeUnit.MILLISECONDS));
			assertFalse(second.isDone());
			releaseFirstOperation.countDown();
			assertTrue(first.get(1, TimeUnit.SECONDS));
			assertTrue(second.get(1, TimeUnit.SECONDS));
			assertFalse(overlapped.get());
		} finally {
			executor.shutdownNow();
		}
	}

	private boolean runTrackedOperation(HbciSessionRunner runner, BankAccess bankAccess, AtomicInteger activeOperations, AtomicBoolean overlapped,
			CountDownLatch started, CountDownLatch release, AtomicBoolean holdOperation) throws Exception {
		return runner.run(bankAccess, null, session -> {
			if (activeOperations.incrementAndGet() > 1) {
				overlapped.set(true);
			}
			started.countDown();
			boolean isZero = true;
			if (holdOperation.get()) {
				isZero = release.await(1, TimeUnit.SECONDS);
			}
			activeOperations.decrementAndGet();
			return isZero;
		});
	}
}
