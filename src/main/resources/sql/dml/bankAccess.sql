[SQL_SELECT_ID_BANKACCESS_BY_BLZ]
SELECT id FROM bankAccess WHERE blz = ?;

;

[SQL_INSERT_BANKACCESS]
INSERT INTO bankAccess (bankName, country, blz, hbciURL, port, userId, customerId, sysId, tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion, hbciFilterType, active, updatedAt) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);

;

[SQL_UPDATE_BANKACCESS]
UPDATE bankAccess SET bankName = ?, country = ?, blz = ?, hbciURL = ?, port = ?, userId = ?, customerId = ?, sysId = ?, tanProcedure = ?, allowedTwostepMechanisms = ?, hbciVersion = ?, bpdVersion = ?, updVersion = ?, hbciFilterType = ?, active = ?, updatedAt = ? WHERE id = ?

;

[SQL_SELECT_ALL_BANKACCESSES]
SELECT id, bankName, country, blz, hbciURL, port, userId, customerId, sysId, tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion, hbciFilterType, active, updatedAt FROM bankAccess;

;

[SQL_SELECT_BANKACCESS_BY_ID]
SELECT id, bankName, country, blz, hbciURL, port, userId, customerId, sysId, tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion, hbciFilterType, active, updatedAt FROM bankAccess WHERE id = ?

;

[SQL_SELECT_BANKACCESS_BY_BLZ]
SELECT id, bankName, country, blz, hbciURL, port, userId, customerId, sysId, tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion, hbciFilterType, active, updatedAt FROM bankAccess WHERE blz = ?

;

[SQL_DELETE_BANKACCESS_BY_BLZ]
DELETE FROM bankAccess WHERE blz = ?;

;

[SQL_SELECT_ALL_BANKACCOUNTS_BY_BANKACCESS]
SELECT id, bankAccess_id, accountName, currency, accountType, accountSource, iban, bic, number, subNumber, bankName, blz, accountLimit, customerId, ownerName, ownerName2, country, creditorId, isSepaAccount, isOfflineAccount, accountState, balance, updatedAt FROM bankAccount WHERE bankAccess_id = ?;
;
