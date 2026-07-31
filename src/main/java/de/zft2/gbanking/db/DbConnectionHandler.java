package de.zft2.gbanking.db;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sqlite.Function;
import org.sqlite.SQLiteConfig;

import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.tenant.TenantPaths;
import de.zft2.gbanking.util.AppPaths;

abstract class DbConnectionHandler {

    private static final Logger log = LogManager.getLogger(DbConnectionHandler.class);

    private static final String INSTITUTE_DB_ALIAS = "institute_db";
    private static final String INSTITUTE_DB_FILE_NAME = "institute.db";
    private static final String MAIN_DB_ALIAS = "main";
    private static final String SQL_FUNCTION_EQUALS_IGNORE_CASE = "gb_equals_ignore_case";
	private static final String SQL_MAIN_QUICK_CHECK = "PRAGMA main.quick_check";
	private static final String SQL_MAIN_INTEGRITY_CHECK = "PRAGMA main.integrity_check";
	private static final String SQL_INSTITUTE_QUICK_CHECK = "PRAGMA institute_db.quick_check";
	private static final String SQL_INSTITUTE_INTEGRITY_CHECK = "PRAGMA institute_db.integrity_check";
	private static final String SQL_MAIN_CHECKPOINT = "PRAGMA main.wal_checkpoint(TRUNCATE)";
    private static final int SQLITE_BUSY_TIMEOUT_MILLIS = 5_000;

    protected static Connection connection;
    private static String currentDatabasePath;
    private static boolean shutdownHookRegistered;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            log.error("Error loading JDBC-driver", e);
        }
    }

    DbConnectionHandler() {
    }

    public static Connection getConnection() {
        return connection;
    }

    static void initialize(String dbFilePath) {
        initialize(dbFilePath, null);
    }

    static void initialize(String dbFilePath, DbMigrationProgressListener migrationProgressListener) {
        initialize(dbFilePath, migrationProgressListener, false);
    }

    static void initialize(String dbFilePath, DbMigrationProgressListener migrationProgressListener, boolean allowMissingInstituteDatabase) {
        DbTransactionManager.withLifecycleLock(() -> {
            String resolvedDbFilePath = DbRuntimeContext.resolveDbDirectory(dbFilePath);
            DbRuntimeContext.verifyDatabaseAccess();
            initializeLocked(resolvedDbFilePath, migrationProgressListener, allowMissingInstituteDatabase);
        });
    }

    private static void initializeLocked(String dbFilePath, DbMigrationProgressListener migrationProgressListener,
            boolean allowMissingInstituteDatabase) {
        Path dbDirectory = AppPaths.resolveInApplicationDirectory(dbFilePath);
        Path dbFile = dbDirectory.resolve("gbanking.db").toAbsolutePath().normalize();
        String path = dbFile.toString();

        if (isCurrentConnection(path)) {
            return;
        }

        closeCurrentConnection();
        ensureParentDirectoryExists(dbFile);

        if (Files.exists(dbFile)) {
            initDBConnection(path, false, migrationProgressListener, allowMissingInstituteDatabase);
        } else {
            log.info("Creating new database file: {}", dbFile.getFileName());
            log.debug("Creating new database at {}", dbFile);
            initDBConnection(path, true, migrationProgressListener, allowMissingInstituteDatabase);
        }

        currentDatabasePath = path;
        registerShutdownHook();
    }

    public static boolean hasPendingMigrations(String dbFilePath) {
        return DbTransactionManager.withLifecycleLock(() -> hasPendingMigrationsLocked(DbRuntimeContext.resolveDbDirectory(dbFilePath)));
    }

    private static boolean hasPendingMigrationsLocked(String dbFilePath) {
        Path dbDirectory = AppPaths.resolveInApplicationDirectory(dbFilePath);
        Path dbFile = dbDirectory.resolve("gbanking.db").toAbsolutePath().normalize();
        if (!Files.exists(dbFile)) {
            return false;
        }

        try (Connection migrationCheckConnection = createConnection(dbFile.toString())) {
			verifyIntegrity(migrationCheckConnection, DatabaseAlias.MAIN, false);
            return DbMigrationRunner.hasPendingMigrations(migrationCheckConnection);
        } catch (SQLException e) {
            throw new GBankingException("Error checking pending database migrations", e);
        }
    }

	static void validateDatabaseFileIntegrity(Path databaseFile, boolean fullIntegrityCheck) {
		DbTransactionManager.withLifecycleLock(() -> {
			DbRuntimeContext.verifyLifecycleAccess();
			Path normalizedDatabaseFile = databaseFile.toAbsolutePath().normalize();
			if (!Files.isRegularFile(normalizedDatabaseFile)) {
				return;
			}

			try (Connection validationConnection = createConnection(normalizedDatabaseFile.toString())) {
				verifyIntegrity(validationConnection, DatabaseAlias.MAIN, fullIntegrityCheck);
				checkpoint(validationConnection);
			} catch (SQLException exception) {
				throw new DatabaseIntegrityException("Database integrity validation failed", exception);
			}
		});
	}

    public static void resetConnection() {
        DbTransactionManager.withLifecycleLock(() -> {
            DbRuntimeContext.verifyLifecycleAccess();
            resetConnectionLocked();
        });
    }

    public static boolean resetConnectionIfIdle() {
        return DbTransactionManager.withLifecycleLock(() -> {
            DbRuntimeContext.verifyLifecycleAccess();
            if (DbRuntimeContext.hasActiveBackgroundTasks()) {
                return false;
            }
            resetConnectionLocked();
            return true;
        });
    }

    private static void resetConnectionLocked() {
        closeCurrentConnection();
        currentDatabasePath = null;
        DbRuntimeContext.invalidateDatabaseSession();
    }

    private static void initDBConnection(String path, boolean setupDB, DbMigrationProgressListener migrationProgressListener,
            boolean allowMissingInstituteDatabase) {
        try {
            log.info("Creating Connection to Database...");
            connection = createConnection(path);
			registerSqlFunctions();
			verifyIntegrity(connection, DatabaseAlias.MAIN, false);
            attachInstituteDatabase(Path.of(path).getParent(), allowMissingInstituteDatabase);
			verifyIntegrity(connection, DatabaseAlias.INSTITUTE, false);
            Path dbPath = Path.of(path);
            log.info("Using database file: {}", dbPath.getFileName());
            log.debug("Using database path: {}", dbPath);
            if (!connection.isClosed()) {
                log.info("...Connection established");
                log.info("Foreign Keys enabled: {}", () -> executeConfigStatement("foreign_keys", "PRAGMA foreign_keys"));
            }
            ensureInstituteDatabaseSchema();
            if (setupDB) {
                if (!DbDdlSetup.setupDB()) {
                    throw new GBankingException("Error in initialisation of database connection: setup DB failed");
                }
                DbMigrationRunner.markFreshSchemaAsApplied(connection);
            }
            DbMigrationRunner.migrate(connection, migrationProgressListener);
		} catch (SQLException e) {
			GBankingException failure = new GBankingException("Error in initialisation of database connection:", e);
			closeFailedConnection(failure);
			throw failure;
		} catch (RuntimeException e) {
			closeFailedConnection(e);
			throw e;
        }
    }

    private static Connection createConnection(String path) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setDateClass("TEXT");
		config.setBusyTimeout(SQLITE_BUSY_TIMEOUT_MILLIS);
        return DriverManager.getConnection("jdbc:sqlite:" + path, config.toProperties());
    }

	private static void registerSqlFunctions() throws SQLException {
		Function.create(connection, SQL_FUNCTION_EQUALS_IGNORE_CASE, new CaseInsensitiveEqualsFunction(), 2, Function.FLAG_DETERMINISTIC);
	}

	private static final class CaseInsensitiveEqualsFunction extends Function {

		@Override
		protected void xFunc() throws SQLException {
			String firstValue = value_text(0);
			String secondValue = value_text(1);
			result(firstValue != null && secondValue != null && firstValue.equalsIgnoreCase(secondValue) ? 1 : 0);
		}
	}

	private enum DatabaseAlias {
		MAIN(MAIN_DB_ALIAS, SQL_MAIN_QUICK_CHECK, SQL_MAIN_INTEGRITY_CHECK),
		INSTITUTE(INSTITUTE_DB_ALIAS, SQL_INSTITUTE_QUICK_CHECK, SQL_INSTITUTE_INTEGRITY_CHECK);

		private final String alias;
		private final String quickCheckSql;
		private final String integrityCheckSql;

		DatabaseAlias(String alias, String quickCheckSql, String integrityCheckSql) {
			this.alias = alias;
			this.quickCheckSql = quickCheckSql;
			this.integrityCheckSql = integrityCheckSql;
		}

		private String integritySql(boolean fullIntegrityCheck) {
			return fullIntegrityCheck ? integrityCheckSql : quickCheckSql;
		}

		@Override
		public String toString() {
			return alias;
		}
	}

	private static void verifyIntegrity(Connection connectionToCheck, DatabaseAlias databaseAlias, boolean fullIntegrityCheck) throws SQLException {
		boolean resultReturned = false;
		try (Statement statement = connectionToCheck.createStatement();
				ResultSet resultSet = statement.executeQuery(databaseAlias.integritySql(fullIntegrityCheck))) {
			while (resultSet.next()) {
				resultReturned = true;
				String result = resultSet.getString(1);
				if (!"ok".equalsIgnoreCase(result)) {
					throw new DatabaseIntegrityException("Database integrity check failed for " + databaseAlias + ": " + result);
				}
			}
		}
		if (!resultReturned) {
			throw new DatabaseIntegrityException("Database integrity check returned no result for " + databaseAlias);
		}
	}

	private static void checkpoint(Connection connectionToCheckpoint) throws SQLException {
		try (Statement statement = connectionToCheckpoint.createStatement();
				ResultSet resultSet = statement.executeQuery(SQL_MAIN_CHECKPOINT)) {
			if (!resultSet.next() || resultSet.getInt(1) != 0) {
				throw new DatabaseIntegrityException("Database WAL checkpoint failed for " + MAIN_DB_ALIAS);
			}
		}
	}

	private static void closeFailedConnection(Throwable originalFailure) {
		Connection failedConnection = connection;
		connection = null;
		currentDatabasePath = null;
		if (failedConnection == null) {
			return;
		}
		try {
			failedConnection.close();
		} catch (SQLException closeFailure) {
			originalFailure.addSuppressed(closeFailure);
			log.error("Error closing database connection after failed initialization", closeFailure);
		}
	}

    static boolean prepareInstituteDatabaseFile(Path dataDirectory) {
        return prepareInstituteDatabaseFile(dataDirectory, bundledInstituteDatabaseFile());
    }

    static boolean prepareInstituteDatabaseFile(Path dataDirectory, Path template) {
        Path instituteDbFile = dataDirectory.resolve(INSTITUTE_DB_FILE_NAME).toAbsolutePath().normalize();
        Optional<String> templateVersion = readInstituteDatabaseVersion(template);
        Optional<String> installedVersion = readInstituteDatabaseVersion(instituteDbFile);

        if (templateVersion.isEmpty()) {
            log.error("Bundled institute database is missing or unusable: {}", template);
            return installedVersion.isPresent();
        }
        if (installedVersion.isPresent() && templateVersion.get().compareTo(installedVersion.get()) <= 0) {
            return true;
        }
        return installInstituteDatabase(template, instituteDbFile, installedVersion.isPresent());
    }

    private static void attachInstituteDatabase(Path dbDirectory, boolean allowMissingInstituteDatabase) {
        Path instituteDbFile = resolveInstituteDatabaseFile(dbDirectory);
        boolean existingInstituteDatabase = Files.isRegularFile(instituteDbFile);
        Optional<Path> configuredDataDirectory = DbRuntimeContext.getCurrentDataDirectory();
		boolean useFile = !allowMissingInstituteDatabase;
		if (useFile && configuredDataDirectory.isPresent()
				&& !prepareInstituteDatabaseFile(configuredDataDirectory.get())) {
			throw new GBankingException("Institute database is unavailable: " + instituteDbFile);
		}
		if (useFile && configuredDataDirectory.isEmpty() && !existingInstituteDatabase) {
			ensureParentDirectoryExists(instituteDbFile);
		}
        String databaseLocation = useFile ? instituteDbFile.toString() : ":memory:";

        try (PreparedStatement statement = connection.prepareStatement("ATTACH DATABASE ? AS " + INSTITUTE_DB_ALIAS)) {
            statement.setString(1, databaseLocation);
            statement.executeUpdate();
            if (useFile) {
                log.info("Using institute database: {} (existing: {})", instituteDbFile, existingInstituteDatabase);
            } else {
                log.warn("Continuing without a persistent institute database; bank names cannot be loaded.");
            }
        } catch (SQLException e) {
            throw new GBankingException("Error attaching institute database", e);
        }
    }

    private static Path resolveInstituteDatabaseFile(Path dbDirectory) {
        Optional<Path> configuredDataDirectory = DbRuntimeContext.getCurrentDataDirectory();
        if (configuredDataDirectory.isPresent()) {
            return configuredDataDirectory.get().resolve(INSTITUTE_DB_FILE_NAME).toAbsolutePath().normalize();
        }
        if (dbDirectory == null) {
            return AppPaths.resolveInApplicationDirectory("data").resolve(INSTITUTE_DB_FILE_NAME).toAbsolutePath().normalize();
        }

        Path normalizedDbDirectory = dbDirectory.toAbsolutePath().normalize();
        if (normalizedDbDirectory.equals(AppPaths.getApplicationBaseDirectory())) {
            return normalizedDbDirectory.resolve("data").resolve(INSTITUTE_DB_FILE_NAME).toAbsolutePath().normalize();
        }

        Optional<Path> dataDirectory = TenantPaths.findDataDirectory(normalizedDbDirectory);
        if (dataDirectory.isPresent()) {
            return dataDirectory.get().resolve(INSTITUTE_DB_FILE_NAME).toAbsolutePath().normalize();
        }
        return dbDirectory.resolve(INSTITUTE_DB_FILE_NAME).toAbsolutePath().normalize();
    }

	private static boolean installInstituteDatabase(Path template, Path instituteDbFile, boolean update) {
		if (template.equals(instituteDbFile)) {
			return true;
		}
		ensureParentDirectoryExists(instituteDbFile);
		Path temporaryFile = instituteDbFile.resolveSibling(INSTITUTE_DB_FILE_NAME + ".tmp");
		try {
			Files.copy(template, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
			if (readInstituteDatabaseVersion(temporaryFile).isEmpty()) {
				throw new IOException("Copied institute database is unusable");
			}
			moveInstituteDatabaseTemplate(temporaryFile, instituteDbFile);
			log.info("{} institute database from bundled template: {}", update ? "Updated" : "Initialized", instituteDbFile);
			return true;
		} catch (IOException exception) {
			deleteTemporaryInstituteDatabase(temporaryFile, exception);
			log.error("Could not install bundled institute database at {}", instituteDbFile, exception);
			return update;
		}
	}

	private static Path bundledInstituteDatabaseFile() {
		return AppPaths.resolveInApplicationDirectory("data").resolve(INSTITUTE_DB_FILE_NAME).toAbsolutePath().normalize();
	}

	private static Optional<String> readInstituteDatabaseVersion(Path databaseFile) {
		if (!Files.isRegularFile(databaseFile)) {
			return Optional.empty();
		}
		try (Connection versionConnection = createConnection(databaseFile.toString())) {
			verifyIntegrity(versionConnection, DatabaseAlias.MAIN, false);
			try (Statement statement = versionConnection.createStatement();
					ResultSet resultSet = statement.executeQuery("SELECT MAX(updatedAt) FROM importHistory")) {
				String latestUpdate = resultSet.next() ? resultSet.getString(1) : null;
				return Optional.ofNullable(latestUpdate);
			}
		} catch (SQLException exception) {
			log.warn("Could not read institute database version from {}", databaseFile, exception);
			return Optional.empty();
		}
	}

	private static void moveInstituteDatabaseTemplate(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void deleteTemporaryInstituteDatabase(Path temporaryFile, IOException originalFailure) {
		try {
			Files.deleteIfExists(temporaryFile);
		} catch (IOException cleanupFailure) {
			originalFailure.addSuppressed(cleanupFailure);
		}
	}

    private static void ensureInstituteDatabaseSchema() {
		String currentSql = null;
		try (Statement statement = connection.createStatement()) {
			currentSql = "SQL_SETUP_CREATE_INSTITUTE_STATUS";
			statement.executeUpdate(SqlTemplateRepository.getDdl(currentSql));
			currentSql = "SQL_SETUP_INSERT_INSTITUTE_STATUS";
			statement.executeUpdate(SqlTemplateRepository.getDdl(currentSql));
			currentSql = "SQL_SETUP_CREATE_IMPORT_HISTORY";
			statement.executeUpdate(SqlTemplateRepository.getDdl(currentSql));
			currentSql = "SQL_SETUP_CREATE_INSTITUTE";
			statement.executeUpdate(SqlTemplateRepository.getDdl(currentSql));
			currentSql = "SQL_SETUP_CREATE_INSTITUTE_DK";
			statement.executeUpdate(SqlTemplateRepository.getDdl(currentSql));
			currentSql = "SQL_SETUP_CREATE_INSTITUTE_DBB";
			statement.executeUpdate(SqlTemplateRepository.getDdl(currentSql));
			currentSql = "SQL_SETUP_CREATE_INSTITUTE_EPC";
			statement.executeUpdate(SqlTemplateRepository.getDdl(currentSql));
			currentSql = "SQL_SETUP_CREATE_TRIGGER_INSTITUTEDK_VALIDATE_UNIQUE_BLZ_IMPORTNUMBER_INSERT";
			statement.executeUpdate(SqlTemplateRepository.getDdl(currentSql));
			currentSql = "SQL_SETUP_CREATE_TRIGGER_INSTITUTEDK_VALIDATE_UNIQUE_BLZ_IMPORTNUMBER_UPDATE";
			statement.executeUpdate(SqlTemplateRepository.getDdl(currentSql));
			currentSql = "SQL_SETUP_CREATE_TRIGGER_INSTITUTEDBB_VALIDATE_UNIQUE_BLZ_DATASETNUMBER_INSERT";
			statement.executeUpdate(SqlTemplateRepository.getDdl(currentSql));
			currentSql = "SQL_SETUP_CREATE_TRIGGER_INSTITUTEDBB_VALIDATE_UNIQUE_BLZ_DATASETNUMBER_UPDATE";
			statement.executeUpdate(SqlTemplateRepository.getDdl(currentSql));

        } catch (SQLException e) {
			throw new GBankingException("Error creating institute database schema for statement: %s", e, currentSql);
        }
    }

    private static boolean isCurrentConnection(String path) {
        try {
            return connection != null && !connection.isClosed() && path.equals(currentDatabasePath);
        } catch (SQLException e) {
            log.warn("Could not inspect current database connection", e);
            return false;
        }
    }

    private static void ensureParentDirectoryExists(Path dbFile) {
        try {
            Path parentDirectory = dbFile.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }
        } catch (Exception e) {
            throw new GBankingException("Error in initialisation of database connection: could not create DB directory", e);
        }
    }

    private static void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(DbConnectionHandler::shutdownConnection));
        shutdownHookRegistered = true;
    }

	private static void shutdownConnection() {
		try {
			DbTransactionManager.withLifecycleLock(() -> {
				closeCurrentConnection();
				currentDatabasePath = null;
				DbRuntimeContext.invalidateDatabaseSession();
			});
		} catch (RuntimeException e) {
			log.error("Error closing database connection during JVM shutdown", e);
		}
	}

    private static void closeCurrentConnection() {
        Connection connectionToClose = connection;
        if (connectionToClose == null) {
            return;
        }
        try {
            if (connectionToClose.isClosed()) {
                connection = null;
                return;
            }
            if (!connectionToClose.getAutoCommit()) {
                log.warn("Rolling back unmanaged transaction before closing database connection");
                connectionToClose.rollback();
                connectionToClose.setAutoCommit(true);
            }
			checkpoint(connectionToClose);
            connectionToClose.close();
            if (!connectionToClose.isClosed()) {
                throw new GBankingException("Database connection did not close cleanly");
            }
            connection = null;
            log.info("Connection to Database closed");
        } catch (SQLException e) {
            throw new GBankingException("Error closing database connection", e);
        }
    }

    private static String executeConfigStatement(String columnHeader, String sql) {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                return rs.getString(columnHeader);
            }
        } catch (SQLException e) {
            log.error("Error executing database config statement: {}", sql, e);
        }
        return null;
    }

    protected void closeStatement(Statement statement) {
        try {
            if (statement != null) {
                statement.close();
            }
        } catch (SQLException e) {
            log.error("Error closing (Prepared) Statement: {}", e.getMessage());
        }
    }
}
