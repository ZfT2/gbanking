[SQL_SELECT_FIELD_LIST_BASE]
SELECT r.id, name, iban, bic, accountnumber, blz, bank, source, note, isDefault, r.updatedAt

[SQL_SELECT_ALL_RECIPIENTS_BASE]
${SQL_SELECT_FIELD_LIST_BASE} FROM recipient r;

[SQL_SELECT_ALL_RECIPIENTS]
${SQL_SELECT_ALL_RECIPIENTS_BASE} ORDER BY name IS NULL, name;

[SQL_SELECT_ALL_RECIPIENTS_WITH_NAME_IBAN]
${SQL_SELECT_ALL_RECIPIENTS_BASE} WHERE NULLIF(TRIM(name), '') IS NOT NULL AND (NULLIF(TRIM(iban), '') IS NOT NULL OR NULLIF(TRIM(accountnumber), '') IS NOT NULL) ORDER BY name;

[SQL_SELECT_ALL_RECIPIENTS_ORDERED_MT]
${SQL_SELECT_FIELD_LIST_BASE}, count(mt.id) as moneytransfer_count 
FROM recipient r
left join moneytransfer mt 
on r.id = mt.recipient_id
group by r.id
order by moneytransfer_count desc, r.name asc;

[SQL_FIND_RECIPIENT_BY_ARGS]
${SQL_SELECT_ALL_RECIPIENTS_BASE} WHERE
    ((? IS NULL AND NULLIF(TRIM(name), '') IS NULL) OR (? IS NOT NULL AND TRIM(name) = ? COLLATE NOCASE))
AND ((? IS NULL AND NULLIF(TRIM(iban), '') IS NULL) OR (? IS NOT NULL AND TRIM(iban) = ? COLLATE NOCASE))
AND ((? IS NULL AND NULLIF(TRIM(accountnumber), '') IS NULL) OR (? IS NOT NULL AND TRIM(accountnumber) = ?))
AND ((? IS NULL AND NULLIF(TRIM(blz), '') IS NULL) OR (? IS NOT NULL AND TRIM(blz) = ?))
AND ((? IS NULL AND NULLIF(TRIM(bic), '') IS NULL) OR (? IS NOT NULL AND TRIM(bic) = ? COLLATE NOCASE))
AND ((? IS NULL AND NULLIF(TRIM(bank), '') IS NULL) OR (? IS NOT NULL AND TRIM(bank) = ? COLLATE NOCASE));

[SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED]
${SQL_SELECT_ALL_RECIPIENTS_BASE} WHERE r.id = ? ${SQL_WHERE_RECIPIENT_BY_ID_IF_NOT_REFERENCED};

[SQL_SELECT_RECIPIENT_BY_ID_IF_EDITABLE]
${SQL_SELECT_ALL_RECIPIENTS_BASE}
WHERE r.id = ?
  AND NOT EXISTS (SELECT 1 FROM moneytransfer WHERE recipient_id = r.id)
  AND NOT EXISTS (
      SELECT 1
      FROM booking
      WHERE recipient_id = r.id
        AND bookingSource NOT IN (5, 14));

[SQL_SELECT_RECIPIENT_CANDIDATES_BY_ACCOUNT_IDENTIFIER]
${SQL_SELECT_ALL_RECIPIENTS_BASE} WHERE ${SQL_WHERE_RECIPIENT_BY_ACCOUNT_IDENTIFIER} ORDER BY name IS NULL, name;

[SQL_SELECT_UNREFERENCED_RECIPIENT_CANDIDATES_BY_ACCOUNT_IDENTIFIER]
${SQL_SELECT_ALL_RECIPIENTS_BASE} WHERE ${SQL_WHERE_RECIPIENT_BY_ACCOUNT_IDENTIFIER} ${SQL_WHERE_RECIPIENT_BY_ID_IF_NOT_REFERENCED} ORDER BY name IS NULL, name;

[SQL_SELECT_PREFERRED_RECIPIENT_BY_IBAN]
${SQL_SELECT_FIELD_LIST_BASE}
FROM recipient r
LEFT JOIN (
    SELECT recipient_id, MAX(usedAt) AS lastUsedAt
    FROM (
        SELECT recipient_id, COALESCE(dateValue, dateBooking, updatedAt) AS usedAt
        FROM booking
        WHERE recipient_id IS NOT NULL
        UNION ALL
        SELECT recipient_id, COALESCE(executionDate, updatedAt) AS usedAt
        FROM moneytransfer
        WHERE recipient_id IS NOT NULL
    ) recipientUsage
    GROUP BY recipient_id
) usage ON usage.recipient_id = r.id
WHERE NULLIF(TRIM(?), '') IS NOT NULL
  AND TRIM(r.iban) COLLATE NOCASE = TRIM(?)
ORDER BY r.isDefault DESC,
         usage.lastUsedAt IS NULL,
         usage.lastUsedAt DESC,
         r.updatedAt DESC,
         r.id DESC
LIMIT 1;

[SQL_INSERT_RECIPIENT]
INSERT INTO recipient (name, iban, bic, accountnumber, blz, bank, source, note, isDefault, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

[SQL_UPDATE_RECIPIENT]
UPDATE recipient SET name = ?, iban = ?, bic = ?, accountnumber = ?, blz = ?, bank = ?, source = ?, note = ?, isDefault = ?, updatedAt = ? WHERE id = ?;

[SQL_UPDATE_RECIPIENT_DEFAULT]
UPDATE recipient SET isDefault = ?, updatedAt = ? WHERE id = ?;

[SQL_WHERE_RECIPIENT_BY_ACCOUNT_IDENTIFIER]
(
    (NULLIF(TRIM(?), '') IS NOT NULL AND TRIM(iban) COLLATE NOCASE = TRIM(?))
 OR (NULLIF(TRIM(?), '') IS NOT NULL AND TRIM(accountnumber) = TRIM(?))
);

[SQL_WHERE_RECIPIENT_BY_ID_IF_NOT_REFERENCED]
 AND NOT EXISTS (SELECT 1 FROM moneytransfer WHERE recipient_id = r.id) AND NOT EXISTS (SELECT 1 FROM booking b WHERE b.recipient_id = r.id);
