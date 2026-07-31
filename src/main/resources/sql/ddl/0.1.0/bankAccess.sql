[SQL_SETUP_CREATE_BANKACCESS]
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
  tanProcedure INTEGER NOT NULL,
  allowedTwostepMechanisms TEXT,
  hbciVersion TEXT,
  bpdVersion TEXT NOT NULL,
  updVersion TEXT NOT NULL,
  hbciFilterType INTEGER,
  active REAL NOT NULL,
  accessType INTEGER NOT NULL DEFAULT 1,
  paypalApiUsername TEXT,
  paypalApiSignature TEXT,
  updatedAt TEXT NOT NULL,
  CHECK (tanProcedure BETWEEN 2 AND 20),
  CHECK (hbciFilterType BETWEEN 1 AND 2),
  CHECK (accessType BETWEEN 1 AND 2),
  CHECK (active IN (0, 1)));
;

[SQL_SETUP_CREATE_TRIGGER_BANKACCESS_VALIDATE_INSERT]
CREATE TRIGGER IF NOT EXISTS validate_bankaccess_insert
BEFORE INSERT ON bankAccess
WHEN NEW.active NOT IN (0, 1)
BEGIN
  SELECT RAISE(FAIL, "invalid bankAccess active flag");
END;

[SQL_SETUP_CREATE_TRIGGER_BANKACCESS_VALIDATE_UPDATE]
CREATE TRIGGER IF NOT EXISTS validate_bankaccess_update
BEFORE UPDATE ON bankAccess
WHEN NEW.active NOT IN (0, 1)
BEGIN
  SELECT RAISE(FAIL, "invalid bankAccess active flag");
END;
