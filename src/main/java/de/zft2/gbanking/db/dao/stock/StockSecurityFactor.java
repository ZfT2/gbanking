package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDate;

import de.zft2.gbanking.db.dao.enu.StockFactorType;

@StockTable(name = "stockSecurityFactor", parentColumn = "security_id", createdAt = true)
public class StockSecurityFactor extends StockDao {

	private int securityId;
	private int sourceId;
	private Integer importRecordId;
	private StockFactorType factorType;
	private LocalDate effectiveAt;
	private long factorE12;
	private Long numerator;
	private Long denominator;
	private String externalReference;

	public int getSecurityId() { return securityId; }
	public void setSecurityId(int securityId) { this.securityId = securityId; }
	public int getSourceId() { return sourceId; }
	public void setSourceId(int sourceId) { this.sourceId = sourceId; }
	public Integer getImportRecordId() { return importRecordId; }
	public void setImportRecordId(Integer importRecordId) { this.importRecordId = importRecordId; }
	public StockFactorType getFactorType() { return factorType; }
	public void setFactorType(StockFactorType factorType) { this.factorType = factorType; }
	public LocalDate getEffectiveAt() { return effectiveAt; }
	public void setEffectiveAt(LocalDate effectiveAt) { this.effectiveAt = effectiveAt; }
	public long getFactorE12() { return factorE12; }
	public void setFactorE12(long factorE12) { this.factorE12 = factorE12; }
	public Long getNumerator() { return numerator; }
	public void setNumerator(Long numerator) { this.numerator = numerator; }
	public Long getDenominator() { return denominator; }
	public void setDenominator(Long denominator) { this.denominator = denominator; }
	public String getExternalReference() { return externalReference; }
	public void setExternalReference(String externalReference) { this.externalReference = externalReference; }
}
