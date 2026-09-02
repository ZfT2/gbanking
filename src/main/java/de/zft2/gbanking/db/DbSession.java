package de.zft2.gbanking.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

final class DbSession implements AutoCloseable {

	private final Path databaseFile;
	private final Connection connection;
	private final JdbcOperations jdbc;
	private final DaoRepositoryCatalog repositoryCatalog;
	private final DaoRepositoryAdapter repositories;
	private final Set<String> completedSetupStatements = new HashSet<>();
	private final long runtimeSessionGeneration;
	private final boolean prevalidatedMainDatabase;
	private Path instituteDatabaseFile;
	private boolean prevalidatedInstituteDatabase;
	private String instituteDatabaseVersion;
	private boolean closed;
	private boolean invalidated;

	DbSession(Path databaseFile, Connection connection) {
		this(databaseFile, connection, false);
	}

	DbSession(Path databaseFile, Connection connection, boolean prevalidatedMainDatabase) {
		this.databaseFile = Objects.requireNonNull(databaseFile, "databaseFile").toAbsolutePath().normalize();
		this.connection = Objects.requireNonNull(connection, "connection");
		this.prevalidatedMainDatabase = prevalidatedMainDatabase;
		runtimeSessionGeneration = DbRuntimeContext.currentSessionGeneration();
		jdbc = new JdbcOperations(connection);
		repositoryCatalog = new DaoRepositoryCatalog(this);
		repositories = new DaoRepositoryAdapter(repositoryCatalog);
	}

	Path databaseFile() {
		return databaseFile;
	}

	Connection connection() {
		return connection;
	}

	JdbcOperations jdbc() {
		return jdbc;
	}

	DaoRepositoryCatalog repositoryCatalog() {
		return repositoryCatalog;
	}

	DaoRepositoryAdapter repositories() {
		return repositories;
	}

	boolean prevalidatedMainDatabase() {
		return prevalidatedMainDatabase;
	}

	Path instituteDatabaseFile() {
		return instituteDatabaseFile;
	}

	boolean prevalidatedInstituteDatabase() {
		return prevalidatedInstituteDatabase;
	}

	String instituteDatabaseVersion() {
		return instituteDatabaseVersion;
	}

	void setInstituteDatabase(Path databaseFile, DatabaseValidationRegistry.Evidence validationEvidence) {
		instituteDatabaseFile = databaseFile;
		prevalidatedInstituteDatabase = validationEvidence != null;
		instituteDatabaseVersion = validationEvidence != null ? validationEvidence.instituteVersion() : null;
	}

	boolean isOpen() throws SQLException {
		return !closed && !invalidated
				&& DbRuntimeContext.isCurrentSessionGeneration(runtimeSessionGeneration)
				&& !connection.isClosed();
	}

	boolean isInvalidated() {
		return invalidated || !DbRuntimeContext.isCurrentSessionGeneration(runtimeSessionGeneration);
	}

	void invalidate() {
		invalidated = true;
	}

	boolean hasCompletedSetupStatement(String key) {
		return completedSetupStatements.contains(key);
	}

	void rememberCompletedSetupStatement(String key) {
		completedSetupStatements.add(key);
	}

	void forgetCompletedSetupStatement(String key) {
		completedSetupStatements.remove(key);
	}

	@Override
	public void close() throws SQLException {
		if (closed) {
			return;
		}
		invalidated = true;
		Exception failure = null;
		try {
			jdbc.close();
		} catch (SQLException | RuntimeException exception) {
			failure = exception;
		}
		try {
			connection.close();
			closed = true;
		} catch (SQLException | RuntimeException exception) {
			failure = addFailure(failure, exception);
		}
		if (failure instanceof SQLException sqlFailure) {
			throw sqlFailure;
		}
		if (failure instanceof RuntimeException runtimeFailure) {
			throw runtimeFailure;
		}
	}

	private static Exception addFailure(Exception failure, Exception additionalFailure) {
		if (failure == null) {
			return additionalFailure;
		}
		failure.addSuppressed(additionalFailure);
		return failure;
	}
}
