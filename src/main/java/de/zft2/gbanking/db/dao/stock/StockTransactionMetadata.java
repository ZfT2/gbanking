package de.zft2.gbanking.db.dao.stock;

@StockTable(name = "stockTransactionMetadata", idColumn = "transaction_id", parentColumn = "transaction_id",
		generatedId = false, updatedAt = true)
public class StockTransactionMetadata extends StockDao {

	private String note;
	private String tags;

	public int getTransactionId() { return getId(); }
	public void setTransactionId(int transactionId) { setId(transactionId); }
	public String getNote() { return note; }
	public void setNote(String note) { this.note = note; }
	public String getTags() { return tags; }
	public void setTags(String tags) { this.tags = tags; }
}
