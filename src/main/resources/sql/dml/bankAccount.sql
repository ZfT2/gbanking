[SQL_SELECT_ID_BANKACCOUNT_BY_IBAN_OR_NUMBER]
SELECT ba.id, ba.accountName 
FROM bankAccount ba
WHERE (ba.iban = ? OR ba.number = ?);

[SQL_SELECT_ALL_BANKACCOUNTS]
SELECT ba.id, ba.bankAccess_id, ba.parentAccount_id, ba.providerAccountId, ba.accountName, ba.baseCurrency,
  ba.accountType, ba.accountSource, ba.iban, ba.bic, ba.number, ba.subNumber, ba.bankName, ba.blz,
  ba.hbciAccountType, ba.accountLimit, ba.customerId, ba.ownerName, ba.ownerName2, ba.country,
  ba.creditorId, ba.isSepaAccount, ba.isOfflineAccount, ba.accountState, ba.balance, ba.createdAt, ba.updatedAt
FROM bankAccount ba
ORDER BY ba.bankAccess_id, ba.id;

[SQL_SELECT_ALL_ONLINE_BANKACCOUNTS]
SELECT ba.id, ba.bankAccess_id, ba.parentAccount_id, ba.providerAccountId, ba.accountName, ba.baseCurrency, ba.accountType, ba.accountSource, ba.iban, ba.bic, ba.number, ba.subNumber, ba.bankName, ba.blz, ba.hbciAccountType, ba.accountLimit, ba.customerId, ba.ownerName, ba.ownerName2, ba.country, ba.creditorId, ba.isSepaAccount, ba.isOfflineAccount, ba.accountState, ba.balance, ba.createdAt, ba.updatedAt
FROM bankAccount ba, bankAccess bc
WHERE bc.id = ba.bankAccess_id;

[SQL_INSERT_BANKACCOUNT]
INSERT INTO bankAccount (bankAccess_id, parentAccount_id, providerAccountId, accountName, baseCurrency, accountType, accountSource, iban, bic, number, subNumber, bankName, blz, hbciAccountType, accountLimit, customerId, ownerName, ownerName2, country, creditorId, isSEPAAccount, isOfflineAccount, accountState, balance, createdAt, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

[SQL_UPDATE_BANKACCOUNT]
UPDATE bankAccount 
SET bankAccess_id = ?, parentAccount_id = ?, providerAccountId = ?, accountName = ?, baseCurrency = ?, accountType = ?, accountSource = ?, iban = ?, bic = ?, number = ?, subNumber = ?, bankName = ?, blz = ?, hbciAccountType = ?, accountLimit = ?, customerId = ?, ownerName = ?, ownerName2 = ?, country = ?, creditorId = ?, isSEPAAccount = ?, isOfflineAccount = ?, accountState = ?, balance = ?, updatedAt = ?
WHERE id = ?;

[SQL_UPDATE_BANKACCOUNT_SOURCE]
UPDATE bankAccount SET accountSource = ?, updatedAt = ? WHERE id = ?;

[SQL_SELECT_BANKACCOUNT_RETRIEVAL_STATUS]
SELECT bankAccount_id, retrievedAt, result, newBookingCount, pendingBookingCount, lastError
FROM bankAccountRetrievalStatus
WHERE bankAccount_id = ?;

[SQL_UPSERT_BANKACCOUNT_RETRIEVAL_STATUS]
INSERT INTO bankAccountRetrievalStatus (bankAccount_id, retrievedAt, result, newBookingCount, pendingBookingCount, lastError)
VALUES (?, ?, ?, ?, ?, ?)
ON CONFLICT(bankAccount_id) DO UPDATE SET
  retrievedAt = excluded.retrievedAt,
  result = excluded.result,
  newBookingCount = excluded.newBookingCount,
  pendingBookingCount = excluded.pendingBookingCount,
  lastError = excluded.lastError;

[SQL_SELECT_ALL_BANKACCOUNT_IDENTIFIERS]
SELECT id, account_id, propertyType, value
FROM bankAccountIdentifiers
ORDER BY account_id, propertyType, value;

[SQL_SELECT_BANKACCOUNT_IDENTIFIERS_BY_ACCOUNT]
SELECT id, account_id, propertyType, value
FROM bankAccountIdentifiers
WHERE account_id = ?
ORDER BY propertyType, value;

[SQL_INSERT_BANKACCOUNT_IDENTIFIER]
INSERT INTO bankAccountIdentifiers (account_id, propertyType, value)
VALUES (?, ?, ?);

[SQL_DELETE_BANKACCOUNT_IDENTIFIERS_BY_ACCOUNT]
DELETE FROM bankAccountIdentifiers
WHERE account_id = ?;

[SQL_SELECT_BANKACCOUNT_IDS_BY_ACCOUNT_NAME]
SELECT id, accountName AS identifier
FROM bankAccount
WHERE accountName IS NOT NULL;

[SQL_SELECT_BANKACCOUNT_IDS_BY_IBAN_OR_NUMBER]
SELECT id, iban AS identifier
FROM bankAccount
WHERE iban IS NOT NULL
UNION
SELECT id, number AS identifier
FROM bankAccount
WHERE number IS NOT NULL;
