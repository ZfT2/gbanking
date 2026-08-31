package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.concurrent.CancellationSupport;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.tenant.TenantPaths;
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

	@Test
	void initializationInsideTransactionShouldPreserveActiveDatabaseContext() {
		String activeDirectory = DbRuntimeContext.getCurrentDbDirectory();
		Connection activeConnection = DBController.getConnection();
		String otherDirectory = Path.of(activeDirectory).resolve("other-database").toString();

		assertThrows(GBankingException.class,
				() -> DbTransactionManager.inTransaction(() -> DBController.getInstance(otherDirectory)));

		assertEquals(activeDirectory, DbRuntimeContext.getCurrentDbDirectory());
		assertSame(activeConnection, DBController.getConnection());
	}

	@Test
	void pendingMigrationCheckShouldPreserveActiveDatabaseContext() {
		String activeDirectory = DbRuntimeContext.getCurrentDbDirectory();
		Connection activeConnection = DBController.getConnection();
		String otherDirectory = Path.of(activeDirectory).resolve("migration-check").toString();

		assertFalse(DBController.hasPendingMigrations(otherDirectory));
		assertEquals(activeDirectory, DbRuntimeContext.getCurrentDbDirectory());
		assertSame(activeConnection, DBController.getConnection());
	}

	@Test
	void integrityValidationInsideTransactionShouldPreserveActiveConnection() {
		Connection activeConnection = DBController.getConnection();
		Path databaseFile = Path.of(DbRuntimeContext.getCurrentDbDirectory()).resolve("gbanking.db");

		assertThrows(GBankingException.class,
				() -> DbTransactionManager.inTransaction(
						() -> DBController.validateDatabaseIntegrity(databaseFile, false)));

		assertSame(activeConnection, DBController.getConnection());
	}

	@Test
	void directContextChangesInsideTransactionShouldPreserveActiveSession() {
		String activeDirectory = DbRuntimeContext.getCurrentDbDirectory();
		Connection activeConnection = DBController.getConnection();
		Path otherDirectory = Path.of(activeDirectory).resolve("other-context");
		TenantPaths otherTenant = new TenantPaths(otherDirectory.resolve("tenant"), otherDirectory.resolve("db"));
		List<Runnable> contextChanges = List.of(
				() -> DbRuntimeContext.setCurrentDbDirectory(otherDirectory.toString()),
				() -> DbRuntimeContext.resolveDbDirectory(otherDirectory.toString()),
				() -> DbRuntimeContext.setCurrentTenantPaths(otherTenant));

		for (Runnable contextChange : contextChanges) {
			assertThrows(GBankingException.class,
					() -> DbTransactionManager.inTransaction(contextChange));
			assertEquals(activeDirectory, DbRuntimeContext.getCurrentDbDirectory());
			assertSame(activeConnection, DBController.getConnection());
			assertTrue(DBController.hasOpenConnection());
		}
	}

	@Test
	void contextChangeShouldCloseExistingSessionUntilItIsReopened() throws Exception {
		String activeDirectory = DbRuntimeContext.getCurrentDbDirectory();
		Connection activeConnection = DBController.getConnection();

		DbRuntimeContext.setCurrentDbDirectory(Path.of(activeDirectory).resolve("other-context").toString());

		assertFalse(DBController.hasOpenConnection());
		assertTrue(activeConnection.isClosed());
		db = DBController.getInstance(activeDirectory);
		assertTrue(DBController.hasOpenConnection());
		assertNotSame(activeConnection, DBController.getConnection());
	}

	@Test
	void boundBackgroundTaskShouldNotCloseDatabaseBeforeRejectedSwitch() throws Exception {
		String activeDirectory = DbRuntimeContext.getCurrentDbDirectory();
		Connection activeConnection = DBController.getConnection();
		String otherDirectory = Path.of(activeDirectory).resolve("background-switch").toString();
		AtomicReference<Throwable> failure = new AtomicReference<>();

		Thread thread = DbRuntimeContext.startBackgroundThread(() -> {
			try {
				DBController.getInstance(otherDirectory);
			} catch (Throwable exception) {
				failure.set(exception);
			}
		}, "database-switch-guard-test");
		thread.join(2_000);

		assertFalse(thread.isAlive());
		assertInstanceOf(GBankingException.class, failure.get());
		assertSame(activeConnection, DBController.getConnection());
		assertTrue(DBController.hasOpenConnection());
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
