[SQL_SELECT_PD]
SELECT bapd.bankAccess_id, pd.pdKey, bapd.pdValue, bapd.updatedAt
FROM bankAccess_parameterData bapd, parameterData pd
WHERE bapd.parameterData_id = pd.id
AND pdType =

;

[SQL_SETUP_VIEW_BPD]
CREATE VIEW bpd AS ${SQL_SELECT_PD} 'BPD';

;

[SQL_SETUP_VIEW_UPD]
CREATE VIEW upd AS ${SQL_SELECT_PD} 'UPD';

;

[SQL_SELECT_ALL_PARAMETERDATA]
SELECT id, pdKey, pdType, updatedAt FROM parameterData;

;

[SQL_SELECT_ALL_BPD_OR_UPD]
SELECT bankAccess_id, pdKey, pdValue, updatedAt FROM %s;

;

[SQL_INSERT_PARAMETERDATA]
INSERT INTO parameterData (pdKey, pdType, updatedAt) VALUES %s

;

[SQL_UPDATE_PARAMETERDATA]
UPDATE parameterData SET pdKey = ?, pdType = ?, updatedAt = ?

;

[SQL_SELECT_ALL_PARAMETERDATA_BY_BANKACCESS]
SELECT bankAccess_id, pdKey, pdValue, updatedAt FROM %s WHERE bankAccess_id = ?

;

[SQL_INSERT_BANKACCESS_PARAMETERDATA]
INSERT INTO bankAccess_parameterData (bankAccess_id, parameterData_id, pdValue, updatedAt) VALUES %s

;

[SQL_UPDATE_BANKACCESS_PARAMETERDATA]
UPDATE bankAccess_parameterData SET pdValue = ?, updatedAt = ? WHERE bankAccess_id = ? AND parameterData_id = ? AND pdType = ?

;

[SQL_DELETE_BANKACCESS_PARAMETERDATA]
DELETE FROM bankAccess_parameterData WHERE bankAccess_id = ? AND parameterData_id = ? AND pdType = ?

;

[SQL_DELETE_ALL_BANKACCESS_PARAMETERDATA_BY_BANKACCESS]
DELETE FROM bankAccess_parameterData WHERE parameterData_id IN (SELECT id from parameterData WHERE pdType = ?) AND bankAccess_id = ?;

;

[SQL_DELETE_UNUSED_PARAMETERDATA]
DELETE FROM parameterData WHERE id NOT IN (SELECT parameterData_id from bankAccess_parameterData);

;

[SQL_UPDATE_BANKACCOUNT]
UPDATE bankAccount SET bankAccess_id = ?, accountName = ?, currency = ?, accountType = ?, accountSource = ?, iban = ?, bic = ?, number = ?, subNumber = ?, bankName = ?, blz = ?, hbciAccountType = ?, accountLimit = ?, customerId = ?, ownerName = ?, ownerName2 = ?, country = ?, creditorId = ?, isSEPAAccount = ?, isOfflineAccount = ?, accountState = ?, balance = ?, updatedAt = ? WHERE id = ?

;

[SQL_UPDATE_BANKACCOUNT_SOURCE]
UPDATE bankAccount SET accountSource = ?, updatedAt = ? WHERE id = ?

;

[SQL_UPDATE_MONEYTRANSFER]
UPDATE moneytransfer SET moneytransferType = ?, recipient_id = ?, purpose = ?, amount = ?, executionDate = ?, standingorderMode = ?, historyorder_id = ?, updatedAt = ? WHERE id = ?

;

[SQL_UPDATE_RECIPIENT]
UPDATE recipient SET name = ?, iban = ?, bic = ?, accountnumber = ?, blz = ?, bank = ?, source = ?, note = ?, updatedAt = ? WHERE id = ?

;

[SQL_UPDATE_RECIPIENT_NOTE]
UPDATE recipient SET note = ?, updatedAt = ? WHERE id = ?

;

[SQL_UPDATE_RECIPIENT_IF_NOT_REFERENCED]
UPDATE recipient SET name = ?, iban = ?, bic = ?, accountnumber = ?, blz = ?, bank = ?, source = ?, note = ?, updatedAt = ? WHERE id = ? AND id NOT IN (SELECT recipient_id from moneytransfer WHERE recipient_id = recipient.id) AND id NOT IN (SELECT recipient_id from booking WHERE recipient_id = recipient.id);

;

[SQL_UPDATE_CATEGORY]
UPDATE category SET name = ?, parent_id = ?, updatedAt = ? WHERE id = ?

;

[SQL_UPDATE_CATEGORY_RULE]
UPDATE categoryRule SET category_id = ?, filterDateFrom = ?, filterDateTo = ?, filterAmountFrom = ?, filterAmountTo = ?, filterRecipient = ?, filterPurpose = ?, filterRecipientIsRegex = ?, filterPurposeIsRegex = ?, joinType = ?, updatedAt = ?) WHERE id = ?;

;

[SQL_UPDATE_CATEGORY_RULE_BANKACCOUNT]
UPDATE categoryRule_bankAccount SET category_id = ?, account_id = ?, updatedAt = ?) WHERE id = ?;

;

[SQL_UPDATE_BOOKING]
UPDATE booking SET account_id = ?, dateBooking = ?, dateValue = ?, purpose = ?, amount = ?, currency = ?, sepaCustomerRef = ?, sepaCreditorId = ?, sepaEndToEnd = ?, sepaMandate = ?, sepaPersonId = ?, sepaPurpose = ?, sepaTyp = ?, bookingType = ?, bookingSource = ?, crossAccount_id = ?, recipient_id = ?, category_id = ?, crossBooking_id = ?, updatedAt = ? WHERE id = ?;

;

[SQL_UPDATE_BOOKINGS_SOURCE]
UPDATE booking SET bookingSource = ?, updatedAt = ? WHERE account_id = ? AND id = ?

;

[SQL_UPDATE_BOOKINGS_RECIPIENT]
UPDATE booking set recipient_id = ? WHERE id IN (%s)

;

[SQL_UPDATE_BOOKINGS_CATEGORY]
UPDATE booking set category_id = ? WHERE id IN (%s)

;

[SQL_UPDATE_INSTIUTE]
UPDATE institute SET importNumber = ?, blz = ?, bic = ?, bankName = ?, place = ?, dataCenter = ?, organisation = ?, hbciDns = ?, hbciIp = ?, hbciVersion = ?, ddv = ?, rdh1 = ?, rdh2 = ?, rdh3 = ?, rdh4 = ?, rdh5 = ?, rdh6 = ?, rdh7 = ?, rdh8 = ?, rdh9 = ?, rdh10 = ?, pinUrl = ?, version = ?, lastChanged = ?, stateType = ?, updatedAt = ? WHERE id = ?;

;

[SQL_UPDATE_SETTING]
UPDATE setting SET attribute = ?, value = ?, dataType = ?, editable = ?, visible = ?, comment = ?, updatedAt = ? WHERE id = ?;

;

[SQL_SETUP_DROP_VIEW_BPD]
DROP VIEW IF EXISTS bpd;

;

[SQL_SETUP_DROP_VIEW_UPD]
DROP VIEW IF EXISTS upd;
;
