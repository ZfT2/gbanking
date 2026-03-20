[SQL_SELECT_ALL_CATEGORYRULES_BASE]
id, category_id, filterDateFrom, filterDateTo, filterAmountFrom, filterAmountTo, filterRecipient, filterPurpose, filterRecipientIsRegex, filterPurposeIsRegex, joinType, updatedAt

[SQL_SELECT_ALL_CATEGORYRULES]
SELECT ${SQL_SELECT_ALL_CATEGORYRULES_BASE} FROM categoryRule;

[SQL_SELECT_ALL_CATEGORYRULES_FULL]
SELECT ${SQL_SELECT_ALL_CATEGORYRULES_BASE}, cg.id, cg.name, cg.fullName, FROM categoryRule cgr, category cg WHERE cgr.category_id = cg.id;

[SQL_SELECT_ID_CATEGORYRULE]
SELECT id FROM categoryRule WHERE id = ?;

[SQL_INSERT_CATEGORYRULE]
INSERT INTO categoryRule (category_id, filterDateFrom, filterDateTo, filterAmountFrom, filterAmountTo, filterRecipient, filterPurpose, filterRecipientIsRegex, filterPurposeIsRegex, joinType, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

[SQL_SELECT_ALL_CATEGORYRULE_BANKACCOUNT]
SELECT id, category_id, account_id, updatedAt FROM categoryRule_bankAccount;

[SQL_INSERT_CATEGORYRULE_BANKACCOUNT]
INSERT INTO categoryRule_bankAccount (category_id, account_id, updatedAt) VALUES (?, ?, ?);

[SQL_UPDATE_CATEGORYRULE]
UPDATE categoryRule 
SET category_id = ?, filterDateFrom = ?, filterDateTo = ?, filterAmountFrom = ?, filterAmountTo = ?, filterRecipient = ?, filterPurpose = ?, filterRecipientIsRegex = ?, filterPurposeIsRegex = ?, joinType = ?, updatedAt = ? 
WHERE id = ?;

[SQL_UPDATE_CATEGORYRULE_BANKACCOUNT]
UPDATE categoryRule_bankAccount SET category_id = ?, account_id = ?, updatedAt = ? WHERE id = ?;
