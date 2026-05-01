package de.zft2.gbanking.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sqlite.SQLiteConfig;

import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.messages.Messages;
import de.zft2.gbanking.util.AppPaths;

class DbConnectionHandler {

    private static final Logger log = LogManager.getLogger(DbConnectionHandler.class);
    private static final DbConnectionHandler dbConnectionHandler = new DbConnectionHandler();

    protected static Connection connection;
    protected static Messages messages;
    private static String currentDatabasePath;
    private static boolean shutdownHookRegistered;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            log.error("Error loading JDBC-driver", e);
        }
    }

    protected DbConnectionHandler() {
    }

    public static Connection getConnection() {
        return connection;
    }

    public static synchronized DbConnectionHandler getInstance(String dbFilePath) {
        Path dbDirectory = AppPaths.resolveInApplicationDirectory(dbFilePath);
        Path dbFile = dbDirectory.resolve("gbanking.db").toAbsolutePath().normalize();
        String path = dbFile.toString();

        messages = Messages.getInstance();
        if (isCurrentConnection(path)) {
            return dbConnectionHandler;
        }

        closeCurrentConnection();
        ensureParentDirectoryExists(dbFile);

        if (Files.exists(dbFile)) {
            initDBConnection(path, false);
        } else {
            log.info("Creating new database: {}", dbFile);
            initDBConnection(path, true);
        }

        currentDatabasePath = path;
        registerShutdownHook();
        return dbConnectionHandler;
    }

    public static synchronized void resetConnection() {
        closeCurrentConnection();
        currentDatabasePath = null;
        messages = null;
    }

    private static void initDBConnection(String path, boolean setupDB) {
        try {
            log.info("Creating Connection to Database...");
            SQLiteConfig config = new SQLiteConfig();
            config.enforceForeignKeys(true);
            config.setDateClass("TEXT");
            connection = DriverManager.getConnection("jdbc:sqlite:" + path, config.toProperties());
            log.info("Using database: {}", path);
            if (!connection.isClosed()) {
                log.info("...Connection established");
                log.info("Foreign Keys enabled: {}", () -> executeConfigStatement("foreign_keys", "PRAGMA foreign_keys"));
            }
            if (setupDB) {
                if (!DbDdlSetup.setupDB()) {
                    throw new GBankingException("Error in initialisation of database connection: setup DB failed");
                }
                DbMigrationRunner.markFreshSchemaAsApplied(connection);
            }
            DbMigrationRunner.migrate(connection);
        } catch (SQLException e) {
            throw new GBankingException("Error in initialisation of database connection:", e);
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

        Runtime.getRuntime().addShutdownHook(new Thread(DbConnectionHandler::closeCurrentConnection));
        shutdownHookRegistered = true;
    }

    private static synchronized void closeCurrentConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                if (connection.isClosed()) {
                    log.info("Connection to Database closed");
                }
            }
        } catch (SQLException e) {
            log.error("Error closing database connection", e);
        } finally {
            connection = null;
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
