[INSTITUTE_FIELD_LIST]
importNumber, blz, bic, bankName, place, 
dataCenter, organisation, hbciDns, hbciIp, hbciVersion, ddv, rdh1, rdh2, rdh3, rdh4, rdh5, rdh6, rdh7, rdh8, rdh9, rdh10, pinUrl, version, lastChanged, 
datasetNumber, feature, postcode, bankNameShort, pan, checkdigitMethod, featureChange, blzDeletion, blzSuccession, 
country, address, readinessDate, schemeLeavingDate, schemeOptions,
serviceSct, serviceCor, serviceCor1, serviceB2b, serviceScc,
stateType, importFile, ih.importFileName;

[SQL_SELECT_ALL_INSTITUTES_BASE]
SELECT i.id, ${INSTITUTE_FIELD_LIST}, i.updatedAt
FROM institute_db.institute i
LEFT JOIN institute_db.instituteDk idk ON idk.institute_id = i.id
LEFT JOIN institute_db.instituteDbb idbb ON idbb.institute_id = i.id
LEFT JOIN institute_db.instituteEpc iepc ON iepc.institute_id = i.id
LEFT JOIN institute_db.instituteDbbReachable idr ON idr.institute_id = i.id
LEFT JOIN institute_db.importHistory ih ON ih.id = i.importFile;

[SQL_SELECT_ALL_INSTITUTES]
${SQL_SELECT_ALL_INSTITUTES_BASE}
ORDER BY i.id, idk.importNumber ASC;

[SQL_INSERT_INSTIUTE]
INSERT INTO institute_db.institute ( blz, bic, bankName, place, stateType, importFile, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?);

[SQL_INSERT_INSTITUTE_BATCH]
INSERT INTO institute_db.institute (id, blz, bic, bankName, place, stateType, importFile, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

[SQL_SELECT_MAX_INSTITUTE_ID]
SELECT COALESCE(MAX(id), 0) FROM institute_db.institute;

[SQL_INSERT_INSTITUTE_DK]
INSERT INTO institute_db.instituteDk (institute_id, importNumber, dataCenter, organisation, hbciDns, hbciIp, hbciVersion, ddv, rdh1, rdh2, rdh3, rdh4, rdh5, rdh6, rdh7, rdh8, rdh9, rdh10, pinUrl, version, lastChanged, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
;

[SQL_INSERT_INSTITUTE_DBB]
INSERT INTO institute_db.instituteDbb (institute_id, datasetNumber, feature, postcode, bankNameShort, pan, checkdigitMethod, featureChange, blzDeletion, blzSuccession, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
;
    
[SQL_INSERT_INSTITUTE_EPC]
INSERT INTO institute_db.instituteEpc (institute_id, country, address, readinessDate, schemeLeavingDate, schemeOptions, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?)
;

[SQL_INSERT_INSTITUTE_DBB_REACHABLE]
INSERT INTO institute_db.instituteDbbReachable (institute_id, serviceSct, serviceCor, serviceCor1, serviceB2b, serviceScc, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(institute_id) DO UPDATE SET
    serviceSct = excluded.serviceSct,
    serviceCor = excluded.serviceCor,
    serviceCor1 = excluded.serviceCor1,
    serviceB2b = excluded.serviceB2b,
    serviceScc = excluded.serviceScc,
    updatedAt = excluded.updatedAt;

[SQL_UPDATE_INSTIUTE]
UPDATE institute_db.institute SET blz = ?, bic = ?, bankName = ?, place = ?, stateType = ?, importFile = ?, updatedAt = ?
WHERE id = ?;

[SQL_UPDATE_INSTITUTE_DK]
UPDATE institute_db.instituteDk SET institute_id = ?, importNumber = ?, dataCenter = ?, organisation = ?, hbciDns = ?, hbciIp = ?, hbciVersion = ?, ddv = ?, rdh1 = ?, rdh2 = ?, rdh3 = ?, rdh4 = ?, rdh5 = ?, rdh6 = ?, rdh7 = ?, rdh8 = ?, rdh9 = ?, rdh10 = ?, pinUrl = ?, version = ?, lastChanged = ?, updatedAt = ?
WHERE institute_id = ?;

[SQL_UPDATE_INSTITUTE_DBB]
UPDATE institute_db.instituteDbb SET institute_id = ?, datasetNumber = ?, feature = ?, postcode = ?, bankNameShort = ?, pan = ?,  checkdigitMethod = ?, featureChange = ?,  blzDeletion = ?, blzSuccession = ?, updatedAt = ?
WHERE institute_id = ?;

[SQL_UPDATE_INSTITUTE_EPC]
UPDATE institute_db.instituteEpc SET institute_id = ?, country = ?, address = ?, readinessDate = ?, schemeLeavingDate = ?, schemeOptions = ?, updatedAt = ?
WHERE institute_id = ?;

[SQL_UPDATE_INSTITUTE_DBB_REACHABLE]
UPDATE institute_db.instituteDbbReachable SET institute_id = ?, serviceSct = ?, serviceCor = ?, serviceCor1 = ?, serviceB2b = ?, serviceScc = ?, updatedAt = ?
WHERE institute_id = ?;

[SQL_DELETE_INSTITUTE_DK]
DELETE FROM institute_db.instituteDk WHERE institute_id = ?;

[SQL_DELETE_INSTITUTE_DBB]
DELETE FROM institute_db.instituteDbb WHERE institute_id = ?;

[SQL_DELETE_INSTITUTE_EPC]
DELETE FROM institute_db.instituteEpc WHERE institute_id = ?;

[SQL_DELETE_INSTITUTE_DBB_REACHABLE]
DELETE FROM institute_db.instituteDbbReachable WHERE institute_id = ?;
