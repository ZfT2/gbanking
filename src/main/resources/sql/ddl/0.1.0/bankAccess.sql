[SQL_SETUP_CREATE_BANKACCESS]
CREATE TABLE bankAccess (
  id INTEGER PRIMARY KEY,
  bankName TEXT NOT NULL,
  active REAL NOT NULL,
  accessType INTEGER NOT NULL DEFAULT 1,
  updatedAt TEXT NOT NULL,
  CHECK (accessType BETWEEN 1 AND 3),
  CHECK (active IN (0, 1)));
;

[SQL_SETUP_CREATE_BANKACCESS_FINTS]
CREATE TABLE bankAccessFints (
  bankAccess_id INTEGER PRIMARY KEY,
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
  FOREIGN KEY(bankAccess_id) REFERENCES bankAccess(id) ON DELETE CASCADE,
  CHECK (tanProcedure BETWEEN 2 AND 20),
  CHECK (hbciFilterType BETWEEN 1 AND 2));
;

[SQL_SETUP_CREATE_BANKACCESS_PAYPAL]
CREATE TABLE bankAccessPaypal (
  bankAccess_id INTEGER PRIMARY KEY,
  userId TEXT NOT NULL,
  apiUsername TEXT NOT NULL,
  apiSignature TEXT NOT NULL,
  FOREIGN KEY(bankAccess_id) REFERENCES bankAccess(id) ON DELETE CASCADE);
;

[SQL_SETUP_CREATE_PSD2_CLIENT_CONFIGURATION]
CREATE TABLE psd2ClientConfiguration (
  id INTEGER PRIMARY KEY,
  clientMode INTEGER NOT NULL DEFAULT 1,
  applicationId TEXT,
  privateKeyPkcs8 BLOB,
  callbackUrl TEXT NOT NULL DEFAULT 'https://127.0.0.1:18443/callback',
  callbackPrivateKeyPkcs8 BLOB,
  callbackCertificate BLOB,
  updatedAt TEXT NOT NULL,
  CHECK (clientMode BETWEEN 1 AND 2));
;

[SQL_SETUP_CREATE_BANKACCESS_ENABLEBANKING]
CREATE TABLE bankAccessEnablebanking (
  bankAccess_id INTEGER PRIMARY KEY,
  psd2ClientConfiguration_id INTEGER NOT NULL,
  aspspName TEXT NOT NULL,
  aspspCountry TEXT NOT NULL,
  psuType TEXT NOT NULL,
  authMethod TEXT,
  sessionId TEXT NOT NULL,
  validUntil TEXT,
  rateLimitUntil TEXT,
  FOREIGN KEY(bankAccess_id) REFERENCES bankAccess(id) ON DELETE CASCADE,
  FOREIGN KEY(psd2ClientConfiguration_id) REFERENCES psd2ClientConfiguration(id));
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
