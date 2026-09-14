package de.zft2.gbanking.db.dao.stock;

import de.zft2.gbanking.db.dao.enu.Currency;

@StockTable(name = "stockExchangeRateSource", parentColumn = "source_id", createdAt = true, updatedAt = true)
public class StockExchangeRateSource extends StockDao {

	private int sourceId;
	private Currency baseCurrency;
	private Currency quoteCurrency;
	private int priority;
	private boolean enabled = true;

	public int getSourceId() { return sourceId; }
	public void setSourceId(int sourceId) { this.sourceId = sourceId; }
	public Currency getBaseCurrency() { return baseCurrency; }
	public void setBaseCurrency(Currency baseCurrency) { this.baseCurrency = baseCurrency; }
	public Currency getQuoteCurrency() { return quoteCurrency; }
	public void setQuoteCurrency(Currency quoteCurrency) { this.quoteCurrency = quoteCurrency; }
	public int getPriority() { return priority; }
	public void setPriority(int priority) { this.priority = priority; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
