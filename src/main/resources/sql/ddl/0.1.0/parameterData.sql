[SQL_SETUP_DROP_VIEW_BPD]
DROP VIEW IF EXISTS bpd;

[SQL_SETUP_DROP_VIEW_UPD]
DROP VIEW IF EXISTS upd;

[SQL_SETUP_DROP_PARAMETERDATA]
DROP TABLE IF EXISTS parameterData;

[SQL_SETUP_DROP_BANKACCESS_PARAMETERDATA]
DROP TABLE IF EXISTS bankAccess_parameterData;

[SQL_SETUP_VIEW_BPD]
CREATE VIEW bpd AS ${SQL_SELECT_PD} 1;

[SQL_SETUP_VIEW_UPD]
CREATE VIEW upd AS ${SQL_SELECT_PD} 2;

[SQL_SETUP_CREATE_PARAMETERDATA]
CREATE TABLE parameterData (
  id INTEGER PRIMARY KEY,
  pdKey TEXT NOT NULL,
  pdType INTEGER NOT NULL,
  updatedAt TEXT NOT NULL,
  UNIQUE (pdType, pdKey),
  CHECK (TRIM(pdKey) <> ''),
  CHECK (pdType BETWEEN 1 AND 2));

[SQL_SETUP_CREATE_UNIQUE_INDEX_PARAMETERDATA_KEY]
CREATE UNIQUE INDEX IF NOT EXISTS uk_parameterdata_type_key ON parameterData (pdType, pdKey);

[SQL_SETUP_CREATE_BANKACCESS_PARAMETERDATA]
CREATE TABLE bankAccess_parameterData (
  id INTEGER PRIMARY KEY,
  bankAccess_id INTEGER NOT NULL,
  parameterData_id INTEGER NOT NULL,
  pdValue TEXT,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(bankAccess_id) REFERENCES bankAccess(id) ON DELETE CASCADE,
  FOREIGN KEY(parameterData_id) REFERENCES parameterData(id) ON DELETE CASCADE,
  UNIQUE (bankAccess_id, parameterData_id));

[SQL_SETUP_CREATE_UNIQUE_INDEX_BANKACCESS_PARAMETERDATA]
CREATE UNIQUE INDEX IF NOT EXISTS uk_bankaccess_parameterdata ON bankAccess_parameterData (bankAccess_id, parameterData_id);

[SQL_SETUP_CREATE_TRIGGER_BANKACCESS_PARAMETERDATA_DELETE_UNUSED]
CREATE TRIGGER IF NOT EXISTS delete_unused_parameterdata_after_bankaccess_parameterdata_delete
AFTER DELETE ON bankAccess_parameterData
BEGIN
  DELETE FROM parameterData
  WHERE id = OLD.parameterData_id
    AND NOT EXISTS (
      SELECT 1
      FROM bankAccess_parameterData
      WHERE parameterData_id = OLD.parameterData_id);
END;
