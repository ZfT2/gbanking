package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.concurrent.CancellationSupport;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.testdata.TestDataFactory;

class DbTransactionManagerTest extends DBControllerIntegrationBaseTest {

	@Test
	void databaseAccessShouldBeSerializedAcrossThreads() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch transactionEntered = new CountDownLatch(1);
		CountDownLatch releaseTransaction = new CountDownLatch(1);
		CountDownLatch secondAccessEntered = new CountDownLatch(1);

		try {
			Future<?> firstAccess = executor.submit(() -> DbTransactionManager.inTransaction(() -> {
				transactionEntered.countDown();
				await(releaseTransaction);
			}));
			assertTrue(transactionEntered.await(2, TimeUnit.SECONDS));

			Future<?> secondAccess = executor.submit(() -> DbTransactionManager.withAccess(secondAccessEntered::countDown));
			assertFalse(secondAccessEntered.await(200, TimeUnit.MILLISECONDS));

			releaseTransaction.countDown();
			firstAccess.get(2, TimeUnit.SECONDS);
			secondAccess.get(2, TimeUnit.SECONDS);
			assertEquals(0, secondAccessEntered.getCount());
		} finally {
			releaseTransaction.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void caughtNestedFailureShouldRollBackWholeTransactionAndRestoreIds() {
		BankAccess validBankAccess = TestDataFactory.createSampleBankAccess("11111111");
		BankAccess invalidBankAccess = TestDataFactory.createSampleBankAccess("22222222");
		invalidBankAccess.setBankName(null);

		assertThrows(GBankingException.class, () -> DbTransactionManager.inTransaction(() -> {
			db.insertOrUpdate(validBankAccess);
			try {
				db.insertOrUpdate(invalidBankAccess);
			} catch (GBankingException expected) {
				// The outer transaction must remain rollback-only even if a caller catches this error.
			}
		}));

		assertEquals(0, validBankAccess.getId());
		assertEquals(0, invalidBankAccess.getId());
		assertTrue(db.getAll(BankAccess.class).isEmpty());
	}

	@Test
	void relationFailureShouldRollBackParentInsert() {
		Category category = db.insertOrUpdate(TestDataFactory.createSampleCategory("Mobilitaet:Bahn"));
		BankAccount missingAccount = new BankAccount();
		missingAccount.setId(Integer.MAX_VALUE);
		CategoryRule categoryRule = new CategoryRule();
		categoryRule.setCategory(category);
		categoryRule.setFilterPurpose("Ticket");
		categoryRule.setBankAccountList(List.of(missingAccount));

		assertThrows(GBankingException.class, () -> db.insertOrUpdate(categoryRule));

		assertEquals(0, categoryRule.getId());
		assertTrue(db.getAll(CategoryRule.class).isEmpty());
	}

	@Test
	void cancellationShouldRollBackTransactionAndRestoreEntityState() {
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("33333333");
		AtomicBoolean cancellationRequested = new AtomicBoolean();

		assertThrows(CancellationException.class, () -> CancellationSupport.runWithCancellation(cancellationRequested::get,
				() -> DbTransactionManager.inTransaction(() -> {
					db.insertOrUpdate(bankAccess);
					cancellationRequested.set(true);
				})));

		assertEquals(0, bankAccess.getId());
		assertTrue(db.getAll(BankAccess.class).isEmpty());
	}

	@Test
	void currentDatabaseShouldBeReopenableAfterReset() {
		DBController.resetConnection();

		db = DBController.getInstance(".");

		assertNotNull(DBController.getConnection());
		assertTrue(db.getAll(BankAccess.class).isEmpty());
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(2, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Timed out waiting for test latch");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for test latch", exception);
		}
	}
}
