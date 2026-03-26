[SQL_SETUP_DROP_BANKACCOUNT]
DROP TABLE IF EXISTS bankAccount;

;

[SQL_SETUP_CREATE_BANKACCOUNT]
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
;
