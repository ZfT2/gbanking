[SQL_SELECT_ALL_PARAMETERDATA]
SELECT id, pdKey, pdType, updatedAt FROM parameterData;

[SQL_SELECT_ALL_BPD_OR_UPD]
SELECT bankAccess_id, pdKey, pdValue, updatedAt FROM %s;

[SQL_INSERT_PARAMETERDATA]
INSERT INTO parameterData (pdKey, pdType, updatedAt) VALUES %s;

[SQL_UPDATE_PARAMETERDATA]
UPDATE parameterData SET pdKey = ?, pdType = ?, updatedAt = ?;

[SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS]
SELECT bankAccess_id, pdKey, pdValue, updatedAt FROM %s WHERE bankAccess_id = ?;

[SQL_INSERT_BANKACCESS_PARAMETERDATA]
INSERT INTO bankAccess_parameterData (bankAccess_id, parameterData_id, pdValue, updatedAt) VALUES %s;

[SQL_UPDATE_BANKACCESS_PARAMETERDATA]
UPDATE bankAccess_parameterData SET pdValue = ?, updatedAt = ? WHERE bankAccess_id = ? AND parameterData_id = ? AND pdType = ?;

[SQL_DELETE_BANKACCESS_PARAMETERDATA]
DELETE FROM bankAccess_parameterData WHERE bankAccess_id = ? AND parameterData_id = ? AND pdType = ?;

[SQL_DELETE_ALL_BANKACCESS_PARAMETERDATA_BY_BANKACCESS]
DELETE FROM bankAccess_parameterData WHERE parameterData_id IN (SELECT id from parameterData WHERE pdType = ?) AND bankAccess_id = ?;

[SQL_DELETE_UNUSED_PARAMETERDATA]
DELETE FROM parameterData WHERE id NOT IN (SELECT parameterData_id from bankAccess_parameterData);
