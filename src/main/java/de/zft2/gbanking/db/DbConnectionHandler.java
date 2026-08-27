package de.zft2.gbanking.db;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sqlite.Function;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.JournalMode;
import org.sqlite.SQLiteConfig.SynchronousMode;
import org.sqlite.SQLiteConfig.TempStore;

import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.tenant.TenantPaths;
import de.zft2.gbanking.util.AppPaths;

abstract class DbConnectionHandler {

	private static final Logger log = LogManager.getLogger(DbConnectionHandler.class);
	private static final DatabaseValidationRegistry VALIDATIONS = new DatabaseValidationRegistry();

	private static final String INSTITUTE_DB_ALIAS = "institute_db";
	private static final String INSTITUTE_DB_FILE_NAME = "institute.db";
	private static final String MAIN_DB_ALIAS = "main";
	private static final String SQL_FUNCTION_EQUALS_IGNORE_CASE = "gb_equals_ignore_case";
	private static final int SQLITE_BUSY_TIMEOUT_MILLIS = 5_000;
	private static final int SQLITE_CACHE_SIZE_KIB = 32 * 1_024;
	private static final int SQLITE_WAL_AUTOCHECKPOINT_PAGES = 1_000;
	private static final List<String> INSTITUTE_SCHEMA_KEYS = List.of(
			"SQL_SETUP_CREATE_INSTITUTE_STATUS",
			"SQL_SETUP_INSERT_INSTITUTE_STATUS",
			"SQL_SETUP_CREATE_IMPORT_HISTORY",
			"SQL_SETUP_CREATE_INSTITUTE",
			"SQL_SETUP_CREATE_INSTITUTE_DK",
			"SQL_SETUP_CREATE_INSTITUTE_DBB",
			"SQL_SETUP_CREATE_INSTITUTE_EPC",
			"SQL_SETUP_CREATE_INSTITUTE_DBB_REACHABLE",
			"SQL_SETUP_CREATE_INDEX_INSTITUTE_BLZ_STATE",
			"SQL_SETUP_CREATE_TRIGGER_INSTITUTEDK_VALIDATE_UNIQUE_BLZ_IMPORTNUMBER_INSERT",
			"SQL_SETUP_CREATE_TRIGGER_INSTITUTEDK_VALIDATE_UNIQUE_BLZ_IMPORTNUMBER_UPDATE",
			"SQL_SETUP_CREATE_TRIGGER_INSTITUTEDBB_VALIDATE_UNIQUE_BLZ_DATASETNUMBER_INSERT",
			"SQL_SETUP_CREATE_TRIGGER_INSTITUTEDBB_VALIDATE_UNIQUE_BLZ_DATASETNUMBER_UPDATE");

	protected static Connection connection;
	private static DbSession currentSession;
	private static String currentDatabasePath;
	private static boolean shutdownHookRegistered;

	static {
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException exception) {
			log.error("Error loading JDBC-driver", exception);
		}
	}

	DbConnectionHandler() {
	}

	public static Connection getConnection() {
		return connection;
	}

	static DbSession getSession() {
		return currentSession;
	}

	static void initialize(String dbFilePath) {
		initialize(dbFilePath, null);
	}

	static void initialize(String dbFilePath, DbMigrationProgressListener migrationProgressListener) {
		initialize(dbFilePath, migrationProgressListener, false);
	}

	static void initialize(String dbFilePath, DbMigrationProgressListener migrationProgressListener,
			boolean allowMissingInstituteDatabase) {
		DbTransactionManager.withLifecycleLock(() -> {
			String candidateDbFilePath = DbRuntimeContext.resolveDbDirectoryForSessionAccess(dbFilePath);
			Path candidateDbFile = AppPaths.resolveInApplicationDirectory(candidateDbFilePath)
					.resolve("gbanking.db").toAbsolutePath().normalize();
			if (isCurrentSession(candidateDbFile.toString())) {
				return;
			}
			DbTransactionManager.verifyLifecycleChangeAllowed();
			closeCurrentConnection();
			currentDatabasePath = null;
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

		if (isCurrentSession(path)) {
			return;
		}

		ensureParentDirectoryExists(dbFile);
		if (!Files.exists(dbFile)) {
			log.info("Creating new database file: {}", dbFile.getFileName());
			log.debug("Creating new database at {}", dbFile);
			createFreshDatabase(dbFile, allowMissingInstituteDatabase);
		}

		DbSession newSession = null;
		try {
			log.info("Creating Connection to Database...");
			newSession = openSession(dbFile, dbDirectory, allowMissingInstituteDatabase, ConnectionProfile.RUNTIME);
			initializeSession(newSession, migrationProgressListener);
			installSession(newSession);
			currentDatabasePath = path;
			log.info("Using database file: {}", dbFile.getFileName());
			log.debug("Using database path: {}", dbFile);
			log.info("...Connection established");
			log.info("Foreign Keys enabled: {}",
					executeConfigStatement(newSession.connection(), "foreign_keys", "SQL_READ_FOREIGN_KEYS"));
			registerShutdownHook();
		} catch (SQLException exception) {
			GBankingException failure = new GBankingException("Error in initialisation of database connection", exception);
			closeFailedSession(newSession, failure);
			throw failure;
		} catch (RuntimeException exception) {
			closeFailedSession(newSession, exception);
			throw exception;
		}
	}

	private static void createFreshDatabase(Path dbFile, boolean allowMissingInstituteDatabase) {
		try {
			AtomicDatabaseFile.create(dbFile, stagingFile -> initializeFreshDatabase(
					stagingFile, dbFile.getParent(), allowMissingInstituteDatabase));
			VALIDATIONS.remember(dbFile, false, null);
		} catch (IOException | SQLException exception) {
			throw new GBankingException("Error creating database atomically", exception);
		}
	}

	private static void initializeFreshDatabase(Path stagingFile, Path databaseDirectory,
			boolean allowMissingInstituteDatabase) throws SQLException {
		DbSession stagingSession = null;
		Throwable failure = null;
		try {
			stagingSession = openSession(stagingFile, databaseDirectory, allowMissingInstituteDatabase, ConnectionProfile.STAGING);
			boolean instituteSchemaChanged = ensureInstituteDatabaseSchema(stagingSession.connection());
			verifyDatabaseIfRequired(stagingSession.connection(), DatabaseAlias.INSTITUTE,
					stagingSession.prevalidatedInstituteDatabase() && !instituteSchemaChanged);
			DbDdlSetup.setupDB(stagingSession.connection());
			verifyForeignKeys(stagingSession.connection(), DatabaseAlias.MAIN);
			verifyIntegrity(stagingSession.connection(), DatabaseAlias.MAIN, false);
			optimize(stagingSession);
			checkpoint(stagingSession.connection());
		} catch (SQLException | RuntimeException exception) {
			failure = exception;
			throw exception;
		} finally {
			closeSession(stagingSession, failure);
		}
		if (stagingSession != null && stagingSession.instituteDatabaseFile() != null) {
			VALIDATIONS.remember(stagingSession.instituteDatabaseFile(), false,
					stagingSession.instituteDatabaseVersion());
		}
	}

	private static DbSession openSession(Path databaseFile, Path databaseDirectory,
			boolean allowMissingInstituteDatabase, ConnectionProfile connectionProfile) throws SQLException {
		boolean prevalidatedMainDatabase = VALIDATIONS.consume(databaseFile, false).isPresent();
		DbSession session = new DbSession(databaseFile, createConnection(databaseFile, connectionProfile),
				prevalidatedMainDatabase);
		try {
			registerSqlFunctions(session.connection());
			attachInstituteDatabase(session, databaseDirectory, allowMissingInstituteDatabase);
			configureMemoryMapping(session);
			return session;
		} catch (SQLException | RuntimeException exception) {
			closeSession(session, exception);
			throw exception;
		}
	}

	private static void initializeSession(DbSession session, DbMigrationProgressListener migrationProgressListener)
			throws SQLException {
		verifyDatabaseIfRequired(session.connection(), DatabaseAlias.MAIN,
				session.prevalidatedMainDatabase());
		boolean instituteSchemaChanged = ensureInstituteDatabaseSchema(session.connection());
		verifyDatabaseIfRequired(session.connection(), DatabaseAlias.INSTITUTE,
				session.prevalidatedInstituteDatabase() && !instituteSchemaChanged);
		DbMigrationRunner.migrate(session.connection(), migrationProgressListener);
		session.jdbc().execute(SqlTemplateRepository.getConfig("SQL_OPTIMIZE_ON_OPEN"));
	}

	static void installSession(DbSession session) {
		currentSession = session;
		connection = session.connection();
	}

	static void closeForRuntimeContextChange() {
		closeCurrentConnection();
		currentDatabasePath = null;
	}

	public static boolean hasPendingMigrations(String dbFilePath) {
		return DbTransactionManager.withLifecycleLock(() -> {
			DbTransactionManager.verifyLifecycleChangeAllowed();
			DbRuntimeContext.verifyLifecycleAccess();
			return hasPendingMigrationsLocked(DbRuntimeContext.resolveDbDirectoryForSessionAccess(dbFilePath));
		});
	}

	private static boolean hasPendingMigrationsLocked(String dbFilePath) {
		Path dbDirectory = AppPaths.resolveInApplicationDirectory(dbFilePath);
		Path dbFile = dbDirectory.resolve("gbanking.db").toAbsolutePath().normalize();
		if (!Files.exists(dbFile)) {
			return false;
		}

		boolean pendingMigrations;
		try (Connection migrationCheckConnection = createConnection(dbFile, ConnectionProfile.VALIDATION)) {
			verifyIntegrity(migrationCheckConnection, DatabaseAlias.MAIN, false);
			verifyForeignKeys(migrationCheckConnection, DatabaseAlias.MAIN);
			pendingMigrations = DbMigrationRunner.hasPendingMigrations(migrationCheckConnection);
		} catch (SQLException exception) {
			throw new GBankingException("Error checking pending database migrations", exception);
		}
		VALIDATIONS.remember(dbFile, false, null);
		return pendingMigrations;
	}

	static void validateDatabaseFileIntegrity(Path databaseFile, boolean fullIntegrityCheck) {
		DbTransactionManager.withLifecycleLock(() -> {
			DbTransactionManager.verifyLifecycleChangeAllowed();
			DbRuntimeContext.verifyLifecycleAccess();
			Path normalizedDatabaseFile = databaseFile.toAbsolutePath().normalize();
			if (!Files.isRegularFile(normalizedDatabaseFile)) {
				return;
			}

			try (Connection validationConnection = createConnection(normalizedDatabaseFile, ConnectionProfile.VALIDATION)) {
				verifyIntegrity(validationConnection, DatabaseAlias.MAIN, fullIntegrityCheck);
				verifyForeignKeys(validationConnection, DatabaseAlias.MAIN);
				checkpoint(validationConnection);
			} catch (SQLException exception) {
				throw new DatabaseIntegrityException("Database integrity validation failed", exception);
			}
			VALIDATIONS.remember(normalizedDatabaseFile, fullIntegrityCheck, null);
		});
	}

	public static void resetConnection() {
		DbTransactionManager.withLifecycleLock(() -> {
			DbTransactionManager.verifyLifecycleChangeAllowed();
			DbRuntimeContext.verifyLifecycleAccess();
			resetConnectionLocked();
		});
	}

	public static boolean resetConnectionIfIdle() {
		return DbTransactionManager.withLifecycleLock(() -> {
			DbTransactionManager.verifyLifecycleChangeAllowed();
			DbRuntimeContext.verifyLifecycleAccess();
			if (DbRuntimeContext.hasActiveBackgroundTasks()) {
				return false;
			}
			resetConnectionLocked();
			return true;
		});
	}

	private static void resetConnectionLocked() {
		try {
			closeCurrentConnection();
		} finally {
			currentDatabasePath = null;
			DbRuntimeContext.invalidateDatabaseSession();
		}
	}

	private static Connection createConnection(Path databaseFile, ConnectionProfile connectionProfile)
			throws SQLException {
		SQLiteConfig config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		config.setDateClass("TEXT");
		config.setBusyTimeout(SQLITE_BUSY_TIMEOUT_MILLIS);
		config.setCacheSize(-SQLITE_CACHE_SIZE_KIB);
		config.setTempStore(TempStore.MEMORY);
		config.setSynchronous(SynchronousMode.FULL);
		config.setWalAutocheckpoint(SQLITE_WAL_AUTOCHECKPOINT_PAGES);
		if (connectionProfile.journalMode() != null) {
			config.setJournalMode(connectionProfile.journalMode());
		}
		return DriverManager.getConnection("jdbc:sqlite:" + databaseFile, config.toProperties());
	}

	private static void registerSqlFunctions(Connection targetConnection) throws SQLException {
		Function.create(targetConnection, SQL_FUNCTION_EQUALS_IGNORE_CASE,
				new CaseInsensitiveEqualsFunction(), 2, Function.FLAG_DETERMINISTIC);
	}

	private static final class CaseInsensitiveEqualsFunction extends Function {

		@Override
		protected void xFunc() throws SQLException {
			String firstValue = value_text(0);
			String secondValue = value_text(1);
			result(firstValue != null && secondValue != null && firstValue.equalsIgnoreCase(secondValue) ? 1 : 0);
		}
	}

	private enum ConnectionProfile {
		RUNTIME(JournalMode.WAL),
		STAGING(JournalMode.DELETE),
		VALIDATION(null);

		private final JournalMode journalMode;

		ConnectionProfile(JournalMode journalMode) {
			this.journalMode = journalMode;
		}

		JournalMode journalMode() {
			return journalMode;
		}
	}

	private enum DatabaseAlias {
		MAIN(MAIN_DB_ALIAS, "SQL_MAIN_QUICK_CHECK", "SQL_MAIN_INTEGRITY_CHECK",
				"SQL_MAIN_FOREIGN_KEY_CHECK"),
		INSTITUTE(INSTITUTE_DB_ALIAS, "SQL_INSTITUTE_QUICK_CHECK", "SQL_INSTITUTE_INTEGRITY_CHECK",
				"SQL_INSTITUTE_FOREIGN_KEY_CHECK");

		private final String alias;
		private final String quickCheckKey;
		private final String integrityCheckKey;
		private final String foreignKeyCheckKey;

		DatabaseAlias(String alias, String quickCheckKey, String integrityCheckKey, String foreignKeyCheckKey) {
			this.alias = alias;
			this.quickCheckKey = quickCheckKey;
			this.integrityCheckKey = integrityCheckKey;
			this.foreignKeyCheckKey = foreignKeyCheckKey;
		}

		String integritySql(boolean fullIntegrityCheck) {
			return SqlTemplateRepository.getConfig(fullIntegrityCheck ? integrityCheckKey : quickCheckKey);
		}

		String foreignKeyCheckSql() {
			return SqlTemplateRepository.getConfig(foreignKeyCheckKey);
		}

		@Override
		public String toString() {
			return alias;
		}
	}

	private static void verifyIntegrity(Connection connectionToCheck, DatabaseAlias databaseAlias,
			boolean fullIntegrityCheck) throws SQLException {
		boolean resultReturned = false;
		try (Statement statement = connectionToCheck.createStatement();
				ResultSet resultSet = statement.executeQuery(databaseAlias.integritySql(fullIntegrityCheck))) {
			while (resultSet.next()) {
				resultReturned = true;
				String result = resultSet.getString(1);
				if (!"ok".equalsIgnoreCase(result)) {
					throw new DatabaseIntegrityException(
							"Database integrity check failed for " + databaseAlias + ": " + result);
				}
			}
		}
		if (!resultReturned) {
			throw new DatabaseIntegrityException("Database integrity check returned no result for " + databaseAlias);
		}
	}

	private static void verifyForeignKeys(Connection targetConnection, DatabaseAlias databaseAlias)
			throws SQLException {
		try (Statement statement = targetConnection.createStatement();
				ResultSet resultSet = statement.executeQuery(databaseAlias.foreignKeyCheckSql())) {
			if (resultSet.next()) {
				throw new DatabaseIntegrityException("Database foreign key check failed for "
						+ databaseAlias + " table "
						+ resultSet.getString(1) + " at row " + resultSet.getString(2));
			}
		}
	}

	private static void checkpoint(Connection connectionToCheckpoint) throws SQLException {
		try (Statement statement = connectionToCheckpoint.createStatement();
				ResultSet resultSet = statement.executeQuery(
						SqlTemplateRepository.getConfig("SQL_MAIN_CHECKPOINT"))) {
			if (!resultSet.next() || resultSet.getInt(1) != 0) {
				throw new DatabaseIntegrityException("Database WAL checkpoint failed for " + MAIN_DB_ALIAS);
			}
		}
	}

	private static void configureMemoryMapping(DbSession session) throws SQLException {
		session.jdbc().execute(SqlTemplateRepository.getConfig("SQL_MAIN_MMAP_SIZE"));
		session.jdbc().execute(SqlTemplateRepository.getConfig("SQL_INSTITUTE_MMAP_SIZE"));
	}

	private static void optimize(DbSession session) throws SQLException {
		session.jdbc().execute(SqlTemplateRepository.getConfig("SQL_OPTIMIZE"));
	}

	private static void closeFailedSession(DbSession failedSession, Throwable originalFailure) {
		currentDatabasePath = null;
		if (failedSession == null) {
			return;
		}
		clearSessionReferences(failedSession);
		closeSession(failedSession, originalFailure);
	}

	private static void closeSession(DbSession session, Throwable originalFailure) {
		if (session == null) {
			return;
		}
		try {
			session.close();
		} catch (SQLException | RuntimeException closeFailure) {
			retainUnclosedSession(session);
			if (originalFailure != null) {
				originalFailure.addSuppressed(closeFailure);
			}
			log.error("Error closing database session", closeFailure);
			if (originalFailure == null) {
				throw new GBankingException("Error closing database session", closeFailure);
			}
		}
	}

	private static void verifyDatabaseIfRequired(Connection targetConnection, DatabaseAlias databaseAlias,
			boolean prevalidated) throws SQLException {
		if (!prevalidated) {
			verifyIntegrity(targetConnection, databaseAlias, false);
			verifyForeignKeys(targetConnection, databaseAlias);
		}
	}

	private static void retainUnclosedSession(DbSession session) {
		if (!isClosed(session.connection())) {
			session.invalidate();
			currentSession = session;
			connection = session.connection();
			currentDatabasePath = null;
		}
	}

	private static void clearSessionReferences(DbSession session) {
		if (currentSession == session) {
			currentSession = null;
			connection = null;
		}
	}

	static boolean prepareInstituteDatabaseFile(Path dataDirectory) {
		return DbTransactionManager.withLifecycleLock(
				() -> prepareInstituteDatabaseFileLocked(dataDirectory, bundledInstituteDatabaseFile()));
	}

	static boolean prepareInstituteDatabaseFile(Path dataDirectory, Path template) {
		return DbTransactionManager.withLifecycleLock(
				() -> prepareInstituteDatabaseFileLocked(dataDirectory, template));
	}

	private static boolean prepareInstituteDatabaseFileLocked(Path dataDirectory, Path template) {
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

	private static void attachInstituteDatabase(DbSession session, Path dbDirectory,
			boolean allowMissingInstituteDatabase) {
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

		try {
			DatabaseValidationRegistry.Evidence evidence = useFile
					? VALIDATIONS.consume(instituteDbFile, false).orElse(null)
					: null;
			session.setInstituteDatabase(useFile ? instituteDbFile : null, evidence);
			session.jdbc().update(SqlTemplateRepository.getConfig("SQL_ATTACH_INSTITUTE_DATABASE"),
					statement -> statement.setString(1, databaseLocation));
			if (useFile) {
				log.info("Using institute database: {} (existing: {})", instituteDbFile, existingInstituteDatabase);
			} else {
				log.warn("Continuing without a persistent institute database; bank names cannot be loaded.");
			}
		} catch (SQLException exception) {
			throw new GBankingException("Error attaching institute database", exception);
		}
	}

	private static Path resolveInstituteDatabaseFile(Path dbDirectory) {
		Optional<Path> configuredDataDirectory = DbRuntimeContext.getCurrentDataDirectory();
		if (configuredDataDirectory.isPresent()) {
			return configuredDataDirectory.get().resolve(INSTITUTE_DB_FILE_NAME).toAbsolutePath().normalize();
		}
		if (dbDirectory == null) {
			return AppPaths.resolveInApplicationDirectory("data").resolve(INSTITUTE_DB_FILE_NAME)
					.toAbsolutePath().normalize();
		}

		Path normalizedDbDirectory = dbDirectory.toAbsolutePath().normalize();
		if (normalizedDbDirectory.equals(AppPaths.getApplicationBaseDirectory())) {
			return normalizedDbDirectory.resolve("data").resolve(INSTITUTE_DB_FILE_NAME)
					.toAbsolutePath().normalize();
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
			Optional<String> copiedVersion = readInstituteDatabaseVersion(temporaryFile);
			if (copiedVersion.isEmpty()) {
				throw new IOException("Copied institute database is unusable");
			}
			moveInstituteDatabaseTemplate(temporaryFile, instituteDbFile);
			VALIDATIONS.remember(instituteDbFile, false, copiedVersion.get());
			log.info("{} institute database from bundled template: {}",
					update ? "Updated" : "Initialized", instituteDbFile);
			return true;
		} catch (IOException exception) {
			deleteTemporaryInstituteDatabase(temporaryFile, exception);
			log.error("Could not install bundled institute database at {}", instituteDbFile, exception);
			return update;
		}
	}

	private static Path bundledInstituteDatabaseFile() {
		return AppPaths.resolveInApplicationDirectory("data").resolve(INSTITUTE_DB_FILE_NAME)
				.toAbsolutePath().normalize();
	}

	private static Optional<String> readInstituteDatabaseVersion(Path databaseFile) {
		if (!Files.isRegularFile(databaseFile)) {
			return Optional.empty();
		}
		Optional<String> validatedVersion = VALIDATIONS.validatedInstituteVersion(databaseFile);
		if (validatedVersion.isPresent()) {
			return validatedVersion;
		}
		String latestUpdate;
		try (Connection versionConnection = createConnection(databaseFile, ConnectionProfile.VALIDATION)) {
			verifyIntegrity(versionConnection, DatabaseAlias.MAIN, false);
			verifyForeignKeys(versionConnection, DatabaseAlias.MAIN);
			try (Statement statement = versionConnection.createStatement();
					ResultSet resultSet = statement.executeQuery(
							SqlTemplateRepository.getConfig("SQL_READ_INSTITUTE_DATABASE_VERSION"))) {
				latestUpdate = resultSet.next() ? resultSet.getString(1) : null;
			}
		} catch (SQLException exception) {
			log.warn("Could not read institute database version from {}", databaseFile, exception);
			return Optional.empty();
		}
		VALIDATIONS.remember(databaseFile, false, latestUpdate);
		return Optional.ofNullable(latestUpdate);
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

	private static boolean ensureInstituteDatabaseSchema(Connection targetConnection) throws SQLException {
		if (isInstituteDatabaseSchemaComplete(targetConnection)) {
			return false;
		}

		boolean oldAutoCommit = targetConnection.getAutoCommit();
		targetConnection.setAutoCommit(false);
		String currentKey = null;
		boolean transactionUsable = true;
		Throwable failure = null;
		try (Statement statement = targetConnection.createStatement()) {
			for (String schemaKey : INSTITUTE_SCHEMA_KEYS) {
				currentKey = schemaKey;
				statement.executeUpdate(SqlTemplateRepository.getDdl(schemaKey));
			}
			targetConnection.commit();
		} catch (SQLException | RuntimeException exception) {
			failure = exception;
			transactionUsable = rollback(targetConnection, exception);
			throw new GBankingException(
					"Error creating institute database schema for statement: " + currentKey, exception);
		} finally {
			restoreAutoCommit(targetConnection, oldAutoCommit, transactionUsable, failure);
		}
		return true;
	}

	private static boolean isInstituteDatabaseSchemaComplete(Connection targetConnection) throws SQLException {
		try (Statement statement = targetConnection.createStatement();
				ResultSet resultSet = statement.executeQuery(
						SqlTemplateRepository.getConfig("SQL_IS_INSTITUTE_SCHEMA_COMPLETE"))) {
			return resultSet.next() && resultSet.getBoolean(1);
		}
	}

	private static boolean rollback(Connection targetConnection, Throwable originalFailure) {
		try {
			targetConnection.rollback();
			return true;
		} catch (SQLException | RuntimeException rollbackFailure) {
			originalFailure.addSuppressed(rollbackFailure);
			return false;
		}
	}

	private static void restoreAutoCommit(Connection targetConnection, boolean oldAutoCommit,
			boolean transactionUsable, Throwable originalFailure) throws SQLException {
		if (!transactionUsable) {
			return;
		}
		try {
			targetConnection.setAutoCommit(oldAutoCommit);
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

	private static boolean isCurrentSession(String path) {
		try {
			return currentSession != null && currentSession.isOpen() && path.equals(currentDatabasePath);
		} catch (SQLException | RuntimeException exception) {
			log.warn("Could not inspect current database connection", exception);
			return false;
		}
	}

	private static void ensureParentDirectoryExists(Path dbFile) {
		try {
			Path parentDirectory = dbFile.getParent();
			if (parentDirectory != null) {
				Files.createDirectories(parentDirectory);
			}
		} catch (Exception exception) {
			throw new GBankingException(
					"Error in initialisation of database connection: could not create DB directory", exception);
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
		} catch (RuntimeException exception) {
			log.error("Error closing database connection during JVM shutdown", exception);
		}
	}

	private static void closeCurrentConnection() {
		DbSession sessionToClose = currentSession;
		if (sessionToClose == null) {
			connection = null;
			return;
		}

		Exception failure = null;
		Connection connectionToClose = sessionToClose.connection();
		boolean usableSession = !sessionToClose.isInvalidated();
		if (usableSession) {
			try {
				if (!connectionToClose.isClosed() && !connectionToClose.getAutoCommit()) {
					log.warn("Rolling back unmanaged transaction before closing database connection");
					connectionToClose.rollback();
					connectionToClose.setAutoCommit(true);
				}
			} catch (SQLException | RuntimeException exception) {
				failure = exception;
				sessionToClose.invalidate();
				usableSession = false;
			}
			if (usableSession && !isClosed(connectionToClose)) {
				try {
					optimize(sessionToClose);
				} catch (SQLException | RuntimeException exception) {
					failure = addFailure(failure, exception);
				}
				try {
					checkpoint(connectionToClose);
				} catch (SQLException | RuntimeException exception) {
					failure = addFailure(failure, exception);
				}
			}
		}
		try {
			sessionToClose.close();
		} catch (SQLException | RuntimeException exception) {
			failure = addFailure(failure, exception);
		}
		boolean connectionClosed = isClosed(connectionToClose);
		if (!connectionClosed) {
			failure = addFailure(failure,
					new SQLException("Database connection did not close cleanly"));
		} else {
			clearSessionReferences(sessionToClose);
		}
		if (failure != null) {
			throw new GBankingException("Error closing database connection", failure);
		}
		log.info("Connection to Database closed");
	}

	private static boolean isClosed(Connection targetConnection) {
		try {
			return targetConnection.isClosed();
		} catch (SQLException | RuntimeException exception) {
			return false;
		}
	}

	private static Exception addFailure(Exception failure, Exception additionalFailure) {
		if (failure == null) {
			return additionalFailure;
		}
		failure.addSuppressed(additionalFailure);
		return failure;
	}

	private static String executeConfigStatement(Connection targetConnection, String columnHeader,
			String sqlKey) {
		String sql = SqlTemplateRepository.getConfig(sqlKey);
		try (Statement statement = targetConnection.createStatement();
				ResultSet resultSet = statement.executeQuery(sql)) {
			return resultSet.next() ? resultSet.getString(columnHeader) : null;
		} catch (SQLException exception) {
			log.error("Error executing database config statement: {}", sqlKey, exception);
			return null;
		}
	}

	protected void closeStatement(Statement statement) {
		try {
			if (statement != null) {
				statement.close();
			}
		} catch (SQLException exception) {
			log.error("Error closing (Prepared) Statement: {}", exception.getMessage());
		}
	}
}
