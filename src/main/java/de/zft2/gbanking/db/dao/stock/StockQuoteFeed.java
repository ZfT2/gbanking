package de.zft2.gbanking.db.dao.stock;

@StockTable(name = "stockQuoteFeed", idColumn = "source_id", generatedId = false,
		createdAt = true, updatedAt = true)
public class StockQuoteFeed extends StockDao {

	private String uuid;
	private int sortOrder;
	private String urlTemplate;
	private String format;
	private String secret;
	private String headerName;
	private String headerValue;
	private String itemsPath;
	private String datePath;
	private String pricePath;
	private String dateFormat;
	private String csvDelimiter;

	public int getSourceId() { return getId(); }
	public void setSourceId(int sourceId) { setId(sourceId); }
	public String getUuid() { return uuid; }
	public void setUuid(String uuid) { this.uuid = uuid; }
	public int getSortOrder() { return sortOrder; }
	public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
	public String getUrlTemplate() { return urlTemplate; }
	public void setUrlTemplate(String urlTemplate) { this.urlTemplate = urlTemplate; }
	public String getFormat() { return format; }
	public void setFormat(String format) { this.format = format; }
	public String getSecret() { return secret; }
	public void setSecret(String secret) { this.secret = secret; }
	public String getHeaderName() { return headerName; }
	public void setHeaderName(String headerName) { this.headerName = headerName; }
	public String getHeaderValue() { return headerValue; }
	public void setHeaderValue(String headerValue) { this.headerValue = headerValue; }
	public String getItemsPath() { return itemsPath; }
	public void setItemsPath(String itemsPath) { this.itemsPath = itemsPath; }
	public String getDatePath() { return datePath; }
	public void setDatePath(String datePath) { this.datePath = datePath; }
	public String getPricePath() { return pricePath; }
	public void setPricePath(String pricePath) { this.pricePath = pricePath; }
	public String getDateFormat() { return dateFormat; }
	public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }
	public String getCsvDelimiter() { return csvDelimiter; }
	public void setCsvDelimiter(String csvDelimiter) { this.csvDelimiter = csvDelimiter; }
}
