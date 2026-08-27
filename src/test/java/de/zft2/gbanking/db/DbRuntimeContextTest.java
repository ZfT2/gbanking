package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.tenant.TenantPaths;

class DbRuntimeContextTest {

	@AfterEach
	void resetContext() {
		DbRuntimeContext.setCurrentDbDirectory(".");
	}

	@Test
	void setCurrentDbDirectory_shouldNormalizePath() {
		DbRuntimeContext.setCurrentDbDirectory("db/tenant/../tenant-a");

		assertEquals(Paths.get("db", "tenant-a").toString(), DbRuntimeContext.getCurrentDbDirectory());
	}

	@Test
	void resolveDbDirectory_shouldReturnCurrentDirectoryForBlankInput() {
		DbRuntimeContext.setCurrentDbDirectory("db\\tenant-a");

		assertEquals(Paths.get("db", "tenant-a").toString(), DbRuntimeContext.resolveDbDirectory(null));
		assertEquals(Paths.get("db", "tenant-a").toString(), DbRuntimeContext.resolveDbDirectory(" "));
		assertEquals(Paths.get("db", "tenant-a").toString(), DbRuntimeContext.resolveDbDirectory("."));
	}

	@Test
	void resolveDbDirectory_shouldUpdateCurrentDirectoryForExplicitInput() {
		String resolved = DbRuntimeContext.resolveDbDirectory("db/tenant-b/../tenant-c");

		assertEquals(Paths.get("db", "tenant-c").toString(), resolved);
		assertEquals(Paths.get("db", "tenant-c").toString(), DbRuntimeContext.getCurrentDbDirectory());
	}

	@Test
	void resolveDbDirectoryWithoutActivationShouldNotUpdateCurrentDirectory() {
		DbRuntimeContext.setCurrentDbDirectory("db/tenant-a");

		String resolved = DbRuntimeContext.resolveDbDirectoryWithoutActivation("db/tenant-b/../tenant-c");

		assertEquals(Paths.get("db", "tenant-c").toString(), resolved);
		assertEquals(Paths.get("db", "tenant-a").toString(), DbRuntimeContext.getCurrentDbDirectory());
	}

	@Test
	void settingIdenticalDatabaseContextShouldPreserveGeneration() {
		DbRuntimeContext.setCurrentDbDirectory("db/tenant-a");
		long generation = DbRuntimeContext.currentSessionGeneration();

		DbRuntimeContext.setCurrentDbDirectory("db/tenant-a");

		assertEquals(generation, DbRuntimeContext.currentSessionGeneration());
	}

	@Test
	void settingIdenticalTenantContextShouldPreserveGeneration() {
		TenantPaths paths = new TenantPaths(
				Path.of("data", TenantPaths.TENANT_DIRECTORY_NAME, "tenant-a"), Path.of("tenant-db"));
		DbRuntimeContext.setCurrentTenantPaths(paths);
		long generation = DbRuntimeContext.currentSessionGeneration();

		DbRuntimeContext.setCurrentTenantPaths(paths);

		assertEquals(generation, DbRuntimeContext.currentSessionGeneration());
	}

	@Test
	void invalidTenantPathsShouldNotPartiallyChangeContext() {
		DbRuntimeContext.setCurrentDbDirectory("db/tenant-a");
		String activeDirectory = DbRuntimeContext.getCurrentDbDirectory();
		long generation = DbRuntimeContext.currentSessionGeneration();
		TenantPaths invalidPaths = new TenantPaths(Path.of("tenant"), Path.of("other-db"));

		assertThrows(IllegalStateException.class,
				() -> DbRuntimeContext.setCurrentTenantPaths(invalidPaths));

		assertEquals(activeDirectory, DbRuntimeContext.getCurrentDbDirectory());
		assertEquals(generation, DbRuntimeContext.currentSessionGeneration());
	}

	@Test
	void backgroundTaskShouldNotChangeTenantDirectoriesForSameDatabase() throws Exception {
		Path sharedDatabaseDirectory = Path.of("shared-db");
		TenantPaths activePaths = new TenantPaths(
				Path.of("data-a", TenantPaths.TENANT_DIRECTORY_NAME, "tenant-a"), sharedDatabaseDirectory);
		TenantPaths otherPaths = new TenantPaths(
				Path.of("data-b", TenantPaths.TENANT_DIRECTORY_NAME, "tenant-b"), sharedDatabaseDirectory);
		DbRuntimeContext.setCurrentTenantPaths(activePaths);
		AtomicReference<Throwable> failure = new AtomicReference<>();

		Thread thread = DbRuntimeContext.startBackgroundThread(() -> {
			try {
				DbRuntimeContext.setCurrentTenantPaths(otherPaths);
			} catch (Throwable exception) {
				failure.set(exception);
			}
		}, "tenant-context-change-guard-test");
		thread.join(2_000);

		assertFalse(thread.isAlive());
		assertInstanceOf(GBankingException.class, failure.get());
		assertEquals(activePaths.dataDirectory().toAbsolutePath().normalize(),
				DbRuntimeContext.getCurrentDataDirectory().orElseThrow());
	}

	@Test
	void staleBackgroundTaskShouldNotCheckPendingMigrations() throws Exception {
		DbRuntimeContext.setCurrentDbDirectory("db/tenant-a");
		CountDownLatch taskStarted = new CountDownLatch(1);
		CountDownLatch continueTask = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread thread = DbRuntimeContext.startBackgroundThread(() -> {
			taskStarted.countDown();
			await(continueTask);
			try {
				DBController.hasPendingMigrations(".");
			} catch (Throwable exception) {
				failure.set(exception);
			}
		}, "stale-migration-check-guard-test");

		assertTrue(taskStarted.await(2, TimeUnit.SECONDS));
		DbRuntimeContext.setCurrentDbDirectory("db/tenant-b");
		continueTask.countDown();
		thread.join(2_000);

		assertFalse(thread.isAlive());
		assertInstanceOf(GBankingException.class, failure.get());
	}

	@Test
	void backgroundTaskShouldKeepItsDatabaseSessionToken() throws Exception {
		CountDownLatch taskStarted = new CountDownLatch(1);
		CountDownLatch continueTask = new CountDownLatch(1);
		AtomicReference<Throwable> accessFailure = new AtomicReference<>();

		Thread thread = DbRuntimeContext.startBackgroundThread(() -> {
			taskStarted.countDown();
			await(continueTask);
			try {
				DbRuntimeContext.verifyDatabaseAccess();
			} catch (Throwable failure) {
				accessFailure.set(failure);
			}
		}, "database-session-token-test");

		assertTrue(taskStarted.await(2, TimeUnit.SECONDS));
		DbRuntimeContext.setCurrentDbDirectory("db/another-tenant");
		continueTask.countDown();
		thread.join(2_000);

		assertFalse(thread.isAlive());
		assertInstanceOf(GBankingException.class, accessFailure.get());
	}

	@Test
	void connectionResetShouldBeRejectedWhileBackgroundTaskIsActive() throws Exception {
		CountDownLatch taskStarted = new CountDownLatch(1);
		CountDownLatch continueTask = new CountDownLatch(1);
		Thread thread = DbRuntimeContext.startBackgroundThread(() -> {
			taskStarted.countDown();
			await(continueTask);
		}, "database-reset-guard-test");

		assertTrue(taskStarted.await(2, TimeUnit.SECONDS));
		assertFalse(DBController.resetConnectionIfIdle());
		assertTrue(DbRuntimeContext.hasActiveBackgroundTasks());

		continueTask.countDown();
		thread.join(2_000);
		assertFalse(thread.isAlive());
		assertFalse(DbRuntimeContext.hasActiveBackgroundTasks());
	}

	@Test
	void backgroundTaskShouldRemainActiveUntilScheduledCompletionRuns() throws Exception {
		AtomicReference<Runnable> scheduledCompletion = new AtomicReference<>();

		Thread thread = DbRuntimeContext.startBackgroundThread(() -> {
			// No work required; the completion scheduling itself is under test.
		}, "database-completion-scheduler-test", scheduledCompletion::set);
		thread.join(2_000);

		assertFalse(thread.isAlive());
		assertTrue(DbRuntimeContext.hasActiveBackgroundTasks());
		scheduledCompletion.get().run();
		assertFalse(DbRuntimeContext.hasActiveBackgroundTasks());
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
