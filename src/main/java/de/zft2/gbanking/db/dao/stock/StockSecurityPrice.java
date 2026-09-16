package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDateTime;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockPriceBasis;
import de.zft2.gbanking.db.dao.enu.StockPriceType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;

@StockTable(name = "stockSecurityPrice", parentColumn = "priceSource_id", createdAt = true,
		writeMode = StockWriteMode.APPEND_ONLY)
public class StockSecurityPrice extends StockDao {

	private int priceSourceId;
	private Integer importRecordId;
	private LocalDateTime quotedAt;
	private long priceE8;
	private Currency quoteCurrency;
	private StockQuotationType quotationType;
	private StockPriceBasis priceBasis;
	private StockPriceType priceType;
	private Long volumeE9;
	private String externalReference;
	private boolean deleted;
	private Integer supersedesPriceId;

	public int getPriceSourceId() { return priceSourceId; }
	public void setPriceSourceId(int priceSourceId) { this.priceSourceId = priceSourceId; }
	public Integer getImportRecordId() { return importRecordId; }
	public void setImportRecordId(Integer importRecordId) { this.importRecordId = importRecordId; }
	public LocalDateTime getQuotedAt() { return quotedAt; }
	public void setQuotedAt(LocalDateTime quotedAt) { this.quotedAt = quotedAt; }
	public long getPriceE8() { return priceE8; }
	public void setPriceE8(long priceE8) { this.priceE8 = priceE8; }
	public Currency getQuoteCurrency() { return quoteCurrency; }
	public void setQuoteCurrency(Currency quoteCurrency) { this.quoteCurrency = quoteCurrency; }
	public StockQuotationType getQuotationType() { return quotationType; }
	public void setQuotationType(StockQuotationType quotationType) { this.quotationType = quotationType; }
	public StockPriceBasis getPriceBasis() { return priceBasis; }
	public void setPriceBasis(StockPriceBasis priceBasis) { this.priceBasis = priceBasis; }
	public StockPriceType getPriceType() { return priceType; }
	public void setPriceType(StockPriceType priceType) { this.priceType = priceType; }
	public Long getVolumeE9() { return volumeE9; }
	public void setVolumeE9(Long volumeE9) { this.volumeE9 = volumeE9; }
	public String getExternalReference() { return externalReference; }
	public void setExternalReference(String externalReference) { this.externalReference = externalReference; }
	public boolean isDeleted() { return deleted; }
	public void setDeleted(boolean deleted) { this.deleted = deleted; }
	public Integer getSupersedesPriceId() { return supersedesPriceId; }
	public void setSupersedesPriceId(Integer supersedesPriceId) { this.supersedesPriceId = supersedesPriceId; }
}
