package de.zft2.gbanking.db;

import java.sql.Connection;
import java.sql.SQLException;

final class JdbcTransaction {

	private JdbcTransaction() {
	}

	static void run(Connection connection, SqlWork work) throws SQLException {
		boolean oldAutoCommit = connection.getAutoCommit();
		connection.setAutoCommit(false);
		boolean transactionUsable = true;
		Throwable failure = null;
		try {
			work.execute();
			connection.commit();
		} catch (SQLException | RuntimeException exception) {
			failure = exception;
			transactionUsable = rollback(connection, exception);
			throw exception;
		} finally {
			restoreAutoCommit(connection, oldAutoCommit, transactionUsable, failure);
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

	@FunctionalInterface
	interface SqlWork {

		void execute() throws SQLException;
	}
}
