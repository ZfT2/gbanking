[SQL_SETUP_DROP_SETTING]
DROP TABLE IF EXISTS setting;

[SQL_SETUP_CREATE_SETTING]
CREATE TABLE IF NOT EXISTS setting (
  id INTEGER PRIMARY KEY,
  attribute TEXT NOT NULL,
  value TEXT,
  dataType INTEGER NOT NULL,
  editable INTEGER NOT NULL,
  visible INTEGER NOT NULL,
  comment TEXT,
  updatedAt TEXT NOT NULL,
  UNIQUE (attribute));

[SQL_SETUP_INSERT_SETTING_DEFAULT_VALUES]
INSERT OR IGNORE INTO setting (attribute, 'value', dataType, editable, visible, comment, updatedAt) VALUES ('productKey', 'DE80D48E73C59BBBECAC9BA2A', 8, 0, 1, 'FinTS-Produkt-Registrierungsnummer für GBanking', datetime());

[SQL_MIGRATION_0_1_0_SETTING_DB_SCHEMA_VERSION]
INSERT OR IGNORE INTO setting (attribute, value, dataType, editable, visible, comment, updatedAt)
VALUES ('db.schema.version', '0.1.0', 8, 0, 0, 'Zuletzt erfolgreich angewendete DB-Schemaversion', datetime());

