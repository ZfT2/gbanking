[SQL_FOREIGN_KEY_CHECKS_ON]
PRAGMA foreign_keys = ON;
;

[SQL_SELECT_PD]
SELECT bapd.bankAccess_id, pd.pdKey, bapd.pdValue, bapd.updatedAt
FROM bankAccess_parameterData bapd, parameterData pd
WHERE bapd.parameterData_id = pd.id
AND pdType = ;
