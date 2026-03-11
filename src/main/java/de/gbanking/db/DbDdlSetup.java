package de.gbanking.db;

import static de.gbanking.db.SqlStatements.SQL_SETUP_DROP_VIEW_BPD;
import static de.gbanking.db.SqlStatements.SQL_SETUP_DROP_VIEW_CATEGORY_FULL;
import static de.gbanking.db.SqlStatements.SQL_SETUP_DROP_VIEW_UPD;
import static de.gbanking.db.SqlStatements.SQL_SETUP_VIEW_BPD;
import static de.gbanking.db.SqlStatements.SQL_SETUP_VIEW_CATEGORY_FULL;
import static de.gbanking.db.SqlStatements.SQL_SETUP_VIEW_UPD;
import static de.gbanking.db.SqlStatementsDDL.*;

import java.sql.SQLException;
import java.sql.Statement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class DbDdlSetup {
	
	private DbDdlSetup() {
	}
	
	private static Logger log = LogManager.getLogger(DbDdlSetup.class);

	static boolean setupDB() {

		boolean result = true;

		try (Statement stmt = DbConnectionHandler.getConnection().createStatement()) {

			stmt.executeUpdate(SQL_FOREIGN_KEY_CHECKS_ON);

			stmt.executeUpdate(SQL_SETUP_DROP_BANKACCESS);
			stmt.executeUpdate(SQL_SETUP_CREATE_BANKACCESS);

			stmt.executeUpdate(SQL_SETUP_DROP_BANKACCOUNT);
			stmt.executeUpdate(SQL_SETUP_CREATE_BANKACCOUNT);

			stmt.executeUpdate(SQL_SETUP_DROP_BOOKING);
			stmt.executeUpdate(SQL_SETUP_CREATE_BOOKING);

			stmt.executeUpdate(SQL_SETUP_DROP_BUSINESSCASE);
			stmt.executeUpdate(SQL_SETUP_CREATE_BUSINESSCASE);

			stmt.executeUpdate(SQL_SETUP_DROP_MONEYTRANSFER);
			stmt.executeUpdate(SQL_SETUP_CREATE_MONEYTRANSFER);

			stmt.executeUpdate(SQL_SETUP_DROP_RECIPIENT);
			stmt.executeUpdate(SQL_SETUP_CREATE_RECIPIENT);

			stmt.executeUpdate(SQL_SETUP_DROP_CATEGORY);
			stmt.executeUpdate(SQL_SETUP_CREATE_CATEGORY);

			stmt.executeUpdate(SQL_SETUP_DROP_CATEGORY_RULE);
			stmt.executeUpdate(SQL_SETUP_CREATE_CATEGORY_RULE);

			stmt.executeUpdate(SQL_SETUP_DROP_CATEGORY_RULE_BANKACCOUNT);
			stmt.executeUpdate(SQL_SETUP_CREATE_CATEGORY_RULE_BANKACCOUNT);

			stmt.executeUpdate(SQL_SETUP_DROP_MONEYTRANSFER_PROTOCOL);
			stmt.executeUpdate(SQL_SETUP_CREATE_MONEYTRANSFER_PROTOCOL);

			stmt.executeUpdate(SQL_SETUP_DROP_BANKACCOUNT_BUSINESSCASE);
			stmt.executeUpdate(SQL_SETUP_CREATE_BANKACCOUNT_BUSINESSCASE);

			stmt.executeUpdate(SQL_SETUP_DROP_PARAMETERDATA);
			stmt.executeUpdate(SQL_SETUP_CREATE_PARAMETERDATA);

			stmt.executeUpdate(SQL_SETUP_DROP_BANKACCESS_PARAMETERDATA);
			stmt.executeUpdate(SQL_SETUP_CREATE_BANKACCESS_PARAMETERDATA);
			
			stmt.executeUpdate(SQL_SETUP_DROP_INSTITUTE);
			stmt.executeUpdate(SQL_SETUP_DROP_UNIQUE_INDEX_INSTITUTE);
			stmt.executeUpdate(SQL_SETUP_CREATE_INSTITUTE);
			stmt.executeUpdate(SQL_SETUP_CREATE_UNIQUE_INDEX_INSTITUTE);
			
			stmt.executeUpdate(SQL_SETUP_DROP_SETTING);
			stmt.executeUpdate(SQL_SETUP_CREATE_SETTING);
			
			stmt.executeUpdate(SQL_SETUP_DROP_BOOKING_CATEGORY);
			stmt.executeUpdate(SQL_SETUP_CREATE_BOOKING_CATEGORY);

			stmt.executeUpdate(SQL_SETUP_DROP_VIEW_CATEGORY_FULL);
			stmt.executeUpdate(SQL_SETUP_VIEW_CATEGORY_FULL);

			stmt.executeUpdate(SQL_SETUP_DROP_VIEW_BPD);
			stmt.executeUpdate(SQL_SETUP_VIEW_BPD);

			stmt.executeUpdate(SQL_SETUP_DROP_VIEW_UPD);
			stmt.executeUpdate(SQL_SETUP_VIEW_UPD);

		} catch (SQLException se) {
			log.error("Couldn't handle DB-Query (SETUP)", se);
			result = false;
		}

		return result;
	}
	
	static boolean setUpDefaultTableValues() {

		boolean result = true;

		try (Statement stmt = DbConnectionHandler.getConnection().createStatement()) {

			stmt.executeUpdate(SQL_SETUP_INSERT_SETTING_DEFAULT_VALUES);

		} catch (SQLException se) {
			log.error("Couldn't handle DB-Query (SETUP INSERTS)", se);
			result = false;
		}

		return result;
	}

}
