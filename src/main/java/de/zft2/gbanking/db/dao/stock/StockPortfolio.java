package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDate;

@StockTable(name = "stockPortfolio", createdAt = true, updatedAt = true)
public class StockPortfolio extends StockDao {

	private int accountId;
	private int currentSettlementRelationId;
	private LocalDate openedAt;
	private LocalDate closedAt;

	public int getAccountId() { return accountId; }
	public void setAccountId(int accountId) { this.accountId = accountId; }
	public int getCurrentSettlementRelationId() { return currentSettlementRelationId; }
	public void setCurrentSettlementRelationId(int currentSettlementRelationId) { this.currentSettlementRelationId = currentSettlementRelationId; }
	public LocalDate getOpenedAt() { return openedAt; }
	public void setOpenedAt(LocalDate openedAt) { this.openedAt = openedAt; }
	public LocalDate getClosedAt() { return closedAt; }
	public void setClosedAt(LocalDate closedAt) { this.closedAt = closedAt; }
}
