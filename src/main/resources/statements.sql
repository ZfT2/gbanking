DROP TABLE IF EXISTS bankAccess;
CREATE TABLE bankAccess (id INTEGER PRIMARY KEY, bankName TEXT, hbciURL TEXT, userId TEXT, pinProcedure TEXT, bpdVersion TEXT, updVersion TEXT, active REAL, updatedAt TEXT);

DROP TABLE IF EXISTS bpd;
CREATE TABLE bpd (id INTEGER PRIMARY KEY, bankAccess_id INTEGER, bpdKey TEXT, bpdValue TEXT, updatedAt TEXT);
DROP TABLE IF EXISTS upd;
CREATE TABLE upd (id INTEGER PRIMARY KEY, bankAccess_id INTEGER, updKey TEXT, updValue TEXT, updatedAt TEXT);

DROP TABLE IF EXISTS bankAccount;
CREATE TABLE bankAccount (id INTEGER PRIMARY KEY,
						  bankAccess_id INTEGER, 
						  accountName TEXT,
						  currency, 
						  accountType TEXT, 
						  iban TEXT, 
						  bic TEXT, 
						  number TEXT, 
						  subNumber TEXT, 
						  bankName TEXT, 
						  blz TEXT, 
						  hbciAccountType INTEGER, 
						  accountLimit TEXT, 
						  customerId TEXT ,
						  ownerName TEXT,
						  ownerName2 TEXT,
						  country TEXT,
						  creditorId TEXT,
						  isSEPAAccount REAL,
						  updatedAt TEXT);

DROP TABLE IF EXISTS booking;
CREATE TABLE booking (id INTEGER PRIMARY KEY, account_id INTEGER, dateBooking TEXT, dateValue TEXT, purpose TEXT, amount REAL, typ TEXT, crossAccount_id INTEGER);
						  
DROP TABLE IF EXISTS businessCase;
CREATE TABLE businessCase (id INTEGER PRIMARY KEY, bankAccount_id INTEGER, caseValue TEXT, updatedAt TEXT);


INSERT INTO bankAccess (bankName, hbciURL, userId, pinProcedure, active, updatedAt) VALUES ('TestBank', 'https://test.test/hbci', 'testuser', 'PINTAN', true, '2025-06-05 14:02:04.000');