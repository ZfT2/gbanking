[SQL_SETUP_DROP_CATEGORYRULE]
DROP TABLE IF EXISTS categoryRule;

[SQL_SETUP_DROP_CATEGORYRULE_RULE]
DROP TABLE IF EXISTS categoryRule;

[SQL_SETUP_DROP_CATEGORYRULE_BANKACCOUNT]
DROP TABLE IF EXISTS categoryRule_bankAccount;

[SQL_SETUP_CREATE_CATEGORYRULE]
CREATE TABLE categoryRule (
  id INTEGER PRIMARY KEY,
  name TEXT NOT NULL UNIQUE,
  category_id INTEGER NOT NULL,
  filterDateFrom TEXT,
  filterDateTo TEXT,
  filterAmountFrom REAL,
  filterAmountTo REAL,
  filterRecipientName TEXT,
  filterRecipientIban TEXT,
  filterRecipientAccountNumber TEXT,
  filterPurpose TEXT,
  filterRecipientIsRegex REAL NOT NULL,
  filterPurposeIsRegex REAL NOT NULL,
  joinType INTEGER NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(category_id) REFERENCES category(id) ON DELETE CASCADE,
  CHECK (joinType BETWEEN 1 AND 2),
  CHECK (filterRecipientIsRegex IN (0, 1)),
  CHECK (filterPurposeIsRegex IN (0, 1)),
  CHECK (filterDateFrom IS NULL OR filterDateTo IS NULL OR filterDateFrom <= filterDateTo),
  CHECK (filterAmountFrom IS NULL OR filterAmountTo IS NULL OR filterAmountFrom <= filterAmountTo));

[SQL_SETUP_CREATE_CATEGORYRULE_BANKACCOUNT]
CREATE TABLE categoryRule_bankAccount (
  id INTEGER PRIMARY KEY,
  categoryRule_id INTEGER NOT NULL,
  account_id INTEGER NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(categoryRule_id) REFERENCES categoryRule(id) ON DELETE CASCADE,
  FOREIGN KEY(account_id) REFERENCES bankAccount(id) ON DELETE CASCADE,
  UNIQUE (categoryRule_id, account_id));

[SQL_SETUP_CREATE_UNIQUE_INDEX_CATEGORYRULE_BANKACCOUNT]
CREATE UNIQUE INDEX IF NOT EXISTS uk_categoryrule_bankaccount ON categoryRule_bankAccount (categoryRule_id, account_id);
