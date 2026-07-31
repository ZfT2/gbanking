[SQL_SELECT_ID_BANKACCOUNT_STATEMENT_BY_ID]
SELECT id FROM bankAccountStatement WHERE id = ?;

[SQL_SELECT_ALL_BANKACCOUNT_STATEMENTS_BY_ACCOUNT]
SELECT id, account_id, accountName, fileName, format, retrievedAt, statementDate, startDate, endDate, year, number, size, iban, bic, sourceJob,
       receiptAvailable, receipt, acknowledged, acknowledgedAt, updatedAt
FROM bankAccountStatement
WHERE account_id = ?
ORDER BY statementDate DESC, retrievedAt DESC, fileName;

[SQL_SELECT_ALL_BANKACCOUNT_STATEMENTS]
SELECT id, account_id, accountName, fileName, format, retrievedAt, statementDate, startDate, endDate, year, number, size, iban, bic, sourceJob,
       receiptAvailable, receipt, acknowledged, acknowledgedAt, updatedAt
FROM bankAccountStatement
ORDER BY statementDate DESC, retrievedAt DESC, fileName;

[SQL_INSERT_BANKACCOUNT_STATEMENT]
INSERT INTO bankAccountStatement
  (account_id, accountName, fileName, format, retrievedAt, statementDate, startDate, endDate, year, number, size, iban, bic, sourceJob,
   receiptAvailable, receipt, acknowledged, acknowledgedAt, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

[SQL_UPDATE_BANKACCOUNT_STATEMENT]
UPDATE bankAccountStatement
SET account_id = ?, accountName = ?, fileName = ?, format = ?, retrievedAt = ?, statementDate = ?, startDate = ?, endDate = ?, year = ?,
    number = ?, size = ?, iban = ?, bic = ?, sourceJob = ?, receiptAvailable = ?, receipt = ?, acknowledged = ?, acknowledgedAt = ?, updatedAt = ?
WHERE id = ?;
