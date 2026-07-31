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
  source INTEGER NOT NULL,
  note TEXT,
  isDefault REAL NOT NULL DEFAULT 0,
  updatedAt TEXT NOT NULL,
  CHECK (source BETWEEN 1 AND 17),
  CHECK (isDefault IN (0, 1)),
  CHECK(COALESCE(name, iban, bic, accountnumber, blz, bank) IS NOT NULL));
;

[SQL_SETUP_CREATE_INDEX_RECIPIENT_IBAN]
CREATE INDEX idx_recipient_iban_lookup ON recipient (TRIM(iban) COLLATE NOCASE);
;

[SQL_SETUP_CREATE_INDEX_RECIPIENT_ACCOUNTNUMBER]
CREATE INDEX idx_recipient_accountnumber_lookup ON recipient (TRIM(accountnumber));
;

[SQL_SETUP_CREATE_TRIGGER_RECIPIENT_BLOCK_REFERENCED_IDENTITY_UPDATE]
CREATE TRIGGER IF NOT EXISTS block_referenced_recipient_identity_update
BEFORE UPDATE OF name, iban, bic, accountnumber, blz, bank ON recipient
WHEN (EXISTS (
        SELECT 1
        FROM booking
        WHERE recipient_id = OLD.id
          AND bookingSource NOT IN (5, 14))
      OR EXISTS (
        SELECT 1
        FROM moneytransfer
        WHERE recipient_id = OLD.id))
    AND (NEW.iban IS NOT OLD.iban
      OR NEW.bic IS NOT OLD.bic
      OR NEW.accountnumber IS NOT OLD.accountnumber
      OR NEW.blz IS NOT OLD.blz
      OR (NEW.name IS NOT OLD.name
        AND NOT gb_equals_ignore_case(NEW.name, OLD.name))
      OR (NEW.bank IS NOT OLD.bank
        AND NULLIF(TRIM(OLD.bank), '') IS NOT NULL
        AND NOT gb_equals_ignore_case(NEW.bank, OLD.bank)))
BEGIN
  SELECT RAISE(FAIL, "identity fields of referenced recipients are immutable");
END;
