package de.zft2.gbanking.db.dao.stock;

@StockTable(name = "stockSecurityExchangeLink", createdAt = true)
public class StockSecurityExchangeLink extends StockDao {

	private int sourceLegId;
	private int targetLegId;

	public int getSourceLegId() { return sourceLegId; }
	public void setSourceLegId(int sourceLegId) { this.sourceLegId = sourceLegId; }
	public int getTargetLegId() { return targetLegId; }
	public void setTargetLegId(int targetLegId) { this.targetLegId = targetLegId; }
}
