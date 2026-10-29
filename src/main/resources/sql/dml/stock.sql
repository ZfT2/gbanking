[SQL_SELECT_STOCK_SECURITY_LEGS_BY_PORTFOLIO]
SELECT leg.* FROM stockTransactionSecurityLeg leg
JOIN stockTransaction tx ON tx.id = leg.transaction_id
WHERE tx.portfolio_id = ?
ORDER BY leg.id;

[SQL_SELECT_STOCK_SECURITY_PRICE_SUPERSEDING]
SELECT * FROM stockSecurityPrice WHERE supersedesPrice_id = ?;
