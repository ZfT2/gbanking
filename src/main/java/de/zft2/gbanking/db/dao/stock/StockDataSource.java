package de.zft2.gbanking.db.dao.stock;

import de.zft2.gbanking.db.dao.enu.StockDataSourceType;

@StockTable(name = "stockDataSource", updatedAt = true)
public class StockDataSource extends StockDao {

	private String sourceCode;
	private String sourceName;
	private StockDataSourceType sourceType;
	private int defaultPriority = 100;
	private boolean enabled = true;

	public String getSourceCode() {
		return sourceCode;
	}

	public void setSourceCode(String sourceCode) {
		this.sourceCode = sourceCode;
	}

	public String getSourceName() {
		return sourceName;
	}

	public void setSourceName(String sourceName) {
		this.sourceName = sourceName;
	}

	public StockDataSourceType getSourceType() {
		return sourceType;
	}

	public void setSourceType(StockDataSourceType sourceType) {
		this.sourceType = sourceType;
	}

	public int getDefaultPriority() {
		return defaultPriority;
	}

	public void setDefaultPriority(int defaultPriority) {
		this.defaultPriority = defaultPriority;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
