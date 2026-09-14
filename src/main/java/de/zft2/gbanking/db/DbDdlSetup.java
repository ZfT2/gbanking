package de.zft2.gbanking.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import de.zft2.gbanking.db.repository.SqlTemplateRepository;

class DbDdlSetup {

	private DbDdlSetup() {
	}

	static void setupDB(Connection connection) throws SQLException {
		requireAutoCommit(connection);
		JdbcTransaction.run(connection, () -> {
			executeBaseline(connection);
			DbMigrationRunner.markFreshSchemaAsApplied(connection);
		});
	}

	private static void requireAutoCommit(Connection connection) throws SQLException {
		if (!connection.getAutoCommit()) {
			throw new SQLException("Database setup requires an auto-commit connection");
		}
	}

	private static void executeBaseline(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			for (String sql : SqlTemplateRepository.getMainBaselineStatements()) {
				statement.addBatch(sql);
			}
			statement.executeBatch();
		}
	}

}
