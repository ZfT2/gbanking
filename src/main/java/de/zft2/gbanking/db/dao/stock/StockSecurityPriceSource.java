package de.zft2.gbanking.db.dao.stock;

@StockTable(name = "stockSecurityPriceSource", parentColumn = "security_id", createdAt = true, updatedAt = true)
public class StockSecurityPriceSource extends StockDao {

	private int securityId;
	private int sourceId;
	private String providerSymbol;
	private String marketIdentifierCode;
	private int priority;
	private boolean enabled = true;

	public int getSecurityId() { return securityId; }
	public void setSecurityId(int securityId) { this.securityId = securityId; }
	public int getSourceId() { return sourceId; }
	public void setSourceId(int sourceId) { this.sourceId = sourceId; }
	public String getProviderSymbol() { return providerSymbol; }
	public void setProviderSymbol(String providerSymbol) { this.providerSymbol = providerSymbol; }
	public String getMarketIdentifierCode() { return marketIdentifierCode; }
	public void setMarketIdentifierCode(String marketIdentifierCode) { this.marketIdentifierCode = marketIdentifierCode; }
	public int getPriority() { return priority; }
	public void setPriority(int priority) { this.priority = priority; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
