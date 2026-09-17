package de.zft2.gbanking.db.dao.stock;

import java.util.List;

public final class StockDaoTypes {

	private static final List<Class<? extends StockDao>> ALL = List.of(
			StockDataSource.class,
			StockImportBatch.class,
			StockImportRecord.class,
			StockPortfolio.class,
			StockPortfolioSettlementAccount.class,
			StockSecurity.class,
			StockSecurityIdentifier.class,
			StockSecurityInterestTerms.class,
			StockCouponPeriod.class,
			StockSecurityFactor.class,
			StockSecurityPriceSource.class,
			StockSecurityPrice.class,
			StockExchangeRateSource.class,
			StockExchangeRate.class,
			StockPortfolioStatement.class,
			StockPortfolioStatementPosition.class,
			StockPortfolioStatementSubBalance.class,
			StockTransaction.class,
			StockTransactionExternalReference.class,
			StockTransactionSecurityLeg.class,
			StockTransactionCashLeg.class,
			StockTransactionMetadata.class,
			StockImmutableTransaction.class,
			StockPortfolioPosition.class);

	private StockDaoTypes() {
	}

	public static List<Class<? extends StockDao>> all() {
		return ALL;
	}
}
