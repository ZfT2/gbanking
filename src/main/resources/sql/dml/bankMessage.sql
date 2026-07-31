[SQL_SELECT_ID_BANK_MESSAGE_BY_ID]
SELECT id FROM bankMessage WHERE id = ?;

[SQL_SELECT_ALL_BANK_MESSAGES_BY_BANKACCESS]
SELECT id, bankAccess_id, bankName, messageKey, code, type, format, description, versionDate, comments, message, retrievedAt, updatedAt
FROM bankMessage
WHERE bankAccess_id = ?
ORDER BY versionDate DESC, retrievedAt DESC, code, description;

[SQL_SELECT_ALL_BANK_MESSAGES]
SELECT id, bankAccess_id, bankName, messageKey, code, type, format, description, versionDate, comments, message, retrievedAt, updatedAt
FROM bankMessage
ORDER BY bankName, versionDate DESC, retrievedAt DESC, code, description;

[SQL_INSERT_BANK_MESSAGE]
INSERT INTO bankMessage
  (bankAccess_id, bankName, messageKey, code, type, format, description, versionDate, comments, message, retrievedAt, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

[SQL_UPDATE_BANK_MESSAGE]
UPDATE bankMessage
SET bankAccess_id = ?, bankName = ?, messageKey = ?, code = ?, type = ?, format = ?, description = ?, versionDate = ?,
    comments = ?, message = ?, retrievedAt = ?, updatedAt = ?
WHERE id = ?;
