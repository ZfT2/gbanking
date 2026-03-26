[SQL_SELECT_ALL_BUSINESSCASES]
SELECT id, caseValue, updatedAt FROM businessCase

;

[SQL_INSERT_BUSINESSCASE]
INSERT INTO businessCase (caseValue, updatedAt) VALUES (?,?)
;
