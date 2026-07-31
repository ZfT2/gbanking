package de.zft2.gbanking.gui.enu;

import java.util.Arrays;

import de.zft2.gbanking.enu.GBankingEnum;

public enum FileType implements GBankingEnum {
	
	CSV(".csv"),
	XML(".xml"),
	FP3(".fp3", ".xml"),
	MT940(".sta", ".mt940", ".mta");

	private final String[] suffixes;

	private FileType(String... suffixes) {
		this.suffixes = suffixes;
	}

	public String getSuffix() {
		return suffixes[0];
	}

	public String[] getExtensionPatterns() {
		return Arrays.stream(suffixes).map(suffix -> "*" + suffix).toArray(String[]::new);
	}

	@Override
	public final String toString() {
		return getDisplayName();
	}

}
