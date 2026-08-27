package de.zft2.gbanking.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

class DbDdlSetup {

	private DbDdlSetup() {
	}

	static void setupDB(Connection connection) throws SQLException {
		boolean oldAutoCommit = requireAutoCommit(connection);
		connection.setAutoCommit(false);
		boolean transactionUsable = true;
		Throwable failure = null;
		try {
			executeBaseline(connection);
			DbMigrationRunner.markFreshSchemaAsApplied(connection);
			connection.commit();
		} catch (SQLException | RuntimeException exception) {
			failure = exception;
			transactionUsable = rollback(connection, exception);
			throw exception;
		} finally {
			restoreAutoCommit(connection, oldAutoCommit, transactionUsable, failure);
		}
	}

	private static boolean requireAutoCommit(Connection connection) throws SQLException {
		boolean autoCommit = connection.getAutoCommit();
		if (!autoCommit) {
			throw new SQLException("Database setup requires an auto-commit connection");
		}
		return autoCommit;
	}

	private static void executeBaseline(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			for (String sql : SqlTemplateRepository.getMainBaselineStatements()) {
				statement.addBatch(sql);
			}
			statement.executeBatch();
		}
	}

	private static boolean rollback(Connection connection, Throwable originalFailure) {
		try {
			connection.rollback();
			return true;
		} catch (SQLException | RuntimeException rollbackFailure) {
			originalFailure.addSuppressed(rollbackFailure);
			return false;
		}
	}

	private static void restoreAutoCommit(Connection connection, boolean oldAutoCommit,
			boolean transactionUsable, Throwable originalFailure) throws SQLException {
		if (!transactionUsable) {
			return;
		}
		try {
			connection.setAutoCommit(oldAutoCommit);
		} catch (SQLException | RuntimeException restoreFailure) {
			if (originalFailure == null) {
				if (restoreFailure instanceof SQLException sqlFailure) {
					throw sqlFailure;
				}
				throw restoreFailure;
			}
			originalFailure.addSuppressed(restoreFailure);
		}
	}

}
