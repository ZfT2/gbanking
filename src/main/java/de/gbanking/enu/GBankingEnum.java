package de.gbanking.enu;

public interface GBankingEnum<E extends Enum<?>> {
	
	public String getDescription();

//	public default E getByIndex(int index) {
//		if (!this.getClass().isEnum()) {
//			// not implemented on enum, you can do as you like here
//		}
//		Enum<?>[] vals = (Enum<?>[]) this.getClass().getEnumConstants();
//		if (index < 0 || index >= vals.length) {
//			// illegal arg exception
//		}
//		return (E) vals[index];
//	}
	
//	public default String getDescription() {
//		return this.getDescription();
//	}
	
	public default GBankingEnum<?> forString(String strValue) {
		GBankingEnum<?>[] vals = this.getClass().getEnumConstants();
		for (GBankingEnum<?> x : vals) {
			if (x.getDescription().equals(strValue))
				return x;
		}
		return null;
	}

}
