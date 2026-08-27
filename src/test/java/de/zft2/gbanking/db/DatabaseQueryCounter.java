package de.zft2.gbanking.db;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

final class DatabaseQueryCounter {

	private DatabaseQueryCounter() {
	}

	static <T> Measurement<T> measure(Supplier<T> operation) {
		JdbcOperations jdbc = DbConnectionHandler.getSession().jdbc();
		AtomicInteger queryCount = new AtomicInteger();
		JdbcOperations.QueryObserver previousObserver = jdbc.replaceQueryObserver(
				sql -> queryCount.incrementAndGet());
		try {
			return new Measurement<>(operation.get(), queryCount.get());
		} finally {
			jdbc.replaceQueryObserver(previousObserver);
		}
	}

	record Measurement<T>(T result, int queryCount) {
	}
}
