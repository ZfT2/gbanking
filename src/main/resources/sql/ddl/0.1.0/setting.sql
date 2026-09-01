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
INSERT OR IGNORE INTO setting (attribute, `value`, dataType, editable, visible, comment, updatedAt) VALUES ('productKey', 'DE80D48E73C59BBBECAC9BA2A', 8, 0, 1, 'FinTS-Produkt-Registrierungsnummer für GBanking', datetime());

[SQL_SETUP_INSERT_SETTING_HBCI_LOG_LEVEL]
INSERT OR IGNORE INTO setting (attribute, `value`, dataType, editable, visible, comment, updatedAt) VALUES ('hbci.log.level', 'WARN', 7, 1, 1, 'HBCI4Java-Loglevel', datetime());

[SQL_SETUP_INSERT_SETTING_GBANKING_LOG_LEVEL]
INSERT OR IGNORE INTO setting (attribute, `value`, dataType, editable, visible, comment, updatedAt) VALUES ('gbanking.log.level', 'INFO', 7, 1, 1, 'GBanking-Loglevel', datetime());

[SQL_SETUP_INSERT_SETTING_LOG_MASK_SENSITIVE_DATA]
INSERT OR IGNORE INTO setting (attribute, `value`, dataType, editable, visible, comment, updatedAt) VALUES ('log.mask.sensitiveData', 'true', 5, 1, 1, 'Vertrauliche Daten in Log-Ausgaben maskieren', datetime());

[SQL_SETUP_INSERT_SETTING_IMPORT_EMPTY_XML_ACCOUNTS]
INSERT OR IGNORE INTO setting (attribute, `value`, dataType, editable, visible, comment, updatedAt) VALUES ('import.xml.emptyAccounts', 'true', 5, 1, 1, 'Auch leere Konten aus XML-Importdateien importieren', datetime());

[SQL_SETUP_INSERT_SETTING_ACCOUNT_STATEMENT_AUTO_ACKNOWLEDGE]
INSERT OR IGNORE INTO setting (attribute, `value`, dataType, editable, visible, comment, updatedAt) VALUES ('accountstatement.auto.acknowledge', 'true', 5, 1, 1, 'Kontoauszuege nach erfolgreichem Speichern automatisch bei der Bank quittieren', datetime());

[SQL_SETUP_INSERT_SETTING_ACCOUNT_STATEMENT_REDOWNLOAD_ACKNOWLEDGED]
INSERT OR IGNORE INTO setting (attribute, `value`, dataType, editable, visible, comment, updatedAt) VALUES ('accountstatement.redownload.acknowledged', 'false', 5, 1, 1, 'Bereits quittierte Kontoauszuege per Jahr und Auszugsnummer erneut abrufen', datetime());

[SQL_SETUP_INSERT_SETTING_ACCOUNT_STATEMENT_DOWNLOAD_OVERVIEW]
INSERT OR IGNORE INTO setting (attribute, `value`, dataType, editable, visible, comment, updatedAt) VALUES ('accountstatement.download.overview', 'false', 5, 1, 1, 'Uebersicht der Kontoauszuege per HKKAU vor dem Abruf laden', datetime());

[SQL_SETUP_INSERT_SETTING_PATTERN_DEFAULTS]
INSERT OR IGNORE INTO setting (attribute, `value`, dataType, editable, visible, comment, updatedAt) VALUES
('pattern.bookings.INTEREST', 'Haben Zins;Haben-Zins;Zinsabrechnung;Ihre Habenzinsen;Zinsen abgeltungssteuerpfl.;HABENZINSEN;ABSCHLUSS PER;Zinsen abgeltungsteuerpfl.;HabenzinsenZ;Habenzinsen ;Zinsen Festgeld;Zinsabschluss;Habenzins:;HABENZINS ;Habenzins auf;Zinsabschluss ;Saldo der Abschlussposten;ZINSPILOT  Auszahlung;Abschluss lt. Anlage ;00519741', 8, 1, 1, 'Muster fuer Zinsertraege', datetime()),
('pattern.bookings.INTEREST_WHOLE_WORD', 'Habenzinsen;Habenzins;Zinsen geschaetzt;ZINSEN;Zinsen manuell;Abschluss;Ihre Tagesgeldzinsen;Zinsgutschrift;Bonus', 8, 1, 1, 'Ganzwort-Muster fuer Zinsertraege', datetime()),
('pattern.bookings.FEES', 'Gebühren;Porto', 8, 1, 1, 'Muster fuer Gebuehren', datetime()),
('pattern.bookings.INTEREST_CHARGE', 'Zinsabrechnung', 8, 1, 1, 'Muster fuer Sollzinsen', datetime()),
('pattern.bookings.TAX', 'Steuerausgleich;Kapitalertragsteuer;Abgeltungssteuer;Solidaritaetszuschlag;Solidaritätszuschlag;Kirchensteuer;Abgeltungsteuer;Kirchensteuer', 8, 1, 1, 'Muster fuer Steuern', datetime()),
('pattern.bookings.DIVIDENDS', 'Dividendenzahlung', 8, 1, 1, 'Muster fuer Dividenden', datetime()),
('pattern.accountCancel.DEFAULT', '^.*Retoure SEPA Ueberweisung vom .* Rueckgabegrund: .* RETURN, (.*) IBAN: .* RETOUREN', 8, 1, 1, 'Standardmuster fuer Rueckbuchungen', datetime());

[SQL_MIGRATION_0_1_0_SETTING_DB_SCHEMA_VERSION]
INSERT OR IGNORE INTO setting (attribute, value, dataType, editable, visible, comment, updatedAt)
VALUES ('db.schema.version', '0.1.0', 8, 0, 0, 'Zuletzt erfolgreich angewendete DB-Schemaversion', datetime());
