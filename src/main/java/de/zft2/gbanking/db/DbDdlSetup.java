package de.zft2.gbanking.db;

import java.sql.SQLException;
import java.sql.Statement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class DbDdlSetup {

	private static final Logger log = LogManager.getLogger(DbDdlSetup.class);

	private DbDdlSetup() {
	}

	static boolean setupDB() {
		boolean result = true;

		String sql = null;
		try (Statement stmt = DbConnectionHandler.getConnection().createStatement()) {
			for (String statement : SqlTemplateRepository.getBaselineStatements()) {
				sql = statement;
				stmt.addBatch(sql);
			}
			stmt.executeBatch();
		} catch (SQLException se) {
			log.error("Couldn't handle DB-Query (SETUP), SQL: {}", sql, se);
			result = false;
		}

		return result;
	}

}
