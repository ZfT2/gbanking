package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDateTime;

import de.zft2.gbanking.db.dao.enu.StockTransactionStatus;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;

@StockTable(name = "stockTransaction", parentColumn = "portfolio_id", createdAt = true, updatedAt = true)
public class StockTransaction extends StockDao {

	private int portfolioId;
	private int sourceId;
	private Integer importRecordId;
	private StockTransactionType transactionType;
	private StockTransactionStatus transactionStatus;
	private LocalDateTime tradeAt;
	private LocalDateTime settlementDueAt;
	private LocalDateTime settledAt;
	private LocalDateTime cashValueAt;
	private LocalDateTime providerBookedAt;
	private boolean settlementDateInferred;
	private Integer reversalOfTransactionId;
	private Integer reconciliationStatementId;
	private String fingerprint;

	public int getPortfolioId() { return portfolioId; }
	public void setPortfolioId(int portfolioId) { this.portfolioId = portfolioId; }
	public int getSourceId() { return sourceId; }
	public void setSourceId(int sourceId) { this.sourceId = sourceId; }
	public Integer getImportRecordId() { return importRecordId; }
	public void setImportRecordId(Integer importRecordId) { this.importRecordId = importRecordId; }
	public StockTransactionType getTransactionType() { return transactionType; }
	public void setTransactionType(StockTransactionType transactionType) { this.transactionType = transactionType; }
	public StockTransactionStatus getTransactionStatus() { return transactionStatus; }
	public void setTransactionStatus(StockTransactionStatus value) { this.transactionStatus = value; }
	public LocalDateTime getTradeAt() { return tradeAt; }
	public void setTradeAt(LocalDateTime tradeAt) { this.tradeAt = tradeAt; }
	public LocalDateTime getSettlementDueAt() { return settlementDueAt; }
	public void setSettlementDueAt(LocalDateTime settlementDueAt) { this.settlementDueAt = settlementDueAt; }
	public LocalDateTime getSettledAt() { return settledAt; }
	public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }
	public LocalDateTime getCashValueAt() { return cashValueAt; }
	public void setCashValueAt(LocalDateTime cashValueAt) { this.cashValueAt = cashValueAt; }
	public LocalDateTime getProviderBookedAt() { return providerBookedAt; }
	public void setProviderBookedAt(LocalDateTime providerBookedAt) { this.providerBookedAt = providerBookedAt; }
	public boolean isSettlementDateInferred() { return settlementDateInferred; }
	public void setSettlementDateInferred(boolean value) { this.settlementDateInferred = value; }
	public Integer getReversalOfTransactionId() { return reversalOfTransactionId; }
	public void setReversalOfTransactionId(Integer value) { this.reversalOfTransactionId = value; }
	public Integer getReconciliationStatementId() { return reconciliationStatementId; }
	public void setReconciliationStatementId(Integer value) { this.reconciliationStatementId = value; }
	public String getFingerprint() { return fingerprint; }
	public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
}
