package de.gbanking.db;

public class SqlStatements {

	private SqlStatements() {
	}
	
	//static final String SQL_FOREIGN_KEY_CHECKS_ON = "PRAGMA foreign_keys = ON;"; 
	
	static final String SQL_SELECT_ID_BANKACCESS_BY_BLZ = "SELECT id FROM bankAccess WHERE blz = ?;";
	static final String SQL_INSERT_BANKACCESS = "INSERT INTO bankAccess (bankName, country, blz, hbciURL, port, userId, customerId, sysId, tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion, hbciFilterType, active, updatedAt) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";
	static final String SQL_UPDATE_BANKACCESS = "UPDATE bankAccess SET bankName = ?, country = ?, blz = ?, hbciURL = ?, port = ?, userId = ?, customerId = ?, sysId = ?, tanProcedure = ?, allowedTwostepMechanisms = ?, hbciVersion = ?, bpdVersion = ?, updVersion = ?, hbciFilterType = ?, active = ?, updatedAt = ? WHERE id = ?";
	static final String SQL_SELECT_ALL_BANKACCESSES = "SELECT id, bankName, country, blz, hbciURL, port, userId, customerId, sysId, tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion, hbciFilterType, active, updatedAt FROM bankAccess;";
	static final String SQL_SELECT_BANKACCESS_BY_ID = "SELECT id, bankName, country, blz, hbciURL, port, userId, customerId, sysId, tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion, hbciFilterType, active, updatedAt FROM bankAccess WHERE id = ?";
	static final String SQL_SELECT_BANKACCESS_BY_BLZ = "SELECT id, bankName, country, blz, hbciURL, port, userId, customerId, sysId, tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion, hbciFilterType, active, updatedAt FROM bankAccess WHERE blz = ?";
	static final String SQL_DELETE_BANKACCESS_BY_BLZ = "DELETE FROM bankAccess WHERE blz = ?;";
	
	static final String SQL_SELECT_ALL_PARAMETERDATA = "SELECT id, pdKey, pdType, updatedAt FROM parameterData;";
	static final String SQL_SELECT_ALL_BPD_OR_UPD = "SELECT bankAccess_id, pdKey, pdValue, updatedAt FROM %s;";
	public static final String SQL_INSERT_PARAMETERDATA = "INSERT INTO parameterData (pdKey, pdType, updatedAt) VALUES %s";
	static final String SQL_UPDATE_PARAMETERDATA = "UPDATE parameterData SET pdKey = ?, pdType = ?, updatedAt = ?";
	public static final String SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS = "SELECT bankAccess_id, pdKey, pdValue, updatedAt FROM %s WHERE bankAccess_id = ?";
	public static final String SQL_INSERT_BANKACCESS_PARAMETERDATA = "INSERT INTO bankAccess_parameterData (bankAccess_id, parameterData_id, pdValue, updatedAt) VALUES %s";
	static final String SQL_UPDATE_BANKACCESS_PARAMETERDATA = "UPDATE bankAccess_parameterData SET pdValue = ?, updatedAt = ? WHERE bankAccess_id = ? AND parameterData_id = ? AND pdType = ?";
	static final String SQL_DELETE_BANKACCESS_PARAMETERDATA = "DELETE FROM bankAccess_parameterData WHERE bankAccess_id = ? AND parameterData_id = ? AND pdType = ?";
	public static final String SQL_DELETE_ALL_BANKACCESS_PARAMETERDATA_BY_BANKACCESS = "DELETE FROM bankAccess_parameterData WHERE parameterData_id IN (SELECT id from parameterData WHERE pdType = ?) AND bankAccess_id = ?;";
	public static final String SQL_DELETE_UNUSED_PARAMETERDATA = "DELETE FROM parameterData WHERE id NOT IN (SELECT parameterData_id from bankAccess_parameterData);";
//	static final String SQL_INSERT_BPD = "INSERT INTO bpd (bankAccess_id, bpdKey, bpdValue, updatedAt) VALUES (?,?,?,?)";
//	static final String SQL_INSERT_UPD = "INSERT INTO upd (bankAccess_id, updKey, updValue, updatedAt) VALUES (?,?,?,?)";
	
	public static final String SQL_SELECT_ALL_BUSINESSCASES = "SELECT id, caseValue, updatedAt FROM businessCase";
	public static final String SQL_SELECT_ALL_BUSINESSCASES_BY_BANKACCOUNT = "SELECT bc.id, caseValue, bc.updatedAt FROM businessCase bc, bankAccount_businessCase babc WHERE bc.id = babc.businessCase_id AND babc.account_id = ?";
	public static final String SQL_INSERT_BUSINESSCASE = "INSERT INTO businessCase (caseValue, updatedAt) VALUES (?,?)";
	public static final String SQL_INSERT_BANKACCOUNT_BUSINESSCASE = "INSERT INTO bankAccount_businessCase (account_id, businessCase_id, updatedAt) VALUES (?,?,?)";
	
	static final String SQL_SELECT_ID_BANKACCOUNT_BY_IBAN_OR_NUMBER = "SELECT id, accountName FROM bankAccount WHERE (iban = ? OR number = ?);";
	static final String SQL_INSERT_BANKACCOUNT = "INSERT INTO bankAccount (bankAccess_id, accountName, currency, accountType, accountSource, iban, bic, number, subNumber, bankName, blz, hbciAccountType, accountLimit, customerId, ownerName, ownerName2, country, creditorId, isSEPAAccount, isOfflineAccount, accountState, balance, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
	static final String SQL_UPDATE_BANKACCOUNT = "UPDATE bankAccount SET bankAccess_id = ?, accountName = ?, currency = ?, accountType = ?, accountSource = ?, iban = ?, bic = ?, number = ?, subNumber = ?, bankName = ?, blz = ?, hbciAccountType = ?, accountLimit = ?, customerId = ?, ownerName = ?, ownerName2 = ?, country = ?, creditorId = ?, isSEPAAccount = ?, isOfflineAccount = ?, accountState = ?, balance = ?, updatedAt = ? WHERE id = ?";
	static final String SQL_UPDATE_BANKACCOUNT_SOURCE = "UPDATE bankAccount SET accountSource = ?, updatedAt = ? WHERE id = ?";
	static final String SQL_SELECT_ALL_BANKACCOUNTS = "SELECT id, bankAccess_id, accountName, currency, accountType, accountSource, iban, bic, number, subNumber, bankName, blz, accountLimit, customerId, ownerName, ownerName2, country, creditorId, isSepaAccount, isOfflineAccount, accountState, balance, updatedAt FROM bankAccount;";
	static final String SQL_SELECT_ALL_BANKACCOUNTS_BY_BANKACCESS = "SELECT id, bankAccess_id, accountName, currency, accountType, accountSource, iban, bic, number, subNumber, bankName, blz, accountLimit, customerId, ownerName, ownerName2, country, creditorId, isSepaAccount, isOfflineAccount, accountState, balance, updatedAt FROM bankAccount WHERE bankAccess_id = ?;";
	static final String SQL_SELECT_ACCOUNT_LAST_BOOKING_DATE = "SELECT MAX(dateBooking) AS lastBookingDate FROM booking where account_id = ?;";
	
	static final String SQL_SELECT_ID_MONEYTRANSFER_BY_ID_AND_ACCOUNT_ID = "SELECT id FROM moneytransfer WHERE id = ? AND account_id = ?;";
	static final String SQL_INSERT_MONEYTRANSFER = "INSERT INTO moneytransfer (account_id, moneytransferType, recipient_id, purpose, amount, executionDate, moneytransferStatus, standingorderMode, historyorder_id, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
	static final String SQL_UPDATE_MONEYTRANSFER = "UPDATE moneytransfer SET moneytransferType = ?, recipient_id = ?, purpose = ?, amount = ?, executionDate = ?, standingorderMode = ?, historyorder_id = ?, updatedAt = ? WHERE id = ?";
	static final String SQL_SELECT_ALL_MONEYTRANSFERS_BY_ACCOUNT = "SELECT * FROM moneytransfer where account_id = ?";
	/*private*/ static final String SQL_SELECT_ALL_MONEYTRANSFERS = "SELECT m.id, m.account_id, m.moneytransferType, m.recipient_id, m.purpose, m.amount, m.executionDate, m.moneytransferStatus, m.standingorderMode, m.historyorder_id, m.updatedAt, r.id AS r_id, r.name, r.iban, r.bic, r.bank FROM moneytransfer m, recipient r WHERE m.recipient_id = r.id;";
	static final String SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT = removeLast(SQL_SELECT_ALL_MONEYTRANSFERS) + " AND m.account_id = ?";
	static final String SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_STATE = removeLast(SQL_SELECT_ALL_MONEYTRANSFERS) + " AND moneytransferstatus = ?";
	static final String SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT_AND_STATE = removeLast(SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT) + " AND moneytransferstatus = ?";
	static final String SQL_DELETE_MONEYTRANSFER_BY_ID = "DELETE FROM moneytransfer WHERE id = ?;";
	
	public static final String SQL_INSERT_RECIPIENT = "INSERT INTO recipient (name, iban, bic, accountnumber, blz, bank, source, note, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
	public static final String SQL_UPDATE_RECIPIENT = "UPDATE recipient SET name = ?, iban = ?, bic = ?, accountnumber = ?, blz = ?, bank = ?, source = ?, note = ?, updatedAt = ? WHERE id = ?";
	public static final String SQL_UPDATE_RECIPIENT_NOTE = "UPDATE recipient SET note = ?, updatedAt = ? WHERE id = ?";
	public static final String SQL_SELECT_ID_RECIPIENT_BY_IBAN = "SELECT id FROM recipient WHERE iban = ?;";
	static final String SQL_SELECT_ALL_RECIPIENTS = "SELECT id, name, iban, bic, accountnumber, blz, bank, source, note, updatedAt FROM recipient;";
	static final String SQL_SELECT_ALL_RECIPIENTS_WITH_NAME_IBAN = removeLast(SQL_SELECT_ALL_RECIPIENTS) + " WHERE name IS NOT NULL AND name <> '' AND iban IS NOT NULL AND iban <> '' ORDER BY name";
	static final String SQL_SELECT_RECIPIENT_BY_ID = removeLast(SQL_SELECT_ALL_RECIPIENTS) + " WHERE id = ?;";
	static final String SQL_FIND_RECIPIENT_BY_ARGS = removeLast(SQL_SELECT_ALL_RECIPIENTS) + " WHERE (name  = ? or name IS NULL) AND (iban  = ? or iban IS NULL) AND (accountnumber = ? or accountnumber IS NULL) AND (blz  = ? or blz IS NULL) AND (bic  = ? or bic IS NULL)"; 
	private static final String SQL_WHERE_RECIPIENT_BY_ID_IF_NOT_REFERENCED = " AND id NOT IN (SELECT recipient_id from moneytransfer WHERE recipient_id = recipient.id) AND id NOT IN (SELECT recipient_id from booking WHERE recipient_id = recipient.id);";
	static final String SQL_DELETE_RECIPIENT_BY_ID_IF_NOT_REFERENCED = "DELETE FROM recipient WHERE id = ? " + SQL_WHERE_RECIPIENT_BY_ID_IF_NOT_REFERENCED;
	public static final String SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED = removeLast(SQL_SELECT_ALL_RECIPIENTS) + " WHERE id = ? " + SQL_WHERE_RECIPIENT_BY_ID_IF_NOT_REFERENCED;
	static final String SQL_UPDATE_RECIPIENT_IF_NOT_REFERENCED = SQL_UPDATE_RECIPIENT + SQL_WHERE_RECIPIENT_BY_ID_IF_NOT_REFERENCED;
	
	public static final String SQL_INSERT_CATEGORY = "INSERT INTO category (name, parent_id, updatedAt) VALUES (?, ?, ?);";
	static final String SQL_UPDATE_CATEGORY = "UPDATE category SET name = ?, parent_id = ?, updatedAt = ? WHERE id = ?";
	static final String SQL_SELECT_ID_CATEGORY_BY_NAME = "SELECT id FROM category WHERE name = ?;";
	static final String SQL_SELECT_ALL_CATEGORIES = "SELECT id, parent_id, name, null AS fullName, updatedAt FROM category;";
	static final String SQL_FIND_CATEGORY = removeLast(SQL_SELECT_ALL_CATEGORIES) + " WHERE name LIKE ? AND (parent_id = ? OR parent_id IS NULL);";
	private static final String SQL_SELECT_CATEGORY_RECURSIVE_WITH = """
                        WITH RECURSIVE category_tree (id, parent_id, "singleName", name, updatedAt)  AS (
                            SELECT id, parent_id, name, name, updatedAt
                            FROM category
                            WHERE parent_id IS NULL
                            UNION ALL
                            SELECT c.id, c.parent_id, c.name, CONCAT(ct.name, ':', c.name), c.updatedAt
                            FROM category c
                            JOIN category_tree ct ON c.parent_id = ct.id)
                        """;
	static final String SQL_SELECT_ALL_CATEGORIES_FULL = removeLast(SQL_SELECT_CATEGORY_RECURSIVE_WITH) + " SELECT id, parent_id, singleName as name, name AS fullName, updatedAt FROM category_tree";
	static final String SQL_SELECT_CATEGORY_FULL_BY_NAME = removeLast(SQL_SELECT_ALL_CATEGORIES_FULL) + " WHERE name like %?%";
	static final String SQL_DELETE_CATEGORY = "DELETE FROM category WHERE id = ?";
	
	static final String SQL_SELECT_ID_CATEGORY_RULE = "SELECT id FROM categoryRule WHERE id = ?;";
	static final String SQL_SELECT_ALL_CATEGORY_RULE = "SELECT id, category_id, filterDateFrom, filterDateTo, filterAmountFrom, filterAmountTo, filterRecipient, filterPurpose, filterRecipientIsRegex, filterPurposeIsRegex, joinType, updatedAt FROM categoryRule";
	static final String SQL_INSERT_CATEGORY_RULE = "INSERT INTO categoryRule (category_id, filterDateFrom, filterDateTo, filterAmountFrom, filterAmountTo, filterRecipient, filterPurpose, filterRecipientIsRegex, filterPurposeIsRegex, joinType, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
	static final String SQL_UPDATE_CATEGORY_RULE = "UPDATE categoryRule SET category_id = ?, filterDateFrom = ?, filterDateTo = ?, filterAmountFrom = ?, filterAmountTo = ?, filterRecipient = ?, filterPurpose = ?, filterRecipientIsRegex = ?, filterPurposeIsRegex = ?, joinType = ?, updatedAt = ?) WHERE id = ?;";
	
	static final String SQL_SELECT_ALL_CATEGORY_RULE_BANKACCOUNT = "SELECT id, category_id, account_id, updatedAt FROM categoryRule_bankAccount";
	static final String SQL_INSERT_CATEGORY_RULE_BANKACCOUNT = "INSERT INTO categoryRule_bankAccount (category_id, account_id, updatedAt) VALUES (?, ?, ?);";
	static final String SQL_UPDATE_CATEGORY_RULE_BANKACCOUNT = "UPDATE categoryRule_bankAccount SET category_id = ?, account_id = ?, updatedAt = ?) WHERE id = ?;";
	
	static final String SQL_SELECT_ID_BOOKING = "SELECT id FROM booking WHERE id = ?;";
	static final String SQL_INSERT_BOOKING = "INSERT INTO booking (account_id, dateBooking, dateValue, purpose, amount, currency, sepaCustomerRef, sepaCreditorId, sepaEndToEnd, sepaMandate, sepaPersonId, sepaPurpose, sepaTyp, bookingType, bookingSource, crossAccount_id, recipient_id, category_id, crossBooking_id, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
	static final String SQL_UPDATE_BOOKING = "UPDATE booking SET account_id = ?, dateBooking = ?, dateValue = ?, purpose = ?, amount = ?, currency = ?, sepaCustomerRef = ?, sepaCreditorId = ?, sepaEndToEnd = ?, sepaMandate = ?, sepaPersonId = ?, sepaPurpose = ?, sepaTyp = ?, bookingType = ?, bookingSource = ?, crossAccount_id = ?, recipient_id = ?, category_id = ?, crossBooking_id = ?, updatedAt = ? WHERE id = ?;";
	static final String SQL_SELECT_ALL_BOOKINGS = "SELECT id, account_id, bookingType, bookingSource, dateBooking, dateValue, purpose, amount, currency, sepaCustomerRef, sepaCreditorId, sepaEndToEnd, sepaMandate, sepaPersonId, sepaPurpose, sepaTyp, crossAccount_id, updatedAt FROM booking;";
	
	static final String SQL_SELECT_ALL_BOOKINGS_BY_ACCOUNT = "SELECT id, account_id, bookingType, bookingSource, dateBooking, dateValue, purpose, amount, currency, sepaCustomerRef, sepaCreditorId, sepaEndToEnd, sepaMandate, sepaPersonId, sepaPurpose, sepaTyp, crossAccount_id, recipient_id, category_id, crossBooking_id, updatedAt FROM booking where account_id = ?;";
	static final String SQL_SELECT_ALL_BOOKINGS_FULL = """
            SELECT b.id, b.account_id, b.bookingType, b.bookingSource, b.dateBooking, b.dateValue, b.purpose, b.amount, b.currency, b.sepaCustomerRef, b.sepaCreditorId, b.sepaEndToEnd, b.sepaMandate, b.sepaPersonId, b.sepaPurpose, b.sepaTyp, b.crossAccount_id, b.recipient_id, b.category_id, crossBooking_id, b.updatedAt,
                a.accountName,
                ca.accountName AS crossAccountName,
                r.name, r.iban, r.bic, r.accountnumber, r.blz, r.bank, r.source, r.note,
                c.fullName AS categoryFullName,
                SUM(b.amount) OVER (ORDER BY b.id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS balance
            FROM booking b
            JOIN bankAccount a on b.account_id = a.id
            LEFT JOIN bankAccount ca on b.crossAccount_id = ca.id
            LEFT JOIN recipient r on b.recipient_id = r.id
            LEFT JOIN categoryFull c on b.category_id = c.id 
            """;
	static final String SQL_SELECT_ALL_BOOKINGS_WITH_BALANCE = """
            SELECT b.id, b.account_id, b.bookingType, b.bookingSource, b.dateBooking, b.dateValue, b.purpose, b.amount, b.currency, b.sepaCustomerRef, b.sepaCreditorId, b.sepaEndToEnd, b.sepaMandate, b.sepaPersonId, b.sepaPurpose, b.sepaTyp, b.crossAccount_id, b.recipient_id, b.category_id, crossBooking_id, b.updatedAt,
                a.accountName,
                ca.accountName AS crossAccountName,
                c.fullName AS categoryFullName,
                SUM(b.amount) OVER (ORDER BY b.dateBooking ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS balance
            FROM booking b
            JOIN bankAccount a on b.account_id = a.id
            LEFT JOIN bankAccount ca on b.crossAccount_id = ca.id
            LEFT JOIN categoryFull c on b.category_id = c.id 
            """;
	static final String SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT = SQL_SELECT_ALL_BOOKINGS_FULL + 
			"""
            WHERE account_id = ?
            ORDER BY b.id desc;
            """;
	
	static final String SQL_FIND_CROSS_BOOKINGS_FULL = SQL_SELECT_ALL_BOOKINGS_FULL + 
			"""
            WHERE (a.iban LIKE ? OR a.number LIKE ?)
            AND b.amount = ?
            AND b.dateBooking = ?
            AND b.bookingType NOT IN ('REBOOKING_IN', 'REBOOKING_OUT')
            ORDER BY b.id desc;
            """;
	
	static final String SQL_UPDATE_BOOKINGS_SOURCE = "UPDATE booking SET bookingSource = ?, updatedAt = ? WHERE account_id = ? AND id = ?";
	static final String SQL_UPDATE_BOOKINGS_RECIPIENT = "UPDATE booking set recipient_id = ? WHERE id IN (%s)";
	static final String SQL_UPDATE_BOOKINGS_CATEGORY = "UPDATE booking set category_id = ? WHERE id IN (%s)";
	
	static final String SQL_INSERT_MONEYTRANSFER_PROTOCOL = "INSERT INTO moneytransferProtocol (moneytransfer_id, moneytransferStatus, timeStart, timeFinish, protocolText, updatedAt) VALUES (?, ?, ?, ?, ?, ?);";
	
	private static final String INSTITUTE_FIELD_LIST = "importNumber, blz, bic, bankName, place, dataCenter, organisation, hbciDns, hbciIp, hbciVersion, ddv, rdh1, rdh2, rdh3, rdh4, rdh5, rdh6, rdh7, rdh8, rdh9, rdh10, pinUrl, version, lastChanged, stateType, updatedAt";
	static final String SQL_SELECT_ALL_INSTITUTES = "SELECT id, " + INSTITUTE_FIELD_LIST + " FROM institute;";
	static final String SQL_SELECT_ID_INSTIUTE_BY_ID = "SELECT id FROM institute WHERE id = ?;";
	static final String SQL_SELECT_ID_INSTIUTE_BY_BLZ = "SELECT id FROM institute WHERE blz = ?;";
	static final String SQL_INSERT_INSTIUTE = "INSERT INTO institute ( " + INSTITUTE_FIELD_LIST + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
	static final String SQL_UPDATE_INSTIUTE = "UPDATE institute SET importNumber = ?, blz = ?, bic = ?, bankName = ?, place = ?, dataCenter = ?, organisation = ?, hbciDns = ?, hbciIp = ?, hbciVersion = ?, ddv = ?, rdh1 = ?, rdh2 = ?, rdh3 = ?, rdh4 = ?, rdh5 = ?, rdh6 = ?, rdh7 = ?, rdh8 = ?, rdh9 = ?, rdh10 = ?, pinUrl = ?, version = ?, lastChanged = ?, stateType = ?, updatedAt = ? WHERE id = ?;";
	
	static final String SQL_SETUP_DROP_VIEW_CATEGORY_FULL = "DROP VIEW IF EXISTS categoryFull;";
	static final String SQL_SETUP_VIEW_CATEGORY_FULL = "CREATE VIEW categoryFull AS " + SQL_SELECT_ALL_CATEGORIES_FULL; 
	
	private static final String SETTING_FIELD_LIST = "attribute, value, dataType, editable, visible, comment, updatedAt";
	static final String SQL_SELECT_ALL_SETTINGS = "SELECT id, " + SETTING_FIELD_LIST + " FROM setting;";
	static final String SQL_SELECT_ID_SETTING_BY_ID = "SELECT id FROM setting WHERE id = ?;";
	static final String SQL_SELECT_ID_SETTING_BY_ATTRIBUTE = "SELECT id FROM setting WHERE attribute = ?;";
	static final String SQL_INSERT_SETTING = "INSERT INTO setting ( " + SETTING_FIELD_LIST + ") VALUES (?, ?, ?, ?, ?, ?, ?);";
	static final String SQL_UPDATE_SETTING = "UPDATE setting SET attribute = ?, value = ?, dataType = ?, editable = ?, visible = ?, comment = ?, updatedAt = ? WHERE id = ?;";
	
	private static final String SQL_SELECT_PD = """
            SELECT bapd.bankAccess_id, pd.pdKey, bapd.pdValue, bapd.updatedAt 
            FROM bankAccess_parameterData bapd, parameterData pd 
            WHERE bapd.parameterData_id = pd.id 
            AND pdType =
            """;
	
	static final String SQL_SETUP_VIEW_BPD = "CREATE VIEW bpd AS " + SQL_SELECT_PD + "'BPD';";
	static final String SQL_SETUP_VIEW_UPD = "CREATE VIEW upd AS " + SQL_SELECT_PD + "'UPD';";
	
	static final String SQL_SETUP_DROP_VIEW_BPD = "DROP VIEW IF EXISTS bpd;";
	static final String SQL_SETUP_DROP_VIEW_UPD = "DROP VIEW IF EXISTS upd;";
	
	private static String removeLast(String sql) {
		return sql.substring(0, sql.length() - 1);
	}
	
}
