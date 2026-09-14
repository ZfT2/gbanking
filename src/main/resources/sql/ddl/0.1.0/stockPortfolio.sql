[SQL_SETUP_CREATE_STOCK_NUMERIC_SCALE]
CREATE TABLE stockNumericScale (
  valueType INTEGER PRIMARY KEY,
  scaleDigits INTEGER NOT NULL,
  scaleFactor INTEGER NOT NULL,
  description TEXT NOT NULL,
  CHECK (valueType BETWEEN 1 AND 4),
  CHECK (scaleDigits BETWEEN 0 AND 18),
  CHECK (scaleFactor > 0));

[SQL_SETUP_INSERT_STOCK_NUMERIC_SCALE]
INSERT INTO stockNumericScale (valueType, scaleDigits, scaleFactor, description)
VALUES
  (1, 9, 1000000000, 'Stueckzahl oder Nominale'),
  (2, 8, 100000000, 'Absoluter oder prozentualer Kurs'),
  (3, 9, 1000000000, 'Zins- oder Prozentsatz'),
  (4, 12, 1000000000000, 'Wechselkurs oder Anpassungsfaktor');

[SQL_SETUP_CREATE_STOCK_DATA_SOURCE]
CREATE TABLE stockDataSource (
  id INTEGER PRIMARY KEY,
  sourceCode TEXT NOT NULL UNIQUE COLLATE NOCASE,
  sourceName TEXT NOT NULL,
  sourceType INTEGER NOT NULL,
  defaultPriority INTEGER NOT NULL DEFAULT 100,
  enabled INTEGER NOT NULL DEFAULT 1,
  updatedAt TEXT NOT NULL,
  CHECK (TRIM(sourceCode) <> ''),
  CHECK (TRIM(sourceName) <> ''),
  CHECK (sourceType BETWEEN 1 AND 5),
  CHECK (defaultPriority >= 0),
  CHECK (enabled IN (0, 1)));

[SQL_SETUP_INSERT_STOCK_DATA_SOURCE]
INSERT INTO stockDataSource (id, sourceCode, sourceName, sourceType, defaultPriority, enabled, updatedAt)
VALUES
  (1, 'MANUAL', 'Manuelle Eingabe', 1, 100, 1, datetime()),
  (2, 'FINTS', 'FinTS', 2, 100, 1, datetime()),
  (3, 'PORTFOLIO_PERFORMANCE', 'Portfolio Performance', 3, 100, 1, datetime()),
  (4, 'GENERIC_FILE', 'Generischer Dateiimport', 3, 100, 1, datetime());

[SQL_SETUP_CREATE_STOCK_IMPORT_BATCH]
CREATE TABLE stockImportBatch (
  id INTEGER PRIMARY KEY,
  source_id INTEGER NOT NULL,
  importerKey TEXT NOT NULL,
  formatType TEXT NOT NULL,
  formatVersion TEXT,
  importerVersion TEXT NOT NULL,
  fileName TEXT,
  hashAlgorithm TEXT NOT NULL DEFAULT 'SHA-256',
  contentHash TEXT NOT NULL COLLATE NOCASE,
  rawContent BLOB,
  rawContentEncoding TEXT,
  rawContentEncrypted INTEGER NOT NULL DEFAULT 0,
  importStatus INTEGER NOT NULL,
  startedAt TEXT NOT NULL,
  completedAt TEXT,
  errorText TEXT,
  FOREIGN KEY(source_id) REFERENCES stockDataSource(id),
  CHECK (TRIM(importerKey) <> ''),
  CHECK (TRIM(formatType) <> ''),
  CHECK (TRIM(importerVersion) <> ''),
  CHECK (TRIM(hashAlgorithm) <> ''),
  CHECK (TRIM(contentHash) <> ''),
  CHECK (rawContentEncrypted IN (0, 1)),
  CHECK ((rawContent IS NULL AND rawContentEncoding IS NULL AND rawContentEncrypted = 0)
      OR (rawContent IS NOT NULL AND rawContentEncoding IS NOT NULL)),
  CHECK (importStatus BETWEEN 1 AND 5),
  CHECK ((importStatus IN (1, 2) AND completedAt IS NULL)
      OR (importStatus IN (3, 4, 5) AND completedAt IS NOT NULL)));

[SQL_SETUP_CREATE_INDEX_STOCK_IMPORT_BATCH_HASH]
CREATE UNIQUE INDEX idx_stockimportbatch_completed_hash
ON stockImportBatch (source_id, hashAlgorithm, contentHash)
WHERE importStatus = 3;

[SQL_SETUP_CREATE_STOCK_IMPORT_RECORD]
CREATE TABLE stockImportRecord (
  id INTEGER PRIMARY KEY,
  importBatch_id INTEGER NOT NULL,
  recordNumber INTEGER NOT NULL,
  occurrenceNumber INTEGER NOT NULL DEFAULT 1,
  recordType TEXT NOT NULL,
  externalReference TEXT,
  fingerprint TEXT NOT NULL,
  rawRecord BLOB,
  recordStatus INTEGER NOT NULL,
  errorText TEXT,
  createdAt TEXT NOT NULL,
  FOREIGN KEY(importBatch_id) REFERENCES stockImportBatch(id) ON DELETE CASCADE,
  UNIQUE (importBatch_id, recordNumber),
  UNIQUE (importBatch_id, fingerprint, occurrenceNumber),
  CHECK (recordNumber > 0),
  CHECK (occurrenceNumber > 0),
  CHECK (TRIM(recordType) <> ''),
  CHECK (TRIM(fingerprint) <> ''),
  CHECK (recordStatus BETWEEN 1 AND 5));

[SQL_SETUP_CREATE_TRIGGER_STOCK_IMPORT_BATCH_BLOCK_UPDATE]
CREATE TRIGGER block_completed_stockimportbatch_update
BEFORE UPDATE ON stockImportBatch
WHEN OLD.importStatus = 3
BEGIN
  SELECT RAISE(FAIL, 'completed stockImportBatch is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_IMPORT_BATCH_BLOCK_DELETE]
CREATE TRIGGER block_completed_stockimportbatch_delete
BEFORE DELETE ON stockImportBatch
WHEN OLD.importStatus = 3
BEGIN
  SELECT RAISE(FAIL, 'completed stockImportBatch is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_IMPORT_RECORD_BLOCK_INSERT]
CREATE TRIGGER block_completed_stockimportrecord_insert
BEFORE INSERT ON stockImportRecord
WHEN (SELECT importStatus FROM stockImportBatch WHERE id = NEW.importBatch_id) = 3
BEGIN
  SELECT RAISE(FAIL, 'record of completed stockImportBatch is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_IMPORT_RECORD_BLOCK_UPDATE]
CREATE TRIGGER block_completed_stockimportrecord_update
BEFORE UPDATE ON stockImportRecord
WHEN (SELECT importStatus FROM stockImportBatch WHERE id = OLD.importBatch_id) = 3
BEGIN
  SELECT RAISE(FAIL, 'record of completed stockImportBatch is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_IMPORT_RECORD_BLOCK_DELETE]
CREATE TRIGGER block_completed_stockimportrecord_delete
BEFORE DELETE ON stockImportRecord
WHEN (SELECT importStatus FROM stockImportBatch WHERE id = OLD.importBatch_id) = 3
BEGIN
  SELECT RAISE(FAIL, 'record of completed stockImportBatch is immutable');
END;

[SQL_SETUP_CREATE_STOCK_PORTFOLIO]
CREATE TABLE stockPortfolio (
  id INTEGER PRIMARY KEY,
  account_id INTEGER NOT NULL UNIQUE,
  currentSettlementRelation_id INTEGER NOT NULL UNIQUE,
  openedAt TEXT NOT NULL,
  closedAt TEXT,
  createdAt TEXT NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(account_id) REFERENCES bankAccount(id) ON DELETE CASCADE,
  FOREIGN KEY(currentSettlementRelation_id, id)
    REFERENCES stockPortfolioSettlementAccount(id, portfolio_id)
    DEFERRABLE INITIALLY DEFERRED,
  CHECK (closedAt IS NULL OR openedAt < closedAt));

[SQL_SETUP_CREATE_STOCK_PORTFOLIO_SETTLEMENT_ACCOUNT]
CREATE TABLE stockPortfolioSettlementAccount (
  id INTEGER PRIMARY KEY,
  portfolio_id INTEGER NOT NULL,
  account_id INTEGER NOT NULL,
  validFrom TEXT NOT NULL,
  validTo TEXT,
  createdAt TEXT NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(portfolio_id) REFERENCES stockPortfolio(id) ON DELETE CASCADE
    DEFERRABLE INITIALLY DEFERRED,
  FOREIGN KEY(account_id) REFERENCES bankAccount(id) ON DELETE RESTRICT,
  UNIQUE (id, portfolio_id),
  CHECK (validTo IS NULL OR validFrom < validTo));

[SQL_SETUP_CREATE_INDEX_STOCK_PORTFOLIO_CURRENT_SETTLEMENT_ACCOUNT]
CREATE UNIQUE INDEX idx_stockportfolio_current_settlement_account
ON stockPortfolioSettlementAccount (portfolio_id)
WHERE validTo IS NULL;

[SQL_SETUP_CREATE_INDEX_STOCK_PORTFOLIO_SETTLEMENT_ACCOUNT_HISTORY]
CREATE INDEX idx_stockportfolio_settlement_account_history
ON stockPortfolioSettlementAccount (portfolio_id, validFrom, validTo);

[SQL_SETUP_CREATE_TRIGGER_STOCK_PORTFOLIO_VALIDATE_INSERT]
CREATE TRIGGER validate_stockportfolio_insert
BEFORE INSERT ON stockPortfolio
WHEN (SELECT accountType FROM bankAccount WHERE id = NEW.account_id) IS NOT 16
BEGIN
  SELECT RAISE(FAIL, 'stockPortfolio requires a DEPOT bankAccount');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_PORTFOLIO_VALIDATE_UPDATE]
CREATE TRIGGER validate_stockportfolio_update
BEFORE UPDATE OF account_id, currentSettlementRelation_id ON stockPortfolio
WHEN (SELECT accountType FROM bankAccount WHERE id = NEW.account_id) IS NOT 16
    OR EXISTS (
      SELECT 1
      FROM stockPortfolioSettlementAccount relation
      WHERE relation.id = NEW.currentSettlementRelation_id
        AND (relation.portfolio_id <> NEW.id OR relation.validTo IS NOT NULL))
BEGIN
  SELECT RAISE(FAIL, 'invalid stockPortfolio account or current settlement relation');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_PORTFOLIO_SETTLEMENT_VALIDATE_INSERT]
CREATE TRIGGER validate_stockportfolio_settlement_insert
BEFORE INSERT ON stockPortfolioSettlementAccount
WHEN (SELECT accountType FROM bankAccount WHERE id = NEW.account_id) NOT IN (1, 12)
    OR NEW.account_id = (SELECT account_id FROM stockPortfolio WHERE id = NEW.portfolio_id)
    OR (NEW.validTo IS NOT NULL AND EXISTS (
      SELECT 1
      FROM stockPortfolio portfolio
      WHERE portfolio.currentSettlementRelation_id = NEW.id))
    OR EXISTS (
      SELECT 1
      FROM stockPortfolioSettlementAccount existing
      WHERE existing.portfolio_id = NEW.portfolio_id
        AND NEW.validFrom < COALESCE(existing.validTo, '9999-12-31T23:59:59.999999999')
        AND existing.validFrom < COALESCE(NEW.validTo, '9999-12-31T23:59:59.999999999'))
BEGIN
  SELECT RAISE(FAIL, 'invalid or overlapping settlement-account assignment');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_PORTFOLIO_SETTLEMENT_VALIDATE_UPDATE]
CREATE TRIGGER validate_stockportfolio_settlement_update
BEFORE UPDATE ON stockPortfolioSettlementAccount
WHEN (SELECT accountType FROM bankAccount WHERE id = NEW.account_id) NOT IN (1, 12)
    OR NEW.account_id = (SELECT account_id FROM stockPortfolio WHERE id = NEW.portfolio_id)
    OR (NEW.validTo IS NOT NULL AND EXISTS (
      SELECT 1
      FROM stockPortfolio portfolio
      WHERE portfolio.currentSettlementRelation_id = OLD.id))
    OR EXISTS (
      SELECT 1
      FROM stockPortfolioSettlementAccount existing
      WHERE existing.portfolio_id = NEW.portfolio_id
        AND existing.id <> OLD.id
        AND NEW.validFrom < COALESCE(existing.validTo, '9999-12-31T23:59:59.999999999')
        AND existing.validFrom < COALESCE(NEW.validTo, '9999-12-31T23:59:59.999999999'))
BEGIN
  SELECT RAISE(FAIL, 'invalid or overlapping settlement-account assignment');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_PORTFOLIO_SETTLEMENT_BLOCK_CURRENT_DELETE]
CREATE TRIGGER block_current_stockportfolio_settlement_delete
BEFORE DELETE ON stockPortfolioSettlementAccount
WHEN EXISTS (
  SELECT 1
  FROM stockPortfolio portfolio
  WHERE portfolio.currentSettlementRelation_id = OLD.id)
BEGIN
  SELECT RAISE(FAIL, 'current settlement-account assignment cannot be deleted');
END;

[SQL_SETUP_CREATE_TRIGGER_BANKACCOUNT_VALIDATE_STOCK_ROLES]
CREATE TRIGGER validate_bankaccount_stock_roles
BEFORE UPDATE OF accountType ON bankAccount
WHEN (NEW.accountType <> 16 AND EXISTS (
      SELECT 1 FROM stockPortfolio portfolio WHERE portfolio.account_id = OLD.id))
    OR (NEW.accountType NOT IN (1, 12) AND EXISTS (
      SELECT 1 FROM stockPortfolioSettlementAccount relation WHERE relation.account_id = OLD.id))
BEGIN
  SELECT RAISE(FAIL, 'bankAccount type conflicts with its portfolio role');
END;

[SQL_SETUP_CREATE_STOCK_SECURITY]
CREATE TABLE stockSecurity (
  id INTEGER PRIMARY KEY,
  securityType INTEGER NOT NULL,
  name TEXT NOT NULL,
  issuer TEXT,
  domicileCountry TEXT,
  maturityDate TEXT,
  defaultQuantityType INTEGER NOT NULL,
  nominalCurrency INTEGER,
  defaultQuoteCurrency INTEGER,
  defaultQuotationType INTEGER NOT NULL,
  defaultPriceBasis INTEGER,
  securityState INTEGER NOT NULL DEFAULT 1,
  createdAt TEXT NOT NULL,
  updatedAt TEXT NOT NULL,
  CHECK (TRIM(name) <> ''),
  CHECK (domicileCountry IS NULL OR LENGTH(domicileCountry) = 2),
  CHECK (securityType BETWEEN 1 AND 14),
  CHECK (defaultQuantityType BETWEEN 1 AND 2),
  CHECK (defaultQuotationType BETWEEN 1 AND 3),
  CHECK (defaultPriceBasis IS NULL OR defaultPriceBasis BETWEEN 1 AND 2),
  CHECK ((defaultQuotationType = 2
      AND defaultQuantityType = 2
      AND nominalCurrency IS NOT NULL
      AND defaultPriceBasis IS NOT NULL)
    OR (defaultQuotationType <> 2 AND defaultPriceBasis IS NULL)),
  CHECK (securityState BETWEEN 1 AND 3),
  CHECK (nominalCurrency IS NULL OR nominalCurrency BETWEEN 1 AND 40),
  CHECK (defaultQuoteCurrency IS NULL OR defaultQuoteCurrency BETWEEN 1 AND 40));

[SQL_SETUP_CREATE_STOCK_SECURITY_IDENTIFIER]
CREATE TABLE stockSecurityIdentifier (
  id INTEGER PRIMARY KEY,
  security_id INTEGER NOT NULL,
  source_id INTEGER,
  identifierType INTEGER NOT NULL,
  identifierValue TEXT NOT NULL COLLATE NOCASE,
  marketIdentifierCode TEXT COLLATE NOCASE,
  validFrom TEXT,
  validTo TEXT,
  createdAt TEXT NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(security_id) REFERENCES stockSecurity(id) ON DELETE CASCADE,
  FOREIGN KEY(source_id) REFERENCES stockDataSource(id),
  CHECK (identifierType BETWEEN 1 AND 7),
  CHECK (TRIM(identifierValue) <> ''),
  CHECK (marketIdentifierCode IS NULL OR LENGTH(marketIdentifierCode) = 4),
  CHECK (identifierType <> 7 OR source_id IS NOT NULL),
  CHECK (validTo IS NULL OR validFrom IS NULL OR validFrom < validTo));

[SQL_SETUP_CREATE_INDEX_STOCK_SECURITY_IDENTIFIER_GLOBAL]
CREATE UNIQUE INDEX idx_stocksecurityidentifier_global
ON stockSecurityIdentifier (identifierType, identifierValue)
WHERE identifierType IN (1, 2, 4, 5, 6) AND validTo IS NULL;

[SQL_SETUP_CREATE_INDEX_STOCK_SECURITY_IDENTIFIER_MARKET]
CREATE UNIQUE INDEX idx_stocksecurityidentifier_market
ON stockSecurityIdentifier (
  identifierType,
  identifierValue,
  COALESCE(marketIdentifierCode, ''),
  COALESCE(source_id, 0))
WHERE identifierType IN (3, 7) AND validTo IS NULL;

[SQL_SETUP_CREATE_INDEX_STOCK_SECURITY_IDENTIFIER_SECURITY]
CREATE INDEX idx_stocksecurityidentifier_security
ON stockSecurityIdentifier (security_id, identifierType, validTo);

[SQL_SETUP_CREATE_STOCK_SECURITY_INTEREST_TERMS]
CREATE TABLE stockSecurityInterestTerms (
  security_id INTEGER PRIMARY KEY,
  source_id INTEGER NOT NULL,
  interestType INTEGER NOT NULL,
  fixedRateE9 INTEGER,
  referenceRateName TEXT,
  spreadRateE9 INTEGER,
  floorRateE9 INTEGER,
  capRateE9 INTEGER,
  paymentFrequencyPerYear INTEGER,
  dayCountConvention INTEGER NOT NULL,
  exCouponDays INTEGER NOT NULL DEFAULT 0,
  createdAt TEXT NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(security_id) REFERENCES stockSecurity(id) ON DELETE CASCADE,
  FOREIGN KEY(source_id) REFERENCES stockDataSource(id),
  CHECK (interestType BETWEEN 1 AND 5),
  CHECK ((interestType = 1 AND fixedRateE9 IS NOT NULL)
      OR (interestType <> 1 AND fixedRateE9 IS NULL)),
  CHECK ((interestType IN (2, 5) AND referenceRateName IS NOT NULL)
      OR interestType NOT IN (2, 5)),
  CHECK (floorRateE9 IS NULL OR capRateE9 IS NULL OR floorRateE9 <= capRateE9),
  CHECK (paymentFrequencyPerYear IS NULL OR paymentFrequencyPerYear IN (1, 2, 3, 4, 6, 12)),
  CHECK (dayCountConvention BETWEEN 1 AND 6),
  CHECK (exCouponDays >= 0));

[SQL_SETUP_CREATE_STOCK_COUPON_PERIOD]
CREATE TABLE stockCouponPeriod (
  id INTEGER PRIMARY KEY,
  security_id INTEGER NOT NULL,
  source_id INTEGER NOT NULL,
  importRecord_id INTEGER,
  accrualStart TEXT NOT NULL,
  accrualEnd TEXT NOT NULL,
  paymentDate TEXT,
  rateE9 INTEGER NOT NULL,
  rateStatus INTEGER NOT NULL,
  createdAt TEXT NOT NULL,
  FOREIGN KEY(security_id) REFERENCES stockSecurity(id) ON DELETE CASCADE,
  FOREIGN KEY(source_id) REFERENCES stockDataSource(id),
  FOREIGN KEY(importRecord_id) REFERENCES stockImportRecord(id),
  UNIQUE (security_id, accrualStart, accrualEnd),
  CHECK (accrualStart < accrualEnd),
  CHECK (paymentDate IS NULL OR accrualEnd <= paymentDate),
  CHECK (rateStatus BETWEEN 1 AND 2));

[SQL_SETUP_CREATE_INDEX_STOCK_COUPON_PERIOD_SECURITY]
CREATE INDEX idx_stockcouponperiod_security
ON stockCouponPeriod (security_id, accrualStart, accrualEnd);

[SQL_SETUP_CREATE_STOCK_SECURITY_FACTOR]
CREATE TABLE stockSecurityFactor (
  id INTEGER PRIMARY KEY,
  security_id INTEGER NOT NULL,
  source_id INTEGER NOT NULL,
  importRecord_id INTEGER,
  factorType INTEGER NOT NULL,
  effectiveAt TEXT NOT NULL,
  factorE12 INTEGER NOT NULL,
  numerator INTEGER,
  denominator INTEGER,
  externalReference TEXT,
  createdAt TEXT NOT NULL,
  FOREIGN KEY(security_id) REFERENCES stockSecurity(id) ON DELETE CASCADE,
  FOREIGN KEY(source_id) REFERENCES stockDataSource(id),
  FOREIGN KEY(importRecord_id) REFERENCES stockImportRecord(id),
  UNIQUE (security_id, factorType, effectiveAt, source_id),
  CHECK (factorType BETWEEN 1 AND 3),
  CHECK (factorE12 > 0),
  CHECK ((numerator IS NULL AND denominator IS NULL)
      OR (numerator > 0 AND denominator > 0)),
  CHECK (factorType <> 1 OR (numerator IS NOT NULL AND denominator IS NOT NULL)));

[SQL_SETUP_CREATE_STOCK_SECURITY_PRICE_SOURCE]
CREATE TABLE stockSecurityPriceSource (
  id INTEGER PRIMARY KEY,
  security_id INTEGER NOT NULL,
  source_id INTEGER NOT NULL,
  providerSymbol TEXT COLLATE NOCASE,
  marketIdentifierCode TEXT COLLATE NOCASE,
  priority INTEGER NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  createdAt TEXT NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(security_id) REFERENCES stockSecurity(id) ON DELETE CASCADE,
  FOREIGN KEY(source_id) REFERENCES stockDataSource(id),
  CHECK (marketIdentifierCode IS NULL OR LENGTH(marketIdentifierCode) = 4),
  CHECK (priority >= 0),
  CHECK (enabled IN (0, 1)));

[SQL_SETUP_CREATE_INDEX_STOCK_SECURITY_PRICE_SOURCE_UNIQUE]
CREATE UNIQUE INDEX idx_stocksecuritypricesource_unique
ON stockSecurityPriceSource (
  security_id,
  source_id,
  COALESCE(providerSymbol, ''),
  COALESCE(marketIdentifierCode, ''));

[SQL_SETUP_CREATE_STOCK_SECURITY_PRICE]
CREATE TABLE stockSecurityPrice (
  id INTEGER PRIMARY KEY,
  priceSource_id INTEGER NOT NULL,
  importRecord_id INTEGER,
  quotedAt TEXT NOT NULL,
  priceE8 INTEGER NOT NULL,
  quoteCurrency INTEGER NOT NULL,
  quotationType INTEGER NOT NULL,
  priceBasis INTEGER,
  priceType INTEGER NOT NULL,
  volumeE9 INTEGER,
  externalReference TEXT,
  supersedesPrice_id INTEGER UNIQUE,
  createdAt TEXT NOT NULL,
  FOREIGN KEY(priceSource_id) REFERENCES stockSecurityPriceSource(id) ON DELETE RESTRICT,
  FOREIGN KEY(importRecord_id) REFERENCES stockImportRecord(id),
  FOREIGN KEY(supersedesPrice_id) REFERENCES stockSecurityPrice(id) ON DELETE RESTRICT,
  CHECK (quotationType BETWEEN 1 AND 3),
  CHECK (quoteCurrency BETWEEN 1 AND 40),
  CHECK (priceBasis IS NULL OR priceBasis BETWEEN 1 AND 2),
  CHECK ((quotationType = 2 AND priceBasis IS NOT NULL)
      OR (quotationType <> 2 AND priceBasis IS NULL)),
  CHECK (priceType BETWEEN 1 AND 6),
  CHECK (volumeE9 IS NULL OR volumeE9 >= 0),
  CHECK (supersedesPrice_id IS NULL OR supersedesPrice_id <> id));

[SQL_SETUP_CREATE_INDEX_STOCK_SECURITY_PRICE_LOOKUP]
CREATE INDEX idx_stocksecurityprice_lookup
ON stockSecurityPrice (priceSource_id, quotedAt DESC, priceType, createdAt DESC);

[SQL_SETUP_CREATE_TRIGGER_STOCK_SECURITY_PRICE_VALIDATE_SUPERSESSION]
CREATE TRIGGER validate_stocksecurityprice_supersession
BEFORE INSERT ON stockSecurityPrice
WHEN NEW.supersedesPrice_id IS NOT NULL
    AND NOT EXISTS (
      SELECT 1
      FROM stockSecurityPrice previous
      WHERE previous.id = NEW.supersedesPrice_id
        AND previous.priceSource_id = NEW.priceSource_id
        AND previous.quotedAt = NEW.quotedAt
        AND previous.priceType = NEW.priceType)
BEGIN
  SELECT RAISE(FAIL, 'corrected price must match source, timestamp and price type');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_SECURITY_PRICE_BLOCK_UPDATE]
CREATE TRIGGER block_stocksecurityprice_update
BEFORE UPDATE ON stockSecurityPrice
BEGIN
  SELECT RAISE(FAIL, 'stockSecurityPrice is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_SECURITY_PRICE_BLOCK_DELETE]
CREATE TRIGGER block_stocksecurityprice_delete
BEFORE DELETE ON stockSecurityPrice
BEGIN
  SELECT RAISE(FAIL, 'stockSecurityPrice is immutable');
END;

[SQL_SETUP_CREATE_STOCK_EXCHANGE_RATE_SOURCE]
CREATE TABLE stockExchangeRateSource (
  id INTEGER PRIMARY KEY,
  source_id INTEGER NOT NULL,
  baseCurrency INTEGER NOT NULL,
  quoteCurrency INTEGER NOT NULL,
  priority INTEGER NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  createdAt TEXT NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(source_id) REFERENCES stockDataSource(id),
  UNIQUE (source_id, baseCurrency, quoteCurrency),
  CHECK (baseCurrency <> quoteCurrency),
  CHECK (baseCurrency BETWEEN 1 AND 40),
  CHECK (quoteCurrency BETWEEN 1 AND 40),
  CHECK (priority >= 0),
  CHECK (enabled IN (0, 1)));

[SQL_SETUP_CREATE_STOCK_EXCHANGE_RATE]
CREATE TABLE stockExchangeRate (
  id INTEGER PRIMARY KEY,
  exchangeRateSource_id INTEGER NOT NULL,
  importRecord_id INTEGER,
  quotedAt TEXT NOT NULL,
  rateE12 INTEGER NOT NULL,
  externalReference TEXT,
  supersedesExchangeRate_id INTEGER UNIQUE,
  createdAt TEXT NOT NULL,
  FOREIGN KEY(exchangeRateSource_id) REFERENCES stockExchangeRateSource(id) ON DELETE RESTRICT,
  FOREIGN KEY(importRecord_id) REFERENCES stockImportRecord(id),
  FOREIGN KEY(supersedesExchangeRate_id) REFERENCES stockExchangeRate(id) ON DELETE RESTRICT,
  CHECK (rateE12 > 0),
  CHECK (supersedesExchangeRate_id IS NULL OR supersedesExchangeRate_id <> id));

[SQL_SETUP_CREATE_INDEX_STOCK_EXCHANGE_RATE_LOOKUP]
CREATE INDEX idx_stockexchangerate_lookup
ON stockExchangeRate (exchangeRateSource_id, quotedAt DESC, createdAt DESC);

[SQL_SETUP_CREATE_TRIGGER_STOCK_EXCHANGE_RATE_VALIDATE_SUPERSESSION]
CREATE TRIGGER validate_stockexchangerate_supersession
BEFORE INSERT ON stockExchangeRate
WHEN NEW.supersedesExchangeRate_id IS NOT NULL
    AND NOT EXISTS (
      SELECT 1
      FROM stockExchangeRate previous
      WHERE previous.id = NEW.supersedesExchangeRate_id
        AND previous.exchangeRateSource_id = NEW.exchangeRateSource_id
        AND previous.quotedAt = NEW.quotedAt)
BEGIN
  SELECT RAISE(FAIL, 'corrected exchange rate must match source and timestamp');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_EXCHANGE_RATE_BLOCK_UPDATE]
CREATE TRIGGER block_stockexchangerate_update
BEFORE UPDATE ON stockExchangeRate
BEGIN
  SELECT RAISE(FAIL, 'stockExchangeRate is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_EXCHANGE_RATE_BLOCK_DELETE]
CREATE TRIGGER block_stockexchangerate_delete
BEFORE DELETE ON stockExchangeRate
BEGIN
  SELECT RAISE(FAIL, 'stockExchangeRate is immutable');
END;

[SQL_SETUP_CREATE_STOCK_PORTFOLIO_STATEMENT]
CREATE TABLE stockPortfolioStatement (
  id INTEGER PRIMARY KEY,
  portfolio_id INTEGER NOT NULL,
  source_id INTEGER NOT NULL,
  importRecord_id INTEGER,
  statementAt TEXT NOT NULL,
  statementStatus INTEGER NOT NULL,
  externalReference TEXT,
  reportedTotalValueMinor INTEGER,
  reportedAccruedInterestMinor INTEGER,
  valueCurrency INTEGER,
  createdAt TEXT NOT NULL,
  FOREIGN KEY(portfolio_id) REFERENCES stockPortfolio(id) ON DELETE CASCADE,
  FOREIGN KEY(source_id) REFERENCES stockDataSource(id),
  FOREIGN KEY(importRecord_id) REFERENCES stockImportRecord(id),
  CHECK (statementStatus BETWEEN 1 AND 2),
  CHECK ((reportedTotalValueMinor IS NULL AND reportedAccruedInterestMinor IS NULL AND valueCurrency IS NULL)
      OR valueCurrency IS NOT NULL),
  CHECK (valueCurrency IS NULL OR valueCurrency BETWEEN 1 AND 40));

[SQL_SETUP_CREATE_INDEX_STOCK_PORTFOLIO_STATEMENT_REFERENCE]
CREATE UNIQUE INDEX idx_stockportfoliostatement_reference
ON stockPortfolioStatement (portfolio_id, source_id, externalReference)
WHERE externalReference IS NOT NULL;

[SQL_SETUP_CREATE_INDEX_STOCK_PORTFOLIO_STATEMENT_DATE]
CREATE INDEX idx_stockportfoliostatement_date
ON stockPortfolioStatement (portfolio_id, statementAt DESC);

[SQL_SETUP_CREATE_STOCK_PORTFOLIO_STATEMENT_POSITION]
CREATE TABLE stockPortfolioStatementPosition (
  id INTEGER PRIMARY KEY,
  statement_id INTEGER NOT NULL,
  security_id INTEGER NOT NULL,
  quantityE9 INTEGER NOT NULL,
  quantityType INTEGER NOT NULL,
  reportedPriceE8 INTEGER,
  priceCurrency INTEGER,
  quotationType INTEGER,
  priceBasis INTEGER,
  reportedValueMinor INTEGER,
  accruedInterestMinor INTEGER,
  valueCurrency INTEGER,
  createdAt TEXT NOT NULL,
  FOREIGN KEY(statement_id) REFERENCES stockPortfolioStatement(id) ON DELETE CASCADE,
  FOREIGN KEY(security_id) REFERENCES stockSecurity(id) ON DELETE RESTRICT,
  UNIQUE (statement_id, security_id, quantityType),
  CHECK (quantityType BETWEEN 1 AND 2),
  CHECK ((reportedPriceE8 IS NULL AND priceCurrency IS NULL AND quotationType IS NULL AND priceBasis IS NULL)
      OR (reportedPriceE8 IS NOT NULL AND priceCurrency IS NOT NULL AND quotationType IS NOT NULL)),
  CHECK (quotationType IS NULL OR quotationType BETWEEN 1 AND 3),
  CHECK (priceBasis IS NULL OR priceBasis BETWEEN 1 AND 2),
  CHECK ((quotationType = 2 AND priceBasis IS NOT NULL)
      OR quotationType IS NULL
      OR (quotationType <> 2 AND priceBasis IS NULL)),
  CHECK ((reportedValueMinor IS NULL AND accruedInterestMinor IS NULL AND valueCurrency IS NULL)
      OR valueCurrency IS NOT NULL),
  CHECK (priceCurrency IS NULL OR priceCurrency BETWEEN 1 AND 40),
  CHECK (valueCurrency IS NULL OR valueCurrency BETWEEN 1 AND 40));

[SQL_SETUP_CREATE_STOCK_PORTFOLIO_STATEMENT_SUB_BALANCE]
CREATE TABLE stockPortfolioStatementSubBalance (
  id INTEGER PRIMARY KEY,
  statementPosition_id INTEGER NOT NULL,
  qualifier INTEGER NOT NULL,
  quantityE9 INTEGER NOT NULL,
  locked INTEGER NOT NULL DEFAULT 0,
  lockedUntil TEXT,
  custodyCountry TEXT,
  custodyType TEXT,
  custodyPlace TEXT,
  comment TEXT,
  createdAt TEXT NOT NULL,
  FOREIGN KEY(statementPosition_id) REFERENCES stockPortfolioStatementPosition(id) ON DELETE CASCADE,
  CHECK (qualifier BETWEEN 1 AND 5),
  CHECK (locked IN (0, 1)),
  CHECK (locked = 1 OR lockedUntil IS NULL),
  CHECK (custodyCountry IS NULL OR LENGTH(custodyCountry) = 2));

[SQL_SETUP_CREATE_TRIGGER_STOCK_PORTFOLIO_STATEMENT_BLOCK_UPDATE]
CREATE TRIGGER block_stockportfoliostatement_update
BEFORE UPDATE ON stockPortfolioStatement
WHEN OLD.statementStatus = 2
BEGIN
  SELECT RAISE(FAIL, 'stockPortfolioStatement is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_PORTFOLIO_STATEMENT_BLOCK_DELETE]
CREATE TRIGGER block_stockportfoliostatement_delete
BEFORE DELETE ON stockPortfolioStatement
WHEN OLD.statementStatus = 2
BEGIN
  SELECT RAISE(FAIL, 'stockPortfolioStatement is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_PORTFOLIO_STATEMENT_POSITION_BLOCK_UPDATE]
CREATE TRIGGER block_stockportfoliostatementposition_update
BEFORE UPDATE ON stockPortfolioStatementPosition
WHEN (SELECT statementStatus FROM stockPortfolioStatement WHERE id = OLD.statement_id) = 2
BEGIN
  SELECT RAISE(FAIL, 'stockPortfolioStatementPosition is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_PORTFOLIO_STATEMENT_POSITION_BLOCK_DELETE]
CREATE TRIGGER block_stockportfoliostatementposition_delete
BEFORE DELETE ON stockPortfolioStatementPosition
WHEN (SELECT statementStatus FROM stockPortfolioStatement WHERE id = OLD.statement_id) = 2
BEGIN
  SELECT RAISE(FAIL, 'stockPortfolioStatementPosition is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_PORTFOLIO_STATEMENT_SUB_BALANCE_BLOCK_UPDATE]
CREATE TRIGGER block_stockportfoliostatementsubbalance_update
BEFORE UPDATE ON stockPortfolioStatementSubBalance
WHEN (SELECT statement.statementStatus
      FROM stockPortfolioStatementPosition position
      JOIN stockPortfolioStatement statement ON statement.id = position.statement_id
      WHERE position.id = OLD.statementPosition_id) = 2
BEGIN
  SELECT RAISE(FAIL, 'stockPortfolioStatementSubBalance is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_PORTFOLIO_STATEMENT_SUB_BALANCE_BLOCK_DELETE]
CREATE TRIGGER block_stockportfoliostatementsubbalance_delete
BEFORE DELETE ON stockPortfolioStatementSubBalance
WHEN (SELECT statement.statementStatus
      FROM stockPortfolioStatementPosition position
      JOIN stockPortfolioStatement statement ON statement.id = position.statement_id
      WHERE position.id = OLD.statementPosition_id) = 2
BEGIN
  SELECT RAISE(FAIL, 'stockPortfolioStatementSubBalance is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_PORTFOLIO_STATEMENT_POSITION_BLOCK_INSERT]
CREATE TRIGGER block_stockportfoliostatementposition_insert
BEFORE INSERT ON stockPortfolioStatementPosition
WHEN (SELECT statementStatus FROM stockPortfolioStatement WHERE id = NEW.statement_id) = 2
BEGIN
  SELECT RAISE(FAIL, 'stockPortfolioStatementPosition is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_PORTFOLIO_STATEMENT_SUB_BALANCE_BLOCK_INSERT]
CREATE TRIGGER block_stockportfoliostatementsubbalance_insert
BEFORE INSERT ON stockPortfolioStatementSubBalance
WHEN (SELECT statement.statementStatus
      FROM stockPortfolioStatementPosition position
      JOIN stockPortfolioStatement statement ON statement.id = position.statement_id
      WHERE position.id = NEW.statementPosition_id) = 2
BEGIN
  SELECT RAISE(FAIL, 'stockPortfolioStatementSubBalance is immutable');
END;

[SQL_SETUP_CREATE_STOCK_TRANSACTION]
CREATE TABLE stockTransaction (
  id INTEGER PRIMARY KEY,
  portfolio_id INTEGER NOT NULL,
  source_id INTEGER NOT NULL,
  importRecord_id INTEGER,
  transactionType INTEGER NOT NULL,
  transactionStatus INTEGER NOT NULL,
  tradeAt TEXT NOT NULL,
  settlementDueAt TEXT,
  settledAt TEXT,
  cashValueAt TEXT,
  providerBookedAt TEXT,
  settlementDateInferred INTEGER NOT NULL DEFAULT 0,
  reversalOfTransaction_id INTEGER UNIQUE,
  reconciliationStatement_id INTEGER,
  fingerprint TEXT,
  createdAt TEXT NOT NULL,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(portfolio_id) REFERENCES stockPortfolio(id) ON DELETE CASCADE,
  FOREIGN KEY(source_id) REFERENCES stockDataSource(id),
  FOREIGN KEY(importRecord_id) REFERENCES stockImportRecord(id),
  FOREIGN KEY(reversalOfTransaction_id) REFERENCES stockTransaction(id) ON DELETE RESTRICT,
  FOREIGN KEY(reconciliationStatement_id) REFERENCES stockPortfolioStatement(id) ON DELETE RESTRICT,
  UNIQUE (id, portfolio_id),
  CHECK (transactionType BETWEEN 1 AND 18),
  CHECK (transactionStatus BETWEEN 1 AND 4),
  CHECK ((transactionStatus = 2 AND settledAt IS NOT NULL)
      OR (transactionStatus <> 2 AND settledAt IS NULL)),
  CHECK (settlementDueAt IS NULL OR tradeAt <= settlementDueAt),
  CHECK (settledAt IS NULL OR tradeAt <= settledAt),
  CHECK (settlementDateInferred IN (0, 1)),
  CHECK (settlementDateInferred = 0 OR settledAt IS NOT NULL),
  CHECK (reversalOfTransaction_id IS NULL OR reversalOfTransaction_id <> id),
  CHECK (importRecord_id IS NULL OR fingerprint IS NOT NULL));

[SQL_SETUP_CREATE_INDEX_STOCK_TRANSACTION_PORTFOLIO_DATE]
CREATE INDEX idx_stocktransaction_portfolio_date
ON stockTransaction (portfolio_id, settledAt, tradeAt, id);

[SQL_SETUP_CREATE_INDEX_STOCK_TRANSACTION_FINGERPRINT]
CREATE INDEX idx_stocktransaction_fingerprint
ON stockTransaction (portfolio_id, source_id, fingerprint)
WHERE fingerprint IS NOT NULL;

[SQL_SETUP_CREATE_STOCK_TRANSACTION_EXTERNAL_REFERENCE]
CREATE TABLE stockTransactionExternalReference (
  id INTEGER PRIMARY KEY,
  transaction_id INTEGER NOT NULL,
  portfolio_id INTEGER NOT NULL,
  source_id INTEGER NOT NULL,
  referenceType TEXT NOT NULL,
  referenceValue TEXT NOT NULL COLLATE NOCASE,
  createdAt TEXT NOT NULL,
  FOREIGN KEY(transaction_id, portfolio_id) REFERENCES stockTransaction(id, portfolio_id) ON DELETE CASCADE,
  FOREIGN KEY(source_id) REFERENCES stockDataSource(id),
  UNIQUE (portfolio_id, source_id, referenceType, referenceValue),
  CHECK (TRIM(referenceType) <> ''),
  CHECK (TRIM(referenceValue) <> ''));

[SQL_SETUP_CREATE_STOCK_TRANSACTION_SECURITY_LEG]
CREATE TABLE stockTransactionSecurityLeg (
  id INTEGER PRIMARY KEY,
  transaction_id INTEGER NOT NULL,
  legNumber INTEGER NOT NULL,
  security_id INTEGER NOT NULL,
  legRole INTEGER NOT NULL,
  quantityE9 INTEGER NOT NULL,
  quantityType INTEGER NOT NULL,
  priceE8 INTEGER,
  priceCurrency INTEGER,
  quotationType INTEGER,
  priceBasis INTEGER,
  accruedInterestDays INTEGER,
  createdAt TEXT NOT NULL,
  FOREIGN KEY(transaction_id) REFERENCES stockTransaction(id) ON DELETE CASCADE,
  FOREIGN KEY(security_id) REFERENCES stockSecurity(id) ON DELETE RESTRICT,
  UNIQUE (transaction_id, legNumber),
  CHECK (legNumber > 0),
  CHECK (legRole BETWEEN 1 AND 4),
  CHECK (legRole = 2 OR quantityE9 <> 0),
  CHECK (quantityType BETWEEN 1 AND 2),
  CHECK ((priceE8 IS NULL AND priceCurrency IS NULL AND quotationType IS NULL AND priceBasis IS NULL)
      OR (priceE8 IS NOT NULL AND priceCurrency IS NOT NULL AND quotationType IS NOT NULL)),
  CHECK (quotationType IS NULL OR quotationType BETWEEN 1 AND 3),
  CHECK (priceBasis IS NULL OR priceBasis BETWEEN 1 AND 2),
  CHECK ((quotationType = 2 AND priceBasis IS NOT NULL)
      OR quotationType IS NULL
      OR (quotationType <> 2 AND priceBasis IS NULL)),
  CHECK (accruedInterestDays IS NULL OR accruedInterestDays >= 0),
  CHECK (priceCurrency IS NULL OR priceCurrency BETWEEN 1 AND 40));

[SQL_SETUP_CREATE_INDEX_STOCK_TRANSACTION_SECURITY_LEG_POSITION]
CREATE INDEX idx_stocktransactionsecurityleg_position
ON stockTransactionSecurityLeg (security_id, transaction_id, quantityType);

[SQL_SETUP_CREATE_STOCK_TRANSACTION_CASH_LEG]
CREATE TABLE stockTransactionCashLeg (
  id INTEGER PRIMARY KEY,
  transaction_id INTEGER NOT NULL,
  legNumber INTEGER NOT NULL,
  account_id INTEGER NOT NULL,
  booking_id INTEGER,
  legRole INTEGER NOT NULL,
  amountMinor INTEGER NOT NULL,
  currency INTEGER NOT NULL,
  exchangeRate_id INTEGER,
  valueAt TEXT,
  createdAt TEXT NOT NULL,
  FOREIGN KEY(transaction_id) REFERENCES stockTransaction(id) ON DELETE CASCADE,
  FOREIGN KEY(account_id) REFERENCES bankAccount(id) ON DELETE RESTRICT,
  FOREIGN KEY(booking_id) REFERENCES booking(id) ON DELETE SET NULL,
  FOREIGN KEY(exchangeRate_id) REFERENCES stockExchangeRate(id) ON DELETE RESTRICT,
  UNIQUE (transaction_id, legNumber),
  CHECK (legNumber > 0),
  CHECK (legRole BETWEEN 1 AND 9),
  CHECK (amountMinor <> 0),
  CHECK (currency BETWEEN 1 AND 40));

[SQL_SETUP_CREATE_INDEX_STOCK_TRANSACTION_CASH_LEG_BOOKING]
CREATE INDEX idx_stocktransactioncashleg_booking
ON stockTransactionCashLeg (booking_id)
WHERE booking_id IS NOT NULL;

[SQL_SETUP_CREATE_INDEX_STOCK_TRANSACTION_CASH_LEG_ACCOUNT]
CREATE INDEX idx_stocktransactioncashleg_account
ON stockTransactionCashLeg (account_id, valueAt, transaction_id);

[SQL_SETUP_CREATE_STOCK_TRANSACTION_METADATA]
CREATE TABLE stockTransactionMetadata (
  transaction_id INTEGER PRIMARY KEY,
  note TEXT,
  tags TEXT,
  updatedAt TEXT NOT NULL,
  FOREIGN KEY(transaction_id) REFERENCES stockTransaction(id) ON DELETE CASCADE);

[SQL_SETUP_CREATE_VIEW_STOCK_IMMUTABLE_TRANSACTION]
CREATE VIEW stockImmutableTransaction AS
SELECT transactionEntry.id
FROM stockTransaction transactionEntry
LEFT JOIN stockImportRecord importRecord ON importRecord.id = transactionEntry.importRecord_id
LEFT JOIN stockImportBatch importBatch ON importBatch.id = importRecord.importBatch_id
WHERE (transactionEntry.importRecord_id IS NULL AND transactionEntry.transactionStatus <> 1)
   OR importBatch.importStatus = 3;

[SQL_SETUP_CREATE_TRIGGER_STOCK_TRANSACTION_VALIDATE_INSERT]
CREATE TRIGGER validate_stocktransaction_insert
BEFORE INSERT ON stockTransaction
WHEN ((SELECT sourceType FROM stockDataSource WHERE id = NEW.source_id) <> 1
      AND NEW.importRecord_id IS NULL)
    OR (NEW.importRecord_id IS NOT NULL AND NOT EXISTS (
      SELECT 1
      FROM stockImportRecord importRecord
      JOIN stockImportBatch importBatch ON importBatch.id = importRecord.importBatch_id
      WHERE importRecord.id = NEW.importRecord_id
        AND importBatch.source_id = NEW.source_id
        AND importBatch.importStatus = 2))
    OR (NEW.transactionStatus = 2 AND NEW.importRecord_id IS NULL)
    OR (NEW.transactionType = 18 AND NEW.reversalOfTransaction_id IS NULL)
    OR (NEW.transactionType <> 18 AND NEW.reversalOfTransaction_id IS NOT NULL)
    OR (NEW.transactionType = 8 AND NEW.reconciliationStatement_id IS NULL)
    OR (NEW.transactionType <> 8 AND NEW.reconciliationStatement_id IS NOT NULL)
    OR (NEW.reversalOfTransaction_id IS NOT NULL AND NOT EXISTS (
      SELECT 1
      FROM stockTransaction original
      WHERE original.id = NEW.reversalOfTransaction_id
        AND original.portfolio_id = NEW.portfolio_id
        AND original.transactionType <> 18))
    OR (NEW.reconciliationStatement_id IS NOT NULL AND NOT EXISTS (
      SELECT 1
      FROM stockPortfolioStatement statement
      WHERE statement.id = NEW.reconciliationStatement_id
        AND statement.portfolio_id = NEW.portfolio_id))
BEGIN
  SELECT RAISE(FAIL, 'invalid reversal or reconciliation transaction');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_TRANSACTION_VALIDATE_UPDATE]
CREATE TRIGGER validate_stocktransaction_update
BEFORE UPDATE ON stockTransaction
WHEN (((SELECT sourceType FROM stockDataSource WHERE id = NEW.source_id) <> 1
          AND NEW.importRecord_id IS NULL)
      OR (NEW.importRecord_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM stockImportRecord importRecord
        JOIN stockImportBatch importBatch ON importBatch.id = importRecord.importBatch_id
        WHERE importRecord.id = NEW.importRecord_id
          AND importBatch.source_id = NEW.source_id
          AND importBatch.importStatus = 2))
      OR (OLD.transactionStatus = 1
        AND NEW.transactionStatus = 2
        AND NOT EXISTS (
          SELECT 1 FROM stockTransactionSecurityLeg securityLeg WHERE securityLeg.transaction_id = OLD.id)
        AND NOT EXISTS (
          SELECT 1 FROM stockTransactionCashLeg cashLeg WHERE cashLeg.transaction_id = OLD.id))
      OR (NEW.transactionType = 18 AND NEW.reversalOfTransaction_id IS NULL)
      OR (NEW.transactionType <> 18 AND NEW.reversalOfTransaction_id IS NOT NULL)
      OR (NEW.transactionType = 8 AND NEW.reconciliationStatement_id IS NULL)
      OR (NEW.transactionType <> 8 AND NEW.reconciliationStatement_id IS NOT NULL)
      OR (NEW.reversalOfTransaction_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM stockTransaction original
        WHERE original.id = NEW.reversalOfTransaction_id
          AND original.portfolio_id = NEW.portfolio_id
          AND original.transactionType <> 18))
      OR (NEW.reconciliationStatement_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM stockPortfolioStatement statement
        WHERE statement.id = NEW.reconciliationStatement_id
          AND statement.portfolio_id = NEW.portfolio_id)))
BEGIN
  SELECT RAISE(FAIL, 'invalid reversal or reconciliation transaction');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_TRANSACTION_BLOCK_UPDATE]
CREATE TRIGGER block_immutable_stocktransaction_update
BEFORE UPDATE ON stockTransaction
WHEN EXISTS (SELECT 1 FROM stockImmutableTransaction immutable WHERE immutable.id = OLD.id)
BEGIN
  SELECT RAISE(FAIL, 'settled or imported stockTransaction is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_TRANSACTION_BLOCK_DELETE]
CREATE TRIGGER block_immutable_stocktransaction_delete
BEFORE DELETE ON stockTransaction
WHEN EXISTS (SELECT 1 FROM stockImmutableTransaction immutable WHERE immutable.id = OLD.id)
BEGIN
  SELECT RAISE(FAIL, 'settled or imported stockTransaction is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_TRANSACTION_EXTERNAL_REFERENCE_BLOCK_UPDATE]
CREATE TRIGGER block_immutable_stocktransactionreference_update
BEFORE UPDATE ON stockTransactionExternalReference
WHEN EXISTS (SELECT 1 FROM stockImmutableTransaction immutable WHERE immutable.id = OLD.transaction_id)
BEGIN
  SELECT RAISE(FAIL, 'reference of settled or imported transaction is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_TRANSACTION_EXTERNAL_REFERENCE_BLOCK_DELETE]
CREATE TRIGGER block_immutable_stocktransactionreference_delete
BEFORE DELETE ON stockTransactionExternalReference
WHEN EXISTS (SELECT 1 FROM stockImmutableTransaction immutable WHERE immutable.id = OLD.transaction_id)
BEGIN
  SELECT RAISE(FAIL, 'reference of settled or imported transaction is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_TRANSACTION_EXTERNAL_REFERENCE_BLOCK_INSERT]
CREATE TRIGGER block_immutable_stocktransactionreference_insert
BEFORE INSERT ON stockTransactionExternalReference
WHEN EXISTS (SELECT 1 FROM stockImmutableTransaction immutable WHERE immutable.id = NEW.transaction_id)
BEGIN
  SELECT RAISE(FAIL, 'reference of settled or imported transaction is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_SECURITY_LEG_VALIDATE_INSERT]
CREATE TRIGGER validate_stocktransactionsecurityleg_insert
BEFORE INSERT ON stockTransactionSecurityLeg
WHEN NEW.quantityType = 2
    AND (SELECT nominalCurrency FROM stockSecurity WHERE id = NEW.security_id) IS NULL
BEGIN
  SELECT RAISE(FAIL, 'nominal security leg requires a nominal currency');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_SECURITY_LEG_BLOCK_UPDATE]
CREATE TRIGGER block_immutable_stocktransactionsecurityleg_update
BEFORE UPDATE ON stockTransactionSecurityLeg
WHEN EXISTS (SELECT 1 FROM stockImmutableTransaction immutable WHERE immutable.id = OLD.transaction_id)
BEGIN
  SELECT RAISE(FAIL, 'security leg of settled or imported transaction is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_SECURITY_LEG_BLOCK_DELETE]
CREATE TRIGGER block_immutable_stocktransactionsecurityleg_delete
BEFORE DELETE ON stockTransactionSecurityLeg
WHEN EXISTS (SELECT 1 FROM stockImmutableTransaction immutable WHERE immutable.id = OLD.transaction_id)
BEGIN
  SELECT RAISE(FAIL, 'security leg of settled or imported transaction is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_SECURITY_LEG_BLOCK_INSERT]
CREATE TRIGGER block_immutable_stocktransactionsecurityleg_insert
BEFORE INSERT ON stockTransactionSecurityLeg
WHEN EXISTS (SELECT 1 FROM stockImmutableTransaction immutable WHERE immutable.id = NEW.transaction_id)
BEGIN
  SELECT RAISE(FAIL, 'security leg of settled or imported transaction is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_CASH_LEG_VALIDATE_BOOKING_INSERT]
CREATE TRIGGER validate_stocktransactioncashleg_booking_insert
BEFORE INSERT ON stockTransactionCashLeg
WHEN NEW.booking_id IS NOT NULL
    AND NOT EXISTS (
      SELECT 1
      FROM booking linkedBooking
      WHERE linkedBooking.id = NEW.booking_id
        AND linkedBooking.account_id = NEW.account_id)
BEGIN
  SELECT RAISE(FAIL, 'cash leg and booking must use the same bankAccount');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_CASH_LEG_VALIDATE_BOOKING_UPDATE]
CREATE TRIGGER validate_stocktransactioncashleg_booking_update
BEFORE UPDATE OF account_id, booking_id ON stockTransactionCashLeg
WHEN NEW.booking_id IS NOT NULL
    AND NOT EXISTS (
      SELECT 1
      FROM booking linkedBooking
      WHERE linkedBooking.id = NEW.booking_id
        AND linkedBooking.account_id = NEW.account_id)
BEGIN
  SELECT RAISE(FAIL, 'cash leg and booking must use the same bankAccount');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_CASH_LEG_BLOCK_UPDATE]
CREATE TRIGGER block_immutable_stocktransactioncashleg_update
BEFORE UPDATE ON stockTransactionCashLeg
WHEN EXISTS (SELECT 1 FROM stockImmutableTransaction immutable WHERE immutable.id = OLD.transaction_id)
BEGIN
  SELECT RAISE(FAIL, 'cash leg of settled or imported transaction is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_CASH_LEG_BLOCK_DELETE]
CREATE TRIGGER block_immutable_stocktransactioncashleg_delete
BEFORE DELETE ON stockTransactionCashLeg
WHEN EXISTS (SELECT 1 FROM stockImmutableTransaction immutable WHERE immutable.id = OLD.transaction_id)
BEGIN
  SELECT RAISE(FAIL, 'cash leg of settled or imported transaction is immutable');
END;

[SQL_SETUP_CREATE_TRIGGER_STOCK_CASH_LEG_BLOCK_INSERT]
CREATE TRIGGER block_immutable_stocktransactioncashleg_insert
BEFORE INSERT ON stockTransactionCashLeg
WHEN EXISTS (SELECT 1 FROM stockImmutableTransaction immutable WHERE immutable.id = NEW.transaction_id)
BEGIN
  SELECT RAISE(FAIL, 'cash leg of settled or imported transaction is immutable');
END;

[SQL_SETUP_CREATE_VIEW_STOCK_PORTFOLIO_POSITION]
CREATE VIEW stockPortfolioPosition AS
SELECT
  transactionEntry.portfolio_id,
  securityLeg.security_id,
  securityLeg.quantityType,
  SUM(securityLeg.quantityE9) AS quantityE9,
  MAX(transactionEntry.settledAt) AS lastSettledAt
FROM stockTransaction transactionEntry
JOIN stockTransactionSecurityLeg securityLeg
  ON securityLeg.transaction_id = transactionEntry.id
WHERE transactionEntry.transactionStatus = 2
  AND securityLeg.legRole <> 2
GROUP BY transactionEntry.portfolio_id, securityLeg.security_id, securityLeg.quantityType;
