[SQL_SELECT_ALL_BOOKINGS_FULL_BASE]
SELECT b.id, b.account_id, b.bookingType, b.bookingSource, b.dateBooking, b.dateValue, b.purpose, b.amount, b.currency, b.sepaCustomerRef, b.sepaCreditorId, b.sepaEndToEnd, b.sepaMandate, b.sepaPersonId, b.sepaPurpose, b.sepaTyp, b.crossAccount_id, b.recipient_id, b.category_id, crossBooking_id, b.updatedAt,
    a.accountName,
    ca.accountName AS crossAccountName,
    r.name, r.iban, r.bic, r.accountnumber, r.blz, r.bank, r.source, r.note,
    c.fullName AS categoryFullName,
    SUM(b.amount) OVER (ORDER BY b.id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS balance
FROM booking b
JOIN bankAccount a on b.account_id = a.id
LEFT JOIN bankAccount ca on b.crossAccount_id = ca.id
LEFT JOIN recipient r on b.recipient_id = r.id
LEFT JOIN categoryFull c on b.category_id = c.id

;

[SQL_SELECT_ALL_BOOKINGS_FULL]
${SQL_SELECT_ALL_BOOKINGS_FULL_BASE}

;

[SQL_SELECT_ALL_BOOKINGS_FULL_BY_ACCOUNT]
${SQL_SELECT_ALL_BOOKINGS_FULL_BASE} WHERE account_id = ?
ORDER BY b.id desc;

;

[SQL_FIND_CROSS_BOOKINGS_FULL]
${SQL_SELECT_ALL_BOOKINGS_FULL_BASE} WHERE (a.iban LIKE ? OR a.number LIKE ?)
AND b.amount = ?
AND b.dateBooking = ?
AND b.bookingType NOT IN ('REBOOKING_IN', 'REBOOKING_OUT')
ORDER BY b.id desc;

;

[SQL_SELECT_ACCOUNT_LAST_BOOKING_DATE]
SELECT MAX(dateBooking) AS lastBookingDate FROM booking where account_id = ?;

;

[SQL_SELECT_ID_BOOKING]
SELECT id FROM booking WHERE id = ?;

;

[SQL_INSERT_BOOKING]
INSERT INTO booking (account_id, dateBooking, dateValue, purpose, amount, currency, sepaCustomerRef, sepaCreditorId, sepaEndToEnd, sepaMandate, sepaPersonId, sepaPurpose, sepaTyp, bookingType, bookingSource, crossAccount_id, recipient_id, category_id, crossBooking_id, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

;

[SQL_SELECT_ALL_BOOKINGS]
SELECT id, account_id, bookingType, bookingSource, dateBooking, dateValue, purpose, amount, currency, sepaCustomerRef, sepaCreditorId, sepaEndToEnd, sepaMandate, sepaPersonId, sepaPurpose, sepaTyp, crossAccount_id, updatedAt FROM booking;

;

[SQL_SELECT_ALL_BOOKINGS_BY_ACCOUNT]
SELECT id, account_id, bookingType, bookingSource, dateBooking, dateValue, purpose, amount, currency, sepaCustomerRef, sepaCreditorId, sepaEndToEnd, sepaMandate, sepaPersonId, sepaPurpose, sepaTyp, crossAccount_id, recipient_id, category_id, crossBooking_id, updatedAt FROM booking where account_id = ?;

;

[SQL_SELECT_ALL_BOOKINGS_WITH_BALANCE]
SELECT b.id, b.account_id, b.bookingType, b.bookingSource, b.dateBooking, b.dateValue, b.purpose, b.amount, b.currency, b.sepaCustomerRef, b.sepaCreditorId, b.sepaEndToEnd, b.sepaMandate, b.sepaPersonId, b.sepaPurpose, b.sepaTyp, b.crossAccount_id, b.recipient_id, b.category_id, crossBooking_id, b.updatedAt,
    a.accountName,
    ca.accountName AS crossAccountName,
    c.fullName AS categoryFullName,
    SUM(b.amount) OVER (ORDER BY b.dateBooking ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS balance
FROM booking b
JOIN bankAccount a on b.account_id = a.id
LEFT JOIN bankAccount ca on b.crossAccount_id = ca.id
LEFT JOIN categoryFull c on b.category_id = c.id
;
