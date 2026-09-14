package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDate;

@StockTable(name = "stockPortfolioSettlementAccount", parentColumn = "portfolio_id", createdAt = true, updatedAt = true)
public class StockPortfolioSettlementAccount extends StockDao {

	private int portfolioId;
	private int accountId;
	private LocalDate validFrom;
	private LocalDate validTo;

	public int getPortfolioId() { return portfolioId; }
	public void setPortfolioId(int portfolioId) { this.portfolioId = portfolioId; }
	public int getAccountId() { return accountId; }
	public void setAccountId(int accountId) { this.accountId = accountId; }
	public LocalDate getValidFrom() { return validFrom; }
	public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
	public LocalDate getValidTo() { return validTo; }
	public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
}
