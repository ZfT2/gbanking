package de.zft2.gbanking.db.dao.stock;

@StockTable(name = "stockTransactionExternalReference", parentColumn = "transaction_id", createdAt = true)
public class StockTransactionExternalReference extends StockDao {

	private int transactionId;
	private int portfolioId;
	private int sourceId;
	private String referenceType;
	private String referenceValue;

	public int getTransactionId() { return transactionId; }
	public void setTransactionId(int transactionId) { this.transactionId = transactionId; }
	public int getPortfolioId() { return portfolioId; }
	public void setPortfolioId(int portfolioId) { this.portfolioId = portfolioId; }
	public int getSourceId() { return sourceId; }
	public void setSourceId(int sourceId) { this.sourceId = sourceId; }
	public String getReferenceType() { return referenceType; }
	public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
	public String getReferenceValue() { return referenceValue; }
	public void setReferenceValue(String referenceValue) { this.referenceValue = referenceValue; }
}
