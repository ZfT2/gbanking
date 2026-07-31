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
		Connection connection = requireConnection();
		boolean oldAutoCommit = getAutoCommit(connection);
		if (!oldAutoCommit) {
			throw new GBankingException("Database connection already has an unmanaged transaction");
		}
		TransactionState state = new TransactionState();
		RuntimeException runtimeFailure = null;
		boolean committed = false;

		try {
			connection.setAutoCommit(false);
			TRANSACTION_STATE.set(state);
			CancellationSupport.throwIfCancellationRequested();
			T result = operation.get();
			CancellationSupport.throwIfCancellationRequested();
			if (state.rollbackOnly()) {
				throw rollbackOnlyException(state);
			}
			connection.commit();
			committed = true;
			return result;
		} catch (SQLException exception) {
			GBankingException failure = new GBankingException("Error committing database transaction", exception);
			runtimeFailure = failure;
			rollback(connection, state, failure);
			throw failure;
		} catch (RuntimeException exception) {
			runtimeFailure = exception;
			rollback(connection, state, exception);
			throw exception;
		} finally {
			TRANSACTION_STATE.remove();
			boolean uncaughtFailure = runtimeFailure == null && !committed;
			if (uncaughtFailure) {
				rollbackAfterUncaughtFailure(connection, state);
			}
			restoreAutoCommit(connection, oldAutoCommit, runtimeFailure, uncaughtFailure);
		}
	}

	private static Connection requireConnection() {
		Connection connection = DbConnectionHandler.getConnection();
		try {
			if (connection == null || connection.isClosed()) {
				throw new GBankingException("No open database connection");
			}
			return connection;
		} catch (SQLException exception) {
			throw new GBankingException("Error checking database connection", exception);
		}
	}

	private static boolean getAutoCommit(Connection connection) {
		try {
			return connection.getAutoCommit();
		} catch (SQLException exception) {
			throw new GBankingException("Error reading database transaction state", exception);
		}
	}

	private static GBankingException rollbackOnlyException(TransactionState state) {
		RuntimeException cause = state.rollbackCause();
		return cause != null
				? new GBankingException("Database transaction was marked for rollback", cause)
				: new GBankingException("Database transaction was marked for rollback");
	}

	private static void rollback(Connection connection, TransactionState state, Throwable originalFailure) {
		try {
			connection.rollback();
		} catch (SQLException rollbackFailure) {
			originalFailure.addSuppressed(rollbackFailure);
			log.error("Error rolling back database transaction", rollbackFailure);
		}
		state.runRollbackActions(originalFailure);
	}

	private static void rollbackAfterUncaughtFailure(Connection connection, TransactionState state) {
		GBankingException failure = new GBankingException("Database transaction aborted by an unrecoverable failure");
		rollback(connection, state, failure);
	}

	private static void restoreAutoCommit(Connection connection, boolean oldAutoCommit, RuntimeException runtimeFailure,
			boolean uncaughtFailure) {
		try {
			connection.setAutoCommit(oldAutoCommit);
		} catch (SQLException exception) {
			if (runtimeFailure != null) {
				runtimeFailure.addSuppressed(exception);
				log.error("Error restoring auto commit after failed database transaction", exception);
				return;
			}
			if (uncaughtFailure) {
				log.error("Error restoring auto commit after unrecoverable database failure", exception);
				return;
			}
			throw new GBankingException("Error restoring auto commit after database transaction", exception);
		}
	}

	private static final class TransactionState {

		private boolean rollbackOnly;
		private RuntimeException rollbackCause;
		private final List<Runnable> rollbackActions = new ArrayList<>();

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
				} catch (RuntimeException rollbackActionFailure) {
					originalFailure.addSuppressed(rollbackActionFailure);
					log.error("Error restoring entity state after database rollback", rollbackActionFailure);
				}
			}
		}
	}
}
