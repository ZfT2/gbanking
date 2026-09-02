[SQL_SELECT_ALL_PARAMETERDATA]
SELECT id, pdKey, pdType, updatedAt FROM parameterData;

[SQL_SELECT_ALL_BPD_OR_UPD]
SELECT bankAccess_id, pdKey, pdValue, updatedAt FROM %s;

[SQL_INSERT_PARAMETERDATA]
INSERT OR IGNORE INTO parameterData (pdKey, pdType, updatedAt) VALUES (?,?,?);

[SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS]
SELECT bankAccess_id, pdKey, pdValue, updatedAt FROM %s WHERE bankAccess_id = ?;

[SQL_SELECT_PARAMETERDATA_BY_BANKACCESS]
SELECT pd.pdType, pd.pdKey, bapd.pdValue
FROM bankAccess_parameterData bapd
JOIN parameterData pd ON pd.id = bapd.parameterData_id
WHERE bapd.bankAccess_id = ?;

[SQL_UPSERT_BANKACCESS_PARAMETERDATA]
INSERT INTO bankAccess_parameterData (bankAccess_id, parameterData_id, pdValue, updatedAt)
SELECT ?, id, ?, ? FROM parameterData WHERE pdType = ? AND pdKey = ?
ON CONFLICT(bankAccess_id, parameterData_id) DO UPDATE SET
    pdValue = excluded.pdValue,
    updatedAt = excluded.updatedAt;

[SQL_DELETE_BANKACCESS_PARAMETERDATA_BY_KEY]
DELETE FROM bankAccess_parameterData
WHERE bankAccess_id = ?
AND parameterData_id = (
    SELECT id FROM parameterData WHERE pdType = ? AND pdKey = ?
);
