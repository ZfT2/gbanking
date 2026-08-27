# Reproducible scale-up based on the regular demo data. This file is used only by
# the opt-in DbIndexBenchmarkTest and is never installed into an application database.

[SQL_BENCHMARK_SCALE_BANK_ACCESSES]
WITH RECURSIVE sequence(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM sequence WHERE n < 25
)
INSERT INTO bankAccess (
    id, bankName, active, accessType, updatedAt
)
SELECT 100000 + n, 'Benchmark Bank ' || n, 1, 1, '2026-01-01 00:00:00.000'
FROM sequence;

[SQL_BENCHMARK_SCALE_BANK_ACCESS_FINTS]
WITH RECURSIVE sequence(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM sequence WHERE n < 25
)
INSERT INTO bankAccessFints (
    bankAccess_id, country, blz, hbciURL, port, userId, customerId, sysId,
    tanProcedure, allowedTwostepMechanisms, hbciVersion, bpdVersion, updVersion,
    hbciFilterType
)
SELECT 100000 + n, 'DE', printf('%08d', n), NULL, 443,
       'benchmark.user.' || n, NULL, NULL, 6, NULL, '300', '1', '1', 2
FROM sequence;

[SQL_BENCHMARK_SCALE_BANK_ACCOUNTS]
WITH RECURSIVE sequence(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM sequence WHERE n < 75
)
INSERT INTO bankAccount (
    id, bankAccess_id, parentAccount_id, providerAccountId, accountName, baseCurrency, accountType, accountSource,
    iban, number, isSEPAAccount, isOfflineAccount, accountState, balance, createdAt, updatedAt
)
SELECT 110000 + n, 100001 + ((n - 1) % 25), CASE WHEN n = 2 THEN 110001 END,
       'benchmark-provider-' || n, 'Benchmark Account ' || n, 1, 1, 1, 'DEBENCH' || printf('%014d', n),
       'BENCH' || printf('%010d', n), 1, 0, 1, 0,
       '2026-01-01 00:00:00.000', '2026-01-01 00:00:00.000'
FROM sequence;

[SQL_BENCHMARK_SCALE_RECIPIENTS]
WITH RECURSIVE thousands(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM thousands WHERE n < 1000
), forties(n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1 FROM forties WHERE n < 39
)
INSERT INTO recipient (id, name, iban, source, isDefault, updatedAt)
SELECT 200000 + forties.n * 1000 + thousands.n,
       'Benchmark Recipient ' || (forties.n * 1000 + thousands.n),
       CASE WHEN forties.n * 1000 + thousands.n <= 100
            THEN 'DEBENCHMARKTARGET'
            ELSE 'DEOTHER' || printf('%014d', forties.n * 1000 + thousands.n) END,
       1, 0, '2026-01-01 00:00:00.000'
FROM forties CROSS JOIN thousands;

[SQL_BENCHMARK_SCALE_CATEGORY_RULES]
WITH RECURSIVE sequence(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM sequence WHERE n < 2000
)
INSERT INTO categoryRule (
    id, name, category_id, filterRecipientIsRegex, filterPurposeIsRegex, joinType, updatedAt
)
SELECT 400000 + n, 'Benchmark Rule ' || n, 920021, 0, 0, 1,
       '2026-01-01 00:00:00.000'
FROM sequence;

[SQL_BENCHMARK_SCALE_BOOKINGS]
WITH RECURSIVE thousands(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM thousands WHERE n < 1000
), hundreds(n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1 FROM hundreds WHERE n < 299
), rows(sequenceNumber) AS (
    SELECT hundreds.n * 1000 + thousands.n FROM hundreds CROSS JOIN thousands
)
INSERT INTO booking (
    id, account_id, parentBooking_id, dateBooking, dateValue, purpose, amount,
    bookingType, bookingSource, recipient_id, category_id, updatedAt
)
SELECT 1000000 + sequenceNumber, 110001 + ((sequenceNumber - 1) % 75), NULL,
       strftime('%Y-%m-%d 00:00:00.000', '2019-01-01', printf('+%d days', sequenceNumber % 2500)),
       strftime('%Y-%m-%d 00:00:00.000', '2019-01-01', printf('+%d days', sequenceNumber % 2500)),
       'Benchmark Booking ' || sequenceNumber,
       ROUND(((sequenceNumber % 10000) + 1) / 100.0, 2), 1,
       CASE WHEN sequenceNumber % 10 = 0 THEN 7 ELSE 1 END,
       200001 + ((sequenceNumber - 1) % 40000),
       CASE WHEN sequenceNumber % 3 = 0 THEN 920021 END,
       '2026-01-01 00:00:00.000'
FROM rows;

[SQL_BENCHMARK_SCALE_SPLIT_BOOKINGS]
WITH RECURSIVE sequence(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM sequence WHERE n < 6000
)
INSERT INTO booking (
    id, account_id, parentBooking_id, dateBooking, dateValue, purpose, amount,
    bookingType, bookingSource, recipient_id, updatedAt
)
SELECT 1400000 + n, 110001 + ((n - 1) % 75), 1000000 + n,
       '2026-01-01 00:00:00.000', '2026-01-01 00:00:00.000',
       'Benchmark Split ' || n, 1.00, 1, 5,
       200001 + ((n - 1) % 40000), '2026-01-01 00:00:00.000'
FROM sequence;

[SQL_BENCHMARK_SCALE_CROSS_BOOKINGS]
WITH RECURSIVE sequence(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM sequence WHERE n < 3000
)
INSERT INTO booking (
    id, account_id, parentBooking_id, dateBooking, dateValue, purpose, amount,
    bookingType, bookingSource, crossAccount_id, crossBooking_id, updatedAt
)
SELECT 1500000 + n, 110001 + ((n - 1) % 75), NULL,
       '2026-01-01 00:00:00.000', '2026-01-01 00:00:00.000',
       'Benchmark Cross ' || n, 1.00, 5, 5,
       110001 + ((n - 1) % 75), 1000000 + n,
       '2026-01-01 00:00:00.000'
FROM sequence;

[SQL_BENCHMARK_SCALE_BOOKING_CATEGORY_RELATIONS]
WITH RECURSIVE thousands(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM thousands WHERE n < 1000
), hundreds(n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1 FROM hundreds WHERE n < 99
), rows(sequenceNumber) AS (
    SELECT hundreds.n * 1000 + thousands.n FROM hundreds CROSS JOIN thousands
)
INSERT INTO booking_category (id, booking_id, category_id, categoryRuleMode, updatedAt)
SELECT 1600000 + sequenceNumber, 1000000 + sequenceNumber,
       CASE sequenceNumber % 5
           WHEN 0 THEN 920021 WHEN 1 THEN 920022 WHEN 2 THEN 920031
           WHEN 3 THEN 920041 ELSE 920050 END,
       1, '2026-01-01 00:00:00.000'
FROM rows;

[SQL_BENCHMARK_SCALE_CATEGORY_RULE_RELATIONS]
WITH RECURSIVE accounts(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM accounts WHERE n < 75
), rules(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM rules WHERE n < 2000
)
INSERT INTO categoryRule_bankAccount (categoryRule_id, account_id, updatedAt)
SELECT 400000 + rules.n, 110000 + accounts.n, '2026-01-01 00:00:00.000'
FROM accounts CROSS JOIN rules;

[SQL_BENCHMARK_SCALE_MONEY_TRANSFERS]
WITH RECURSIVE thousands(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM thousands WHERE n < 1000
), fifties(n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1 FROM fifties WHERE n < 49
), rows(sequenceNumber) AS (
    SELECT fifties.n * 1000 + thousands.n FROM fifties CROSS JOIN thousands
)
INSERT INTO moneytransfer (
    id, account_id, moneytransferType, recipient_id, purpose, amount,
    moneytransferStatus, historyorder_id, updatedAt
)
SELECT 500000 + sequenceNumber, 110001 + ((sequenceNumber - 1) % 75), 1,
       200001 + ((sequenceNumber - 1) % 40000), 'Benchmark Transfer ' || sequenceNumber,
       1.00 + (sequenceNumber % 1000), 1 + (sequenceNumber % 5),
       CASE WHEN sequenceNumber > 1 AND sequenceNumber % 100 = 0
            THEN 500000 + sequenceNumber - 1 END,
       '2026-01-01 00:00:00.000'
FROM rows;

[SQL_BENCHMARK_SCALE_MONEY_TRANSFER_PROTOCOLS]
WITH RECURSIVE thousands(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM thousands WHERE n < 1000
), hundreds(n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1 FROM hundreds WHERE n < 99
), rows(sequenceNumber) AS (
    SELECT hundreds.n * 1000 + thousands.n FROM hundreds CROSS JOIN thousands
)
INSERT INTO moneytransferProtocol (
    id, moneytransfer_id, moneytransferStatus, timeStart, protocolText, updatedAt
)
SELECT 600000 + sequenceNumber, 500001 + ((sequenceNumber - 1) % 50000),
       1, strftime('%Y-%m-%d 00:00:00.000', '2020-01-01', printf('+%d seconds', sequenceNumber)),
       'Benchmark Protocol ' || sequenceNumber, '2026-01-01 00:00:00.000'
FROM rows;

[SQL_BENCHMARK_SCALE_PARAMETER_DATA]
WITH RECURSIVE thousands(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM thousands WHERE n < 1000
), hundreds(n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1 FROM hundreds WHERE n < 99
), rows(sequenceNumber) AS (
    SELECT hundreds.n * 1000 + thousands.n FROM hundreds CROSS JOIN thousands
)
INSERT INTO parameterData (id, pdKey, pdType, updatedAt)
SELECT 1900000 + sequenceNumber, 'Benchmark.Parameter.' || sequenceNumber,
       1 + (sequenceNumber % 2), '2026-01-01 00:00:00.000'
FROM rows;

[SQL_BENCHMARK_SCALE_PARAMETER_DATA_RELATIONS]
WITH RECURSIVE thousands(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM thousands WHERE n < 1000
), hundreds(n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1 FROM hundreds WHERE n < 99
), rows(sequenceNumber) AS (
    SELECT hundreds.n * 1000 + thousands.n FROM hundreds CROSS JOIN thousands
)
INSERT INTO bankAccess_parameterData (
    id, bankAccess_id, parameterData_id, pdValue, updatedAt
)
SELECT 2100000 + sequenceNumber, 100001 + ((sequenceNumber - 1) % 25),
       1900000 + sequenceNumber, 'Benchmark Value ' || sequenceNumber,
       '2026-01-01 00:00:00.000'
FROM rows;

[SQL_BENCHMARK_LOOKUP_BOOKING_CROSS]
SELECT id FROM booking WHERE crossBooking_id = ?;

[SQL_BENCHMARK_LOOKUP_BOOKING_ACCOUNT_CHILDREN]
SELECT id FROM booking WHERE account_id = ? AND parentBooking_id IS NOT NULL;

[SQL_BENCHMARK_LOOKUP_BOOKING_CATEGORY]
SELECT booking_id FROM booking_category WHERE category_id = ?;

[SQL_BENCHMARK_LOOKUP_CATEGORY_RULE_ACCOUNT]
SELECT categoryRule_id FROM categoryRule_bankAccount WHERE account_id = ?;

[SQL_BENCHMARK_LOOKUP_MONEY_TRANSFER_HISTORY]
SELECT id FROM moneytransfer WHERE historyorder_id = ?;

[SQL_BENCHMARK_DELETE_BANKACCESS_PARAMETER_DATA]
DELETE FROM bankAccess_parameterData WHERE bankAccess_id = ?;

[SQL_BENCHMARK_LOOKUP_BOOKING_AMOUNT_DATE]
SELECT id FROM booking
WHERE account_id = ? AND amount = ? AND dateBooking = ? AND parentBooking_id IS NULL;

[SQL_BENCHMARK_ANALYZE]
ANALYZE;

[SQL_BENCHMARK_SELECT_INDEX_DDL]
SELECT sql FROM sqlite_master WHERE type = 'index' AND name = ?;

[SQL_BENCHMARK_DROP_BOOKING_ACCOUNT]
DROP INDEX IF EXISTS idx_booking_account;

[SQL_BENCHMARK_DROP_BOOKING_AMOUNT_DATE]
DROP INDEX IF EXISTS idx_booking_account_root_amount_date;

[SQL_BENCHMARK_DROP_BOOKING_RECIPIENT_USAGE]
DROP INDEX IF EXISTS idx_booking_recipient_usage;

[SQL_BENCHMARK_DROP_BOOKING_CROSS]
DROP INDEX IF EXISTS idx_booking_cross_booking;

[SQL_BENCHMARK_DROP_BOOKING_CATEGORY]
DROP INDEX IF EXISTS idx_booking_category_category;

[SQL_BENCHMARK_DROP_CATEGORYRULE_BANKACCOUNT]
DROP INDEX IF EXISTS idx_categoryrule_bankaccount_account;

[SQL_BENCHMARK_DROP_MONEYTRANSFER_ACCOUNT_STATUS]
DROP INDEX IF EXISTS idx_moneytransfer_account_status;

[SQL_BENCHMARK_DROP_MONEYTRANSFER_RECIPIENT_USAGE]
DROP INDEX IF EXISTS idx_moneytransfer_recipient_usage;

[SQL_BENCHMARK_DROP_MONEYTRANSFER_HISTORY]
DROP INDEX IF EXISTS idx_moneytransfer_history;

[SQL_BENCHMARK_DROP_MONEYTRANSFER_PROTOCOL]
DROP INDEX IF EXISTS idx_moneytransferprotocol_transfer;

[SQL_BENCHMARK_DROP_BANKACCESS_PARAMETERDATA]
DROP INDEX IF EXISTS idx_bankaccess_parameterdata_parameter;

[SQL_BENCHMARK_ANALYZE_INSTITUTE]
ANALYZE institute_db;

[SQL_BENCHMARK_SCALE_INSTITUTE_DK_PARENTS]
WITH RECURSIVE thousands(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM thousands WHERE n < 1000
), tens(n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1 FROM tens WHERE n < 9
), rows(sequenceNumber) AS (
    SELECT tens.n * 1000 + thousands.n FROM tens CROSS JOIN thousands
)
INSERT INTO institute_db.institute (
    id, blz, bic, bankName, place, stateType, importFile, updatedAt
)
SELECT ? + sequenceNumber, printf('%08d', sequenceNumber % 5000), NULL,
       'DK Benchmark ' || sequenceNumber, NULL, 1,
       (SELECT MIN(id) FROM institute_db.importHistory), '2026-01-01 00:00:00.000'
FROM rows;

[SQL_BENCHMARK_SCALE_INSTITUTE_DK_DETAILS]
WITH RECURSIVE thousands(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM thousands WHERE n < 1000
), tens(n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1 FROM tens WHERE n < 9
), rows(sequenceNumber) AS (
    SELECT tens.n * 1000 + thousands.n FROM tens CROSS JOIN thousands
)
INSERT INTO institute_db.instituteDk (institute_id, importNumber, updatedAt)
SELECT ? + sequenceNumber, sequenceNumber, '2026-01-01 00:00:00.000'
FROM rows;

[SQL_BENCHMARK_SCALE_INSTITUTE_DBB_PARENTS]
WITH RECURSIVE thousands(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM thousands WHERE n < 1000
), tens(n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1 FROM tens WHERE n < 9
), rows(sequenceNumber) AS (
    SELECT tens.n * 1000 + thousands.n FROM tens CROSS JOIN thousands
)
INSERT INTO institute_db.institute (
    id, blz, bic, bankName, place, stateType, importFile, updatedAt
)
SELECT ? + sequenceNumber, printf('%08d', sequenceNumber % 5000), NULL,
       'DBB Benchmark ' || sequenceNumber, NULL, 1,
       (SELECT MIN(id) FROM institute_db.importHistory), '2026-01-01 00:00:00.000'
FROM rows;

[SQL_BENCHMARK_SCALE_INSTITUTE_DBB_DETAILS]
WITH RECURSIVE thousands(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM thousands WHERE n < 1000
), tens(n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1 FROM tens WHERE n < 9
), rows(sequenceNumber) AS (
    SELECT tens.n * 1000 + thousands.n FROM tens CROSS JOIN thousands
)
INSERT INTO institute_db.instituteDbb (institute_id, datasetNumber, updatedAt)
SELECT ? + sequenceNumber, printf('%06d', sequenceNumber),
       '2026-01-01 00:00:00.000'
FROM rows;

[SQL_BENCHMARK_SELECT_INSTITUTE_DK_PAIR]
SELECT i.blz, dk.importNumber
FROM institute_db.institute i
JOIN institute_db.instituteDk dk ON dk.institute_id = i.id
WHERE i.blz IS NOT NULL AND i.stateType IN (1, 2)
LIMIT 1;

[SQL_BENCHMARK_SELECT_INSTITUTE_DBB_PAIR]
SELECT i.blz, dbb.datasetNumber
FROM institute_db.institute i
JOIN institute_db.instituteDbb dbb ON dbb.institute_id = i.id
WHERE i.blz IS NOT NULL AND i.stateType IN (1, 2)
LIMIT 1;

[SQL_BENCHMARK_INSERT_INSTITUTE]
INSERT INTO institute_db.institute (
    id, blz, bic, bankName, place, stateType, importFile, updatedAt
)
SELECT ?, ?, NULL, 'Index Benchmark', NULL, 1, MIN(id), '2026-01-01 00:00:00.000'
FROM institute_db.importHistory;

[SQL_BENCHMARK_INSERT_INSTITUTE_DK]
INSERT INTO institute_db.instituteDk (institute_id, importNumber, updatedAt)
VALUES (?, ?, '2026-01-01 00:00:00.000');

[SQL_BENCHMARK_INSERT_INSTITUTE_DBB]
INSERT INTO institute_db.instituteDbb (institute_id, datasetNumber, updatedAt)
VALUES (?, ?, '2026-01-01 00:00:00.000');

[SQL_BENCHMARK_UPDATE_INSTITUTE_DK_IMPORT_NUMBER]
UPDATE institute_db.instituteDk SET importNumber = ? WHERE institute_id = ?;

[SQL_BENCHMARK_UPDATE_INSTITUTE_DBB_DATASET_NUMBER]
UPDATE institute_db.instituteDbb SET datasetNumber = ? WHERE institute_id = ?;

[SQL_BENCHMARK_DROP_INSTITUTE_BLZ_STATE]
DROP INDEX IF EXISTS institute_db.idx_institute_blz_state;

[SQL_BENCHMARK_DROP_INSTITUTE_DK_IMPORT_NUMBER]
DROP INDEX IF EXISTS institute_db.idx_institutedk_importnumber_institute;

[SQL_BENCHMARK_DROP_INSTITUTE_DBB_DATASET_NUMBER]
DROP INDEX IF EXISTS institute_db.idx_institutedbb_datasetnumber_institute;

[SQL_BENCHMARK_CREATE_INSTITUTE_DK_IMPORT_NUMBER]
CREATE INDEX IF NOT EXISTS institute_db.idx_institutedk_importnumber_institute
ON instituteDk (importNumber, institute_id);

[SQL_BENCHMARK_CREATE_INSTITUTE_DBB_DATASET_NUMBER]
CREATE INDEX IF NOT EXISTS institute_db.idx_institutedbb_datasetnumber_institute
ON instituteDbb (datasetNumber, institute_id);
