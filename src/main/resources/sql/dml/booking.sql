[SQL_SELECT_BOOKING_COLUMNS]
b.id, b.account_id, b.parentBooking_id, b.bookingType, b.bookingSource, b.dateBooking, b.dateValue, b.purpose, b.amount, b.sepaCustomerRef, b.sepaCreditorId, b.sepaEndToEnd, b.sepaMandate, b.sepaPersonId, b.sepaPurpose, b.sepaTyp, b.bookingNote, b.bookingReviewRequired, b.addInstref, b.addGvcode, b.addText, b.addPrimanota, b.addKey, b.addIsStorno, b.addRawData, b.addIsSepa, b.addIsCamt, b.addBankSaldo, b.creditcardTransactionDate, b.creditcardType, b.creditcardMerchantArea, b.creditcardMerchantCategory, b.foreignAmount, b.foreignCurrency, b.exchangeRateToBaseCurrency, b.feeAmount, b.feeCurrency, b.crossAccount_id, b.recipient_id, b.category_id, b.categoryRule_id, b.crossBooking_id, b.updatedAt
;

[SQL_SELECT_ALL_BOOKINGS_FULL_BASE]
SELECT ${SQL_SELECT_BOOKING_COLUMNS},
    ba.accountName,
    cba.accountName AS crossAccountName,
    r.name AS recipientName,
    r.iban AS recipientIban,
    r.bic AS recipientBic,
    r.accountnumber AS recipientAccountNumber,
    r.blz AS recipientBlz,
    r.bank AS recipientBank,
    r.source AS recipientSource,
    r.note AS recipientNote,
    r.isDefault AS recipientIsDefault,
    r.updatedAt AS recipientUpdatedAt,
    cg.name AS categoryName,
    cg.parent_id AS categoryParentId,
    cg.fullName AS categoryFullName,
    cg.updatedAt AS categoryUpdatedAt,
    b.categoryRuleName
FROM bookingFull b
JOIN bankAccount ba on b.account_id = ba.id
LEFT JOIN bankAccount cba on b.crossAccount_id = cba.id
LEFT JOIN recipient r on b.recipient_id = r.id
LEFT JOIN categoryFull cg on b.category_id = cg.id;

[SQL_SELECT_ALL_BOOKINGS_FULL]
${SQL_SELECT_ALL_BOOKINGS_FULL_BASE} WHERE b.parentBooking_id IS NULL;

[SQL_SELECT_BOOKINGS_FULL_FOR_ACCOUNT_RELATIONS]
${SQL_SELECT_ALL_BOOKINGS_FULL_BASE} WHERE b.parentBooking_id IS NULL
ORDER BY b.account_id, b.id DESC;

[SQL_SELECT_BOOKING_FULL_BY_ID]
${SQL_SELECT_ALL_BOOKINGS_FULL_BASE} WHERE b.id = ?;

[SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT]
${SQL_SELECT_ALL_BOOKINGS_FULL_BASE} WHERE b.account_id = ? AND b.parentBooking_id IS NULL
ORDER BY b.id desc;

[SQL_SELECT_BOOKINGS_FULL_BY_ACCOUNT_IDS]
${SQL_SELECT_ALL_BOOKINGS_FULL_BASE} WHERE b.account_id IN (%s) AND b.parentBooking_id IS NULL
ORDER BY b.account_id, b.id DESC;

[SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT_AND_DATE_RANGE]
${SQL_SELECT_ALL_BOOKINGS_FULL_BASE} WHERE b.account_id = ?
AND COALESCE(b.dateValue, b.dateBooking) BETWEEN ? AND ?
AND b.parentBooking_id IS NULL
ORDER BY b.id desc;

[SQL_SELECT_SPLIT_BOOKINGS_FULL_BY_PARENT]
${SQL_SELECT_ALL_BOOKINGS_FULL_BASE} WHERE b.parentBooking_id = ?
ORDER BY b.id;

[SQL_FIND_CROSS_BOOKINGS_FULL]
${SQL_SELECT_ALL_BOOKINGS_FULL_BASE} WHERE (ba.iban = ? OR ba.number = ?)
AND b.amount = ?
AND b.dateBooking = ?
AND b.parentBooking_id IS NULL
AND b.bookingType NOT IN (?, ?)
ORDER BY b.id DESC
LIMIT 1;

[SQL_SELECT_ACCOUNT_LAST_BOOKING_DATE]
SELECT MAX(b.dateBooking) AS lastBookingDate FROM booking b where b.account_id = ? AND b.parentBooking_id IS NULL AND b.bookingSource IN (1, 7, 8, 10, 16, 17);

[SQL_SELECT_ALL_BOOKINGS]
SELECT ${SQL_SELECT_BOOKING_COLUMNS}, NULL AS categoryRuleName
FROM bookingFull b
WHERE b.parentBooking_id IS NULL;

[SQL_INSERT_BOOKING]
INSERT INTO booking (account_id, parentBooking_id, dateBooking, dateValue, purpose, amount, bookingType, bookingSource, crossAccount_id, recipient_id, category_id, categoryRule_id, crossBooking_id, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

[SQL_INSERT_BOOKING_BATCH]
INSERT INTO booking (id, account_id, parentBooking_id, dateBooking, dateValue, purpose, amount, bookingType, bookingSource, crossAccount_id, recipient_id, category_id, categoryRule_id, crossBooking_id, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

[SQL_SELECT_MAX_BOOKING_ID]
SELECT COALESCE(MAX(id), 0) FROM booking;

[SQL_UPDATE_BOOKING]
UPDATE booking
SET account_id = ?, parentBooking_id = ?, dateBooking = ?, dateValue = ?, purpose = ?, amount = ?, bookingType = ?, bookingSource = ?, crossAccount_id = ?, recipient_id = ?, category_id = ?, categoryRule_id = ?, crossBooking_id = ?, updatedAt = ?
WHERE id = ?;

[SQL_INSERT_BOOKING_ADDITIONAL_SEPA]
INSERT INTO bookingAdditionalSepa (booking_id, sepa_customer_ref, sepa_creditor_id, sepa_end_to_end, sepa_mandate, sepa_person_id, sepa_purpose, sepa_typ, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(booking_id) DO UPDATE SET
    sepa_customer_ref = excluded.sepa_customer_ref,
    sepa_creditor_id = excluded.sepa_creditor_id,
    sepa_end_to_end = excluded.sepa_end_to_end,
    sepa_mandate = excluded.sepa_mandate,
    sepa_person_id = excluded.sepa_person_id,
    sepa_purpose = excluded.sepa_purpose,
    sepa_typ = excluded.sepa_typ,
    updatedAt = excluded.updatedAt;

[SQL_INSERT_BOOKING_ADDITIONAL_NOTE]
INSERT INTO bookingAdditionalNote (booking_id, note, review_required, updatedAt)
VALUES (?, ?, ?, ?)
ON CONFLICT(booking_id) DO UPDATE SET
    note = excluded.note,
    review_required = excluded.review_required,
    updatedAt = excluded.updatedAt;

[SQL_INSERT_BOOKING_ADDITIONAL]
INSERT INTO bookingAdditional (booking_id, add_instref, add_gvcode, add_text, add_primanota, add_key, add_is_storno, add_raw_data, add_is_sepa, add_is_camt, add_bank_saldo, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(booking_id) DO UPDATE SET
    add_instref = excluded.add_instref,
    add_gvcode = excluded.add_gvcode,
    add_text = excluded.add_text,
    add_primanota = excluded.add_primanota,
    add_key = excluded.add_key,
    add_is_storno = excluded.add_is_storno,
    add_raw_data = excluded.add_raw_data,
    add_is_sepa = excluded.add_is_sepa,
    add_is_camt = excluded.add_is_camt,
    add_bank_saldo = excluded.add_bank_saldo,
    updatedAt = excluded.updatedAt;

[SQL_INSERT_BOOKING_ADDITIONAL_CREDITCARD]
INSERT INTO bookingAdditionalCreditcard (booking_id, creditcard_transaction_date, creditcard_type, creditcard_merchant_area, creditcard_merchant_category, updatedAt)
VALUES (?, ?, ?, ?, ?, ?)
ON CONFLICT(booking_id) DO UPDATE SET
    creditcard_transaction_date = excluded.creditcard_transaction_date,
    creditcard_type = excluded.creditcard_type,
    creditcard_merchant_area = excluded.creditcard_merchant_area,
    creditcard_merchant_category = excluded.creditcard_merchant_category,
    updatedAt = excluded.updatedAt;

[SQL_INSERT_BOOKING_ADDITIONAL_FOREIGNCURRENCY]
INSERT INTO bookingAdditionalForeigncurrency (booking_id, foreignAmount, foreignCurrency, exchangeRateToBaseCurrency, updatedAt)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT(booking_id) DO UPDATE SET
    foreignAmount = excluded.foreignAmount,
    foreignCurrency = excluded.foreignCurrency,
    exchangeRateToBaseCurrency = excluded.exchangeRateToBaseCurrency,
    updatedAt = excluded.updatedAt;

[SQL_INSERT_BOOKING_FEE]
INSERT INTO bookingFee (booking_id, amount, currency, updatedAt)
VALUES (?, ?, ?, ?)
ON CONFLICT(booking_id) DO UPDATE SET
    amount = excluded.amount,
    currency = excluded.currency,
    updatedAt = excluded.updatedAt;

[SQL_DELETE_BOOKING_ADDITIONAL_SEPA]
DELETE FROM bookingAdditionalSepa WHERE booking_id = ?;

[SQL_DELETE_BOOKING_ADDITIONAL_NOTE]
DELETE FROM bookingAdditionalNote WHERE booking_id = ?;

[SQL_DELETE_BOOKING_ADDITIONAL]
DELETE FROM bookingAdditional WHERE booking_id = ?;

[SQL_DELETE_BOOKING_ADDITIONAL_CREDITCARD]
DELETE FROM bookingAdditionalCreditcard WHERE booking_id = ?;

[SQL_DELETE_BOOKING_ADDITIONAL_FOREIGNCURRENCY]
DELETE FROM bookingAdditionalForeigncurrency WHERE booking_id = ?;

[SQL_DELETE_BOOKING_FEE]
DELETE FROM bookingFee WHERE booking_id = ?;

[SQL_DELETE_BOOKING]
DELETE FROM booking WHERE id = ?;

[SQL_UPDATE_BOOKINGS_SOURCE]
UPDATE booking SET bookingSource = ?, updatedAt = ? WHERE account_id = ? AND id = ?;

[SQL_UPDATE_BOOKINGS_RECIPIENT]
UPDATE booking SET recipient_id = ? WHERE recipient_id IS NULL AND id IN (%s);

[SQL_UPDATE_BOOKINGS_CATEGORY]
UPDATE booking SET category_id = ?, categoryRule_id = NULL WHERE id IN (%s);

[SQL_UPDATE_BOOKINGS_CATEGORY_RULE]
UPDATE booking SET category_id = ?, categoryRule_id = ? WHERE id = ?;

[SQL_CLEAR_BOOKING_CATEGORY]
UPDATE booking
SET category_id = NULL, categoryRule_id = NULL, updatedAt = ?
WHERE category_id IS NOT NULL
AND id = ?;

[SQL_CLEAR_BOOKING_CROSS_BOOKING_ID]
UPDATE booking
SET crossBooking_id = NULL, updatedAt = ?
WHERE crossBooking_id IS NOT NULL
AND (
  id = ?
  OR crossBooking_id = ?
  OR id IN (
    SELECT selected.crossBooking_id
    FROM booking selected
    WHERE selected.id = ?
      AND selected.crossBooking_id IS NOT NULL
  )
);
