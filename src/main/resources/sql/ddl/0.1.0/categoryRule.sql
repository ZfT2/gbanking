[SQL_SETUP_DROP_CATEGORYRULE]
DROP TABLE IF EXISTS category;

[SQL_SETUP_DROP_CATEGORYRULE_RULE]
DROP TABLE IF EXISTS categoryRule;

[SQL_SETUP_DROP_CATEGORYRULE_BANKACCOUNT]
DROP TABLE IF EXISTS categoryRule_bankAccount;

[SQL_SETUP_CREATE_CATEGORYRULE]
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

[SQL_SETUP_CREATE_CATEGORYRULE_BANKACCOUNT]
CREATE TABLE categoryRule_bankAccount (
  id INTEGER PRIMARY KEY,
  category_id INTEGER NOT NULL,
  account_id INTEGER NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(category_id) REFERENCES category(id) ON DELETE CASCADE,
  FOREIGN KEY(account_id) REFERENCES bankAccount(id) ON DELETE CASCADE);
