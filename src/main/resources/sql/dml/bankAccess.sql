[SQL_SELECT_ID_BANKACCESS_BY_BLZ]
SELECT id FROM bankAccess WHERE blz = ?;

;

[SQL_INSERT_BANKACCESS]
INSERT INTO bankAccess (bankName, country, blz, hbciURL, port, userId, customerId, sysId, tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion, hbciFilterType, active, updatedAt, accessType, paypalApiUsername, paypalApiSignature) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);

;

[SQL_UPDATE_BANKACCESS]
UPDATE bankAccess SET bankName = ?, country = ?, blz = ?, hbciURL = ?, port = ?, userId = ?, customerId = ?, sysId = ?, tanProcedure = ?, allowedTwostepMechanisms = ?, hbciVersion = ?, bpdVersion = ?, updVersion = ?, hbciFilterType = ?, active = ?, updatedAt = ?, accessType = ?, paypalApiUsername = ?, paypalApiSignature = ? WHERE id = ?

;

[SQL_SELECT_ALL_BANKACCESSES]
SELECT id, bankName, country, blz, hbciURL, port, userId, customerId, sysId, tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion, hbciFilterType, active, updatedAt, accessType, paypalApiUsername, paypalApiSignature FROM bankAccess;

;

[SQL_SELECT_BANKACCESS_BY_ID]
SELECT id, bankName, country, blz, hbciURL, port, userId, customerId, sysId, tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion, hbciFilterType, active, updatedAt, accessType, paypalApiUsername, paypalApiSignature FROM bankAccess WHERE id = ?

;

[SQL_SELECT_BANKACCESS_BY_BLZ]
SELECT id, bankName, country, blz, hbciURL, port, userId, customerId, sysId, tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion, hbciFilterType, active, updatedAt, accessType, paypalApiUsername, paypalApiSignature FROM bankAccess WHERE blz = ?

;

[SQL_SELECT_BANKACCESS_BY_BLZ_AND_USER_ID]
SELECT id, bankName, country, blz, hbciURL, port, userId, customerId, sysId, tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion, hbciFilterType, active, updatedAt, accessType, paypalApiUsername, paypalApiSignature FROM bankAccess WHERE blz = ? AND userId = ?

;

[SQL_DELETE_BANKACCESS_BY_ID]
DELETE FROM bankAccess WHERE id = ?;

;

[SQL_SELECT_ALL_BANKACCOUNTS_BY_BANKACCESS]
SELECT ba.id, ba.bankAccess_id, ba.accountName, ba.parentAccount_id, ba.currency, ba.accountType, ba.accountSource, ba.iban, ba.bic, ba.number, ba.subNumber, ba.bankName, ba.blz, ba.hbciAccountType, ba.accountLimit, ba.customerId, ba.ownerName, ba.ownerName2, ba.country, ba.creditorId, ba.isSepaAccount, ba.isOfflineAccount, ba.accountState, ba.balance, ba.createdAt, ba.updatedAt FROM bankAccount ba WHERE ba.bankAccess_id = ?;
;
