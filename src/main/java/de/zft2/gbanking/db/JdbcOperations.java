package de.zft2.gbanking.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class JdbcOperations implements AutoCloseable {

	private static final int DEFAULT_STATEMENT_CACHE_SIZE = 64;
	private static final int MAXIMUM_BATCH_SIZE = 1_000;

	private final Connection connection;
	private final int maximumCachedStatements;
	private final Map<StatementKey, CachedStatement> statementCache = new LinkedHashMap<>(16, 0.75f, true);
	private QueryObserver queryObserver;
	private boolean closed;

	JdbcOperations(Connection connection) {
		this(connection, DEFAULT_STATEMENT_CACHE_SIZE);
	}

	JdbcOperations(Connection connection, int maximumCachedStatements) {
		if (maximumCachedStatements < 0) {
			throw new IllegalArgumentException("maximumCachedStatements must not be negative");
		}
		this.connection = connection;
		this.maximumCachedStatements = maximumCachedStatements;
	}

	<T> T query(String sql, StatementBinder binder, ResultExtractor<T> extractor) throws SQLException {
		try (StatementLease lease = borrow(sql, GeneratedKeysMode.NONE)) {
			PreparedStatement statement = lease.statement();
			try {
				bind(binder, statement);
				notifyQuery(sql);
				try (ResultSet resultSet = statement.executeQuery()) {
					return extractor.extract(resultSet);
				}
			} catch (SQLException | RuntimeException exception) {
				lease.invalidate();
				throw exception;
			}
		}
	}

	int update(String sql, StatementBinder binder) throws SQLException {
		try (StatementLease lease = borrow(sql, GeneratedKeysMode.NONE)) {
			PreparedStatement statement = lease.statement();
			try {
				bind(binder, statement);
				return statement.executeUpdate();
			} catch (SQLException | RuntimeException exception) {
				lease.invalidate();
				throw exception;
			}
		}
	}

	int insertReturningKey(String sql, StatementBinder binder) throws SQLException {
		try (StatementLease lease = borrow(sql, GeneratedKeysMode.RETURN)) {
			PreparedStatement statement = lease.statement();
			try {
				bind(binder, statement);
				if (statement.executeUpdate() <= 0) {
					throw new SQLException("Database insert did not affect a row");
				}
				try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
					if (generatedKeys.next()) {
						return generatedKeys.getInt(1);
					}
				}
				throw new SQLException("Database insert returned no generated key");
			} catch (SQLException | RuntimeException exception) {
				lease.invalidate();
				throw exception;
			}
		}
	}

	<T> int[] batch(String sql, Iterable<T> items, BatchItemBinder<T> binder) throws SQLException {
		Objects.requireNonNull(items, "items");
		Objects.requireNonNull(binder, "binder");
		try (StatementLease lease = borrow(sql, GeneratedKeysMode.NONE)) {
			PreparedStatement statement = lease.statement();
			try {
				return executeBatch(statement, items, binder);
			} catch (SQLException | RuntimeException exception) {
				lease.invalidate();
				throw exception;
			}
		}
	}

	void execute(String sql) throws SQLException {
		try (StatementLease lease = borrow(sql, GeneratedKeysMode.NONE)) {
			try {
				lease.statement().execute();
			} catch (SQLException | RuntimeException exception) {
				lease.invalidate();
				throw exception;
			}
		}
	}

	int cachedStatementCount() {
		return statementCache.size();
	}

	QueryObserver replaceQueryObserver(QueryObserver observer) {
		QueryObserver previousObserver = queryObserver;
		queryObserver = observer;
		return previousObserver;
	}

	@Override
	public void close() throws SQLException {
		if (closed) {
			return;
		}
		closed = true;
		Exception failure = null;
		for (CachedStatement cachedStatement : statementCache.values()) {
			try {
				cachedStatement.statement().close();
			} catch (SQLException | RuntimeException exception) {
				failure = addFailure(failure, exception);
			}
		}
		statementCache.clear();
		throwFailure(failure);
	}

	private StatementLease borrow(String sql, GeneratedKeysMode generatedKeysMode) throws SQLException {
		if (closed) {
			throw new SQLException("JDBC operations are closed");
		}

		StatementKey key = new StatementKey(sql, generatedKeysMode);
		CachedStatement cachedStatement = statementCache.get(key);
		if (cachedStatement == null && maximumCachedStatements > 0) {
			evictStatementIfRequired();
			if (statementCache.size() < maximumCachedStatements) {
				cachedStatement = new CachedStatement(prepareStatement(key));
				statementCache.put(key, cachedStatement);
			}
		}
		if (cachedStatement == null || cachedStatement.inUse()) {
			return new StatementLease(key, prepareStatement(key), null);
		}

		cachedStatement.setInUse(true);
		return new StatementLease(key, cachedStatement.statement(), cachedStatement);
	}

	private PreparedStatement prepareStatement(StatementKey key) throws SQLException {
		return key.generatedKeysMode() == GeneratedKeysMode.RETURN
				? connection.prepareStatement(key.sql(), Statement.RETURN_GENERATED_KEYS)
				: connection.prepareStatement(key.sql());
	}

	private void evictStatementIfRequired() throws SQLException {
		if (statementCache.size() < maximumCachedStatements) {
			return;
		}
		Iterator<Map.Entry<StatementKey, CachedStatement>> iterator = statementCache.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<StatementKey, CachedStatement> entry = iterator.next();
			if (!entry.getValue().inUse()) {
				iterator.remove();
				entry.getValue().statement().close();
				return;
			}
		}
	}

	private void release(StatementLease lease) throws SQLException {
		CachedStatement cachedStatement = lease.cachedStatement();
		if (lease.invalid()) {
			if (cachedStatement != null) {
				statementCache.remove(lease.key());
			}
			lease.statement().close();
			return;
		}

		Exception failure = reset(lease.statement());
		if (failure != null) {
			if (cachedStatement != null) {
				statementCache.remove(lease.key());
			}
			try {
				lease.statement().close();
			} catch (SQLException | RuntimeException closeFailure) {
				failure.addSuppressed(closeFailure);
			}
			throwFailure(failure);
			return;
		}
		if (cachedStatement == null) {
			lease.statement().close();
		} else {
			cachedStatement.setInUse(false);
		}
	}

	private static Exception reset(PreparedStatement statement) {
		try {
			statement.clearParameters();
		} catch (SQLException | RuntimeException exception) {
			return exception;
		}
		return null;
	}

	private static void bind(StatementBinder binder, PreparedStatement statement) throws SQLException {
		if (binder != null) {
			binder.bind(statement);
		}
	}

	private void notifyQuery(String sql) throws SQLException {
		if (queryObserver != null) {
			queryObserver.executing(sql);
		}
	}

	private static void ensureBatchSucceeded(int[] updateCounts) throws SQLException {
		for (int updateCount : updateCounts) {
			if (updateCount == Statement.EXECUTE_FAILED) {
				throw new SQLException("Database batch update failed");
			}
		}
	}

	private static <T> int[] executeBatch(PreparedStatement statement, Iterable<T> items,
			BatchItemBinder<T> binder) throws SQLException {
		int[] updateCounts = new int[0];
		int count = 0;
		int pendingItems = 0;
		for (T item : items) {
			binder.bind(statement, item);
			statement.addBatch();
			statement.clearParameters();
			pendingItems++;
			if (pendingItems == MAXIMUM_BATCH_SIZE) {
				BatchResult batchResult = executePendingBatch(statement, updateCounts, count);
				updateCounts = batchResult.updateCounts();
				count = batchResult.count();
				pendingItems = 0;
			}
		}
		if (pendingItems > 0) {
			BatchResult batchResult = executePendingBatch(statement, updateCounts, count);
			updateCounts = batchResult.updateCounts();
			count = batchResult.count();
		}
		return count == updateCounts.length ? updateCounts : Arrays.copyOf(updateCounts, count);
	}

	private static BatchResult executePendingBatch(PreparedStatement statement, int[] allUpdateCounts,
			int count) throws SQLException {
		int[] batchUpdateCounts = statement.executeBatch();
		ensureBatchSucceeded(batchUpdateCounts);
		statement.clearBatch();
		int requiredLength = count + batchUpdateCounts.length;
		int[] updateCounts = allUpdateCounts.length >= requiredLength
				? allUpdateCounts
				: Arrays.copyOf(allUpdateCounts, Math.max(requiredLength, Math.max(MAXIMUM_BATCH_SIZE, allUpdateCounts.length * 2)));
		System.arraycopy(batchUpdateCounts, 0, updateCounts, count, batchUpdateCounts.length);
		return new BatchResult(updateCounts, requiredLength);
	}

	private static Exception addFailure(Exception failure, Exception additionalFailure) {
		if (failure == null) {
			return additionalFailure;
		}
		failure.addSuppressed(additionalFailure);
		return failure;
	}

	private static void throwFailure(Exception failure) throws SQLException {
		if (failure instanceof SQLException sqlFailure) {
			throw sqlFailure;
		}
		if (failure instanceof RuntimeException runtimeFailure) {
			throw runtimeFailure;
		}
		if (failure != null) {
			throw new IllegalStateException("Unexpected JDBC cleanup failure", failure);
		}
	}

	@FunctionalInterface
	interface StatementBinder {

		/**
		 * Binds every placeholder of the statement for the current execution.
		 */
		void bind(PreparedStatement statement) throws SQLException;
	}

	@FunctionalInterface
	interface ResultExtractor<T> {

		T extract(ResultSet resultSet) throws SQLException;
	}

	@FunctionalInterface
	interface BatchItemBinder<T> {

		void bind(PreparedStatement statement, T item) throws SQLException;
	}

	@FunctionalInterface
	interface QueryObserver {

		void executing(String sql) throws SQLException;
	}

	private enum GeneratedKeysMode {
		NONE,
		RETURN
	}

	private record StatementKey(String sql, GeneratedKeysMode generatedKeysMode) {
	}

	private record BatchResult(int[] updateCounts, int count) {
	}

	private static final class CachedStatement {

		private final PreparedStatement statement;
		private boolean inUse;

		private CachedStatement(PreparedStatement statement) {
			this.statement = statement;
		}

		PreparedStatement statement() {
			return statement;
		}

		boolean inUse() {
			return inUse;
		}

		void setInUse(boolean inUse) {
			this.inUse = inUse;
		}
	}

	private final class StatementLease implements AutoCloseable {

		private final StatementKey key;
		private final PreparedStatement statement;
		private final CachedStatement cachedStatement;
		private boolean invalid;

		private StatementLease(StatementKey key, PreparedStatement statement, CachedStatement cachedStatement) {
			this.key = key;
			this.statement = statement;
			this.cachedStatement = cachedStatement;
		}

		StatementKey key() {
			return key;
		}

		PreparedStatement statement() {
			return statement;
		}

		CachedStatement cachedStatement() {
			return cachedStatement;
		}

		boolean invalid() {
			return invalid;
		}

		void invalidate() {
			invalid = true;
		}

		@Override
		public void close() throws SQLException {
			release(this);
		}
	}
}
