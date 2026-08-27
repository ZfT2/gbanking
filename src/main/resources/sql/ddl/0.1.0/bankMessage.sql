[SQL_SETUP_CREATE_BANKMESSAGE]
CREATE TABLE IF NOT EXISTS bankMessage (
  id INTEGER PRIMARY KEY,
  bankAccess_id INTEGER NOT NULL,
  bankName TEXT,
  messageKey TEXT NOT NULL,
  code TEXT,
  type TEXT,
  format TEXT,
  description TEXT,
  versionDate TEXT,
  comments TEXT,
  message TEXT,
  retrievedAt TEXT,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(bankAccess_id) REFERENCES bankAccess(id) ON DELETE CASCADE,
  UNIQUE(bankAccess_id, messageKey));
