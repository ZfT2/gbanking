package de.zft2.gbanking.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.concurrent.CancellationSupport;
import de.zft2.gbanking.exception.GBankingException;

final class DbTransactionManager {

	private static final Logger log = LogManager.getLogger(DbTransactionManager.class);
	private static final ReentrantLock DB_LOCK = new ReentrantLock(true);
	private static final ThreadLocal<TransactionState> TRANSACTION_STATE = new ThreadLocal<>();

	private DbTransactionManager() {
	}

	static <T> T withAccess(Supplier<T> operation) {
		DB_LOCK.lock();
		try {
			CancellationSupport.throwIfCancellationRequested();
			DbRuntimeContext.verifyDatabaseAccess();
			requireSession();
			T result = operation.get();
			CancellationSupport.throwIfCancellationRequested();
			return result;
		} finally {
			DB_LOCK.unlock();
		}
	}

	static void withAccess(Runnable operation) {
		withAccess(() -> {
			operation.run();
			return null;
		});
	}

	static <T> T inTransaction(Supplier<T> operation) {
		DB_LOCK.lock();
		try {
			CancellationSupport.throwIfCancellationRequested();
			DbRuntimeContext.verifyDatabaseAccess();
			TransactionState existingState = TRANSACTION_STATE.get();
			if (existingState != null) {
				existingState.verifySession(requireSession());
				return executeNested(existingState, operation);
			}
			return executeOutermost(operation);
		} finally {
			DB_LOCK.unlock();
		}
	}

	static void inTransaction(Runnable operation) {
		inTransaction(() -> {
			operation.run();
			return null;
		});
	}

	static void onRollback(Runnable rollbackAction) {
		TransactionState state = TRANSACTION_STATE.get();
		if (state == null) {
			throw new IllegalStateException("Rollback actions require an active database transaction");
		}
		state.addRollbackAction(rollbackAction);
	}

	static <T> T withLifecycleLock(Supplier<T> operation) {
		DB_LOCK.lock();
		try {
			return operation.get();
		} finally {
			DB_LOCK.unlock();
		}
	}

	static void withLifecycleLock(Runnable operation) {
		withLifecycleLock(() -> {
			operation.run();
			return null;
		});
	}

	static void verifyLifecycleChangeAllowed() {
		if (TRANSACTION_STATE.get() != null) {
			throw new GBankingException("Database lifecycle changes are not allowed inside a transaction");
		}
	}

	private static <T> T executeNested(TransactionState state, Supplier<T> operation) {
		boolean completed = false;
		try {
			CancellationSupport.throwIfCancellationRequested();
			T result = operation.get();
			CancellationSupport.throwIfCancellationRequested();
			completed = true;
			return result;
		} catch (RuntimeException exception) {
			state.markRollbackOnly(exception);
			throw exception;
		} finally {
			if (!completed) {
				state.markRollbackOnly(null);
			}
		}
	}

	private static <T> T executeOutermost(Supplier<T> operation) {
		DbSession session = requireSession();
		Connection connection = session.connection();
		TransactionState state = new TransactionState(session);
		boolean oldAutoCommit = getAutoCommit(connection, state);
		if (!oldAutoCommit) {
			GBankingException failure = new GBankingException(
					"Database connection already has an unmanaged transaction");
			quarantineSession(state, failure);
			throw failure;
		}
		beginTransaction(connection, state);
		TRANSACTION_STATE.set(state);
		try {
			T result = executeOperation(connection, oldAutoCommit, state, operation);
			commitTransaction(connection, state);
			restoreAfterCommit(connection, oldAutoCommit, state);
			return result;
		} finally {
			TRANSACTION_STATE.remove();
		}
	}

	private static void beginTransaction(Connection connection, TransactionState state) {
		try {
			connection.setAutoCommit(false);
		} catch (SQLException | RuntimeException exception) {
			GBankingException failure = new GBankingException("Error starting database transaction", exception);
			quarantineSession(state, failure);
			throw failure;
		}
	}

	private static <T> T executeOperation(Connection connection, boolean oldAutoCommit, TransactionState state,
			Supplier<T> operation) {
		try {
			CancellationSupport.throwIfCancellationRequested();
			T result = operation.get();
			CancellationSupport.throwIfCancellationRequested();
			if (state.rollbackOnly()) {
				throw rollbackOnlyException(state);
			}
			return result;
		} catch (RuntimeException | Error failure) {
			recoverAfterOperationFailure(connection, oldAutoCommit, state, failure);
			throw failure;
		}
	}

	private static void commitTransaction(Connection connection, TransactionState state) {
		try {
			connection.commit();
		} catch (SQLException | RuntimeException exception) {
			GBankingException failure = new GBankingException(
					"Database commit outcome is unknown; the operation must not be retried automatically",
					exception);
			rollback(connection, state, failure, false);
			quarantineSession(state, failure);
			throw failure;
		}
	}

	private static void recoverAfterOperationFailure(Connection connection, boolean oldAutoCommit,
			TransactionState state, Throwable failure) {
		if (!rollback(connection, state, failure, true)) {
			quarantineSession(state, failure);
			return;
		}
		try {
			connection.setAutoCommit(oldAutoCommit);
		} catch (SQLException | RuntimeException restoreFailure) {
			failure.addSuppressed(restoreFailure);
			log.error("Error restoring auto commit after failed database transaction", restoreFailure);
			quarantineSession(state, failure);
		}
	}

	private static void restoreAfterCommit(Connection connection, boolean oldAutoCommit, TransactionState state) {
		try {
			connection.setAutoCommit(oldAutoCommit);
		} catch (SQLException | RuntimeException exception) {
			log.error("Database transaction was committed, but auto commit could not be restored", exception);
			quarantineSession(state, exception);
		}
	}

	private static DbSession requireSession() {
		DbSession session = DbConnectionHandler.getSession();
		if (session == null) {
			throw new GBankingException("No open database connection");
		}
		boolean open;
		try {
			open = session.isOpen();
		} catch (SQLException | RuntimeException exception) {
			closeUnusableSession(session, exception);
			throw new GBankingException("Error checking database connection", exception);
		}
		if (!open) {
			GBankingException failure = new GBankingException("No open database connection");
			closeUnusableSession(session, failure);
			throw failure;
		}
		return session;
	}

	private static boolean getAutoCommit(Connection connection, TransactionState state) {
		try {
			return connection.getAutoCommit();
		} catch (SQLException | RuntimeException exception) {
			GBankingException failure = new GBankingException("Error reading database transaction state", exception);
			quarantineSession(state, failure);
			throw failure;
		}
	}

	private static GBankingException rollbackOnlyException(TransactionState state) {
		RuntimeException cause = state.rollbackCause();
		return cause != null
				? new GBankingException("Database transaction was marked for rollback", cause)
				: new GBankingException("Database transaction was marked for rollback");
	}

	private static boolean rollback(Connection connection, TransactionState state, Throwable originalFailure,
			boolean runRollbackActions) {
		boolean successful = true;
		try {
			connection.rollback();
		} catch (SQLException | RuntimeException rollbackFailure) {
			originalFailure.addSuppressed(rollbackFailure);
			log.error("Error rolling back database transaction", rollbackFailure);
			successful = false;
		}
		if (runRollbackActions) {
			state.runRollbackActions(originalFailure);
		}
		return successful;
	}

	private static void quarantineSession(TransactionState state, Throwable originalFailure) {
		closeUnusableSession(state.session, originalFailure);
	}

	private static void closeUnusableSession(DbSession session, Throwable originalFailure) {
		session.invalidate();
		try {
			session.close();
		} catch (SQLException | RuntimeException closeFailure) {
			originalFailure.addSuppressed(closeFailure);
			log.error("Error closing unusable database session", closeFailure);
		}
	}

	private static final class TransactionState {

		private final DbSession session;
		private boolean rollbackOnly;
		private RuntimeException rollbackCause;
		private final List<Runnable> rollbackActions = new ArrayList<>();

		private TransactionState(DbSession session) {
			this.session = session;
		}

		void verifySession(DbSession currentSession) {
			if (session != currentSession) {
				throw new GBankingException("Database session changed inside a transaction");
			}
		}

		boolean rollbackOnly() {
			return rollbackOnly;
		}

		RuntimeException rollbackCause() {
			return rollbackCause;
		}

		void markRollbackOnly(RuntimeException exception) {
			rollbackOnly = true;
			if (rollbackCause == null) {
				rollbackCause = exception;
			}
		}

		void addRollbackAction(Runnable rollbackAction) {
			rollbackActions.add(rollbackAction);
		}

		void runRollbackActions(Throwable originalFailure) {
			for (int index = rollbackActions.size() - 1; index >= 0; index--) {
				try {
					rollbackActions.get(index).run();
				} catch (RuntimeException | Error rollbackActionFailure) {
					originalFailure.addSuppressed(rollbackActionFailure);
					log.error("Error restoring entity state after database rollback", rollbackActionFailure);
				}
			}
		}
	}
}
