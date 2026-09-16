package de.zft2.gbanking.gui.enu;

public enum ExportType {

	BOOKINGS_CSV(FileType.CSV),
	BOOKINGS_FP3(FileType.FP3),
	BOOKINGS_MT940(FileType.MT940),
	BOOKINGS_XML(FileType.XML),
	MONEYTRANSFERS_CSV(FileType.CSV),
	MONEYTRANSFERS_SEPA_XML(FileType.XML),
	STOCK_PP_XML(FileType.XML),
	STOCK_PP_TRANSACTIONS_CSV(FileType.CSV),
	STOCK_PP_ACCOUNT_TRANSACTIONS_CSV(FileType.CSV),
	STOCK_PP_SECURITIES_CSV(FileType.CSV),
	STOCK_PP_PRICES_CSV(FileType.CSV);

	private final FileType fileType;

	private ExportType(FileType fileType) {
		this.fileType = fileType;
	}

	public FileType getFileType() {
		return fileType;
	}

	@Override
	public final String toString() {
		return name();
	}
}
