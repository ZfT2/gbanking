[SQL_SETUP_DROP_INSTITUTE]
DROP TABLE IF EXISTS institute;

;

[SQL_SETUP_DROP_UNIQUE_INDEX_INSTITUTE]
DROP INDEX IF EXISTS uk_institute_blz_importnr_current;

;

[SQL_SETUP_CREATE_INSTITUTE]
CREATE TABLE institute (
  id INTEGER PRIMARY KEY,
  importNumber INTEGER NOT NULL,
  blz TEXT NOT NULL,
  bic TEXT,
  bankName TEXT NOT NULL,
  place TEXT,
  dataCenter TEXT,
  organisation TEXT,
  hbciDns TEXT,
  hbciIp TEXT,
  hbciVersion REAL,
  ddv TEXT,
  rdh1 INTEGER,
  rdh2 INTEGER,
  rdh3 INTEGER,
  rdh4 INTEGER,
  rdh5 INTEGER,
  rdh6 INTEGER,
  rdh7 INTEGER,
  rdh8 INTEGER,
  rdh9 INTEGER,
  rdh10 INTEGER,
  pinUrl TEXT,
  version TEXT,
  lastChanged TEXT,
  stateType INTEGER NOT NULL,
  updatedAt TEXT NOT NULL,
  CHECK (stateType IN (0, 1, 2)));

;

[SQL_SETUP_CREATE_UNIQUE_INDEX_INSTITUTE]
CREATE UNIQUE INDEX uk_institute_blz_importnr_current
  ON institute (blz, importNumber)
  WHERE stateType IN (1, 2);
;
