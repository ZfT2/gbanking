package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDate;

import de.zft2.gbanking.db.dao.enu.StockIdentifierType;

@StockTable(name = "stockSecurityIdentifier", parentColumn = "security_id", createdAt = true, updatedAt = true)
public class StockSecurityIdentifier extends StockDao {

	private int securityId;
	private Integer sourceId;
	private StockIdentifierType identifierType;
	private String identifierValue;
	private String marketIdentifierCode;
	private LocalDate validFrom;
	private LocalDate validTo;

	public int getSecurityId() { return securityId; }
	public void setSecurityId(int securityId) { this.securityId = securityId; }
	public Integer getSourceId() { return sourceId; }
	public void setSourceId(Integer sourceId) { this.sourceId = sourceId; }
	public StockIdentifierType getIdentifierType() { return identifierType; }
	public void setIdentifierType(StockIdentifierType identifierType) { this.identifierType = identifierType; }
	public String getIdentifierValue() { return identifierValue; }
	public void setIdentifierValue(String identifierValue) { this.identifierValue = identifierValue; }
	public String getMarketIdentifierCode() { return marketIdentifierCode; }
	public void setMarketIdentifierCode(String marketIdentifierCode) { this.marketIdentifierCode = marketIdentifierCode; }
	public LocalDate getValidFrom() { return validFrom; }
	public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
	public LocalDate getValidTo() { return validTo; }
	public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
}
