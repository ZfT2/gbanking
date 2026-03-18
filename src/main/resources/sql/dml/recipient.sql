[SQL_SELECT_ALL_RECIPIENTS_BASE]
SELECT id, name, iban, bic, accountnumber, blz, bank, source, note, updatedAt FROM recipient

;

[SQL_SELECT_ALL_RECIPIENTS]
${SQL_SELECT_ALL_RECIPIENTS_BASE};

;

[SQL_SELECT_ALL_RECIPIENTS_WITH_NAME_IBAN]
${SQL_SELECT_ALL_RECIPIENTS_BASE} WHERE name IS NOT NULL AND name <> '' AND iban IS NOT NULL AND iban <> '' ORDER BY name

;

[SQL_SELECT_RECIPIENT_BY_ID]
${SQL_SELECT_ALL_RECIPIENTS_BASE} WHERE id = ?;

;

[SQL_FIND_RECIPIENT_BY_ARGS]
${SQL_SELECT_ALL_RECIPIENTS_BASE} WHERE (name  = ? or name IS NULL) AND (iban  = ? or iban IS NULL) AND (accountnumber = ? or accountnumber IS NULL) AND (blz  = ? or blz IS NULL) AND (bic  = ? or bic IS NULL)

;

[SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED]
${SQL_SELECT_ALL_RECIPIENTS_BASE} WHERE id = ? ${SQL_WHERE_RECIPIENT_BY_ID_IF_NOT_REFERENCED}

;

[SQL_INSERT_RECIPIENT]
INSERT INTO recipient (name, iban, bic, accountnumber, blz, bank, source, note, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);

;

[SQL_SELECT_ID_RECIPIENT_BY_IBAN]
SELECT id FROM recipient WHERE iban = ?;

;

[SQL_WHERE_RECIPIENT_BY_ID_IF_NOT_REFERENCED]
 AND id NOT IN (SELECT recipient_id from moneytransfer WHERE recipient_id = recipient.id) AND id NOT IN (SELECT recipient_id from booking WHERE recipient_id = recipient.id);

;

[SQL_DELETE_RECIPIENT_BY_ID_IF_NOT_REFERENCED]
DELETE FROM recipient WHERE id = ?  AND id NOT IN (SELECT recipient_id from moneytransfer WHERE recipient_id = recipient.id) AND id NOT IN (SELECT recipient_id from booking WHERE recipient_id = recipient.id);
;
