package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.BankAccess;

class JdbcDaoRepositoryTest {

	private static final String SELECT_BY_IDS = "SELECT id FROM sample WHERE id IN (%s)";

	@Test
	void idQueriesShouldUseReusableChunksBelowThePortableSqliteParameterLimit() throws Exception {
		Connection connection = mock(Connection.class);
		List<String> preparedSql = new ArrayList<>();
		List<PreparedStatement> statements = new ArrayList<>();
		when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
			preparedSql.add(invocation.getArgument(0));
			PreparedStatement statement = mock(PreparedStatement.class);
			when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
			statements.add(statement);
			return statement;
		});
		DbSession session = new DbSession(Path.of("query-chunk-test.db"), connection);

		new TestRepository(session).consume(IntStream.rangeClosed(1, 1_901).boxed().toList());
		session.close();

		assertEquals(2, preparedSql.size());
		assertEquals(900, placeholderCount(preparedSql.get(0)));
		assertEquals(101, placeholderCount(preparedSql.get(1)));
		verify(statements.get(0), times(2)).executeQuery();
		verify(statements.get(1)).executeQuery();
	}

	@Test
	void repositoryUpdateShouldRequireExactlyOneAffectedRow() throws Exception {
		Connection connection = mock(Connection.class);
		PreparedStatement statement = mock(PreparedStatement.class);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(statement.executeUpdate()).thenReturn(0);
		BankAccess bankAccess = TestData.createSampleBankAccess("10020030");
		bankAccess.setId(42);

		try (DbSession session = new DbSession(Path.of("repository-update-test.db"), connection)) {
			SQLException exception = assertThrows(SQLException.class,
					() -> new JdbcDaoRepository<>(BankAccess.class, session)
							.executeWrite(bankAccess, StatementType.UPDATE));
			assertEquals("Database update did not affect exactly one row", exception.getMessage());
		}
	}

	@Test
	void exactBatchValidationShouldAcceptOneAndSuccessNoInfo() {
		assertDoesNotThrow(() -> DbExecutor.validateSingleRowBatch(
				new int[] { 1, Statement.SUCCESS_NO_INFO }, 2));
	}

	@Test
	void exactBatchValidationShouldRejectMissingOrAmbiguousUpdates() {
		assertThrows(SQLException.class,
				() -> DbExecutor.validateSingleRowUpdate(0));
		assertThrows(SQLException.class,
				() -> DbExecutor.validateSingleRowUpdate(2));
		assertThrows(SQLException.class,
				() -> DbExecutor.validateSingleRowBatch(new int[] { 1 }, 2));
		assertThrows(SQLException.class,
				() -> DbExecutor.validateSingleRowBatch(new int[] { 0 }, 1));
		assertThrows(SQLException.class,
				() -> DbExecutor.validateSingleRowBatch(new int[] { 2 }, 1));
	}

	private static long placeholderCount(String sql) {
		return sql.chars().filter(character -> character == '?').count();
	}

	private static final class TestRepository extends JdbcDaoRepository<BankAccess> {

		private TestRepository(DbSession session) {
			super(BankAccess.class, session);
		}

		private void consume(Collection<Integer> ids) throws SQLException {
			consumeByIds(SELECT_BY_IDS, ids, resultSet -> null);
		}
	}
}
