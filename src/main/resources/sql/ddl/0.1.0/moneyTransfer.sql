[SQL_SETUP_DROP_MONEYTRANSFER]
DROP TABLE IF EXISTS moneytransfer;

;

[SQL_SETUP_DROP_MONEYTRANSFER_PROTOCOL]
DROP TABLE IF EXISTS moneytransferProtocol;

;

[SQL_SETUP_CREATE_MONEYTRANSFER]
CREATE TABLE moneytransfer (
  id INTEGER PRIMARY KEY,
  account_id INTEGER NOT NULL,
  moneytransferType INTEGER NOT NULL,
  recipient_id INTEGER,
  purpose TEXT,
  purposeCode TEXT,
  endToEndId TEXT,
  amount REAL,
  executionDate TEXT,
  executionDay INTEGER,
  moneytransferStatus INTEGER NOT NULL,
  standingorderMode INTEGER,
  historyorder_id INTEGER,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(account_id) REFERENCES bankAccount(id) ON DELETE CASCADE,
  FOREIGN KEY(recipient_id) REFERENCES recipient(id),
  FOREIGN KEY(historyorder_id) REFERENCES moneytransfer(id),
  CHECK (moneytransferType BETWEEN 1 AND 6),
  CHECK (moneytransferStatus BETWEEN 1 AND 10),
  CHECK (amount IS NOT NULL AND amount > 0),
  CHECK (executionDay IS NULL OR executionDay BETWEEN 1 AND 31),
  CHECK (standingorderMode IS NULL OR standingorderMode BETWEEN 1 AND 5),
  CHECK (moneytransferType <> 3 OR executionDate IS NOT NULL),
  CHECK (moneytransferType <> 4 OR (executionDate IS NOT NULL AND executionDay IS NOT NULL AND standingorderMode IS NOT NULL)));

;

[SQL_SETUP_CREATE_INDEX_MONEYTRANSFER_ACCOUNT_STATUS]
CREATE INDEX IF NOT EXISTS idx_moneytransfer_account_status ON moneytransfer (account_id, moneytransferStatus);
;

[SQL_SETUP_CREATE_INDEX_MONEYTRANSFER_RECIPIENT_USAGE]
CREATE INDEX IF NOT EXISTS idx_moneytransfer_recipient_usage
ON moneytransfer (recipient_id, COALESCE(executionDate, updatedAt) DESC);
;

[SQL_SETUP_CREATE_INDEX_MONEYTRANSFER_HISTORY]
CREATE INDEX IF NOT EXISTS idx_moneytransfer_history ON moneytransfer (historyorder_id);

;

[SQL_SETUP_CREATE_MONEYTRANSFER_FOREIGN]
CREATE TABLE moneytransferForeign (
  id INTEGER PRIMARY KEY,
  moneytransfer_id INTEGER NOT NULL UNIQUE,
  currency TEXT NOT NULL,
  recipientCountry TEXT,
  recipientAccountNumber TEXT,
  recipientBankCode TEXT,
  recipientSubAccount TEXT,
  recipientAddressLine1 TEXT,
  recipientAddressLine2 TEXT,
  recipientBankCountry TEXT,
  recipientBankAddressLine1 TEXT,
  recipientBankAddressLine2 TEXT,
  chargeBearer INTEGER,
  regulatoryReporting TEXT,
  endToEndReference TEXT,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(moneytransfer_id) REFERENCES moneytransfer(id) ON DELETE CASCADE,
  CHECK (chargeBearer BETWEEN 1 AND 3));

;

[SQL_SETUP_CREATE_MONEYTRANSFER_PROTOCOL]
CREATE TABLE moneytransferProtocol (
  id INTEGER PRIMARY KEY,
  moneytransfer_id INTEGER NOT NULL,
  moneytransferStatus INTEGER NOT NULL,
  timeStart TEXT NOT NULL,
  timeFinish TEXT,
  bankOrderId TEXT,
  sepaOrderStatus INTEGER,
  sepaCancellationCode INTEGER,
  protocolText TEXT,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(moneytransfer_id) REFERENCES moneytransfer(id) ON DELETE CASCADE,
  CHECK (moneytransferStatus BETWEEN 1 AND 10),
  CHECK (bankOrderId IS NULL OR length(trim(bankOrderId)) > 0),
  CHECK (sepaOrderStatus IS NULL OR sepaOrderStatus BETWEEN 1 AND 9),
  CHECK (sepaCancellationCode IS NULL OR sepaCancellationCode BETWEEN 1 AND 4));
;

[SQL_SETUP_CREATE_INDEX_MONEYTRANSFER_PROTOCOL_TRANSFER]
CREATE INDEX IF NOT EXISTS idx_moneytransferprotocol_transfer
ON moneytransferProtocol (moneytransfer_id, timeStart DESC, id DESC);
;

[SQL_SETUP_CREATE_TRIGGER_MONEYTRANSFER_BLOCK_UPDATE]
CREATE TRIGGER IF NOT EXISTS block_update_moneytransfer
BEFORE UPDATE ON moneytransfer
WHEN OLD.moneytransferStatus IN (2, 6, 7, 8, 9)
BEGIN
  SELECT RAISE(FAIL, "updates on archived moneytransfer datasets are not allowed");
END;

[SQL_SETUP_CREATE_TRIGGER_MONEYTRANSFER_BLOCK_DELETE]
CREATE TRIGGER IF NOT EXISTS block_delete_moneytransfer
BEFORE DELETE ON moneytransfer
WHEN OLD.moneytransferStatus IN (2, 6, 8, 9)
BEGIN
  SELECT RAISE(FAIL, "deletes on archived moneytransfer datasets are not allowed");
END;

[SQL_SETUP_CREATE_TRIGGER_MONEYTRANSFER_VALIDATE_INSERT]
CREATE TRIGGER IF NOT EXISTS validate_moneytransfer_insert
BEFORE INSERT ON moneytransfer
WHEN NEW.amount IS NULL
    OR NEW.amount <= 0
    OR NEW.executionDay NOT BETWEEN 1 AND 31 AND NEW.executionDay IS NOT NULL
    OR NEW.moneytransferType = 3 AND NEW.executionDate IS NULL
    OR NEW.moneytransferType = 4 AND (NEW.executionDate IS NULL OR NEW.executionDay IS NULL OR NEW.standingorderMode IS NULL)
BEGIN
  SELECT RAISE(FAIL, "invalid moneytransfer data");
END;

[SQL_SETUP_CREATE_TRIGGER_MONEYTRANSFER_VALIDATE_UPDATE]
CREATE TRIGGER IF NOT EXISTS validate_moneytransfer_update
BEFORE UPDATE ON moneytransfer
WHEN NEW.amount IS NULL
    OR NEW.amount <= 0
    OR NEW.executionDay NOT BETWEEN 1 AND 31 AND NEW.executionDay IS NOT NULL
    OR NEW.moneytransferType = 3 AND NEW.executionDate IS NULL
    OR NEW.moneytransferType = 4 AND (NEW.executionDate IS NULL OR NEW.executionDay IS NULL OR NEW.standingorderMode IS NULL)
BEGIN
  SELECT RAISE(FAIL, "invalid moneytransfer data");
END;

[SQL_SETUP_CREATE_TRIGGER_MONEYTRANSFER_FOREIGN_INSERT]
CREATE TRIGGER IF NOT EXISTS validate_moneytransferforeign_insert
BEFORE INSERT ON moneytransferForeign
WHEN NOT EXISTS (
  SELECT 1
  FROM moneytransfer
  WHERE id = NEW.moneytransfer_id
    AND moneytransferType = 5)
BEGIN
  SELECT RAISE(FAIL, "moneytransferForeign rows require a FOREIGN_TRANSFER parent");
END;

[SQL_SETUP_CREATE_TRIGGER_MONEYTRANSFER_FOREIGN_UPDATE]
CREATE TRIGGER IF NOT EXISTS validate_moneytransferforeign_update
BEFORE UPDATE ON moneytransferForeign
WHEN NOT EXISTS (
  SELECT 1
  FROM moneytransfer
  WHERE id = NEW.moneytransfer_id
    AND moneytransferType = 5)
BEGIN
  SELECT RAISE(FAIL, "moneytransferForeign rows require a FOREIGN_TRANSFER parent");
END;

[SQL_SETUP_CREATE_TRIGGER_MONEYTRANSFER_FOREIGN_PARENT_UPDATE]
CREATE TRIGGER IF NOT EXISTS validate_moneytransfer_foreign_parent_update
BEFORE UPDATE OF moneytransferType ON moneytransfer
WHEN NEW.moneytransferType <> 5
    AND EXISTS (
      SELECT 1
      FROM moneytransferForeign
      WHERE moneytransfer_id = OLD.id)
BEGIN
  SELECT RAISE(FAIL, "FOREIGN_TRANSFER details must be removed before changing the moneytransfer type");
END;
