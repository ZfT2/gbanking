[SQL_SETUP_DROP_BUSINESSCASE]
DROP TABLE IF EXISTS businessCase;

;

[SQL_SETUP_DROP_BANKACCOUNT_BUSINESSCASE]
DROP TABLE IF EXISTS bankAccount_businessCase;

;

[SQL_SETUP_CREATE_BUSINESSCASE]
CREATE TABLE businessCase (
  id INTEGER PRIMARY KEY,
  caseValue TEXT NOT NULL,
  updatedAt TEXT NOT NULL,
  UNIQUE (caseValue));

;

[SQL_SETUP_CREATE_BANKACCOUNT_BUSINESSCASE]
CREATE TABLE bankAccount_businessCase (
  id INTEGER PRIMARY KEY,
  account_id INTEGER NOT NULL,
  businessCase_id INTEGER NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(account_id) REFERENCES bankAccount(id) ON DELETE CASCADE,
  FOREIGN KEY(businessCase_id) REFERENCES businessCase(id) ON DELETE CASCADE,
  UNIQUE (account_id, businessCase_id));
;

[SQL_SETUP_CREATE_UNIQUE_INDEX_BANKACCOUNT_BUSINESSCASE]
CREATE UNIQUE INDEX IF NOT EXISTS uk_bankaccount_businesscase ON bankAccount_businessCase (account_id, businessCase_id);
