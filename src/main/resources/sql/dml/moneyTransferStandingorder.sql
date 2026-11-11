[SQL_SELECT_MONEYTRANSFER_STANDINGORDER_BASE]
SELECT id, moneytransfer_id, executionDay, standingorderMode, executionDateNext, updatedAt
FROM moneytransferStandingorder;

[SQL_SELECT_MONEYTRANSFER_STANDINGORDER_BY_MONEYTRANSFER]
${SQL_SELECT_MONEYTRANSFER_STANDINGORDER_BASE} WHERE moneytransfer_id = ?;

[SQL_SELECT_MONEYTRANSFER_STANDINGORDER_BY_PRIMARY_ID]
${SQL_SELECT_MONEYTRANSFER_STANDINGORDER_BASE} WHERE id = ?;

[SQL_SELECT_ID_MONEYTRANSFER_STANDINGORDER]
SELECT id FROM moneytransferStandingorder WHERE id = ?;

[SQL_INSERT_MONEYTRANSFER_STANDINGORDER]
INSERT INTO moneytransferStandingorder (moneytransfer_id, executionDay, standingorderMode, executionDateNext, updatedAt)
VALUES (?, ?, ?, ?, ?);

[SQL_UPDATE_MONEYTRANSFER_STANDINGORDER]
UPDATE moneytransferStandingorder
SET moneytransfer_id = ?, executionDay = ?, standingorderMode = ?, executionDateNext = ?, updatedAt = ?
WHERE id = ?;

[SQL_UPSERT_MONEYTRANSFER_STANDINGORDER]
INSERT INTO moneytransferStandingorder (moneytransfer_id, executionDay, standingorderMode, executionDateNext, updatedAt)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT(moneytransfer_id) DO UPDATE SET
    executionDay = excluded.executionDay,
    standingorderMode = excluded.standingorderMode,
    executionDateNext = excluded.executionDateNext,
    updatedAt = excluded.updatedAt;

[SQL_DELETE_MONEYTRANSFER_STANDINGORDER_BY_MONEYTRANSFER]
DELETE FROM moneytransferStandingorder WHERE moneytransfer_id = ?;

[SQL_DELETE_MONEYTRANSFER_STANDINGORDER_BY_PRIMARY_ID]
DELETE FROM moneytransferStandingorder WHERE id = ?;
