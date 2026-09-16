package de.zft2.gbanking.service.stock;

public record StockPortfolioFinTsRetrievalResult(boolean successful, boolean wrongPin, boolean unchanged,
		int positionCount, int adjustmentCount, String errorMessage) {

	public static StockPortfolioFinTsRetrievalResult success(int positionCount, int adjustmentCount) {
		return new StockPortfolioFinTsRetrievalResult(true, false, false, positionCount, adjustmentCount, null);
	}

	public static StockPortfolioFinTsRetrievalResult unchanged(int positionCount) {
		return new StockPortfolioFinTsRetrievalResult(true, false, true, positionCount, 0, null);
	}

	public static StockPortfolioFinTsRetrievalResult failure(String errorMessage) {
		return new StockPortfolioFinTsRetrievalResult(false, false, false, 0, 0, errorMessage);
	}

	public static StockPortfolioFinTsRetrievalResult wrongPinFailure(String errorMessage) {
		return new StockPortfolioFinTsRetrievalResult(false, true, false, 0, 0, errorMessage);
	}
}
