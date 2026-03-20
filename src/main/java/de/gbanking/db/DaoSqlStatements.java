package de.gbanking.db;

import static de.gbanking.db.SqlStatements.SQL_DELETE_BANKACCESS_BY_BLZ;
import static de.gbanking.db.SqlStatements.SQL_FIND_CATEGORY;
import static de.gbanking.db.SqlStatements.SQL_FIND_RECIPIENT_BY_ARGS;
import static de.gbanking.db.SqlStatements.SQL_INSERT_BANKACCESS;
import static de.gbanking.db.SqlStatements.SQL_INSERT_BANKACCOUNT;
import static de.gbanking.db.SqlStatements.SQL_INSERT_BOOKING;
import static de.gbanking.db.SqlStatements.SQL_INSERT_CATEGORY;
import static de.gbanking.db.SqlStatements.SQL_INSERT_CATEGORY_RULE;
import static de.gbanking.db.SqlStatements.SQL_INSERT_CATEGORY_RULE_BANKACCOUNT;
import static de.gbanking.db.SqlStatements.SQL_INSERT_INSTIUTE;
import static de.gbanking.db.SqlStatements.SQL_INSERT_MONEYTRANSFER;
import static de.gbanking.db.SqlStatements.SQL_INSERT_PARAMETERDATA;
import static de.gbanking.db.SqlStatements.SQL_INSERT_SETTING;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ACCOUNT_LAST_BOOKING_DATE;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BANKACCESSES;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BANKACCOUNTS;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BANKACCOUNTS_BY_BANKACCESS;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BOOKINGS;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BPD_OR_UPD;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BUSINESSCASES;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_BUSINESSCASES_BY_BANKACCOUNT;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_CATEGORIES_FULL;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_INSTITUTES;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT_AND_STATE;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_STATE;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_PARAMETERDATA;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_RECIPIENTS;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_RECIPIENTS_WITH_NAME_IBAN;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ALL_SETTINGS;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ID_BANKACCESS_BY_BLZ;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ID_BANKACCOUNT_BY_IBAN_OR_NUMBER;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ID_CATEGORY_BY_NAME;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ID_CATEGORY_RULE;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ID_INSTIUTE_BY_ID;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ID_MONEYTRANSFER_BY_ID_AND_ACCOUNT_ID;
import static de.gbanking.db.SqlStatements.SQL_SELECT_ID_SETTING_BY_ID;
import static de.gbanking.db.SqlStatements.SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_BANKACCESS;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_BANKACCOUNT;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_BOOKING;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_CATEGORY;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_CATEGORY_RULE;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_INSTIUTE;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_MONEYTRANSFER;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_RECIPIENT_IF_NOT_REFERENCED;
import static de.gbanking.db.SqlStatements.SQL_UPDATE_SETTING;

import java.util.Map;

import de.gbanking.db.StatementsConfig.StatementType;
import de.gbanking.db.dao.enu.ParameterDataType;

class DaoSqlStatements {

	private DaoSqlStatements() {
		/* This utility class should not be instantiated */
	}

	static final Map<StatementType, String> BANK_ACCESS_SQL = Map.of(
	        StatementType.SELECT_ALL, SQL_SELECT_ALL_BANKACCESSES,
	        StatementType.SELECT_ID, SQL_SELECT_ID_BANKACCESS_BY_BLZ,
	        StatementType.INSERT, SQL_INSERT_BANKACCESS,
	        StatementType.UPDATE, SQL_UPDATE_BANKACCESS,
	        StatementType.DELETE_BANKACCESS_BY_BLZ, SQL_DELETE_BANKACCESS_BY_BLZ
	);

	static final Map<StatementType, String> BANK_ACCOUNT_SQL = Map.of(
	        StatementType.SELECT_ALL, SQL_SELECT_ALL_BANKACCOUNTS,
	        StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_BANKACCOUNTS_BY_BANKACCESS,
	        StatementType.SELECT_ID, SQL_SELECT_ID_BANKACCOUNT_BY_IBAN_OR_NUMBER,
	        StatementType.SELECT_FULL_DATA, SQL_SELECT_ALL_BANKACCOUNTS,
	        StatementType.INSERT, SQL_INSERT_BANKACCOUNT,
	        StatementType.UPDATE, SQL_UPDATE_BANKACCOUNT,
	        StatementType.SELECT_ACCOUNT_LAST_BOOKING_DATE, SQL_SELECT_ACCOUNT_LAST_BOOKING_DATE,
	        StatementType.UPDATE_ACCOUNT_SOURCE, SqlStatements.SQL_UPDATE_BANKACCOUNT_SOURCE
	);

	static final Map<StatementType, String> BOOKING_SQL = Map.of(
	        StatementType.SELECT_ALL, SQL_SELECT_ALL_BOOKINGS,
	        StatementType.SELECT_FULL_DATA, SqlStatements.SQL_SELECT_ALL_BOOKINGS_FULL,
	        StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT,
	        StatementType.SELECT_WITH_PARENT_AND_FULL_DATA, SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT,
	        StatementType.INSERT, SQL_INSERT_BOOKING,
	        StatementType.UPDATE, SQL_UPDATE_BOOKING,
	        StatementType.UPDATE_BOOKING_SOURCE, SqlStatements.SQL_UPDATE_BOOKINGS_SOURCE
	);

	static final Map<StatementType, String> CATEGORY_SQL = Map.of(
	        StatementType.SELECT_ALL, SQL_SELECT_ALL_CATEGORIES_FULL,
	        StatementType.SELECT_ID, SQL_SELECT_ID_CATEGORY_BY_NAME,
	        StatementType.SELECT_FIND, SQL_FIND_CATEGORY,
	        StatementType.INSERT, SQL_INSERT_CATEGORY,
	        StatementType.UPDATE, SQL_UPDATE_CATEGORY
	);

	static final Map<StatementType, String> CATEGORY_RULE_SQL = Map.of(
	        StatementType.SELECT_ID, SQL_SELECT_ID_CATEGORY_RULE,
	        StatementType.INSERT, SQL_INSERT_CATEGORY_RULE,
	        StatementType.UPDATE, SQL_UPDATE_CATEGORY_RULE
	);

	static final Map<StatementType, String> MONEY_TRANSFER_SQL = Map.of(
	        StatementType.SELECT_ALL, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT,
	        StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT,
	        StatementType.SELECT_FULL_DATA, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_STATE,
	        StatementType.SELECT_WITH_PARENT_AND_FILTER, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT_AND_STATE,
	        StatementType.SELECT_ID, SQL_SELECT_ID_MONEYTRANSFER_BY_ID_AND_ACCOUNT_ID,
	        StatementType.INSERT, SQL_INSERT_MONEYTRANSFER,
	        StatementType.UPDATE, SQL_UPDATE_MONEYTRANSFER
	);

	static final Map<StatementType, String> RECIPIENT_SQL = Map.of(
	        StatementType.SELECT_ALL, SQL_SELECT_ALL_RECIPIENTS,
	        StatementType.SELECT_FULL_DATA, SQL_SELECT_ALL_RECIPIENTS_WITH_NAME_IBAN,
	        StatementType.SELECT_FIND, SQL_FIND_RECIPIENT_BY_ARGS,
	        StatementType.SELECT_FIND_UNREFERENCED, SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED,
	        StatementType.SELECT_SPECIFIC_EDITABLE, SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED,
	        StatementType.INSERT, SqlStatements.SQL_INSERT_RECIPIENT,
	        StatementType.UPDATE, SqlStatements.SQL_UPDATE_RECIPIENT,
	        StatementType.UPDATE_SPECIFIC_NOT_REFERENCED, SQL_UPDATE_RECIPIENT_IF_NOT_REFERENCED,
	        StatementType.UPDATE_SPECIFIC_REFERENCED, SqlStatements.SQL_UPDATE_RECIPIENT_NOTE
	);

	static final Map<StatementType, String> PARAMETER_DATA_SQL = Map.of(
	        StatementType.SELECT_ALL, SQL_SELECT_ALL_PARAMETERDATA,
	        StatementType.INSERT, SQL_INSERT_PARAMETERDATA
	);

	static final Map<StatementType, String> BPD_SQL = Map.of(
	        StatementType.SELECT_ALL, String.format(SQL_SELECT_ALL_BPD_OR_UPD, ParameterDataType.BPD.name()),
	        StatementType.SELECT_WITH_PARENT, String.format(SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS, ParameterDataType.BPD.name())
	);

	static final Map<StatementType, String> UPD_SQL = Map.of(
	        StatementType.SELECT_ALL, String.format(SQL_SELECT_ALL_BPD_OR_UPD, ParameterDataType.UPD.name()),
	        StatementType.SELECT_WITH_PARENT, String.format(SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS, ParameterDataType.UPD.name())
	);

	static final Map<StatementType, String> INSTITUTE_SQL = Map.of(
	        StatementType.SELECT_ALL, SQL_SELECT_ALL_INSTITUTES,
	        StatementType.SELECT_ID, SQL_SELECT_ID_INSTIUTE_BY_ID,
	        StatementType.INSERT, SQL_INSERT_INSTIUTE,
	        StatementType.UPDATE, SQL_UPDATE_INSTIUTE
	);

	static final Map<StatementType, String> SETTING_SQL = Map.of(
	        StatementType.SELECT_ALL, SQL_SELECT_ALL_SETTINGS,
	        StatementType.SELECT_ID, SQL_SELECT_ID_SETTING_BY_ID,
	        StatementType.INSERT, SQL_INSERT_SETTING,
	        StatementType.UPDATE, SQL_UPDATE_SETTING
	);

	static final Map<StatementType, String> BUSINESS_CASE_SQL = Map.of(
	        StatementType.SELECT_ALL, SQL_SELECT_ALL_BUSINESSCASES,
	        StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_BUSINESSCASES_BY_BANKACCOUNT,
	        StatementType.INSERT, SqlStatements.SQL_INSERT_BUSINESSCASE
	);

	static final Map<StatementType, String> MN_DAO_SQL = Map.of(
	        StatementType.INSERT, SQL_INSERT_CATEGORY_RULE_BANKACCOUNT
	);
	
}
