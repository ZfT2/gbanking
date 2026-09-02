[SQL_SELECT_ALL_BUSINESSCASES_BY_BANKACCOUNT]
SELECT bc.id, caseValue, bc.updatedAt FROM businessCase bc, bankAccount_businessCase babc WHERE bc.id = babc.businessCase_id AND babc.account_id = ?
;

[SQL_SELECT_ALL_BUSINESSCASES_WITH_BANKACCOUNT]
SELECT babc.account_id AS relationParentId, bc.id, bc.caseValue, bc.updatedAt
FROM businessCase bc
JOIN bankAccount_businessCase babc ON bc.id = babc.businessCase_id
ORDER BY babc.account_id, bc.id
;

[SQL_SELECT_BUSINESSCASES_BY_BANKACCOUNT_IDS]
SELECT babc.account_id AS relationParentId, bc.id, bc.caseValue, bc.updatedAt
FROM businessCase bc
JOIN bankAccount_businessCase babc ON bc.id = babc.businessCase_id
WHERE babc.account_id IN (%s)
ORDER BY babc.account_id, bc.id
;

[SQL_INSERT_BANKACCOUNT_BUSINESSCASE]
INSERT OR IGNORE INTO bankAccount_businessCase (account_id, businessCase_id, updatedAt) VALUES (?,?,?)
;

[SQL_SELECT_ID_INSTIUTE_BY_ID]
SELECT id FROM institute_db.institute WHERE id = ?;
;

[SQL_SELECT_SQLITE_TABLE_BY_NAME]
SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?;

