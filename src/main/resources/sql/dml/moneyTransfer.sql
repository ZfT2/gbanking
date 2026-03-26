[SQL_SELECT_ALL_MONEYTRANSFERS_BASE]
SELECT m.id, m.account_id, m.moneytransferType, m.recipient_id, m.purpose, m.amount, m.executionDate, m.executionDay, m.moneytransferStatus, m.standingorderMode, m.historyorder_id, m.updatedAt, r.id AS r_id, r.name, r.iban, r.bic, r.bank 
FROM moneytransfer m, recipient r 
WHERE m.recipient_id = r.id;

[SQL_SELECT_ALL_MONEYTRANSFERS]
${SQL_SELECT_ALL_MONEYTRANSFERS_BASE};

[SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT]
${SQL_SELECT_ALL_MONEYTRANSFERS_BASE} AND m.account_id = ?;

[SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_STATE]
${SQL_SELECT_ALL_MONEYTRANSFERS_BASE} AND moneytransferstatus = ?;

[SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT_AND_STATE]
${SQL_SELECT_ALL_MONEYTRANSFERS_BASE} AND m.account_id = ? AND moneytransferstatus = ?;

[SQL_SELECT_ID_MONEYTRANSFER_BY_ID_AND_ACCOUNT_ID]
SELECT id FROM moneytransfer WHERE id = ? AND account_id = ?;

[SQL_INSERT_MONEYTRANSFER]
INSERT INTO moneytransfer (account_id, moneytransferType, recipient_id, purpose, amount, executionDate, executionDay, moneytransferStatus, standingorderMode, historyorder_id, updatedAt) 
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);;

[SQL_SELECT_ALL_MONEYTRANSFERS_BY_ACCOUNT]
SELECT * FROM moneytransfer where account_id = ?;

[SQL_UPDATE_MONEYTRANSFER]
UPDATE moneytransfer 
SET moneytransferType = ?, recipient_id = ?, purpose = ?, amount = ?, executionDate = ?, executionDay = ?, standingorderMode = ?, historyorder_id = ?, updatedAt = ? 
WHERE id = ?;

[SQL_DELETE_MONEYTRANSFER_BY_ID]
DELETE FROM moneytransfer 
WHERE id = ?;

[SQL_INSERT_MONEYTRANSFER_PROTOCOL]
INSERT INTO moneytransferProtocol (moneytransfer_id, moneytransferStatus, timeStart, timeFinish, protocolText, updatedAt) 
VALUES (?, ?, ?, ?, ?, ?);
