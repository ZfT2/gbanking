[SQL_SELECT_STOCK_SECURITY_LEGS_BY_PORTFOLIO]
SELECT leg.* FROM stockTransactionSecurityLeg leg
JOIN stockTransaction tx ON tx.id = leg.transaction_id
WHERE tx.portfolio_id = ?
ORDER BY leg.id;

[SQL_SELECT_STOCK_CASH_LEGS_BY_PORTFOLIO]
SELECT leg.* FROM stockTransactionCashLeg leg
JOIN stockTransaction tx ON tx.id = leg.transaction_id
WHERE tx.portfolio_id = ?
ORDER BY leg.id;

[SQL_SELECT_STOCK_SECURITY_PRICE_SUPERSEDING]
SELECT * FROM stockSecurityPrice WHERE supersedesPrice_id = ?;

[SQL_SELECT_STOCK_CASH_LEGS_BY_ACCOUNT_AND_DATE_RANGE]
SELECT * FROM stockTransactionCashLeg
WHERE account_id = ? AND date(valueAt) BETWEEN ? AND ?
ORDER BY id;

[SQL_SELECT_STOCK_CASH_LEGS_BY_BOOKING]
SELECT * FROM stockTransactionCashLeg WHERE booking_id = ?;

[SQL_SELECT_STOCK_SETTLEMENT_BOOKINGS_BY_PORTFOLIO]
${SQL_SELECT_ALL_BOOKINGS_FULL_BASE}
WHERE b.id IN (
  SELECT leg.booking_id FROM stockTransactionCashLeg leg
  JOIN stockTransaction tx ON tx.id = leg.transaction_id
  WHERE tx.portfolio_id = ?);
