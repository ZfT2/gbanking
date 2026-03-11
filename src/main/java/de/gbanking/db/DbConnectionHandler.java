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

	private static Logger log = LogManager.getLogger(DbConnectionHandler.class);

	protected DbConnectionHandler() {
	}

	private static final DbConnectionHandler dbConnectionHandler = new DbConnectionHandler();

	/*protected */ static Connection connection;

	protected static Messages messages;

	static {
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			log.error("Error loading JDBC-driver", e);
		}
	}

	public static Connection getConnection() {
		return connection;
	}

	public static DbConnectionHandler getInstance(String dbFilePath) {

		String path = dbFilePath + "/gbanking.db";

		File dbFile = new File(path);
		if (dbFile.exists()) {
			initDBConnection(path, false);
		} else {
			log.info("Creating new database: {}", dbFile.getAbsolutePath());
			initDBConnection(path, true);
		}

		messages = Messages.getInstance();

		return dbConnectionHandler;
	}

	private static void initDBConnection(String path, boolean setupDB) {
		try {
			if (connection != null)
				return;
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
				DbDdlSetup.setupDB();
				DbDdlSetup.setUpDefaultTableValues();
			}

		} catch (SQLException e) {
			throw new GBankingException("Error in initialisation of database connection:", e);
		}

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					if (connection != null && !connection.isClosed()) {
						connection.close();
						if (connection.isClosed())
							log.info("Connection to Database closed");
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		});
	}

	private static String executeConfigStatement(String columnHeader, String sql) {

		try (Statement statement = connection.createStatement()) {
			ResultSet rs = statement.executeQuery(sql);
			while (rs.next()) {
				return rs.getString(columnHeader);

			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	protected void closeStatement(Statement s) {
		try {
			if (s != null) {
				s.close();
			}
		} catch (SQLException e) {
			log.error("Error closing (Prepared) Statement: {}", e.getMessage());
		}
	}

}
