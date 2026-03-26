[INSTITUTE_FIELD_LIST]
importNumber, blz, bic, bankName, place, dataCenter, organisation, hbciDns, hbciIp, hbciVersion, ddv, rdh1, rdh2, rdh3, rdh4, rdh5, rdh6, rdh7, rdh8, rdh9, rdh10, pinUrl, version, lastChanged, stateType, updatedAt;

[SQL_SELECT_ALL_INSTITUTES]
SELECT id, ${INSTITUTE_FIELD_LIST} 
FROM institute;

[SQL_INSERT_INSTIUTE]
INSERT INTO institute ( ${INSTITUTE_FIELD_LIST}) 
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

[SQL_UPDATE_INSTIUTE]
UPDATE institute SET importNumber = ?, blz = ?, bic = ?, bankName = ?, place = ?, dataCenter = ?, organisation = ?, hbciDns = ?, hbciIp = ?, hbciVersion = ?, ddv = ?, rdh1 = ?, rdh2 = ?, rdh3 = ?, rdh4 = ?, rdh5 = ?, rdh6 = ?, rdh7 = ?, rdh8 = ?, rdh9 = ?, rdh10 = ?, pinUrl = ?, version = ?, lastChanged = ?, stateType = ?, updatedAt = ? 
WHERE id = ?;
