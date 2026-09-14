package de.zft2.gbanking.db;

import java.sql.SQLException;

final class JdbcFailures {

	private JdbcFailures() {
	}

	static Exception add(Exception failure, Exception additionalFailure) {
		if (failure == null) {
			return additionalFailure;
		}
		failure.addSuppressed(additionalFailure);
		return failure;
	}

	static void throwIfPresent(Exception failure) throws SQLException {
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
}
