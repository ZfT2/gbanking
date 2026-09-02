package de.zft2.gbanking.db;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.tenant.TenantPaths;

public final class DbRuntimeContext {

	private static Path currentDbDirectory = Paths.get(".");
	private static Path currentDataDirectory;
	private static Path currentAccountStatementsDirectory;
	private static long sessionGeneration = 1;
	private static boolean databaseSessionActive = true;
	private static final ThreadLocal<DatabaseSession> BOUND_SESSION = new ThreadLocal<>();
	private static final Map<Long, Integer> ACTIVE_BACKGROUND_TASKS = new HashMap<>();

	private DbRuntimeContext() {
	}

	public static void setCurrentDbDirectory(String dbDirectory) {
		DbTransactionManager.withLifecycleLock(() -> {
			DbTransactionManager.verifyLifecycleChangeAllowed();
			synchronized (DbRuntimeContext.class) {
				Path normalizedDirectory = normalize(dbDirectory);
				switchContext(normalizedDirectory, null, null);
			}
		});
	}

	public static void setCurrentTenantPaths(TenantPaths tenantPaths) {
		Objects.requireNonNull(tenantPaths, "tenantPaths");
		DbTransactionManager.withLifecycleLock(() -> {
			DbTransactionManager.verifyLifecycleChangeAllowed();
			Path normalizedDirectory = tenantPaths.databaseDirectory().toAbsolutePath().normalize();
			Path normalizedDataDirectory = tenantPaths.dataDirectory().toAbsolutePath().normalize();
			Path normalizedAccountStatementsDirectory = tenantPaths.accountStatementsDirectory()
					.toAbsolutePath().normalize();
			synchronized (DbRuntimeContext.class) {
				switchContext(normalizedDirectory, normalizedDataDirectory,
						normalizedAccountStatementsDirectory);
			}
		});
	}

	public static String resolveDbDirectory(String requestedDbDirectory) {
		return DbTransactionManager.withLifecycleLock(() -> {
			synchronized (DbRuntimeContext.class) {
				if (usesCurrentDbDirectory(requestedDbDirectory)) {
					if (!databaseSessionActive) {
						DbTransactionManager.verifyLifecycleChangeAllowed();
						switchContext(currentDbDirectory, currentDataDirectory,
								currentAccountStatementsDirectory);
					}
					return currentDbDirectory.toString();
				}

				Path normalizedDirectory = normalize(requestedDbDirectory);
				DbTransactionManager.verifyLifecycleChangeAllowed();
				switchContext(normalizedDirectory, null, null);
				return currentDbDirectory.toString();
			}
		});
	}

	static String resolveDbDirectoryWithoutActivation(String requestedDbDirectory) {
		return DbTransactionManager.withLifecycleLock(() -> {
			synchronized (DbRuntimeContext.class) {
				if (usesCurrentDbDirectory(requestedDbDirectory)) {
					return currentDbDirectory.toString();
				}
				return normalize(requestedDbDirectory).toString();
			}
		});
	}

	static String resolveDbDirectoryForSessionAccess(String requestedDbDirectory) {
		return DbTransactionManager.withLifecycleLock(() -> {
			synchronized (DbRuntimeContext.class) {
				Path resolvedDirectory = usesCurrentDbDirectory(requestedDbDirectory)
						? currentDbDirectory
						: normalize(requestedDbDirectory);
				verifyBoundSessionForDatabaseAccess(resolvedDirectory);
				return resolvedDirectory.toString();
			}
		});
	}

	public static String getCurrentDbDirectory() {
		return DbTransactionManager.withLifecycleLock(() -> {
			synchronized (DbRuntimeContext.class) {
				return currentDbDirectory.toString();
			}
		});
	}

	public static synchronized Optional<Path> getCurrentDataDirectory() {
		return Optional.ofNullable(currentDataDirectory);
	}

	public static synchronized Optional<Path> getCurrentAccountStatementsDirectory() {
		return Optional.ofNullable(currentAccountStatementsDirectory);
	}

	public static Thread startBackgroundThread(Runnable operation, String threadName) {
		return startBackgroundThread(operation, threadName, Runnable::run);
	}

	public static Thread startBackgroundThread(Runnable operation, String threadName, Consumer<Runnable> completionScheduler) {
		Objects.requireNonNull(operation, "operation");
		Objects.requireNonNull(completionScheduler, "completionScheduler");
		DatabaseSession session = DbTransactionManager.withLifecycleLock(DbRuntimeContext::registerBackgroundTask);
		Runnable boundOperation = () -> {
			DatabaseSession previousSession = BOUND_SESSION.get();
			BOUND_SESSION.set(session);
			try {
				operation.run();
			} finally {
				restoreBoundSession(previousSession);
				scheduleBackgroundTaskCompletion(session, completionScheduler);
			}
		};

		boolean started = false;
		try {
			Thread thread = threadName == null ? new Thread(boundOperation) : new Thread(boundOperation, threadName);
			thread.setDaemon(true);
			thread.start();
			started = true;
			return thread;
		} finally {
			if (!started) {
				unregisterBackgroundTask(session);
			}
		}
	}

	public static synchronized boolean hasActiveBackgroundTasks() {
		return ACTIVE_BACKGROUND_TASKS.getOrDefault(sessionGeneration, 0) > 0;
	}

	static synchronized void verifyDatabaseAccess() {
		if (!databaseSessionActive) {
			throw new GBankingException("No active database session");
		}
		DatabaseSession boundSession = BOUND_SESSION.get();
		if (boundSession != null && !boundSession.matchesCurrentContext()) {
			throw new GBankingException("Database session changed while background task was running");
		}
	}

	static synchronized long currentSessionGeneration() {
		return sessionGeneration;
	}

	static synchronized boolean isCurrentSessionGeneration(long generation) {
		return databaseSessionActive && sessionGeneration == generation;
	}

	static synchronized void invalidateDatabaseSession() {
		databaseSessionActive = false;
		sessionGeneration++;
		clearTenantDirectories();
	}

	static synchronized void verifyLifecycleAccess() {
		DatabaseSession boundSession = BOUND_SESSION.get();
		if (boundSession != null && !boundSession.matchesCurrentContext()) {
			throw new GBankingException("Stale background task must not change the database lifecycle");
		}
	}

	private static synchronized DatabaseSession registerBackgroundTask() {
		verifyDatabaseAccess();
		DatabaseSession session = new DatabaseSession(currentDbDirectory, currentDataDirectory,
				currentAccountStatementsDirectory, sessionGeneration);
		ACTIVE_BACKGROUND_TASKS.merge(session.generation(), 1, Integer::sum);
		return session;
	}

	private static synchronized void unregisterBackgroundTask(DatabaseSession session) {
		ACTIVE_BACKGROUND_TASKS.computeIfPresent(session.generation(), (generation, count) -> count > 1 ? count - 1 : null);
	}

	private static void scheduleBackgroundTaskCompletion(DatabaseSession session, Consumer<Runnable> completionScheduler) {
		boolean scheduled = false;
		try {
			completionScheduler.accept(() -> unregisterBackgroundTask(session));
			scheduled = true;
		} finally {
			if (!scheduled) {
				unregisterBackgroundTask(session);
			}
		}
	}

	private static void restoreBoundSession(DatabaseSession previousSession) {
		if (previousSession == null) {
			BOUND_SESSION.remove();
		} else {
			BOUND_SESSION.set(previousSession);
		}
	}

	private static void verifyBoundSessionBeforeChange(Path requestedDirectory, Path requestedDataDirectory,
			Path requestedAccountStatementsDirectory) {
		DatabaseSession boundSession = BOUND_SESSION.get();
		if (boundSession != null && (!boundSession.matchesCurrentContext()
				|| !boundSession.matchesTarget(requestedDirectory, requestedDataDirectory,
						requestedAccountStatementsDirectory))) {
			throw new GBankingException("Background task must not change the active database session");
		}
	}

	private static void verifyBoundSessionForDatabaseAccess(Path requestedDirectory) {
		DatabaseSession boundSession = BOUND_SESSION.get();
		if (boundSession != null
				&& (!boundSession.matchesCurrentContext() || !currentDbDirectory.equals(requestedDirectory))) {
			throw new GBankingException("Background task must not access another database session");
		}
	}

	private static boolean matchesCurrentContext(Path dbDirectory, Path dataDirectory,
			Path accountStatementsDirectory) {
		return databaseSessionActive && currentDbDirectory.equals(dbDirectory)
				&& Objects.equals(currentDataDirectory, dataDirectory)
				&& Objects.equals(currentAccountStatementsDirectory, accountStatementsDirectory);
	}

	private static void switchContext(Path dbDirectory, Path dataDirectory,
			Path accountStatementsDirectory) {
		verifyBoundSessionBeforeChange(dbDirectory, dataDirectory, accountStatementsDirectory);
		if (!matchesCurrentContext(dbDirectory, dataDirectory, accountStatementsDirectory)) {
			DbConnectionHandler.closeForRuntimeContextChange();
			activateNewSession(dbDirectory, dataDirectory, accountStatementsDirectory);
		}
	}

	private static void activateNewSession(Path dbDirectory, Path dataDirectory,
			Path accountStatementsDirectory) {
		sessionGeneration++;
		currentDbDirectory = dbDirectory;
		currentDataDirectory = dataDirectory;
		currentAccountStatementsDirectory = accountStatementsDirectory;
		databaseSessionActive = true;
	}

	private static void clearTenantDirectories() {
		currentDataDirectory = null;
		currentAccountStatementsDirectory = null;
	}

	private static boolean usesCurrentDbDirectory(String requestedDbDirectory) {
		return requestedDbDirectory == null || requestedDbDirectory.isBlank()
				|| ".".equals(requestedDbDirectory.trim());
	}

	private static Path normalize(String dbDirectory) {
		if (dbDirectory == null || dbDirectory.isBlank()) {
			return Paths.get(".");
		}
		String normalizedDirectory = dbDirectory.trim().replace('\\', '/');
		return Paths.get(normalizedDirectory).normalize();
	}

	private record DatabaseSession(Path dbDirectory, Path dataDirectory,
			Path accountStatementsDirectory, long generation) {

		boolean matchesCurrentContext() {
			return generation == sessionGeneration
					&& matchesTarget(currentDbDirectory, currentDataDirectory,
							currentAccountStatementsDirectory);
		}

		boolean matchesTarget(Path requestedDirectory, Path requestedDataDirectory,
				Path requestedAccountStatementsDirectory) {
			return dbDirectory.equals(requestedDirectory)
					&& Objects.equals(dataDirectory, requestedDataDirectory)
					&& Objects.equals(accountStatementsDirectory, requestedAccountStatementsDirectory);
		}
	}
}
