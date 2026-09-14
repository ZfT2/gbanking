package de.zft2.gbanking.db.dao.stock;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockPriceBasis;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;

@StockTable(name = "stockPortfolioStatementPosition", parentColumn = "statement_id", createdAt = true)
public class StockPortfolioStatementPosition extends StockDao {

	private int statementId;
	private int securityId;
	private long quantityE9;
	private StockQuantityType quantityType;
	private Long reportedPriceE8;
	private Currency priceCurrency;
	private StockQuotationType quotationType;
	private StockPriceBasis priceBasis;
	private Long reportedValueMinor;
	private Long accruedInterestMinor;
	private Currency valueCurrency;

	public int getStatementId() { return statementId; }
	public void setStatementId(int statementId) { this.statementId = statementId; }
	public int getSecurityId() { return securityId; }
	public void setSecurityId(int securityId) { this.securityId = securityId; }
	public long getQuantityE9() { return quantityE9; }
	public void setQuantityE9(long quantityE9) { this.quantityE9 = quantityE9; }
	public StockQuantityType getQuantityType() { return quantityType; }
	public void setQuantityType(StockQuantityType quantityType) { this.quantityType = quantityType; }
	public Long getReportedPriceE8() { return reportedPriceE8; }
	public void setReportedPriceE8(Long reportedPriceE8) { this.reportedPriceE8 = reportedPriceE8; }
	public Currency getPriceCurrency() { return priceCurrency; }
	public void setPriceCurrency(Currency priceCurrency) { this.priceCurrency = priceCurrency; }
	public StockQuotationType getQuotationType() { return quotationType; }
	public void setQuotationType(StockQuotationType quotationType) { this.quotationType = quotationType; }
	public StockPriceBasis getPriceBasis() { return priceBasis; }
	public void setPriceBasis(StockPriceBasis priceBasis) { this.priceBasis = priceBasis; }
	public Long getReportedValueMinor() { return reportedValueMinor; }
	public void setReportedValueMinor(Long reportedValueMinor) { this.reportedValueMinor = reportedValueMinor; }
	public Long getAccruedInterestMinor() { return accruedInterestMinor; }
	public void setAccruedInterestMinor(Long accruedInterestMinor) { this.accruedInterestMinor = accruedInterestMinor; }
	public Currency getValueCurrency() { return valueCurrency; }
	public void setValueCurrency(Currency valueCurrency) { this.valueCurrency = valueCurrency; }
}
