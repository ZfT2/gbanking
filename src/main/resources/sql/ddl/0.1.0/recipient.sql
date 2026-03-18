[SQL_SETUP_DROP_RECIPIENT]
DROP TABLE IF EXISTS recipient;

;

[SQL_SETUP_CREATE_RECIPIENT]
CREATE TABLE recipient (
  id INTEGER PRIMARY KEY,
  name TEXT,
  iban TEXT,
  bic TEXT,
  accountnumber TEXT,
  blz TEXT,
  bank TEXT,
  source TEXT NOT NULL,
  note TEXT,
  updatedAt TEXT NOT NULL,
  CHECK (source IN ("ONLINE", "MANUELL", "IMPORT", "IMPORT_INITIAL")),
  CHECK(COALESCE(name, iban, bic, accountnumber, blz, bank) IS NOT NULL));
;
