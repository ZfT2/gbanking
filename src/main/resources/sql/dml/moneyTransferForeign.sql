[SQL_SELECT_MONEYTRANSFER_FOREIGN_BY_MONEYTRANSFER]
SELECT id, moneytransfer_id, currency, recipientCountry, recipientAccountNumber, recipientBankCode, recipientSubAccount,
    recipientAddressLine1, recipientAddressLine2, recipientBankCountry, recipientBankAddressLine1, recipientBankAddressLine2,
    chargeBearer, regulatoryReporting, endToEndReference, updatedAt
FROM moneytransferForeign
WHERE moneytransfer_id = ?;

[SQL_SELECT_ID_MONEYTRANSFER_FOREIGN_BY_MONEYTRANSFER]
SELECT id FROM moneytransferForeign WHERE moneytransfer_id = ?;

[SQL_INSERT_MONEYTRANSFER_FOREIGN]
INSERT INTO moneytransferForeign (moneytransfer_id, currency, recipientCountry, recipientAccountNumber, recipientBankCode, recipientSubAccount,
    recipientAddressLine1, recipientAddressLine2, recipientBankCountry, recipientBankAddressLine1, recipientBankAddressLine2,
    chargeBearer, regulatoryReporting, endToEndReference, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

[SQL_UPDATE_MONEYTRANSFER_FOREIGN]
UPDATE moneytransferForeign
SET moneytransfer_id = ?, currency = ?, recipientCountry = ?, recipientAccountNumber = ?, recipientBankCode = ?, recipientSubAccount = ?,
    recipientAddressLine1 = ?, recipientAddressLine2 = ?, recipientBankCountry = ?, recipientBankAddressLine1 = ?, recipientBankAddressLine2 = ?,
    chargeBearer = ?, regulatoryReporting = ?, endToEndReference = ?, updatedAt = ?
WHERE id = ?;

[SQL_UPSERT_MONEYTRANSFER_FOREIGN]
INSERT INTO moneytransferForeign (moneytransfer_id, currency, recipientCountry, recipientAccountNumber, recipientBankCode, recipientSubAccount,
    recipientAddressLine1, recipientAddressLine2, recipientBankCountry, recipientBankAddressLine1, recipientBankAddressLine2,
    chargeBearer, regulatoryReporting, endToEndReference, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(moneytransfer_id) DO UPDATE SET
    currency = excluded.currency,
    recipientCountry = excluded.recipientCountry,
    recipientAccountNumber = excluded.recipientAccountNumber,
    recipientBankCode = excluded.recipientBankCode,
    recipientSubAccount = excluded.recipientSubAccount,
    recipientAddressLine1 = excluded.recipientAddressLine1,
    recipientAddressLine2 = excluded.recipientAddressLine2,
    recipientBankCountry = excluded.recipientBankCountry,
    recipientBankAddressLine1 = excluded.recipientBankAddressLine1,
    recipientBankAddressLine2 = excluded.recipientBankAddressLine2,
    chargeBearer = excluded.chargeBearer,
    regulatoryReporting = excluded.regulatoryReporting,
    endToEndReference = excluded.endToEndReference,
    updatedAt = excluded.updatedAt;

[SQL_DELETE_MONEYTRANSFER_FOREIGN_BY_MONEYTRANSFER]
DELETE FROM moneytransferForeign WHERE moneytransfer_id = ?;
