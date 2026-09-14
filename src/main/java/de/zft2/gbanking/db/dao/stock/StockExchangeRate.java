package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDateTime;

@StockTable(name = "stockExchangeRate", parentColumn = "exchangeRateSource_id", createdAt = true,
		writeMode = StockWriteMode.APPEND_ONLY)
public class StockExchangeRate extends StockDao {

	private int exchangeRateSourceId;
	private Integer importRecordId;
	private LocalDateTime quotedAt;
	private long rateE12;
	private String externalReference;
	private Integer supersedesExchangeRateId;

	public int getExchangeRateSourceId() { return exchangeRateSourceId; }
	public void setExchangeRateSourceId(int exchangeRateSourceId) { this.exchangeRateSourceId = exchangeRateSourceId; }
	public Integer getImportRecordId() { return importRecordId; }
	public void setImportRecordId(Integer importRecordId) { this.importRecordId = importRecordId; }
	public LocalDateTime getQuotedAt() { return quotedAt; }
	public void setQuotedAt(LocalDateTime quotedAt) { this.quotedAt = quotedAt; }
	public long getRateE12() { return rateE12; }
	public void setRateE12(long rateE12) { this.rateE12 = rateE12; }
	public String getExternalReference() { return externalReference; }
	public void setExternalReference(String externalReference) { this.externalReference = externalReference; }
	public Integer getSupersedesExchangeRateId() { return supersedesExchangeRateId; }
	public void setSupersedesExchangeRateId(Integer supersedesExchangeRateId) { this.supersedesExchangeRateId = supersedesExchangeRateId; }
}
