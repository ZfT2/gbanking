[SQL_SETUP_DROP_BOOKING]
DROP TABLE IF EXISTS booking;

;

[SQL_SETUP_DROP_BOOKING_FULL]
DROP VIEW IF EXISTS bookingFull;

;

[SQL_SETUP_DROP_BOOKING_ADDITIONAL_SEPA]
DROP TABLE IF EXISTS bookingAdditionalSepa;

;

[SQL_SETUP_DROP_BOOKING_ADDITIONAL_NOTE]
DROP TABLE IF EXISTS bookingAdditionalNote;

;

[SQL_SETUP_DROP_BOOKING_ADDITIONAL]
DROP TABLE IF EXISTS bookingAdditional;

;

[SQL_SETUP_DROP_BOOKING_ADDITIONAL_CREDITCARD]
DROP TABLE IF EXISTS bookingAdditionalCreditcard;

;

[SQL_SETUP_DROP_BOOKING_ADDITIONAL_FOREIGNCURRENCY]
DROP TABLE IF EXISTS bookingAdditionalForeigncurrency;

;

[SQL_SETUP_DROP_BOOKING_FEE]
DROP TABLE IF EXISTS bookingFee;

;

[SQL_SETUP_DROP_BOOKING_CATEGORY]
DROP TABLE IF EXISTS booking_category;

;

[SQL_SETUP_CREATE_BOOKING]
CREATE TABLE booking (
   id INTEGER PRIMARY KEY,
   account_id INTEGER NOT NULL,
   parentBooking_id INTEGER,
   dateBooking TEXT,
   dateValue TEXT,
   purpose TEXT,
   amount REAL NOT NULL,
   bookingType INTEGER,
   bookingSource INTEGER,
   crossAccount_id INTEGER,
   recipient_id INTEGER,
   category_id INTEGER,
   categoryRule_id INTEGER,
   crossBooking_id INTEGER,
   updatedAt TEXT NOT NULL,
   FOREIGN KEY(account_id) REFERENCES bankAccount(id) ON DELETE CASCADE,
   FOREIGN KEY(parentBooking_id) REFERENCES booking(id) ON DELETE CASCADE,
   FOREIGN KEY(crossAccount_id) REFERENCES bankAccount(id) ON DELETE SET NULL,
   FOREIGN KEY(recipient_id) REFERENCES recipient(id),
   FOREIGN KEY(category_id) REFERENCES category(id) ON DELETE SET NULL,
   FOREIGN KEY(categoryRule_id) REFERENCES categoryRule(id) ON DELETE SET NULL,
   FOREIGN KEY(crossBooking_id) REFERENCES booking(id) ON DELETE SET NULL,
   CHECK (bookingType BETWEEN 1 AND 7),
   CHECK (bookingSource BETWEEN 1 AND 17),
   CHECK (parentBooking_id IS NULL OR parentBooking_id <> id),
   CHECK (crossBooking_id IS NULL OR crossBooking_id <> id));

;

[SQL_SETUP_CREATE_BOOKING_ADDITIONAL_SEPA]
CREATE TABLE bookingAdditionalSepa (
   id INTEGER PRIMARY KEY,
   booking_id INTEGER NOT NULL UNIQUE,
   sepa_customer_ref TEXT,
   sepa_creditor_id TEXT,
   sepa_end_to_end TEXT,
   sepa_mandate TEXT,
   sepa_person_id TEXT,
   sepa_purpose TEXT,
   sepa_typ INTEGER,
   updatedAt TEXT NOT NULL,
   FOREIGN KEY(booking_id) REFERENCES booking(id) ON DELETE CASCADE);

;

[SQL_SETUP_CREATE_BOOKING_ADDITIONAL_NOTE]
CREATE TABLE bookingAdditionalNote (
   id INTEGER PRIMARY KEY,
   booking_id INTEGER NOT NULL UNIQUE,
   note TEXT,
   review_required INTEGER NOT NULL DEFAULT 0,
   updatedAt TEXT NOT NULL,
   FOREIGN KEY(booking_id) REFERENCES booking(id) ON DELETE CASCADE,
   CHECK (review_required IN (0, 1)));

;

[SQL_SETUP_CREATE_BOOKING_ADDITIONAL]
CREATE TABLE bookingAdditional (
   id INTEGER PRIMARY KEY,
   booking_id INTEGER NOT NULL UNIQUE,
   add_instref TEXT,
   add_gvcode TEXT,
   add_text TEXT,
   add_primanota TEXT,
   add_key TEXT,
   add_is_storno INTEGER,
   add_raw_data TEXT,
   add_is_sepa INTEGER,
   add_is_camt INTEGER,
   add_bank_saldo REAL,
   updatedAt TEXT NOT NULL,
   FOREIGN KEY(booking_id) REFERENCES booking(id) ON DELETE CASCADE);

;

[SQL_SETUP_CREATE_BOOKING_ADDITIONAL_CREDITCARD]
CREATE TABLE bookingAdditionalCreditcard (
   id INTEGER PRIMARY KEY,
   booking_id INTEGER NOT NULL UNIQUE,
   creditcard_transaction_date TEXT,
   creditcard_type TEXT,
   creditcard_merchant_area TEXT,
   creditcard_merchant_category TEXT,
   updatedAt TEXT NOT NULL,
   FOREIGN KEY(booking_id) REFERENCES booking(id) ON DELETE CASCADE);

;

[SQL_SETUP_CREATE_BOOKING_ADDITIONAL_FOREIGNCURRENCY]
CREATE TABLE bookingAdditionalForeigncurrency (
   id INTEGER PRIMARY KEY,
   booking_id INTEGER NOT NULL UNIQUE,
   foreignAmount REAL NOT NULL,
   foreignCurrency INTEGER NOT NULL,
   exchangeRateToBaseCurrency REAL NOT NULL,
   updatedAt TEXT NOT NULL,
   FOREIGN KEY(booking_id) REFERENCES booking(id) ON DELETE CASCADE,
   CHECK (foreignCurrency BETWEEN 1 AND 40),
   CHECK (exchangeRateToBaseCurrency > 0));

;

[SQL_SETUP_CREATE_BOOKING_FEE]
CREATE TABLE bookingFee (
   id INTEGER PRIMARY KEY,
   booking_id INTEGER NOT NULL UNIQUE,
   amount REAL NOT NULL,
   currency INTEGER NOT NULL,
   updatedAt TEXT NOT NULL,
   FOREIGN KEY(booking_id) REFERENCES booking(id) ON DELETE CASCADE,
   CHECK (currency BETWEEN 1 AND 40));

;

[SQL_SETUP_VIEW_BOOKING_FULL]
CREATE VIEW bookingFull AS
SELECT b.id,
   b.account_id,
   b.parentBooking_id,
   b.dateBooking,
   b.dateValue,
   b.purpose,
   b.amount,
   bse.sepa_customer_ref AS sepaCustomerRef,
   bse.sepa_creditor_id AS sepaCreditorId,
   bse.sepa_end_to_end AS sepaEndToEnd,
   bse.sepa_mandate AS sepaMandate,
   bse.sepa_person_id AS sepaPersonId,
   bse.sepa_purpose AS sepaPurpose,
   bse.sepa_typ AS sepaTyp,
   bno.note AS bookingNote,
   bno.review_required AS bookingReviewRequired,
   bad.add_instref AS addInstref,
   bad.add_gvcode AS addGvcode,
   bad.add_text AS addText,
   bad.add_primanota AS addPrimanota,
   bad.add_key AS addKey,
   bad.add_is_storno AS addIsStorno,
   bad.add_raw_data AS addRawData,
   bad.add_is_sepa AS addIsSepa,
   bad.add_is_camt AS addIsCamt,
   bad.add_bank_saldo AS addBankSaldo,
   bac.creditcard_transaction_date AS creditcardTransactionDate,
   bac.creditcard_type AS creditcardType,
   bac.creditcard_merchant_area AS creditcardMerchantArea,
   bac.creditcard_merchant_category AS creditcardMerchantCategory,
   baf.foreignAmount,
   baf.foreignCurrency,
   baf.exchangeRateToBaseCurrency,
   bf.amount AS feeAmount,
   bf.currency AS feeCurrency,
   b.bookingType,
   b.bookingSource,
   b.crossAccount_id,
   b.recipient_id,
   b.category_id,
   b.categoryRule_id,
   cgr.name AS categoryRuleName,
   b.crossBooking_id,
   MAX(b.updatedAt, COALESCE(bse.updatedAt, b.updatedAt), COALESCE(bno.updatedAt, b.updatedAt), COALESCE(bad.updatedAt, b.updatedAt), COALESCE(bac.updatedAt, b.updatedAt), COALESCE(baf.updatedAt, b.updatedAt), COALESCE(bf.updatedAt, b.updatedAt)) AS updatedAt
FROM booking b
LEFT JOIN bookingAdditionalSepa bse ON bse.booking_id = b.id
LEFT JOIN bookingAdditionalNote bno ON bno.booking_id = b.id
LEFT JOIN bookingAdditional bad ON bad.booking_id = b.id
LEFT JOIN bookingAdditionalCreditcard bac ON bac.booking_id = b.id
LEFT JOIN bookingAdditionalForeigncurrency baf ON baf.booking_id = b.id
LEFT JOIN bookingFee bf ON bf.booking_id = b.id
LEFT JOIN categoryRule cgr ON cgr.id = b.categoryRule_id;

;

[SQL_SETUP_CREATE_INDEX_BOOKING_RECIPIENT]
CREATE INDEX idx_booking_recipient_id ON booking (recipient_id);

;

[SQL_SETUP_CREATE_BOOKING_CATEGORY]
CREATE TABLE booking_category (
  id INTEGER PRIMARY KEY,
  booking_id INTEGER NOT NULL,
  category_id INTEGER NOT NULL,
  categoryRuleMode INTEGER NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(booking_id) REFERENCES booking(id) ON DELETE CASCADE,
  FOREIGN KEY(category_id) REFERENCES category(id) ON DELETE CASCADE,
  UNIQUE (booking_id, category_id),
  CHECK (categoryRuleMode BETWEEN 1 AND 3));
;

[SQL_SETUP_CREATE_UNIQUE_INDEX_BOOKING_CATEGORY]
CREATE UNIQUE INDEX IF NOT EXISTS uk_booking_category ON booking_category (booking_id, category_id);

[SQL_SETUP_CREATE_TRIGGER_BOOKING_BLOCK_SYSTEM_CORE_UPDATE]
CREATE TRIGGER IF NOT EXISTS block_system_booking_core_update
BEFORE UPDATE ON booking
WHEN OLD.bookingSource IN (1, 2, 3, 4, 10, 11, 12, 13)
    AND (NEW.account_id IS NOT OLD.account_id
      OR NEW.parentBooking_id IS NOT OLD.parentBooking_id
      OR NEW.dateBooking IS NOT OLD.dateBooking
      OR NEW.dateValue IS NOT OLD.dateValue
      OR NEW.purpose IS NOT OLD.purpose
      OR NEW.amount IS NOT OLD.amount
      OR (NEW.recipient_id IS NOT OLD.recipient_id AND OLD.recipient_id IS NOT NULL)
      OR (NEW.bookingType IS NOT OLD.bookingType AND OLD.bookingSource NOT IN (1, 10))
      OR (NEW.crossAccount_id IS NOT OLD.crossAccount_id AND OLD.bookingSource NOT IN (1, 10))
      OR (NEW.crossBooking_id IS NOT OLD.crossBooking_id AND NEW.crossBooking_id IS NOT NULL AND OLD.bookingSource NOT IN (1, 10)))
BEGIN
  SELECT RAISE(FAIL, "core fields of system bookings are immutable");
END;

[SQL_SETUP_CREATE_TRIGGER_BOOKING_BLOCK_PROTECTED_RECIPIENT_UPDATE]
CREATE TRIGGER IF NOT EXISTS block_protected_booking_recipient_update
BEFORE UPDATE OF recipient_id ON booking
WHEN OLD.bookingSource NOT IN (5, 14)
    AND NEW.recipient_id IS NOT OLD.recipient_id
    AND OLD.recipient_id IS NOT NULL
BEGIN
  SELECT RAISE(FAIL, "recipient of non-manual bookings is immutable");
END;

[SQL_SETUP_CREATE_TRIGGER_BOOKING_VALIDATE_RELATIONS_INSERT]
CREATE TRIGGER IF NOT EXISTS validate_booking_relations_insert
BEFORE INSERT ON booking
WHEN (NEW.parentBooking_id IS NOT NULL
      AND (NEW.parentBooking_id = NEW.id
        OR EXISTS (
          SELECT 1
          FROM booking parent
          WHERE parent.id = NEW.parentBooking_id
            AND (parent.parentBooking_id IS NOT NULL OR parent.account_id IS NOT NEW.account_id))))
    OR (NEW.crossBooking_id IS NOT NULL
      AND (NEW.crossBooking_id = NEW.id
        OR NOT EXISTS (
          SELECT 1
          FROM booking crossBooking
          WHERE crossBooking.id = NEW.crossBooking_id)
        OR NEW.crossAccount_id IS NULL
        OR NEW.crossAccount_id IS NOT (
          SELECT account_id
          FROM booking crossBooking
          WHERE crossBooking.id = NEW.crossBooking_id)))
BEGIN
  SELECT RAISE(FAIL, "invalid booking parent or cross-booking relation");
END;

[SQL_SETUP_CREATE_TRIGGER_BOOKING_VALIDATE_RELATIONS_UPDATE]
CREATE TRIGGER IF NOT EXISTS validate_booking_relations_update
BEFORE UPDATE ON booking
WHEN (NEW.parentBooking_id IS NOT NULL
      AND (NEW.parentBooking_id = NEW.id
        OR EXISTS (
          SELECT 1
          FROM booking parent
          WHERE parent.id = NEW.parentBooking_id
            AND (parent.parentBooking_id IS NOT NULL OR parent.account_id IS NOT NEW.account_id))))
    OR (NEW.crossBooking_id IS NOT NULL
      AND (NEW.crossBooking_id = NEW.id
        OR NOT EXISTS (
          SELECT 1
          FROM booking crossBooking
          WHERE crossBooking.id = NEW.crossBooking_id)
        OR NEW.crossAccount_id IS NULL
        OR NEW.crossAccount_id IS NOT (
          SELECT account_id
          FROM booking crossBooking
          WHERE crossBooking.id = NEW.crossBooking_id)))
BEGIN
  SELECT RAISE(FAIL, "invalid booking parent or cross-booking relation");
END;

[SQL_SETUP_CREATE_TRIGGER_BOOKING_CROSS_REFERENCE_DELETE]
CREATE TRIGGER IF NOT EXISTS set_null_booking_cross_reference_delete
AFTER DELETE ON booking
BEGIN
  UPDATE booking
  SET crossBooking_id = NULL
  WHERE crossBooking_id = OLD.id;
END;

[SQL_MIGRATION_BASELINE_0_1_0]
UPDATE setting SET updatedAt = updatedAt WHERE 1 = 0;
