[SQL_SETUP_DROP_BANKACCESS]
DROP TABLE IF EXISTS bankAccess;

;

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
  tanProcedure TEXT NOT NULL,
  allowedTwostepMechanisms TEXT,
  hbciVersion TEXT,
  bpdVersion TEXT NOT NULL,
  updVersion TEXT NOT NULL,
  hbciFilterType TEXT,
  active REAL NOT NULL,
  updatedAt TEXT NOT NULL);
;
