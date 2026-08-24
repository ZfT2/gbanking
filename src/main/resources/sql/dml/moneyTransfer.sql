[SQL_SELECT_ALL_MONEYTRANSFERS_BASE]
SELECT m.id, m.account_id, m.moneytransferType, m.recipient_id, m.purpose, m.purposeCode, m.endToEndId, m.amount, mf.currency,
    m.executionDate, m.executionDay, m.moneytransferStatus, m.standingorderMode, m.bankOrderId, m.historyorder_id, m.updatedAt,
    r.id AS r_id, r.name, r.iban, r.bic, r.accountnumber, r.blz, r.bank,
    mf.id AS foreign_id, mf.moneytransfer_id AS foreign_moneytransfer_id, mf.currency AS foreign_currency,
    mf.recipientCountry, mf.recipientAccountNumber, mf.recipientBankCode, mf.recipientSubAccount,
    mf.recipientAddressLine1, mf.recipientAddressLine2, mf.recipientBankCountry,
    mf.recipientBankAddressLine1, mf.recipientBankAddressLine2, mf.chargeBearer,
    mf.regulatoryReporting, mf.endToEndReference, mf.updatedAt AS foreign_updatedAt
FROM moneytransfer m
JOIN recipient r ON m.recipient_id = r.id
LEFT JOIN moneytransferForeign mf ON mf.moneytransfer_id = m.id
WHERE 1 = 1;

[SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT]
${SQL_SELECT_ALL_MONEYTRANSFERS_BASE} AND m.account_id = ?;

[SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_STATE]
${SQL_SELECT_ALL_MONEYTRANSFERS_BASE} AND moneytransferstatus = ?;

[SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT_AND_STATE]
${SQL_SELECT_ALL_MONEYTRANSFERS_BASE} AND m.account_id = ? AND moneytransferstatus = ?;

[SQL_SELECT_ID_MONEYTRANSFER_BY_ID_AND_ACCOUNT_ID]
SELECT id FROM moneytransfer WHERE id = ? AND account_id = ?;

[SQL_INSERT_MONEYTRANSFER]
INSERT INTO moneytransfer (account_id, moneytransferType, recipient_id, purpose, purposeCode, endToEndId, amount, executionDate, executionDay, moneytransferStatus, standingorderMode, bankOrderId, historyorder_id, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);;

[SQL_UPDATE_MONEYTRANSFER]
UPDATE moneytransfer
SET account_id = ?, moneytransferType = ?, recipient_id = ?, purpose = ?, purposeCode = ?, endToEndId = ?, amount = ?, executionDate = ?, executionDay = ?, moneytransferStatus = ?, standingorderMode = ?, bankOrderId = ?, historyorder_id = ?, updatedAt = ?
WHERE id = ?;

[SQL_INSERT_MONEYTRANSFER_PROTOCOL]
INSERT INTO moneytransferProtocol (moneytransfer_id, moneytransferStatus, timeStart, timeFinish, protocolText, updatedAt) 
VALUES (?, ?, ?, ?, ?, ?);

[SQL_SELECT_ALL_MONEYTRANSFER_PROTOCOLS]
SELECT id, moneytransfer_id, moneytransferStatus, timeStart, timeFinish, protocolText, updatedAt
FROM moneytransferProtocol
ORDER BY timeStart DESC, id DESC;

[SQL_SELECT_ALL_MONEYTRANSFER_PROTOCOLS_BY_MONEYTRANSFER]
SELECT id, moneytransfer_id, moneytransferStatus, timeStart, timeFinish, protocolText, updatedAt
FROM moneytransferProtocol
WHERE moneytransfer_id = ?
ORDER BY timeStart DESC, id DESC;
