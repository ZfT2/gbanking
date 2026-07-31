[INSTITUTE_FIELD_LIST]
importNumber, blz, bic, bankName, place, 
dataCenter, organisation, hbciDns, hbciIp, hbciVersion, ddv, rdh1, rdh2, rdh3, rdh4, rdh5, rdh6, rdh7, rdh8, rdh9, rdh10, pinUrl, version, lastChanged, 
datasetNumber, feature, postcode, bankNameShort, pan, checkdigitMethod, featureChange, blzDeletion, blzSuccession, 
country, address, readinessDate, schemeLeavingDate, schemeOptions,
stateType, importFile, ih.importFileName;

[SQL_SELECT_ALL_INSTITUTES]
SELECT i.id, ${INSTITUTE_FIELD_LIST}, i.updatedAt
FROM institute_db.institute i
LEFT JOIN institute_db.instituteDk idk ON idk.institute_id = i.id
LEFT JOIN institute_db.instituteDbb idbb ON idbb.institute_id = i.id
LEFT JOIN institute_db.instituteEpc iepc ON iepc.institute_id = i.id
LEFT JOIN institute_db.importHistory ih ON ih.id = i.importFile
ORDER BY i.id, idk.importNumber ASC;

[SQL_INSERT_INSTIUTE]
INSERT INTO institute_db.institute ( blz, bic, bankName, place, stateType, importFile, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?);

[SQL_INSERT_INSTITUTE_DK]
INSERT INTO instituteDk (institute_id, importNumber, dataCenter, organisation, hbciDns, hbciIp, hbciVersion, ddv, rdh1, rdh2, rdh3, rdh4, rdh5, rdh6, rdh7, rdh8, rdh9, rdh10, pinUrl, version, lastChanged, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(institute_id) DO UPDATE SET
    importNumber = excluded.importNumber,
    dataCenter = excluded.dataCenter,
    organisation = excluded.organisation,
    hbciDns = excluded.hbciDns,
    hbciIp = excluded.hbciIp,
    hbciVersion = excluded.hbciVersion,
    ddv = excluded.ddv,
    rdh1 = excluded.rdh1,
    rdh2 = excluded.rdh2,
    rdh3 = excluded.rdh3,
    rdh4 = excluded.rdh4,
    rdh5 = excluded.rdh5,
    rdh6 = excluded.rdh6,
    rdh7 = excluded.rdh7,
    rdh8 = excluded.rdh8,
    rdh9 = excluded.rdh9,
    rdh10 = excluded.rdh10,
    pinUrl = excluded.pinUrl,
    version = excluded.version,
    lastChanged = excluded.lastChanged,
    updatedAt = excluded.updatedAt;

[SQL_INSERT_INSTITUTE_DBB]
INSERT INTO instituteDbb (institute_id, datasetNumber, feature, postcode, bankNameShort, pan, checkdigitMethod, featureChange, blzDeletion, blzSuccession, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(institute_id) DO UPDATE SET
    datasetNumber = excluded.datasetNumber,
    feature = excluded.feature,
    postcode = excluded.postcode,
    bankNameShort = excluded.bankNameShort,
    pan = excluded.pan,
    checkdigitMethod = excluded.checkdigitMethod,
    featureChange = excluded.featureChange,
    blzDeletion = excluded.blzDeletion,
    blzSuccession = excluded.blzSuccession,
    updatedAt = excluded.updatedAt;
    
[SQL_INSERT_INSTITUTE_EPC]
INSERT INTO instituteEpc (institute_id, country, address, readinessDate, schemeLeavingDate, schemeOptions, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(institute_id) DO UPDATE SET
    country = excluded.country,
    address = excluded.address,
    readinessDate = excluded.readinessDate,
    schemeLeavingDate = excluded.schemeLeavingDate,
    schemeOptions = excluded.schemeOptions,
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

[SQL_DELETE_INSTITUTE_DK]
DELETE FROM instituteDk WHERE institute_id = ?;

[SQL_DELETE_INSTITUTE_DBB]
DELETE FROM instituteDbb WHERE institute_id = ?;

[SQL_DELETE_INSTITUTE_EPC]
DELETE FROM instituteEpc WHERE institute_id = ?;
