package de.gbanking.db;

class SqlStatementsDDL {

	private SqlStatementsDDL() {
	}

	static final String SQL_FOREIGN_KEY_CHECKS_ON = "PRAGMA foreign_keys = ON;";

	static final String SQL_SETUP_DROP_BANKACCESS = "DROP TABLE IF EXISTS bankAccess;";
	static final String SQL_SETUP_DROP_BPD = "DROP TABLE IF EXISTS bpd;";
	static final String SQL_SETUP_DROP_UPD = "DROP TABLE IF EXISTS upd;";
	static final String SQL_SETUP_DROP_BANKACCOUNT = "DROP TABLE IF EXISTS bankAccount;";
	static final String SQL_SETUP_DROP_BOOKING = "DROP TABLE IF EXISTS booking;";
	static final String SQL_SETUP_DROP_BUSINESSCASE = "DROP TABLE IF EXISTS businessCase;";
	static final String SQL_SETUP_DROP_MONEYTRANSFER = "DROP TABLE IF EXISTS moneytransfer;";
	static final String SQL_SETUP_DROP_RECIPIENT = "DROP TABLE IF EXISTS recipient;";
	static final String SQL_SETUP_DROP_CATEGORY = "DROP TABLE IF EXISTS category;";
	static final String SQL_SETUP_DROP_CATEGORY_RULE = "DROP TABLE IF EXISTS categoryRule;";
	static final String SQL_SETUP_DROP_CATEGORY_RULE_BANKACCOUNT = "DROP TABLE IF EXISTS categoryRule_bankAccount;";
	static final String SQL_SETUP_DROP_MONEYTRANSFER_PROTOCOL = "DROP TABLE IF EXISTS moneytransferProtocol;";
	static final String SQL_SETUP_DROP_BANKACCOUNT_BUSINESSCASE = "DROP TABLE IF EXISTS bankAccount_businessCase;";
	static final String SQL_SETUP_DROP_PARAMETERDATA = "DROP TABLE IF EXISTS parameterData;";
	static final String SQL_SETUP_DROP_BANKACCESS_PARAMETERDATA = "DROP TABLE IF EXISTS bankAccess_parameterData;";
	static final String SQL_SETUP_DROP_INSTITUTE = "DROP TABLE IF EXISTS institute;";
	static final String SQL_SETUP_DROP_SETTING = "DROP TABLE IF EXISTS setting;";
	static final String SQL_SETUP_DROP_BOOKING_CATEGORY = "DROP TABLE IF EXISTS booking_category;";
	
	static final String SQL_SETUP_DROP_UNIQUE_INDEX_INSTITUTE = "DROP INDEX IF EXISTS uk_institute_blz_importnr_current;";

	static final String SQL_SETUP_CREATE_BANKACCESS = """
			CREATE TABLE bankAccess (
			  id INTEGER PRIMARY KEY,
			  bankName TEXT NOT NULL,
			  country TEXT NOT NULL,
			  blz TEXT NOT NULL,
			  hbciURL TEXT,
			  port INTEGER,
			  userId TEXT NOT NULL,
			  customerId TEXT,
			  sysId TEXT,
			  tanProcedure TEXT NOT NULL,
			  allowedTwostepMechanisms TEXT,
			  hbciVersion TEXT,
			  bpdVersion TEXT NOT NULL,
			  updVersion TEXT NOT NULL,
			  hbciFilterType TEXT,
			  active REAL NOT NULL,
			  updatedAt TEXT NOT NULL);
			""";

	static final String SQL_SETUP_CREATE_BPD = """
			CREATE TABLE bpd (
			  id INTEGER PRIMARY KEY,
			  bankAccess_id INTEGER,
			  bpdKey TEXT,
			  bpdValue TEXT,
			  updatedAt TEXT NOT NULL,
			  FOREIGN KEY(bankAccess_id) REFERENCES bankAccess(id) ON DELETE CASCADE);
			""";

	static final String SQL_SETUP_CREATE_UPD = """
			CREATE TABLE upd (
			  id INTEGER PRIMARY KEY,
			  bankAccess_id INTEGER,
			  updKey TEXT,
			  updValue TEXT,
			  updatedAt TEXT NOT NULL,
			  FOREIGN KEY(bankAccess_id) REFERENCES bankAccess(id) ON DELETE CASCADE);
			""";

	static final String SQL_SETUP_CREATE_BANKACCOUNT = """
			CREATE TABLE bankAccount (
			  id INTEGER PRIMARY KEY,
			  bankAccess_id INTEGER,
			  accountName TEXT NOT NULL,
			  currency TEXT NOT NULL,
			  accountType TEXT NOT NULL,
			  accountSource TEXT NOT NULL,
			  iban TEXT,
			  bic TEXT,
			  number TEXT,
			  subNumber TEXT,
			  bankName TEXT,
			  blz TEXT,
			  hbciAccountType INTEGER,
			  accountLimit TEXT,
			  customerId TEXT ,
			  ownerName TEXT,
			  ownerName2 TEXT,
			  country TEXT,
			  creditorId TEXT,
			  isSEPAAccount REAL NOT NULL,
			  isOfflineAccount REAL NOT NULL,
			  accountState TEXT NOT NULL,
			  balance REAL,
			  updatedAt TEXT NOT NULL,
			  FOREIGN KEY(bankAccess_id) REFERENCES bankAccess(id) ON DELETE SET NULL,
			  CHECK (accountType IN ("CURRENT_ACCOUNT", "OVERNIGHT_MONEY",  "SAVINGS_ACCOUNT", "SAVINGS_PLAN", "SAVINGS_BOOK", "FIXED_DEPOSIT", "CREDIT_ACCOUNT")),
			  CHECK (accountSource IN ("ONLINE", "MANUELL", "IMPORT", "IMPORT_INITIAL", "ONLINE_NEW", "MANUELL_NEW", "IMPORT_NEW", "IMPORT_INITIAL_NEW"))
			  CHECK (accountState IN ("ACTIVE","INACTIVE","IGNORE")));
			""";

	static final String SQL_SETUP_CREATE_BOOKING = """
            CREATE TABLE booking (
               id INTEGER PRIMARY KEY,
               account_id INTEGER NOT NULL,
               dateBooking TEXT,
               dateValue TEXT,
               purpose TEXT,
               amount REAL,
               currency TEXT,
               sepaCustomerRef,
               sepaCreditorId,
               sepaEndToEnd,
               sepaMandate,
               sepaPersonId,
               sepaPurpose,
               sepaTyp,
               bookingType TEXT,
               bookingSource TEXT,
               crossAccount_id INTEGER,
               recipient_id INTEGER,
               category_id INTEGER,
               crossBooking_id INTEGER,
               updatedAt TEXT NOT NULL,
               FOREIGN KEY(account_id) REFERENCES bankAccount(id) ON DELETE CASCADE,
               FOREIGN KEY(crossAccount_id) REFERENCES bankAccount(id) ON DELETE SET NULL,
               FOREIGN KEY(recipient_id) REFERENCES recipient(id),
               FOREIGN KEY(category_id) REFERENCES category(id),
               CHECK (bookingType IN ("DEPOSIT", "REMOVAL",  "INTEREST", "INTEREST_CHARGE", "REBOOKING_OUT", "REBOOKING_IN")),
               CHECK (bookingSource IN ("ONLINE", "MANUELL", "IMPORT", "IMPORT_INITIAL", "ONLINE_NEW", "MANUELL_NEW", "IMPORT_NEW", "IMPORT_INITIAL_NEW")));
             """;

	static final String SQL_SETUP_CREATE_BUSINESSCASE = """
			CREATE TABLE businessCase (
			  id INTEGER PRIMARY KEY,
			  caseValue TEXT NOT NULL,
			  updatedAt TEXT NOT NULL);
			""";

	static final String SQL_SETUP_CREATE_MONEYTRANSFER = """
			CREATE TABLE moneytransfer (
			  id INTEGER PRIMARY KEY,
			  account_id INTEGER NOT NULL,
			  moneytransferType TEXT NOT NULL,
			  recipient_id INTEGER,
			  purpose TEXT,
			  amount REAL,
			  executionDate TEXT,
			  moneytransferStatus TEXT NOT NULL,
			  standingorderMode TEXT,
			  historyorder_id INTEGER,
			  updatedAt TEXT NOT NULL,
			  FOREIGN KEY(account_id) REFERENCES bankAccount(id) ON DELETE CASCADE,
			  FOREIGN KEY(recipient_id) REFERENCES recipient(id),
			  FOREIGN KEY(historyorder_id) REFERENCES moneytransfer(id),
			  CHECK (moneytransferType IN ("TRANSFER", "REALTIME_TRANSFER", "SCHEDULED_TRANSFER", "STANDING_ORDER")),
			  CHECK (moneytransferStatus IN ("NEW", "ERROR", "SENT")),
			  CHECK (standingorderMode IN ("MONTHLY", "BIMONTHLY", "QUARTERLY", "SEMI_ANNUALLY", "ANNUALLY")));
			""";

	static final String SQL_SETUP_CREATE_RECIPIENT = """
			CREATE TABLE recipient (
			  id INTEGER PRIMARY KEY,
			  name TEXT,
			  iban TEXT,
			  bic TEXT,
			  accountnumber TEXT,
			  blz TEXT,
			  bank TEXT,
			  source TEXT NOT NULL,
			  note TEXT,
			  updatedAt TEXT NOT NULL,
			  CHECK (source IN ("ONLINE", "MANUELL", "IMPORT", "IMPORT_INITIAL")));
			""";

	static final String SQL_SETUP_CREATE_CATEGORY = """
			CREATE TABLE category (
			  id INTEGER PRIMARY KEY,
			  name TEXT,
			  parent_id INTEGER,
			  updatedAt TEXT NOT NULL,
			  FOREIGN KEY(parent_id) REFERENCES category(id) ON DELETE CASCADE);
			""";

	static final String SQL_SETUP_CREATE_CATEGORY_RULE = """
			CREATE TABLE categoryRule (
			  id INTEGER PRIMARY KEY,
			  category_id INTEGER NOT NULL,
			  filterDateFrom TEXT,
			  filterDateTo TEXT,
			  filterAmountFrom REAL,
			  filterAmountTo REAL,
			  filterRecipient TEXT,
			  filterPurpose TEXT,
			  filterRecipientIsRegex REAL NOT NULL,
			  filterPurposeIsRegex REAL NOT NULL,
			  joinType TEXT NOT NULL,
			  updatedAt TEXT NOT NULL,
			  FOREIGN KEY(category_id) REFERENCES category(id) ON DELETE CASCADE,
			  CHECK (joinType IN ("AND", "OR")));
			""";

	static final String SQL_SETUP_CREATE_CATEGORY_RULE_BANKACCOUNT = """
			CREATE TABLE categoryRule_bankAccount (
			  id INTEGER PRIMARY KEY,
			  category_id INTEGER NOT NULL,
			  account_id INTEGER NOT NULL,
			  updatedAt TEXT NOT NULL,
			  FOREIGN KEY(category_id) REFERENCES category(id) ON DELETE CASCADE,
			  FOREIGN KEY(account_id) REFERENCES bankAccount(id) ON DELETE CASCADE);
			""";

	static final String SQL_SETUP_CREATE_MONEYTRANSFER_PROTOCOL = """
			CREATE TABLE moneytransferProtocol (
			  id INTEGER PRIMARY KEY,
			  moneytransfer_id INTEGER NOT NULL,
			  moneytransferStatus TEXT NOT NULL,
			  timeStart TEXT NOT NULL,
			  timeFinish TEXT,
			  protocolText TEXT,
			  updatedAt TEXT NOT NULL,
			  FOREIGN KEY(moneytransfer_id) REFERENCES moneytransfer(id) ON DELETE CASCADE,
			  CHECK (moneytransferStatus IN ("NEW", "ERROR", "SENT")));
			""";

	static final String SQL_SETUP_CREATE_BANKACCOUNT_BUSINESSCASE = """
			CREATE TABLE bankAccount_businessCase (
			  id INTEGER PRIMARY KEY,
			  account_id INTEGER NOT NULL,
			  businessCase_id INTEGER NOT NULL,
			  updatedAt TEXT NOT NULL,
			  FOREIGN KEY(account_id) REFERENCES bankAccount(id) ON DELETE CASCADE,
			  FOREIGN KEY(businessCase_id) REFERENCES businessCase(id) ON DELETE CASCADE);
			""";

	static final String SQL_SETUP_CREATE_PARAMETERDATA = """
			CREATE TABLE parameterData (
			  id INTEGER PRIMARY KEY,
			  pdKey TEXT,
			  pdType TEXT,
			  updatedAt TEXT NOT NULL
			  CHECK (pdType IN ("BPD", "UPD")));
			""";

	static final String SQL_SETUP_CREATE_BANKACCESS_PARAMETERDATA = """
			CREATE TABLE bankAccess_parameterData (
			  id INTEGER PRIMARY KEY,
			  bankAccess_id INTEGER,
			  parameterData_id INTEGER,
			  pdValue TEXT,
			  updatedAt TEXT NOT NULL,
			  FOREIGN KEY(bankAccess_id) REFERENCES bankAccess(id) ON DELETE CASCADE
			  FOREIGN KEY(parameterData_id) REFERENCES parameterData(id) ON DELETE CASCADE);
			""";

	static final String SQL_SETUP_CREATE_INSTITUTE = """
			CREATE TABLE institute (
			  id INTEGER PRIMARY KEY,
			  importNumber INTEGER NOT NULL,
			  blz TEXT NOT NULL,
			  bic TEXT,
			  bankName TEXT NOT NULL,
			  place TEXT,
			  dataCenter TEXT,
			  organisation TEXT,
			  hbciDns TEXT,
			  hbciIp TEXT,
			  hbciVersion REAL,
			  ddv TEXT,
			  rdh1 INTEGER,
			  rdh2 INTEGER,
			  rdh3 INTEGER,
			  rdh4 INTEGER,
			  rdh5 INTEGER,
			  rdh6 INTEGER,
			  rdh7 INTEGER,
			  rdh8 INTEGER,
			  rdh9 INTEGER,
			  rdh10 INTEGER,
			  pinUrl TEXT,
			  version TEXT,
			  lastChanged TEXT,
			  stateType INTEGER NOT NULL,
			  updatedAt TEXT NOT NULL,
			  CHECK (stateType IN (0, 1, 2)));
			""";
	
	static final String SQL_SETUP_CREATE_UNIQUE_INDEX_INSTITUTE = """
			CREATE UNIQUE INDEX uk_institute_blz_importnr_current
			  ON institute (blz, importNumber)
			  WHERE stateType IN ('ACTIVE', 'DUPLICATE');
			""";
	
	static final String SQL_SETUP_CREATE_SETTING = """
			CREATE TABLE setting (
			  id INTEGER PRIMARY KEY,
			  attribute TEXT NOT NULL,
			  value TEXT,
			  dataType INTEGER NOT NULL,
			  editable INTEGER NOT NULL,
			  visible INTEGER NOT NULL,
			  comment TEXT,
			  updatedAt TEXT NOT NULL,
			  UNIQUE (attribute));
			""";
	
	static final String SQL_SETUP_CREATE_BOOKING_CATEGORY = """
			CREATE TABLE booking_category (
			  id INTEGER PRIMARY KEY,
			  booking_id INTEGER NOT NULL,
			  category_id INTEGER NOT NULL,
			  categoryRuleMode INTEGER NOT NULL,
			  updatedAt TEXT NOT NULL,
			  FOREIGN KEY(booking_id) REFERENCES booking(id) ON DELETE CASCADE,
			  FOREIGN KEY(category_id) REFERENCES category(id) ON DELETE CASCADE);
			""";
	
	static final String SQL_SETUP_INSERT_SETTING_DEFAULT_VALUES = "INSERT INTO setting (attribute, value, dataType, editable, visible, comment, updatedAt) VALUES ('productKey', 'DE80D48E73C59BBBECAC9BA2A', 'String', 0, 1, 'FinTS-Produkt-Registrierungsnummer für GBanking', datetime());";
}
