package de.zft2.gbanking.enu;

public interface GBankingEnum<E extends Enum<?>> {
	
	public String getDescription();
	
	public default GBankingEnum<?> forString(String strValue) {
		GBankingEnum<?>[] vals = this.getClass().getEnumConstants();
		for (GBankingEnum<?> x : vals) {
			if (x.getDescription().equals(strValue))
				return x;
		}
		return null;
	}

}
