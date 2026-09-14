package de.zft2.gbanking.db.dao.stock;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockPriceBasis;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityLegRole;

@StockTable(name = "stockTransactionSecurityLeg", parentColumn = "transaction_id", createdAt = true)
public class StockTransactionSecurityLeg extends StockDao {

	private int transactionId;
	private int legNumber;
	private int securityId;
	private StockSecurityLegRole legRole;
	private long quantityE9;
	private StockQuantityType quantityType;
	private Long priceE8;
	private Currency priceCurrency;
	private StockQuotationType quotationType;
	private StockPriceBasis priceBasis;
	private Integer accruedInterestDays;

	public int getTransactionId() { return transactionId; }
	public void setTransactionId(int transactionId) { this.transactionId = transactionId; }
	public int getLegNumber() { return legNumber; }
	public void setLegNumber(int legNumber) { this.legNumber = legNumber; }
	public int getSecurityId() { return securityId; }
	public void setSecurityId(int securityId) { this.securityId = securityId; }
	public StockSecurityLegRole getLegRole() { return legRole; }
	public void setLegRole(StockSecurityLegRole legRole) { this.legRole = legRole; }
	public long getQuantityE9() { return quantityE9; }
	public void setQuantityE9(long quantityE9) { this.quantityE9 = quantityE9; }
	public StockQuantityType getQuantityType() { return quantityType; }
	public void setQuantityType(StockQuantityType quantityType) { this.quantityType = quantityType; }
	public Long getPriceE8() { return priceE8; }
	public void setPriceE8(Long priceE8) { this.priceE8 = priceE8; }
	public Currency getPriceCurrency() { return priceCurrency; }
	public void setPriceCurrency(Currency priceCurrency) { this.priceCurrency = priceCurrency; }
	public StockQuotationType getQuotationType() { return quotationType; }
	public void setQuotationType(StockQuotationType quotationType) { this.quotationType = quotationType; }
	public StockPriceBasis getPriceBasis() { return priceBasis; }
	public void setPriceBasis(StockPriceBasis priceBasis) { this.priceBasis = priceBasis; }
	public Integer getAccruedInterestDays() { return accruedInterestDays; }
	public void setAccruedInterestDays(Integer accruedInterestDays) { this.accruedInterestDays = accruedInterestDays; }
}
