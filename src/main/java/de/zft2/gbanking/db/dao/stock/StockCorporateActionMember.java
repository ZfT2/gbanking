package de.zft2.gbanking.db.dao.stock;

import de.zft2.gbanking.db.dao.enu.StockCorporateActionMemberRole;

@StockTable(name = "stockCorporateActionMember", parentColumn = "association_id")
public class StockCorporateActionMember extends StockDao {
	private int associationId;
	private int transactionId;
	private StockCorporateActionMemberRole role;

	public int getAssociationId() { return associationId; }
	public void setAssociationId(int id) { associationId = id; }
	public int getTransactionId() { return transactionId; }
	public void setTransactionId(int id) { transactionId = id; }
	public StockCorporateActionMemberRole getRole() { return role; }
	public void setRole(StockCorporateActionMemberRole role) { this.role = role; }
}
