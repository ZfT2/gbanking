[SQL_MIGRATION_0_4_1_DROP_REDUNDANT_BOOKING_CATEGORY_INDEX]
DROP INDEX IF EXISTS uk_booking_category;
;

[SQL_MIGRATION_0_4_1_DROP_REDUNDANT_BANKACCOUNT_BUSINESSCASE_INDEX]
DROP INDEX IF EXISTS uk_bankaccount_businesscase;
;

[SQL_MIGRATION_0_4_1_DROP_REDUNDANT_CATEGORYRULE_BANKACCOUNT_INDEX]
DROP INDEX IF EXISTS uk_categoryrule_bankaccount;
;

[SQL_MIGRATION_0_4_1_DROP_REDUNDANT_PARAMETERDATA_INDEX]
DROP INDEX IF EXISTS uk_parameterdata_type_key;
;

[SQL_MIGRATION_0_4_1_DROP_REDUNDANT_BANKACCESS_PARAMETERDATA_INDEX]
DROP INDEX IF EXISTS uk_bankaccess_parameterdata;
;

[SQL_MIGRATION_0_4_1_DROP_REDUNDANT_MONEYTRANSFER_FOREIGN_INDEX]
DROP INDEX IF EXISTS idx_moneytransferforeign_moneytransfer_id;
;

[SQL_MIGRATION_0_4_1_DROP_OLD_BOOKING_RECIPIENT_INDEX]
DROP INDEX IF EXISTS idx_booking_recipient_id;
;

[SQL_MIGRATION_0_4_1_DROP_OLD_MONEYTRANSFER_RECIPIENT_INDEX]
DROP INDEX IF EXISTS idx_moneytransfer_recipient_id;
;

[SQL_MIGRATION_0_4_1_REPLACE_BANKACCOUNTSTATEMENT_ACCOUNT_INDEX]
DROP INDEX IF EXISTS idx_bankaccountstatement_account;
;

[SQL_MIGRATION_0_4_1_REPLACE_BANKMESSAGE_BANKACCESS_INDEX]
DROP INDEX IF EXISTS idx_bankmessage_bankaccess;
;

[SQL_MIGRATION_0_4_1_CREATE_BOOKING_ACCOUNT_INDEX]
CREATE INDEX IF NOT EXISTS idx_booking_account ON booking (account_id, parentBooking_id, id DESC);
;

[SQL_MIGRATION_0_4_1_CREATE_BOOKING_ACCOUNT_ROOT_AMOUNT_DATE_INDEX]
CREATE INDEX IF NOT EXISTS idx_booking_account_root_amount_date
ON booking (account_id, amount, dateBooking)
WHERE parentBooking_id IS NULL;
;

[SQL_MIGRATION_0_4_1_CREATE_BOOKING_RECIPIENT_USAGE_INDEX]
CREATE INDEX IF NOT EXISTS idx_booking_recipient_usage
ON booking (recipient_id, COALESCE(dateValue, dateBooking, updatedAt) DESC);
;

[SQL_MIGRATION_0_4_1_CREATE_BOOKING_CROSS_BOOKING_INDEX]
CREATE INDEX IF NOT EXISTS idx_booking_cross_booking ON booking (crossBooking_id)
WHERE crossBooking_id IS NOT NULL;
;

[SQL_MIGRATION_0_4_1_CREATE_BOOKING_CATEGORY_CATEGORY_INDEX]
CREATE INDEX IF NOT EXISTS idx_booking_category_category ON booking_category (category_id, booking_id);
;

[SQL_MIGRATION_0_4_1_CREATE_CATEGORYRULE_BANKACCOUNT_ACCOUNT_INDEX]
CREATE INDEX IF NOT EXISTS idx_categoryrule_bankaccount_account
ON categoryRule_bankAccount (account_id, categoryRule_id);
;

[SQL_MIGRATION_0_4_1_CREATE_MONEYTRANSFER_ACCOUNT_STATUS_INDEX]
CREATE INDEX IF NOT EXISTS idx_moneytransfer_account_status ON moneytransfer (account_id, moneytransferStatus);
;

[SQL_MIGRATION_0_4_1_CREATE_MONEYTRANSFER_RECIPIENT_USAGE_INDEX]
CREATE INDEX IF NOT EXISTS idx_moneytransfer_recipient_usage
ON moneytransfer (recipient_id, COALESCE(executionDate, updatedAt) DESC);
;

[SQL_MIGRATION_0_4_1_CREATE_MONEYTRANSFER_HISTORY_INDEX]
CREATE INDEX IF NOT EXISTS idx_moneytransfer_history ON moneytransfer (historyorder_id);
;

[SQL_MIGRATION_0_4_1_CREATE_MONEYTRANSFER_PROTOCOL_TRANSFER_INDEX]
CREATE INDEX IF NOT EXISTS idx_moneytransferprotocol_transfer
ON moneytransferProtocol (moneytransfer_id, timeStart DESC, id DESC);
;

[SQL_MIGRATION_0_4_1_CREATE_BANKACCESS_PARAMETERDATA_PARAMETER_INDEX]
CREATE INDEX IF NOT EXISTS idx_bankaccess_parameterdata_parameter
ON bankAccess_parameterData (parameterData_id, bankAccess_id);
