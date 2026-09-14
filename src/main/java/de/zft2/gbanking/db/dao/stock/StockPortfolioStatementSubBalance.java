package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDate;

import de.zft2.gbanking.db.dao.enu.StockSubBalanceQualifier;

@StockTable(name = "stockPortfolioStatementSubBalance", parentColumn = "statementPosition_id", createdAt = true)
public class StockPortfolioStatementSubBalance extends StockDao {

	private int statementPositionId;
	private StockSubBalanceQualifier qualifier;
	private long quantityE9;
	private boolean locked;
	private LocalDate lockedUntil;
	private String custodyCountry;
	private String custodyType;
	private String custodyPlace;
	private String comment;

	public int getStatementPositionId() { return statementPositionId; }
	public void setStatementPositionId(int value) { this.statementPositionId = value; }
	public StockSubBalanceQualifier getQualifier() { return qualifier; }
	public void setQualifier(StockSubBalanceQualifier qualifier) { this.qualifier = qualifier; }
	public long getQuantityE9() { return quantityE9; }
	public void setQuantityE9(long quantityE9) { this.quantityE9 = quantityE9; }
	public boolean isLocked() { return locked; }
	public void setLocked(boolean locked) { this.locked = locked; }
	public LocalDate getLockedUntil() { return lockedUntil; }
	public void setLockedUntil(LocalDate lockedUntil) { this.lockedUntil = lockedUntil; }
	public String getCustodyCountry() { return custodyCountry; }
	public void setCustodyCountry(String custodyCountry) { this.custodyCountry = custodyCountry; }
	public String getCustodyType() { return custodyType; }
	public void setCustodyType(String custodyType) { this.custodyType = custodyType; }
	public String getCustodyPlace() { return custodyPlace; }
	public void setCustodyPlace(String custodyPlace) { this.custodyPlace = custodyPlace; }
	public String getComment() { return comment; }
	public void setComment(String comment) { this.comment = comment; }
}
