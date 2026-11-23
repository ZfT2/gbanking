package de.zft2.gbanking.db.dao.stock;

@StockTable(name = "stockTransferLink", createdAt = true)
public class StockTransferLink extends StockDao {

	private int outgoingTransactionId;
	private int incomingTransactionId;

	public int getOutgoingTransactionId() { return outgoingTransactionId; }
	public void setOutgoingTransactionId(int outgoingTransactionId) { this.outgoingTransactionId = outgoingTransactionId; }
	public int getIncomingTransactionId() { return incomingTransactionId; }
	public void setIncomingTransactionId(int incomingTransactionId) { this.incomingTransactionId = incomingTransactionId; }
}
