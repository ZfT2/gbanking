package de.gbanking.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sqlite.SQLiteConfig;

import de.gbanking.exception.GBankingException;
import de.gbanking.messages.Messages;

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
        String path = new File(dbFilePath, "gbanking.db").getAbsolutePath();
        File dbFile = new File(path);

        messages = Messages.getInstance();
        if (isCurrentConnection(path)) {
            return dbConnectionHandler;
        }

        closeCurrentConnection();
        ensureParentDirectoryExists(dbFile);

        if (dbFile.exists()) {
            initDBConnection(path, false);
        } else {
            log.info("Creating new database: {}", dbFile.getAbsolutePath());
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

    private static void ensureParentDirectoryExists(File dbFile) {
        File parentFile = dbFile.getParentFile();
        if (parentFile != null && !parentFile.exists() && !parentFile.mkdirs()) {
            throw new GBankingException("Error in initialisation of database connection: could not create DB directory");
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
