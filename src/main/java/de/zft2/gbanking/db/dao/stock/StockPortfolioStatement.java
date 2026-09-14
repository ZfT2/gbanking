package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDateTime;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockStatementStatus;

@StockTable(name = "stockPortfolioStatement", parentColumn = "portfolio_id", createdAt = true)
public class StockPortfolioStatement extends StockDao {

	private int portfolioId;
	private int sourceId;
	private Integer importRecordId;
	private LocalDateTime statementAt;
	private StockStatementStatus statementStatus;
	private String externalReference;
	private Long reportedTotalValueMinor;
	private Long reportedAccruedInterestMinor;
	private Currency valueCurrency;

	public int getPortfolioId() { return portfolioId; }
	public void setPortfolioId(int portfolioId) { this.portfolioId = portfolioId; }
	public int getSourceId() { return sourceId; }
	public void setSourceId(int sourceId) { this.sourceId = sourceId; }
	public Integer getImportRecordId() { return importRecordId; }
	public void setImportRecordId(Integer importRecordId) { this.importRecordId = importRecordId; }
	public LocalDateTime getStatementAt() { return statementAt; }
	public void setStatementAt(LocalDateTime statementAt) { this.statementAt = statementAt; }
	public StockStatementStatus getStatementStatus() { return statementStatus; }
	public void setStatementStatus(StockStatementStatus statementStatus) { this.statementStatus = statementStatus; }
	public String getExternalReference() { return externalReference; }
	public void setExternalReference(String externalReference) { this.externalReference = externalReference; }
	public Long getReportedTotalValueMinor() { return reportedTotalValueMinor; }
	public void setReportedTotalValueMinor(Long reportedTotalValueMinor) { this.reportedTotalValueMinor = reportedTotalValueMinor; }
	public Long getReportedAccruedInterestMinor() { return reportedAccruedInterestMinor; }
	public void setReportedAccruedInterestMinor(Long value) { this.reportedAccruedInterestMinor = value; }
	public Currency getValueCurrency() { return valueCurrency; }
	public void setValueCurrency(Currency valueCurrency) { this.valueCurrency = valueCurrency; }
}
