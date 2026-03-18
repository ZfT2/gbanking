[SQL_SELECT_ALL_BUSINESSCASES_BY_BANKACCOUNT]
SELECT bc.id, caseValue, bc.updatedAt FROM businessCase bc, bankAccount_businessCase babc WHERE bc.id = babc.businessCase_id AND babc.account_id = ?
;

[SQL_INSERT_BANKACCOUNT_BUSINESSCASE]
INSERT INTO bankAccount_businessCase (account_id, businessCase_id, updatedAt) VALUES (?,?,?)
;

[SQL_SELECT_ID_INSTIUTE_BY_ID]
SELECT id FROM institute WHERE id = ?;
;

[SQL_SELECT_ID_INSTIUTE_BY_BLZ]
SELECT id FROM institute WHERE blz = ?;
;
