package de.zft2.gbanking.db.dao.stock;

import de.zft2.gbanking.db.dao.enu.StockNumericValueType;

@StockTable(name = "stockNumericScale", idColumn = "valueType", generatedId = false,
		writeMode = StockWriteMode.READ_ONLY)
public class StockNumericScale extends StockDao {

	private StockNumericValueType valueType;
	private int scaleDigits;
	private long scaleFactor;
	private String description;

	public StockNumericValueType getValueType() {
		return valueType;
	}

	public void setValueType(StockNumericValueType valueType) {
		this.valueType = valueType;
	}

	public int getScaleDigits() {
		return scaleDigits;
	}

	public void setScaleDigits(int scaleDigits) {
		this.scaleDigits = scaleDigits;
	}

	public long getScaleFactor() {
		return scaleFactor;
	}

	public void setScaleFactor(long scaleFactor) {
		this.scaleFactor = scaleFactor;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
