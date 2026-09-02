package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.exception.GBankingException;

class DbTransactionFailureTest {

	private Connection connection;
	private DbSession session;
	private AtomicBoolean connectionClosed;

	@BeforeEach
	void setUp() throws SQLException {
		DBController.resetConnection();
		DbRuntimeContext.setCurrentDbDirectory("transaction-failure-test");
		connection = mock(Connection.class);
		connectionClosed = new AtomicBoolean();
		when(connection.getAutoCommit()).thenReturn(true);
		when(connection.isClosed()).thenAnswer(invocation -> connectionClosed.get());
		doAnswer(invocation -> {
			connectionClosed.set(true);
			return null;
		}).when(connection).close();
		session = new DbSession(Path.of("transaction-failure-test.db"), connection);
		DbConnectionHandler.installSession(session);
	}

	@AfterEach
	void tearDown() {
		session.invalidate();
		DBController.resetConnection();
	}

	@Test
	void committedResultShouldNotBeRetriedWhenAutoCommitRestoreFails() throws SQLException {
		doAnswer(invocation -> {
			if (Boolean.TRUE.equals(invocation.getArgument(0))) {
				throw new SQLException("restore failed");
			}
			return null;
		}).when(connection).setAutoCommit(anyBoolean());

		String result = DbTransactionManager.inTransaction(() -> "committed");

		assertEquals("committed", result);
		assertFalse(session.isOpen());
		verify(connection).commit();
		verify(connection).close();
		assertThrows(GBankingException.class, () -> DbTransactionManager.withAccess(() -> "unreachable"));
	}

	@Test
	void failedRollbackShouldQuarantineSessionWithoutRestoringAutoCommit() throws SQLException {
		doThrow(new SQLException("rollback failed")).when(connection).rollback();

		assertThrows(IllegalStateException.class,
				() -> DbTransactionManager.inTransaction(() -> {
					throw new IllegalStateException("operation failed");
				}));

		assertFalse(session.isOpen());
		verify(connection).rollback();
		verify(connection, never()).setAutoCommit(true);
		verify(connection).close();
	}

	@Test
	void failedCommitShouldQuarantineSessionBecauseOutcomeIsUnknown() throws SQLException {
		doThrow(new SQLException("commit failed")).when(connection).commit();

		GBankingException failure = assertThrows(GBankingException.class,
				() -> DbTransactionManager.inTransaction(() -> "not returned"));

		assertTrue(failure.getMessage().startsWith(
				"Database commit outcome is unknown; the operation must not be retried automatically"));
		assertFalse(session.isOpen());
		verify(connection).commit();
		verify(connection).rollback();
		verify(connection).close();
	}

	@Test
	void failedCommitShouldNotRunEntityRollbackActionsBecauseOutcomeIsUnknown() throws SQLException {
		AtomicBoolean rollbackActionCalled = new AtomicBoolean();
		doThrow(new SQLException("commit failed after unknown outcome")).when(connection).commit();

		assertThrows(GBankingException.class,
				() -> DbTransactionManager.inTransaction(() ->
						DbTransactionManager.onRollback(() -> rollbackActionCalled.set(true))));

		assertFalse(rollbackActionCalled.get());
		verify(connection).rollback();
		verify(connection).close();
	}

	@Test
	void uncheckedCommitFailureShouldQuarantineSessionBecauseOutcomeIsUnknown() throws SQLException {
		doThrow(new IllegalStateException("driver commit failed")).when(connection).commit();

		GBankingException failure = assertThrows(GBankingException.class,
				() -> DbTransactionManager.inTransaction(() -> "not returned"));

		assertTrue(failure.getMessage().startsWith(
				"Database commit outcome is unknown; the operation must not be retried automatically"));
		assertFalse(session.isOpen());
		verify(connection).rollback();
		verify(connection).close();
	}

	@Test
	void uncheckedTransactionStateFailureShouldQuarantineSession() throws SQLException {
		doThrow(new IllegalStateException("driver state failed")).when(connection).getAutoCommit();

		GBankingException failure = assertThrows(GBankingException.class,
				() -> DbTransactionManager.inTransaction(() -> "not executed"));

		assertTrue(failure.getMessage().startsWith("Error reading database transaction state"));
		assertFalse(session.isOpen());
		verify(connection, never()).commit();
		verify(connection).close();
	}

	@Test
	void resetShouldRetainUnclosedSessionForCloseRetry() throws SQLException {
		doThrow(new SQLException("connection close failed"))
				.doAnswer(invocation -> {
					connectionClosed.set(true);
					return null;
				})
				.when(connection).close();
		session.invalidate();

		assertThrows(GBankingException.class, DBController::resetConnection);
		assertFalse(DBController.hasOpenConnection());

		DBController.resetConnection();

		verify(connection, times(2)).close();
	}

	@Test
	void failedTransactionStartShouldQuarantineSession() throws SQLException {
		doThrow(new SQLException("start failed")).when(connection).setAutoCommit(false);

		GBankingException failure = assertThrows(GBankingException.class,
				() -> DbTransactionManager.inTransaction(() -> "not executed"));

		assertTrue(failure.getMessage().startsWith("Error starting database transaction"));
		assertFalse(session.isOpen());
		verify(connection, never()).commit();
		verify(connection, never()).rollback();
		verify(connection).close();
	}

	@Test
	void unmanagedTransactionShouldQuarantineSession() throws SQLException {
		when(connection.getAutoCommit()).thenReturn(false);

		GBankingException failure = assertThrows(GBankingException.class,
				() -> DbTransactionManager.inTransaction(() -> "not executed"));

		assertTrue(failure.getMessage().startsWith(
				"Database connection already has an unmanaged transaction"));
		assertFalse(session.isOpen());
		verify(connection, never()).commit();
		verify(connection, never()).rollback();
		verify(connection).close();
	}

	@Test
	void rollbackActionErrorShouldBeSuppressedWithoutMaskingOperationFailure() throws SQLException {
		IllegalStateException operationFailure = new IllegalStateException("operation failed");
		AssertionError rollbackActionFailure = new AssertionError("rollback action failed");

		IllegalStateException result = assertThrows(IllegalStateException.class,
				() -> DbTransactionManager.inTransaction(() -> {
					DbTransactionManager.onRollback(() -> {
						throw rollbackActionFailure;
					});
					throw operationFailure;
				}));

		assertEquals(operationFailure, result);
		assertEquals(rollbackActionFailure, result.getSuppressed()[0]);
		verify(connection).rollback();
		verify(connection).setAutoCommit(true);
		assertTrue(session.isOpen());
	}
}
