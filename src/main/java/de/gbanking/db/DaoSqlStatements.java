package de.gbanking.db;

import java.util.Map;

import de.gbanking.db.StatementsConfig.StatementType;
import de.gbanking.db.dao.enu.ParameterDataType;

public final class DaoSqlStatements {

    private DaoSqlStatements() {
        /* This utility class should not be instantiated */
    }

    private static String dml(String key) {
        return SqlTemplateRepository.getDml(key);
    }

    static final String SQL_DELETE_BANKACCESS_BY_BLZ = dml("SQL_DELETE_BANKACCESS_BY_BLZ");
    static final String SQL_FIND_CATEGORY = dml("SQL_FIND_CATEGORY");
    public static final String SQL_FIND_CROSS_BOOKINGS_FULL = dml("SQL_FIND_CROSS_BOOKINGS_FULL");
    static final String SQL_FIND_RECIPIENT_BY_ARGS = dml("SQL_FIND_RECIPIENT_BY_ARGS");
    static final String SQL_INSERT_BANKACCESS = dml("SQL_INSERT_BANKACCESS");
    static final String SQL_INSERT_BANKACCOUNT = dml("SQL_INSERT_BANKACCOUNT");
    public static final String SQL_INSERT_BANKACCESS_PARAMETERDATA = dml("SQL_INSERT_BANKACCESS_PARAMETERDATA");
    public static final String SQL_INSERT_BANKACCOUNT_BUSINESSCASE = dml("SQL_INSERT_BANKACCOUNT_BUSINESSCASE");
    static final String SQL_INSERT_BOOKING = dml("SQL_INSERT_BOOKING");
    public static final String SQL_INSERT_BUSINESSCASE = dml("SQL_INSERT_BUSINESSCASE");
    static final String SQL_INSERT_CATEGORY = dml("SQL_INSERT_CATEGORY");
    static final String SQL_INSERT_CATEGORYRULE = dml("SQL_INSERT_CATEGORYRULE");
    static final String SQL_INSERT_CATEGORYRULE_BANKACCOUNT = dml("SQL_INSERT_CATEGORYRULE_BANKACCOUNT");
    static final String SQL_INSERT_INSTIUTE = dml("SQL_INSERT_INSTIUTE");
    static final String SQL_INSERT_MONEYTRANSFER = dml("SQL_INSERT_MONEYTRANSFER");
    public static final String SQL_INSERT_PARAMETERDATA = dml("SQL_INSERT_PARAMETERDATA");
    public static final String SQL_INSERT_RECIPIENT = dml("SQL_INSERT_RECIPIENT");
    static final String SQL_INSERT_SETTING = dml("SQL_INSERT_SETTING");
    static final String SQL_SELECT_ACCOUNT_LAST_BOOKING_DATE = dml("SQL_SELECT_ACCOUNT_LAST_BOOKING_DATE");
    static final String SQL_SELECT_ALL_BANKACCESSES = dml("SQL_SELECT_ALL_BANKACCESSES");
    public static final String SQL_SELECT_ALL_BANKACCOUNTS = dml("SQL_SELECT_ALL_BANKACCOUNTS");
    static final String SQL_SELECT_ALL_BANKACCOUNTS_BY_BANKACCESS = dml("SQL_SELECT_ALL_BANKACCOUNTS_BY_BANKACCESS");
    public static final String SQL_SELECT_ALL_BOOKINGS = dml("SQL_SELECT_ALL_BOOKINGS");
    static final String SQL_SELECT_ALL_BOOKINGS_FULL = dml("SQL_SELECT_ALL_BOOKINGS_FULL");
    static final String SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT = dml("SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT");
    static final String SQL_SELECT_ALL_BPD_OR_UPD = dml("SQL_SELECT_ALL_BPD_OR_UPD");
    public static final String SQL_SELECT_ALL_BUSINESSCASES = dml("SQL_SELECT_ALL_BUSINESSCASES");
    public static final String SQL_SELECT_ALL_BUSINESSCASES_BY_BANKACCOUNT = dml("SQL_SELECT_ALL_BUSINESSCASES_BY_BANKACCOUNT");
    static final String SQL_SELECT_ALL_CATEGORIES_FULL = dml("SQL_SELECT_ALL_CATEGORIES_FULL");
    static final String SQL_SELECT_ALL_CATEGORYRULES = dml("SQL_SELECT_ALL_CATEGORYRULES");
    static final String SQL_SELECT_ALL_CATEGORYRULES_FULL = dml("SQL_SELECT_ALL_CATEGORYRULES_FULL");
    static final String SQL_SELECT_ALL_INSTITUTES = dml("SQL_SELECT_ALL_INSTITUTES");
    static final String SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT = dml("SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT");
    static final String SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT_AND_STATE = dml("SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT_AND_STATE");
    static final String SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_STATE = dml("SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_STATE");
    static final String SQL_SELECT_ALL_PARAMETERDATA = dml("SQL_SELECT_ALL_PARAMETERDATA");
    public static final String SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS = dml("SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS");
    static final String SQL_SELECT_ALL_RECIPIENTS = dml("SQL_SELECT_ALL_RECIPIENTS");
    static final String SQL_SELECT_ALL_RECIPIENTS_WITH_NAME_IBAN = dml("SQL_SELECT_ALL_RECIPIENTS_WITH_NAME_IBAN");
    static final String SQL_SELECT_ALL_SETTINGS = dml("SQL_SELECT_ALL_SETTINGS");
    public static final String SQL_SELECT_BANKACCESS_BY_BLZ = dml("SQL_SELECT_BANKACCESS_BY_BLZ");
    public static final String SQL_SELECT_BANKACCESS_BY_ID = dml("SQL_SELECT_BANKACCESS_BY_ID");
    static final String SQL_SELECT_ID_BANKACCESS_BY_BLZ = dml("SQL_SELECT_ID_BANKACCESS_BY_BLZ");
    static final String SQL_SELECT_ID_BANKACCOUNT_BY_IBAN_OR_NUMBER = dml("SQL_SELECT_ID_BANKACCOUNT_BY_IBAN_OR_NUMBER");
    static final String SQL_SELECT_ID_CATEGORYRULE = dml("SQL_SELECT_ID_CATEGORYRULE");
    static final String SQL_SELECT_ID_CATEGORY_BY_NAME = dml("SQL_SELECT_ID_CATEGORY_BY_NAME");
    static final String SQL_SELECT_ID_INSTIUTE_BY_ID = dml("SQL_SELECT_ID_INSTIUTE_BY_ID");
    public static final String SQL_SELECT_ID_RECIPIENT_BY_IBAN = dml("SQL_SELECT_ID_RECIPIENT_BY_IBAN");
    static final String SQL_SELECT_ID_MONEYTRANSFER_BY_ID_AND_ACCOUNT_ID = dml("SQL_SELECT_ID_MONEYTRANSFER_BY_ID_AND_ACCOUNT_ID");
    static final String SQL_SELECT_ID_SETTING_BY_ID = dml("SQL_SELECT_ID_SETTING_BY_ID");
    public static final String SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED = dml("SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED");
    public static final String SQL_DELETE_ALL_BANKACCESS_PARAMETERDATA_BY_BANKACCESS = dml("SQL_DELETE_ALL_BANKACCESS_PARAMETERDATA_BY_BANKACCESS");
    public static final String SQL_DELETE_UNUSED_PARAMETERDATA = dml("SQL_DELETE_UNUSED_PARAMETERDATA");
    static final String SQL_UPDATE_BANKACCESS = dml("SQL_UPDATE_BANKACCESS");
    static final String SQL_UPDATE_BANKACCOUNT = dml("SQL_UPDATE_BANKACCOUNT");
    static final String SQL_UPDATE_BANKACCOUNT_SOURCE = dml("SQL_UPDATE_BANKACCOUNT_SOURCE");
    static final String SQL_UPDATE_BOOKING = dml("SQL_UPDATE_BOOKING");
    public static final String SQL_UPDATE_BOOKINGS_CATEGORY = dml("SQL_UPDATE_BOOKINGS_CATEGORY");
    public static final String SQL_UPDATE_BOOKINGS_RECIPIENT = dml("SQL_UPDATE_BOOKINGS_RECIPIENT");
    static final String SQL_UPDATE_BOOKINGS_SOURCE = dml("SQL_UPDATE_BOOKINGS_SOURCE");
    static final String SQL_UPDATE_CATEGORY = dml("SQL_UPDATE_CATEGORY");
    static final String SQL_UPDATE_CATEGORYRULE = dml("SQL_UPDATE_CATEGORYRULE");
    static final String SQL_UPDATE_INSTIUTE = dml("SQL_UPDATE_INSTIUTE");
    static final String SQL_UPDATE_MONEYTRANSFER = dml("SQL_UPDATE_MONEYTRANSFER");
    public static final String SQL_UPDATE_RECIPIENT = dml("SQL_UPDATE_RECIPIENT");
    public static final String SQL_UPDATE_RECIPIENT_IF_NOT_REFERENCED = dml("SQL_UPDATE_RECIPIENT_IF_NOT_REFERENCED");
    public static final String SQL_UPDATE_RECIPIENT_NOTE = dml("SQL_UPDATE_RECIPIENT_NOTE");
    static final String SQL_UPDATE_SETTING = dml("SQL_UPDATE_SETTING");

    static final Map<StatementType, String> BANK_ACCESS_SQL = Map.of(
            StatementType.SELECT_ALL, SQL_SELECT_ALL_BANKACCESSES,
            StatementType.SELECT_ID, SQL_SELECT_ID_BANKACCESS_BY_BLZ,
            StatementType.INSERT, SQL_INSERT_BANKACCESS,
            StatementType.UPDATE, SQL_UPDATE_BANKACCESS,
            StatementType.DELETE_BANKACCESS_BY_BLZ, SQL_DELETE_BANKACCESS_BY_BLZ);

    static final Map<StatementType, String> BANK_ACCOUNT_SQL = Map.of(
            StatementType.SELECT_ALL, SQL_SELECT_ALL_BANKACCOUNTS,
            StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_BANKACCOUNTS_BY_BANKACCESS,
            StatementType.SELECT_ID, SQL_SELECT_ID_BANKACCOUNT_BY_IBAN_OR_NUMBER,
            StatementType.SELECT_FULL_DATA, SQL_SELECT_ALL_BANKACCOUNTS,
            StatementType.INSERT, SQL_INSERT_BANKACCOUNT,
            StatementType.UPDATE, SQL_UPDATE_BANKACCOUNT,
            StatementType.SELECT_ACCOUNT_LAST_BOOKING_DATE, SQL_SELECT_ACCOUNT_LAST_BOOKING_DATE,
            StatementType.UPDATE_ACCOUNT_SOURCE, SQL_UPDATE_BANKACCOUNT_SOURCE);

    static final Map<StatementType, String> BOOKING_SQL = Map.of(
            StatementType.SELECT_ALL, SQL_SELECT_ALL_BOOKINGS,
            StatementType.SELECT_FULL_DATA, SQL_SELECT_ALL_BOOKINGS_FULL,
            StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT,
            StatementType.SELECT_WITH_PARENT_AND_FULL_DATA, SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT,
            StatementType.INSERT, SQL_INSERT_BOOKING,
            StatementType.UPDATE, SQL_UPDATE_BOOKING,
            StatementType.UPDATE_BOOKING_SOURCE, SQL_UPDATE_BOOKINGS_SOURCE);

    static final Map<StatementType, String> CATEGORY_SQL = Map.of(
            StatementType.SELECT_ALL, SQL_SELECT_ALL_CATEGORIES_FULL,
            StatementType.SELECT_ID, SQL_SELECT_ID_CATEGORY_BY_NAME,
            StatementType.SELECT_FIND, SQL_FIND_CATEGORY,
            StatementType.INSERT, SQL_INSERT_CATEGORY,
            StatementType.UPDATE, SQL_UPDATE_CATEGORY);

    static final Map<StatementType, String> CATEGORY_RULE_SQL = Map.of(
            StatementType.SELECT_ID, SQL_SELECT_ID_CATEGORYRULE,
            StatementType.SELECT_ALL, SQL_SELECT_ALL_CATEGORYRULES_FULL,
            StatementType.INSERT, SQL_INSERT_CATEGORYRULE,
            StatementType.UPDATE, SQL_UPDATE_CATEGORYRULE);

    static final Map<StatementType, String> MONEY_TRANSFER_SQL = Map.of(
            StatementType.SELECT_ALL, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT,
            StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT,
            StatementType.SELECT_FULL_DATA, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_STATE,
            StatementType.SELECT_WITH_PARENT_AND_FILTER, SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT_AND_STATE,
            StatementType.SELECT_ID, SQL_SELECT_ID_MONEYTRANSFER_BY_ID_AND_ACCOUNT_ID,
            StatementType.INSERT, SQL_INSERT_MONEYTRANSFER,
            StatementType.UPDATE, SQL_UPDATE_MONEYTRANSFER);

    static final Map<StatementType, String> RECIPIENT_SQL = Map.of(
            StatementType.SELECT_ALL, SQL_SELECT_ALL_RECIPIENTS,
            StatementType.SELECT_FULL_DATA, SQL_SELECT_ALL_RECIPIENTS_WITH_NAME_IBAN,
            StatementType.SELECT_FIND, SQL_FIND_RECIPIENT_BY_ARGS,
            StatementType.SELECT_FIND_UNREFERENCED, SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED,
            StatementType.SELECT_SPECIFIC_EDITABLE, SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED,
            StatementType.INSERT, SQL_INSERT_RECIPIENT,
            StatementType.UPDATE, SQL_UPDATE_RECIPIENT,
            StatementType.UPDATE_SPECIFIC_NOT_REFERENCED, SQL_UPDATE_RECIPIENT_IF_NOT_REFERENCED,
            StatementType.UPDATE_SPECIFIC_REFERENCED, SQL_UPDATE_RECIPIENT_NOTE);

    static final Map<StatementType, String> PARAMETER_DATA_SQL = Map.of(
            StatementType.SELECT_ALL, SQL_SELECT_ALL_PARAMETERDATA,
            StatementType.INSERT, SQL_INSERT_PARAMETERDATA);

    static final Map<StatementType, String> BPD_SQL = Map.of(
            StatementType.SELECT_ALL, String.format(SQL_SELECT_ALL_BPD_OR_UPD, ParameterDataType.BPD.name()),
            StatementType.SELECT_WITH_PARENT, String.format(SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS, ParameterDataType.BPD.name()));

    static final Map<StatementType, String> UPD_SQL = Map.of(
            StatementType.SELECT_ALL, String.format(SQL_SELECT_ALL_BPD_OR_UPD, ParameterDataType.UPD.name()),
            StatementType.SELECT_WITH_PARENT, String.format(SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS, ParameterDataType.UPD.name()));

    static final Map<StatementType, String> INSTITUTE_SQL = Map.of(
            StatementType.SELECT_ALL, SQL_SELECT_ALL_INSTITUTES,
            StatementType.SELECT_ID, SQL_SELECT_ID_INSTIUTE_BY_ID,
            StatementType.INSERT, SQL_INSERT_INSTIUTE,
            StatementType.UPDATE, SQL_UPDATE_INSTIUTE);

    static final Map<StatementType, String> SETTING_SQL = Map.of(
            StatementType.SELECT_ALL, SQL_SELECT_ALL_SETTINGS,
            StatementType.SELECT_ID, SQL_SELECT_ID_SETTING_BY_ID,
            StatementType.INSERT, SQL_INSERT_SETTING,
            StatementType.UPDATE, SQL_UPDATE_SETTING);

    static final Map<StatementType, String> BUSINESS_CASE_SQL = Map.of(
            StatementType.SELECT_ALL, SQL_SELECT_ALL_BUSINESSCASES,
            StatementType.SELECT_WITH_PARENT, SQL_SELECT_ALL_BUSINESSCASES_BY_BANKACCOUNT,
            StatementType.INSERT, SQL_INSERT_BUSINESSCASE);

    static final Map<StatementType, String> MN_DAO_SQL = Map.of(
            StatementType.INSERT, SQL_INSERT_CATEGORYRULE_BANKACCOUNT);
}
