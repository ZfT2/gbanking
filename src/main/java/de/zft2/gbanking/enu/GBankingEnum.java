package de.zft2.gbanking.enu;

public interface GBankingEnum extends LocalizedEnumValue {
	
	default String getDescription() {
		return getDisplayName();
	}
	
	default GBankingEnum forString(String strValue) {
		GBankingEnum[] vals = this.getClass().getEnumConstants();
		for (GBankingEnum x : vals) {
			if (x.matches(strValue))
				return x;
		}
		return null;
	}

}
