[SQL_SELECT_ALL_CATEGORYRULES_FULL]
SELECT cgr.id, cgr.name AS ruleName, cgr.category_id, cgr.filterDateFrom, cgr.filterDateTo, cgr.filterAmountFrom, cgr.filterAmountTo, cgr.filterRecipientName, cgr.filterRecipientIban, cgr.filterRecipientAccountNumber, cgr.filterPurpose, cgr.filterRecipientIsRegex, cgr.filterPurposeIsRegex, cgr.joinType, cgr.updatedAt, cg.id AS categoryFull_id, cg.name AS categoryName, cg.fullName FROM categoryRule cgr, categoryFull cg WHERE cgr.category_id = cg.id;

[SQL_SELECT_ID_CATEGORYRULE]
SELECT id FROM categoryRule WHERE id = ?;

[SQL_INSERT_CATEGORYRULE]
INSERT INTO categoryRule (name, category_id, filterDateFrom, filterDateTo, filterAmountFrom, filterAmountTo, filterRecipientName, filterRecipientIban, filterRecipientAccountNumber, filterPurpose, filterRecipientIsRegex, filterPurposeIsRegex, joinType, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

[SQL_INSERT_CATEGORYRULE_BANKACCOUNT]
INSERT OR IGNORE INTO categoryRule_bankAccount (categoryRule_id, account_id, updatedAt) VALUES (?, ?, ?);

[SQL_SELECT_BANKACCOUNTS_BY_CATEGORYRULE]
SELECT ba.* FROM bankAccount ba, categoryRule_bankAccount crba WHERE ba.id = crba.account_id AND crba.categoryRule_id = ?;

[SQL_DELETE_CATEGORYRULE_BANKACCOUNT]
DELETE FROM categoryRule_bankAccount WHERE categoryRule_id = ?;

[SQL_UPDATE_CATEGORYRULE]
UPDATE categoryRule 
SET name = ?, category_id = ?, filterDateFrom = ?, filterDateTo = ?, filterAmountFrom = ?, filterAmountTo = ?, filterRecipientName = ?, filterRecipientIban = ?, filterRecipientAccountNumber = ?, filterPurpose = ?, filterRecipientIsRegex = ?, filterPurposeIsRegex = ?, joinType = ?, updatedAt = ?
WHERE id = ?;

