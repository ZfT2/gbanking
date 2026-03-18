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

;

[SQL_SETUP_CREATE_MONEYTRANSFER_PROTOCOL]
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
;
