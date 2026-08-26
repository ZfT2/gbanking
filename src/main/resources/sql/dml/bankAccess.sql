[SQL_SELECT_ID_BANKACCESS_BY_BLZ]
SELECT ba.id FROM bankAccess ba JOIN bankAccessFints bf ON bf.bankAccess_id = ba.id WHERE bf.blz = ?;
;

[SQL_INSERT_BANKACCESS]
INSERT INTO bankAccess (bankName, active, updatedAt, accessType) VALUES (?, ?, ?, ?);
;

[SQL_UPDATE_BANKACCESS]
UPDATE bankAccess SET bankName = ?, active = ?, updatedAt = ?, accessType = ? WHERE id = ?;
;

[SQL_UPSERT_BANKACCESS_FINTS]
INSERT INTO bankAccessFints (bankAccess_id, country, blz, hbciURL, port, userId, customerId, sysId, tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion, hbciFilterType)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(bankAccess_id) DO UPDATE SET country = excluded.country, blz = excluded.blz, hbciURL = excluded.hbciURL,
  port = excluded.port, userId = excluded.userId, customerId = excluded.customerId, sysId = excluded.sysId,
  tanProcedure = excluded.tanProcedure, allowedTwostepMechanisms = excluded.allowedTwostepMechanisms,
  hbciVersion = excluded.hbciVersion, bpdVersion = excluded.bpdVersion, updVersion = excluded.updVersion,
  hbciFilterType = excluded.hbciFilterType;
;

[SQL_UPSERT_BANKACCESS_PAYPAL]
INSERT INTO bankAccessPaypal (bankAccess_id, userId, apiUsername, apiSignature)
VALUES (?, ?, ?, ?)
ON CONFLICT(bankAccess_id) DO UPDATE SET userId = excluded.userId, apiUsername = excluded.apiUsername,
  apiSignature = excluded.apiSignature;
;

[SQL_UPSERT_BANKACCESS_ENABLEBANKING]
INSERT INTO bankAccessEnablebanking (bankAccess_id, psd2ClientConfiguration_id, aspspName, aspspCountry, psuType, authMethod, sessionId, validUntil, rateLimitUntil)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(bankAccess_id) DO UPDATE SET psd2ClientConfiguration_id = excluded.psd2ClientConfiguration_id,
  aspspName = excluded.aspspName, aspspCountry = excluded.aspspCountry, psuType = excluded.psuType,
  authMethod = excluded.authMethod, sessionId = excluded.sessionId, validUntil = excluded.validUntil,
  rateLimitUntil = excluded.rateLimitUntil;
;

[SQL_DELETE_BANKACCESS_FINTS]
DELETE FROM bankAccessFints WHERE bankAccess_id = ?;
;

[SQL_DELETE_BANKACCESS_PAYPAL]
DELETE FROM bankAccessPaypal WHERE bankAccess_id = ?;
;

[SQL_DELETE_BANKACCESS_ENABLEBANKING]
DELETE FROM bankAccessEnablebanking WHERE bankAccess_id = ?;
;

[SQL_SELECT_ALL_BANKACCESSES]
SELECT ba.id, ba.bankName, ba.active, ba.updatedAt, ba.accessType,
  bf.country AS fintsCountry, bf.blz AS fintsBlz, bf.hbciURL, bf.port, bf.userId AS fintsUserId,
  bf.customerId, bf.sysId, bf.tanProcedure, bf.allowedTwostepMechanisms, bf.hbciVersion,
  bf.bpdVersion, bf.updVersion, bf.hbciFilterType,
  bp.userId AS paypalUserId, bp.apiUsername AS paypalApiUsername, bp.apiSignature AS paypalApiSignature,
  be.psd2ClientConfiguration_id, be.aspspName, be.aspspCountry, be.psuType, be.authMethod,
  be.sessionId, be.validUntil, be.rateLimitUntil
FROM bankAccess ba
LEFT JOIN bankAccessFints bf ON bf.bankAccess_id = ba.id
LEFT JOIN bankAccessPaypal bp ON bp.bankAccess_id = ba.id
LEFT JOIN bankAccessEnablebanking be ON be.bankAccess_id = ba.id;
;

[SQL_SELECT_BANKACCESS_BY_ID]
SELECT ba.id, ba.bankName, ba.active, ba.updatedAt, ba.accessType,
  bf.country AS fintsCountry, bf.blz AS fintsBlz, bf.hbciURL, bf.port, bf.userId AS fintsUserId,
  bf.customerId, bf.sysId, bf.tanProcedure, bf.allowedTwostepMechanisms, bf.hbciVersion,
  bf.bpdVersion, bf.updVersion, bf.hbciFilterType,
  bp.userId AS paypalUserId, bp.apiUsername AS paypalApiUsername, bp.apiSignature AS paypalApiSignature,
  be.psd2ClientConfiguration_id, be.aspspName, be.aspspCountry, be.psuType, be.authMethod,
  be.sessionId, be.validUntil, be.rateLimitUntil
FROM bankAccess ba
LEFT JOIN bankAccessFints bf ON bf.bankAccess_id = ba.id
LEFT JOIN bankAccessPaypal bp ON bp.bankAccess_id = ba.id
LEFT JOIN bankAccessEnablebanking be ON be.bankAccess_id = ba.id
WHERE ba.id = ?;
;

[SQL_SELECT_BANKACCESS_BY_BLZ]
SELECT ba.id, ba.bankName, ba.active, ba.updatedAt, ba.accessType,
  bf.country AS fintsCountry, bf.blz AS fintsBlz, bf.hbciURL, bf.port, bf.userId AS fintsUserId,
  bf.customerId, bf.sysId, bf.tanProcedure, bf.allowedTwostepMechanisms, bf.hbciVersion,
  bf.bpdVersion, bf.updVersion, bf.hbciFilterType,
  bp.userId AS paypalUserId, bp.apiUsername AS paypalApiUsername, bp.apiSignature AS paypalApiSignature,
  be.psd2ClientConfiguration_id, be.aspspName, be.aspspCountry, be.psuType, be.authMethod,
  be.sessionId, be.validUntil, be.rateLimitUntil
FROM bankAccess ba
JOIN bankAccessFints bf ON bf.bankAccess_id = ba.id
LEFT JOIN bankAccessPaypal bp ON bp.bankAccess_id = ba.id
LEFT JOIN bankAccessEnablebanking be ON be.bankAccess_id = ba.id
WHERE bf.blz = ?;
;

[SQL_SELECT_BANKACCESS_BY_BLZ_AND_USER_ID]
SELECT ba.id, ba.bankName, ba.active, ba.updatedAt, ba.accessType,
  bf.country AS fintsCountry, bf.blz AS fintsBlz, bf.hbciURL, bf.port, bf.userId AS fintsUserId,
  bf.customerId, bf.sysId, bf.tanProcedure, bf.allowedTwostepMechanisms, bf.hbciVersion,
  bf.bpdVersion, bf.updVersion, bf.hbciFilterType,
  bp.userId AS paypalUserId, bp.apiUsername AS paypalApiUsername, bp.apiSignature AS paypalApiSignature,
  be.psd2ClientConfiguration_id, be.aspspName, be.aspspCountry, be.psuType, be.authMethod,
  be.sessionId, be.validUntil, be.rateLimitUntil
FROM bankAccess ba
JOIN bankAccessFints bf ON bf.bankAccess_id = ba.id
LEFT JOIN bankAccessPaypal bp ON bp.bankAccess_id = ba.id
LEFT JOIN bankAccessEnablebanking be ON be.bankAccess_id = ba.id
WHERE bf.blz = ? AND bf.userId = ?;
;

[SQL_SELECT_BANKACCESS_PAYPAL_BY_USER_ID]
SELECT ba.id, ba.bankName, ba.active, ba.updatedAt, ba.accessType,
  bf.country AS fintsCountry, bf.blz AS fintsBlz, bf.hbciURL, bf.port, bf.userId AS fintsUserId,
  bf.customerId, bf.sysId, bf.tanProcedure, bf.allowedTwostepMechanisms, bf.hbciVersion,
  bf.bpdVersion, bf.updVersion, bf.hbciFilterType,
  bp.userId AS paypalUserId, bp.apiUsername AS paypalApiUsername, bp.apiSignature AS paypalApiSignature,
  be.psd2ClientConfiguration_id, be.aspspName, be.aspspCountry, be.psuType, be.authMethod,
  be.sessionId, be.validUntil, be.rateLimitUntil
FROM bankAccess ba
LEFT JOIN bankAccessFints bf ON bf.bankAccess_id = ba.id
JOIN bankAccessPaypal bp ON bp.bankAccess_id = ba.id
LEFT JOIN bankAccessEnablebanking be ON be.bankAccess_id = ba.id
WHERE bp.userId = ?;
;

[SQL_DELETE_BANKACCESS_BY_ID]
DELETE FROM bankAccess WHERE id = ?;
;

[SQL_SELECT_ALL_BANKACCOUNTS_BY_BANKACCESS]
SELECT ba.id, ba.bankAccess_id, ba.parentAccount_id, ba.providerAccountId, ba.accountName, ba.baseCurrency,
  ba.accountType, ba.accountSource, ba.iban, ba.bic, ba.number, ba.subNumber, ba.bankName, ba.blz,
  ba.hbciAccountType, ba.accountLimit, ba.customerId, ba.ownerName, ba.ownerName2, ba.country,
  ba.creditorId, ba.isSepaAccount, ba.isOfflineAccount, ba.accountState, ba.balance, ba.createdAt, ba.updatedAt
FROM bankAccount ba WHERE ba.bankAccess_id = ?;
;
