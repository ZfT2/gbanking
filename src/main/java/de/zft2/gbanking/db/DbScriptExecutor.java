package de.zft2.gbanking.db;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

import de.zft2.gbanking.exception.GBankingException;

public final class DbScriptExecutor {

	private DbScriptExecutor() {
	}

	public static void execute(List<String> statements) {
		Objects.requireNonNull(statements, "statements");
		DbTransactionManager.inTransaction(() -> executeStatements(statements));
	}

	private static void executeStatements(List<String> statements) {
		try (Statement statement = DbConnectionHandler.getConnection().createStatement()) {
			for (String sql : statements) {
				if (!sql.isBlank()) {
					statement.executeUpdate(sql);
				}
			}
		} catch (SQLException exception) {
			throw new GBankingException("Error executing SQL script", exception);
		}
	}
}
