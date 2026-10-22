package de.zft2.gbanking.db;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Counts and records the database statements that are executed by an operation.
 */
public final class DatabaseQueryCounter {

	private DatabaseQueryCounter() {
	}

	public static <T> Measurement<T> measure(Supplier<T> operation) {
		JdbcOperations jdbc = DbConnectionHandler.getSession().jdbc();
		AtomicInteger queryCount = new AtomicInteger();
		List<String> statements = new ArrayList<>();
		JdbcOperations.QueryObserver previousObserver = jdbc.replaceQueryObserver(sql -> {
			queryCount.incrementAndGet();
			statements.add(sql);
		});
		try {
			return new Measurement<>(operation.get(), queryCount.get(), List.copyOf(statements));
		} finally {
			jdbc.replaceQueryObserver(previousObserver);
		}
	}

	public static int count(Runnable operation) {
		return measure(() -> {
			operation.run();
			return null;
		}).queryCount();
	}

	/**
	 * The result of an operation together with the database statements that were executed for it.
	 */
	public record Measurement<T>(T result, int queryCount, List<String> statements) {

		/**
		 * Returns the number of executed statements that contain the given fragment.
		 */
		public int countOfStatementsContaining(String fragment) {
			return (int) statements.stream().filter(statement -> statement.contains(fragment)).count();
		}

		/**
		 * Returns true if one of the executed statements contains the given fragment.
		 */
		public boolean executedStatementContaining(String fragment) {
			return countOfStatementsContaining(fragment) > 0;
		}
	}
}
