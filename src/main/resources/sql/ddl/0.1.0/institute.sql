[SQL_SETUP_DROP_INSTITUTE]
DROP TABLE IF EXISTS institute_db.institute;

;

[SQL_SETUP_DROP_UNIQUE_INDEX_INSTITUTE]
DROP INDEX IF EXISTS institute_db.uk_institute_blz_importnr_current;

;

[SQL_SETUP_DROP_INSTITUTE_STATUS]
DROP TABLE IF EXISTS institute_db.instituteStatus;

;

[SQL_SETUP_CREATE_INSTITUTE_STATUS]
CREATE TABLE IF NOT EXISTS institute_db.instituteStatus (
  id INTEGER PRIMARY KEY,
  stateName TEXT NOT NULL UNIQUE,
  description TEXT NOT NULL,
  updatedAt TEXT NOT NULL);

;

[SQL_SETUP_INSERT_INSTITUTE_STATUS]
INSERT INTO institute_db.instituteStatus (id, stateName, description, updatedAt)
VALUES
  (1, 'ACTIVE', 'aktiv', datetime()),
  (2, 'DUPLICATE', 'Dublette', datetime()),
  (3, 'ARCHIVED', 'archiviert', datetime())
ON CONFLICT(id) DO UPDATE SET
  stateName = excluded.stateName,
  description = excluded.description,
  updatedAt = excluded.updatedAt
WHERE instituteStatus.stateName <> excluded.stateName
   OR instituteStatus.description <> excluded.description;

;

[SQL_SETUP_CREATE_IMPORT_HISTORY]
CREATE TABLE IF NOT EXISTS institute_db.importHistory (
  id INTEGER PRIMARY KEY,
  importFileName TEXT NOT NULL,
  updatedAt TEXT NOT NULL);

;

[SQL_SETUP_CREATE_INSTITUTE]
CREATE TABLE IF NOT EXISTS institute_db.institute (
  id INTEGER PRIMARY KEY,
  blz TEXT,
  bic TEXT,
  bankName TEXT NOT NULL,
  place TEXT,
  stateType INTEGER NOT NULL,
  importFile INTEGER NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY (stateType) REFERENCES instituteStatus(id),
  FOREIGN KEY (importFile) REFERENCES importHistory(id),
  CHECK (blz IS NOT NULL OR bic IS NOT NULL)
  CHECK (stateType BETWEEN 1 AND 3));
;

[SQL_SETUP_CREATE_INSTITUTE_DK]
CREATE TABLE IF NOT EXISTS institute_db.instituteDk (
  id INTEGER PRIMARY KEY,
  institute_id INTEGER NOT NULL UNIQUE,
  importNumber INTEGER NOT NULL,
  dataCenter TEXT,
  organisation TEXT,
  hbciDns TEXT,
  hbciIp TEXT,
  hbciVersion REAL,
  ddv TEXT,
  rdh1 INTEGER,
  rdh2 INTEGER,
  rdh3 INTEGER,
  rdh4 INTEGER,
  rdh5 INTEGER,
  rdh6 INTEGER,
  rdh7 INTEGER,
  rdh8 INTEGER,
  rdh9 INTEGER,
  rdh10 INTEGER,
  pinUrl TEXT,
  version TEXT,
  lastChanged TEXT,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(institute_id) REFERENCES institute(id) ON DELETE CASCADE,
  CHECK (rdh1 IS NULL OR rdh1 IN (0, 1)),
  CHECK (rdh2 IS NULL OR rdh2 IN (0, 1)),
  CHECK (rdh3 IS NULL OR rdh3 IN (0, 1)),
  CHECK (rdh4 IS NULL OR rdh4 IN (0, 1)),
  CHECK (rdh5 IS NULL OR rdh5 IN (0, 1)),
  CHECK (rdh6 IS NULL OR rdh6 IN (0, 1)),
  CHECK (rdh7 IS NULL OR rdh7 IN (0, 1)),
  CHECK (rdh8 IS NULL OR rdh8 IN (0, 1)),
  CHECK (rdh9 IS NULL OR rdh9 IN (0, 1)),
  CHECK (rdh10 IS NULL OR rdh10 IN (0, 1)));
;

[SQL_SETUP_CREATE_INSTITUTE_DBB]
CREATE TABLE IF NOT EXISTS institute_db.instituteDbb (
  id INTEGER PRIMARY KEY,
  institute_id INTEGER NOT NULL UNIQUE,
  datasetNumber TEXT NOT NULL,
  feature INTEGER,
  postcode TEXT,
  bankNameShort TEXT,
  pan TEXT,
  checkdigitMethod INTEGER,
  featureChange CHAR,
  blzDeletion INTEGER,
  blzSuccession TEXT,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(institute_id) REFERENCES institute(id) ON DELETE CASCADE);
;

[SQL_SETUP_CREATE_INSTITUTE_EPC]
CREATE TABLE IF NOT EXISTS institute_db.instituteEpc (
  id INTEGER PRIMARY KEY,
  institute_id INTEGER NOT NULL UNIQUE,
  country TEXT NOT NULL,
  address TEXT,
  readinessDate TEXT,
  schemeLeavingDate TEXT,
  schemeOptions TEXT,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(institute_id) REFERENCES institute(id) ON DELETE CASCADE);
;

[SQL_SETUP_CREATE_INSTITUTE_DBB_REACHABLE]
CREATE TABLE IF NOT EXISTS institute_db.instituteDbbReachable (
  id INTEGER PRIMARY KEY,
  institute_id INTEGER NOT NULL UNIQUE,
  serviceSct INTEGER NOT NULL,
  serviceCor INTEGER NOT NULL,
  serviceCor1 INTEGER NOT NULL,
  serviceB2b INTEGER NOT NULL,
  serviceScc INTEGER NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(institute_id) REFERENCES institute(id) ON DELETE CASCADE,
  CHECK (serviceSct IN (0, 1)),
  CHECK (serviceCor IN (0, 1)),
  CHECK (serviceCor1 IN (0, 1)),
  CHECK (serviceB2b IN (0, 1)),
  CHECK (serviceScc IN (0, 1)));
;

[SQL_SETUP_CREATE_INSTITUTE_ADDITIONAL]
CREATE TABLE IF NOT EXISTS institute_db.instituteAdditional (
  id INTEGER PRIMARY KEY,
  institute_id INTEGER NOT NULL UNIQUE,
  bankNameShort TEXT,
  checkdigitMethod TEXT,
  postcode TEXT,
  deletionMarker TEXT,
  blzSuccession TEXT,
  ibanRule TEXT,
  ibanRuleVersion TEXT,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(institute_id) REFERENCES institute(id) ON DELETE CASCADE);
;

[SQL_SETUP_CREATE_TRIGGER_INSTITUTEDK_VALIDATE_UNIQUE_BLZ_IMPORTNUMBER_INSERT]
CREATE TRIGGER IF NOT EXISTS institute_db.instituteDk_unique_blz_importNumber_insert
BEFORE INSERT ON instituteDk
BEGIN
  SELECT RAISE(FAIL, 'InstituteDk (INSERT): Duplicate BLZ/importNumber')
  WHERE EXISTS (
    SELECT 1
    FROM instituteDk dk
    JOIN institute iExisting ON iExisting.id = dk.institute_id
    JOIN institute iNew ON iNew.id = NEW.institute_id
    WHERE iExisting.blz = iNew.blz
    AND iExisting.stateType IN (1, 2)
    AND dk.importNumber = NEW.importNumber
  );
END;
;

[SQL_SETUP_CREATE_TRIGGER_INSTITUTEDK_VALIDATE_UNIQUE_BLZ_IMPORTNUMBER_UPDATE]
CREATE TRIGGER IF NOT EXISTS institute_db.instituteDk_unique_blz_importNumber_update
BEFORE UPDATE OF institute_id, importNumber ON instituteDk
BEGIN
  SELECT RAISE(FAIL, 'InstituteDk (UPDATE):  Duplicate BLZ/importNumber')
  WHERE EXISTS (
    SELECT 1
    FROM instituteDk dk
    JOIN institute iExisting ON iExisting.id = dk.institute_id
    JOIN institute iNew ON iNew.id = NEW.institute_id
    WHERE dk.id <> NEW.id
    AND iExisting.blz = iNew.blz
    AND iExisting.stateType IN (1, 2)
    AND dk.importNumber = NEW.importNumber
  );
END;
;

--[SQL_SETUP_CREATE_TRIGGER_INSTITUTEDK_VALIDATE_UNIQUE_BLZ_IMPORTNUMBER_UPDATE]
--CREATE TRIGGER IF NOT EXISTS institute_db.instituteDk_unique_blz_importNumber_update
--BEFORE UPDATE OF institute_id, importNumber ON instituteDk
--BEGIN
--  SELECT RAISE(
--    FAIL,
--    'InstituteDk (UPDATE): Duplicate BLZ/importNumber'
--    || ' BLZ=' || iExisting.blz
--    || ', importNumber=' || dk.importNumber
--    || ', existing.id=' || dk.id
--    || ', existing.institute_id=' || dk.institute_id
--    || ', new.id=' || NEW.id
--    || ', new.institute_id=' || NEW.institute_id
--    || ', new.importNumber=' || NEW.importNumber
--  )
--  FROM instituteDk dk
--  JOIN institute iExisting ON iExisting.id = dk.institute_id
--  JOIN institute iNew      ON iNew.id = NEW.institute_id
--  WHERE dk.id <> NEW.id
--    AND iExisting.blz = iNew.blz
--    AND iExisting.stateType IN (1, 2)
--    AND dk.importNumber = NEW.importNumber;
--END;
--;

[SQL_SETUP_CREATE_TRIGGER_INSTITUTEDBB_VALIDATE_UNIQUE_BLZ_DATASETNUMBER_INSERT]
CREATE TRIGGER IF NOT EXISTS institute_db.instituteDbb_unique_blz_datasetNumber_insert
BEFORE INSERT ON instituteDbb
BEGIN
  SELECT RAISE(FAIL, 'InstituteDbb (INSERT): Duplicate BLZ/datasetNumber')
  WHERE EXISTS (
    SELECT 1
    FROM instituteDbb dbb
    JOIN institute iExisting ON iExisting.id = dbb.institute_id
    JOIN institute iNew ON iNew.id = NEW.institute_id
    WHERE iExisting.blz = iNew.blz
    AND iExisting.stateType IN (1, 2)
    AND dbb.datasetNumber = NEW.datasetNumber
  );
END;
;

[SQL_SETUP_CREATE_TRIGGER_INSTITUTEDBB_VALIDATE_UNIQUE_BLZ_DATASETNUMBER_UPDATE]
CREATE TRIGGER IF NOT EXISTS institute_db.instituteDbb_unique_blz_datasetNumber_update
BEFORE UPDATE OF institute_id, datasetNumber ON instituteDbb
BEGIN
  SELECT RAISE(FAIL, 'InstituteDbb (UPDATE): Duplicate BLZ/datasetNumber')
  WHERE EXISTS (
    SELECT 1
    FROM instituteDbb dbb
    JOIN institute iExisting ON iExisting.id = dbb.institute_id
    JOIN institute iNew ON iNew.id = NEW.institute_id
    WHERE dbb.id <> NEW.id
    AND iExisting.blz = iNew.blz
    AND iExisting.stateType IN (1, 2)
    AND dbb.datasetNumber = NEW.datasetNumber
  );
END;
;
