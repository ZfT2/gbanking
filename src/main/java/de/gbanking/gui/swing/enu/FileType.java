package de.gbanking.gui.swing.enu;

import de.gbanking.enu.GBankingEnum;

public enum FileType implements GBankingEnum<FileType> {
	
	CSV("CSV Dateien", ".csv"),
	XML("XML Dateien", ".xml" ),
	FP3("FP3 Dateien", ".fp3");

//	public static ExportFileType forString(String strValue) {
//		for (ExportFileType x : values()) {
//			if (x.description.equals(strValue))
//				return x;
//		}
//		return null;
//	}

	private final String description;
	private final String suffix;

	private FileType(String description, String suffix) {
		this.description = description;
		this.suffix = suffix;
	}

	public String getSuffix() {
		return suffix;
	}

	@Override
	public final String toString() {
		return description;
	}

	@Override
	public String getDescription() {
		return description;
	}

}
