[IMPORT_HISTORY_FIELD_LIST]
importFileName, updatedAt;

[SQL_SELECT_ALL_IMPORT_HISTORY]
SELECT id, ${IMPORT_HISTORY_FIELD_LIST}
FROM institute_db.importHistory;

[SQL_SELECT_ID_IMPORT_HISTORY_BY_ID]
SELECT id FROM institute_db.importHistory WHERE id = ?;

[SQL_INSERT_IMPORT_HISTORY]
INSERT INTO institute_db.importHistory ( ${IMPORT_HISTORY_FIELD_LIST})
VALUES (?, ?);

[SQL_UPDATE_IMPORT_HISTORY]
UPDATE institute_db.importHistory SET importFileName = ?, updatedAt = ?
WHERE id = ?;
