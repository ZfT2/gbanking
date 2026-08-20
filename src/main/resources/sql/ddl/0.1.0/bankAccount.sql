[SQL_SETUP_DROP_BANKACCOUNT_IDENTIFIERS]
DROP TABLE IF EXISTS bankAccountIdentifiers;
;

[SQL_SETUP_DROP_BANKACCOUNT]
DROP TABLE IF EXISTS bankAccount;

;

[SQL_SETUP_CREATE_BANKACCOUNT]
CREATE TABLE bankAccount (
  id INTEGER PRIMARY KEY,
  bankAccess_id INTEGER,
  parentAccount_id INTEGER,
  providerAccountId TEXT,
  accountName TEXT NOT NULL,
  currency TEXT NOT NULL,
  accountType INTEGER NOT NULL,
  accountSource INTEGER NOT NULL,
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
  accountState INTEGER NOT NULL,
  balance REAL,
  createdAt TEXT NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(bankAccess_id) REFERENCES bankAccess(id) ON DELETE SET NULL,
  FOREIGN KEY(parentAccount_id) REFERENCES bankAccount(id) ON DELETE SET NULL,
  CHECK (accountType BETWEEN 1 AND 20),
  CHECK (accountSource BETWEEN 1 AND 17),
  CHECK (accountState BETWEEN 1 AND 3),
  CHECK (isSEPAAccount IN (0, 1)),
  CHECK (isOfflineAccount IN (0, 1)),
  CHECK (parentAccount_id IS NULL OR parentAccount_id <> id));
;

[SQL_SETUP_CREATE_BANKACCOUNT_IDENTIFIERS]
CREATE TABLE bankAccountIdentifiers (
  id INTEGER PRIMARY KEY,
  account_id INTEGER NOT NULL,
  propertyType INTEGER NOT NULL,
  value TEXT NOT NULL COLLATE NOCASE,
  FOREIGN KEY(account_id) REFERENCES bankAccount(id) ON DELETE CASCADE,
  UNIQUE (account_id, propertyType, value),
  CHECK (propertyType IN (1, 2)),
  CHECK (TRIM(value) <> ''));
;

[SQL_SETUP_CREATE_BANKACCOUNT_RETRIEVAL_STATUS]
CREATE TABLE bankAccountRetrievalStatus (
  bankAccount_id INTEGER PRIMARY KEY,
  retrievedAt TEXT NOT NULL,
  result INTEGER NOT NULL,
  newBookingCount INTEGER NOT NULL,
  pendingBookingCount INTEGER NOT NULL,
  lastError TEXT,
  FOREIGN KEY(bankAccount_id) REFERENCES bankAccount(id) ON DELETE CASCADE,
  CHECK (result BETWEEN 1 AND 4),
  CHECK (newBookingCount >= 0),
  CHECK (pendingBookingCount >= 0));
;

[SQL_SETUP_CREATE_TRIGGER_BANKACCOUNT_VALIDATE_INSERT]
CREATE TRIGGER IF NOT EXISTS validate_bankaccount_insert
BEFORE INSERT ON bankAccount
WHEN NEW.parentAccount_id = NEW.id
    OR NEW.isSEPAAccount NOT IN (0, 1)
    OR NEW.isOfflineAccount NOT IN (0, 1)
BEGIN
  SELECT RAISE(FAIL, "invalid bankAccount data");
END;

[SQL_SETUP_CREATE_TRIGGER_BANKACCOUNT_VALIDATE_UPDATE]
CREATE TRIGGER IF NOT EXISTS validate_bankaccount_update
BEFORE UPDATE ON bankAccount
WHEN NEW.parentAccount_id = NEW.id
    OR NEW.isSEPAAccount NOT IN (0, 1)
    OR NEW.isOfflineAccount NOT IN (0, 1)
BEGIN
  SELECT RAISE(FAIL, "invalid bankAccount data");
END;
