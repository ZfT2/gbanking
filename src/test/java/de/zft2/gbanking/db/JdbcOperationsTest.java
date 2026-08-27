package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;

class JdbcOperationsTest {

	private static final String UPDATE_SQL = "UPDATE sample SET value = ? WHERE id = 1";

	@Test
	void repeatedExecutionShouldReusePreparedStatement() throws Exception {
		Connection connection = mock(Connection.class);
		PreparedStatement statement = mock(PreparedStatement.class);
		when(connection.prepareStatement(UPDATE_SQL)).thenReturn(statement);
		when(statement.executeUpdate()).thenReturn(1);

		try (JdbcOperations jdbc = new JdbcOperations(connection, 2)) {
			assertEquals(1, jdbc.update(UPDATE_SQL, preparedStatement -> preparedStatement.setInt(1, 10)));
			assertEquals(1, jdbc.update(UPDATE_SQL, preparedStatement -> preparedStatement.setInt(1, 20)));
			assertEquals(1, jdbc.cachedStatementCount());
		}

		verify(connection).prepareStatement(UPDATE_SQL);
		verify(statement, times(2)).executeUpdate();
		verify(statement).close();
	}

	@Test
	void cacheShouldEvictLeastRecentlyUsedStatement() throws Exception {
		String firstSql = "UPDATE first_table SET value = 1";
		String secondSql = "UPDATE second_table SET value = 1";
		Connection connection = mock(Connection.class);
		PreparedStatement firstStatement = mock(PreparedStatement.class);
		PreparedStatement secondStatement = mock(PreparedStatement.class);
		when(connection.prepareStatement(firstSql)).thenReturn(firstStatement);
		when(connection.prepareStatement(secondSql)).thenReturn(secondStatement);
		when(firstStatement.executeUpdate()).thenReturn(1);
		when(secondStatement.executeUpdate()).thenReturn(1);

		try (JdbcOperations jdbc = new JdbcOperations(connection, 1)) {
			jdbc.update(firstSql, null);
			jdbc.update(secondSql, null);

			assertEquals(1, jdbc.cachedStatementCount());
			verify(firstStatement).close();
		}

		verify(secondStatement).close();
	}

	@Test
	void failedBatchShouldInvalidateCachedStatement() throws Exception {
		Connection connection = mock(Connection.class);
		PreparedStatement failedStatement = mock(PreparedStatement.class);
		PreparedStatement replacementStatement = mock(PreparedStatement.class);
		when(connection.prepareStatement(UPDATE_SQL)).thenReturn(failedStatement, replacementStatement);
		when(failedStatement.executeBatch()).thenReturn(new int[] { 1, Statement.EXECUTE_FAILED });
		when(replacementStatement.executeUpdate()).thenReturn(1);

		try (JdbcOperations jdbc = new JdbcOperations(connection, 2)) {
			SQLException exception = assertThrows(SQLException.class,
					() -> jdbc.batch(UPDATE_SQL, List.of(10, 20),
							(statement, value) -> statement.setInt(1, value)));

			assertEquals("Database batch update failed", exception.getMessage());
			assertEquals(0, jdbc.cachedStatementCount());
			verify(failedStatement).close();

			assertEquals(1, jdbc.update(UPDATE_SQL, statement -> statement.setInt(1, 30)));
			assertEquals(1, jdbc.cachedStatementCount());
		}

		verify(connection, times(2)).prepareStatement(UPDATE_SQL);
		verify(replacementStatement).close();
	}

	@Test
	void largeBatchShouldExecuteInFixedChunksAndRetainUpdateCountOrder() throws Exception {
		Connection connection = mock(Connection.class);
		PreparedStatement statement = mock(PreparedStatement.class);
		when(connection.prepareStatement(UPDATE_SQL)).thenReturn(statement);
		when(statement.executeBatch())
				.thenReturn(repeatedUpdateCounts(1_000, 1), repeatedUpdateCounts(1, 2));

		try (JdbcOperations jdbc = new JdbcOperations(connection, 1)) {
			int[] updateCounts = jdbc.batch(UPDATE_SQL, java.util.stream.IntStream.rangeClosed(1, 1_001).boxed().toList(),
					(preparedStatement, value) -> preparedStatement.setInt(1, value));

			assertEquals(1_001, updateCounts.length);
			assertEquals(1, updateCounts[0]);
			assertEquals(1, updateCounts[999]);
			assertEquals(2, updateCounts[1_000]);
		}

		verify(statement, times(2)).executeBatch();
		verify(statement, times(2)).clearBatch();
	}

	@Test
	void updateShouldOnlyClearParametersBeforeReturningStatementToCache() throws Exception {
		Connection connection = mock(Connection.class);
		PreparedStatement statement = mock(PreparedStatement.class);
		when(connection.prepareStatement(UPDATE_SQL)).thenReturn(statement);
		when(statement.executeUpdate()).thenReturn(1);

		try (JdbcOperations jdbc = new JdbcOperations(connection, 1)) {
			jdbc.update(UPDATE_SQL, preparedStatement -> preparedStatement.setInt(1, 1));
		}

		verify(statement).clearParameters();
		verify(statement, times(0)).clearBatch();
		verify(statement, times(0)).clearWarnings();
	}

	@Test
	void uncheckedExecutionFailureShouldInvalidateCachedStatement() throws Exception {
		Connection connection = mock(Connection.class);
		PreparedStatement failedStatement = mock(PreparedStatement.class);
		PreparedStatement replacementStatement = mock(PreparedStatement.class);
		when(connection.prepareStatement(UPDATE_SQL)).thenReturn(failedStatement, replacementStatement);
		when(failedStatement.executeUpdate()).thenThrow(new IllegalStateException("driver failure"));
		when(replacementStatement.executeUpdate()).thenReturn(1);

		try (JdbcOperations jdbc = new JdbcOperations(connection, 2)) {
			assertThrows(IllegalStateException.class, () -> jdbc.update(UPDATE_SQL, null));
			assertEquals(0, jdbc.cachedStatementCount());
			verify(failedStatement).close();

			assertEquals(1, jdbc.update(UPDATE_SQL, null));
		}

		verify(connection, times(2)).prepareStatement(UPDATE_SQL);
		verify(replacementStatement).close();
	}

	@Test
	void cacheCloseShouldContinueAfterUncheckedStatementFailure() throws Exception {
		String secondSql = "UPDATE second_table SET value = 1";
		Connection connection = mock(Connection.class);
		PreparedStatement firstStatement = mock(PreparedStatement.class);
		PreparedStatement secondStatement = mock(PreparedStatement.class);
		when(connection.prepareStatement(UPDATE_SQL)).thenReturn(firstStatement);
		when(connection.prepareStatement(secondSql)).thenReturn(secondStatement);
		when(firstStatement.executeUpdate()).thenReturn(1);
		when(secondStatement.executeUpdate()).thenReturn(1);
		doThrow(new IllegalStateException("statement close failed")).when(firstStatement).close();
		JdbcOperations jdbc = new JdbcOperations(connection, 2);
		jdbc.update(UPDATE_SQL, null);
		jdbc.update(secondSql, null);

		assertThrows(IllegalStateException.class, jdbc::close);

		verify(firstStatement).close();
		verify(secondStatement).close();
		assertEquals(0, jdbc.cachedStatementCount());
	}

	@Test
	void sessionCloseShouldCloseCacheAndConnectionOnlyOnce() throws Exception {
		Connection connection = mock(Connection.class);
		PreparedStatement statement = mock(PreparedStatement.class);
		when(connection.prepareStatement(UPDATE_SQL)).thenReturn(statement);
		when(statement.executeUpdate()).thenReturn(1);
		DbSession session = new DbSession(Path.of("database.db"), connection);
		session.jdbc().update(UPDATE_SQL, null);

		session.close();
		session.close();

		assertFalse(session.isOpen());
		assertThrows(SQLException.class, () -> session.jdbc().execute("SELECT 1"));
		verify(statement).close();
		verify(connection).close();
	}

	@Test
	void sessionCloseShouldStillCloseConnectionWhenCacheCloseFails() throws Exception {
		Connection connection = mock(Connection.class);
		PreparedStatement statement = mock(PreparedStatement.class);
		when(connection.prepareStatement(UPDATE_SQL)).thenReturn(statement);
		when(statement.executeUpdate()).thenReturn(1);
		doThrow(new SQLException("statement close failed")).when(statement).close();
		doThrow(new SQLException("connection close failed")).when(connection).close();
		DbSession session = new DbSession(Path.of("database.db"), connection);
		session.jdbc().update(UPDATE_SQL, null);

		SQLException exception = assertThrows(SQLException.class, session::close);

		assertEquals("statement close failed", exception.getMessage());
		assertEquals(1, exception.getSuppressed().length);
		assertEquals("connection close failed", exception.getSuppressed()[0].getMessage());
		assertFalse(session.isOpen());
		verify(connection).close();
	}

	@Test
	void sessionCloseShouldRetryConnectionAfterCloseFailure() throws Exception {
		Connection connection = mock(Connection.class);
		doThrow(new SQLException("connection close failed"))
				.doNothing()
				.when(connection).close();
		DbSession session = new DbSession(Path.of("database.db"), connection);

		assertThrows(SQLException.class, session::close);
		session.close();

		assertFalse(session.isOpen());
		verify(connection, times(2)).close();
	}

	private static int[] repeatedUpdateCounts(int count, int value) {
		int[] updateCounts = new int[count];
		java.util.Arrays.fill(updateCounts, value);
		return updateCounts;
	}
}
