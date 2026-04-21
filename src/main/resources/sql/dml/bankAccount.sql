[SQL_SELECT_ID_BANKACCOUNT_BY_IBAN_OR_NUMBER]
SELECT id, accountName 
FROM bankAccount 
WHERE (iban = ? OR number = ?);

[SQL_SELECT_ALL_BANKACCOUNTS]
SELECT id, bankAccess_id, parentAccount_id, accountName, currency, accountType, accountSource, iban, bic, number, subNumber, bankName, blz, accountLimit, customerId, ownerName, ownerName2, country, creditorId, isSepaAccount, isOfflineAccount, accountState, balance, updatedAt 
FROM bankAccount;

[SQL_INSERT_BANKACCOUNT]
INSERT INTO bankAccount (bankAccess_id, parentAccount_id, accountName, currency, accountType, accountSource, iban, bic, number, subNumber, bankName, blz, hbciAccountType, accountLimit, customerId, ownerName, ownerName2, country, creditorId, isSEPAAccount, isOfflineAccount, accountState, balance, updatedAt) 
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

[SQL_UPDATE_BANKACCOUNT]
UPDATE bankAccount 
SET bankAccess_id = ?, parentAccount_id = ?, accountName = ?, currency = ?, accountType = ?, accountSource = ?, iban = ?, bic = ?, number = ?, subNumber = ?, bankName = ?, blz = ?, hbciAccountType = ?, accountLimit = ?, customerId = ?, ownerName = ?, ownerName2 = ?, country = ?, creditorId = ?, isSEPAAccount = ?, isOfflineAccount = ?, accountState = ?, balance = ?, updatedAt = ? 
WHERE id = ?;

[SQL_UPDATE_BANKACCOUNT_SOURCE]
UPDATE bankAccount SET accountSource = ?, updatedAt = ? WHERE id = ?;
