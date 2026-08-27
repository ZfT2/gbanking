[SQL_ATTACH_INSTITUTE_DATABASE]
ATTACH DATABASE ? AS institute_db;

[SQL_MAIN_QUICK_CHECK]
PRAGMA main.quick_check;

[SQL_MAIN_INTEGRITY_CHECK]
PRAGMA main.integrity_check;

[SQL_INSTITUTE_QUICK_CHECK]
PRAGMA institute_db.quick_check;

[SQL_INSTITUTE_INTEGRITY_CHECK]
PRAGMA institute_db.integrity_check;

[SQL_MAIN_FOREIGN_KEY_CHECK]
PRAGMA main.foreign_key_check;

[SQL_INSTITUTE_FOREIGN_KEY_CHECK]
PRAGMA institute_db.foreign_key_check;

[SQL_MAIN_CHECKPOINT]
PRAGMA main.wal_checkpoint(TRUNCATE);

[SQL_MAIN_MMAP_SIZE]
PRAGMA main.mmap_size = 268435456;

[SQL_INSTITUTE_MMAP_SIZE]
PRAGMA institute_db.mmap_size = 268435456;

[SQL_OPTIMIZE_ON_OPEN]
PRAGMA main.optimize = 0x10002;

[SQL_OPTIMIZE]
PRAGMA main.optimize;

[SQL_READ_FOREIGN_KEYS]
PRAGMA foreign_keys;

[SQL_READ_INSTITUTE_DATABASE_VERSION]
SELECT MAX(updatedAt) FROM importHistory;

[SQL_IS_INSTITUTE_SCHEMA_COMPLETE]
SELECT COUNT(*) = 11
FROM institute_db.sqlite_master
WHERE (type = 'table' AND name IN (
  'instituteStatus',
  'importHistory',
  'institute',
  'instituteDk',
  'instituteDbb',
  'instituteEpc',
  'instituteDbbReachable'))
OR (type = 'trigger' AND name IN (
  'instituteDk_unique_blz_importNumber_insert',
  'instituteDk_unique_blz_importNumber_update',
  'instituteDbb_unique_blz_datasetNumber_insert',
  'instituteDbb_unique_blz_datasetNumber_update'));
