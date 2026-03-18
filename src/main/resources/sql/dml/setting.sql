[SETTING_FIELD_LIST]
attribute, `value`, dataType, editable, visible, comment, updatedAt;

[SQL_SELECT_ALL_SETTINGS]
SELECT id, ${SETTING_FIELD_LIST} FROM setting;

[SQL_INSERT_SETTING]
INSERT INTO setting ( ${SETTING_FIELD_LIST}) 
VALUES (?, ?, ?, ?, ?, ?, ?);

[SQL_SELECT_ID_SETTING_BY_ID]
SELECT id FROM setting WHERE id = ?;

[SQL_SELECT_ID_SETTING_BY_ATTRIBUTE]
SELECT id FROM setting WHERE attribute = ?;

[SQL_UPDATE_SETTING]
UPDATE setting SET attribute = ?, `value` = ?, dataType = ?, editable = ?, visible = ?, comment = ?, updatedAt = ? 
WHERE id = ?;
