package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDateTime;

import de.zft2.gbanking.db.dao.DaoView;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;

@StockTable(name = "stockPortfolioPosition", idColumn = "", parentColumn = "portfolio_id",
		generatedId = false, writeMode = StockWriteMode.READ_ONLY)
public class StockPortfolioPosition extends StockDao implements DaoView {

	private int portfolioId;
	private int securityId;
	private StockQuantityType quantityType;
	private long quantityE9;
	private LocalDateTime lastSettledAt;

	public int getPortfolioId() { return portfolioId; }
	public void setPortfolioId(int portfolioId) { this.portfolioId = portfolioId; }
	public int getSecurityId() { return securityId; }
	public void setSecurityId(int securityId) { this.securityId = securityId; }
	public StockQuantityType getQuantityType() { return quantityType; }
	public void setQuantityType(StockQuantityType quantityType) { this.quantityType = quantityType; }
	public long getQuantityE9() { return quantityE9; }
	public void setQuantityE9(long quantityE9) { this.quantityE9 = quantityE9; }
	public LocalDateTime getLastSettledAt() { return lastSettledAt; }
	public void setLastSettledAt(LocalDateTime lastSettledAt) { this.lastSettledAt = lastSettledAt; }
}
