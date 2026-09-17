package de.zft2.gbanking.file.imp.csv;

import java.util.List;

public enum CsvImportDefinitionType {

	BOOKING("Buchung", List.of(CsvImportTarget.AMOUNT)),
	STOCK_PORTFOLIO_TRANSACTION("Depotumsatz", List.of(CsvImportTarget.STOCK_DATE,
			CsvImportTarget.STOCK_TRANSACTION_TYPE, CsvImportTarget.STOCK_VALUE,
			CsvImportTarget.STOCK_BOOKING_CURRENCY, CsvImportTarget.STOCK_QUANTITY,
			CsvImportTarget.SECURITY_NAME)),
	STOCK_ACCOUNT_TRANSACTION("Kontoumsatz", List.of(CsvImportTarget.STOCK_DATE,
			CsvImportTarget.STOCK_TRANSACTION_TYPE, CsvImportTarget.STOCK_VALUE,
			CsvImportTarget.STOCK_BOOKING_CURRENCY, CsvImportTarget.STOCK_QUANTITY,
			CsvImportTarget.SECURITY_NAME)),
	STOCK_SECURITY("Wertpapier", List.of(CsvImportTarget.SECURITY_NAME,
			CsvImportTarget.SECURITY_CURRENCY)),
	STOCK_SECURITY_PRICE("Wertpapierkurs", List.of(CsvImportTarget.STOCK_PRICE_DATE));

	private final String sectionPrefix;
	private final List<CsvImportTarget> requiredTargets;

	CsvImportDefinitionType(String sectionPrefix, List<CsvImportTarget> requiredTargets) {
		this.sectionPrefix = sectionPrefix;
		this.requiredTargets = requiredTargets;
	}

	public List<CsvImportTarget> getRequiredTargets() {
		return requiredTargets;
	}

	public boolean isStockImport() {
		return this != BOOKING;
	}

	public boolean acceptsDynamicHeaders() {
		return this == STOCK_SECURITY_PRICE;
	}

	static CsvImportDefinitionType fromSectionName(String sectionName) {
		for (CsvImportDefinitionType type : values()) {
			String prefix = type.sectionPrefix + ":";
			if (sectionName.regionMatches(true, 0, prefix, 0, prefix.length())) {
				return type;
			}
		}
		return null;
	}
}
