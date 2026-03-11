package de.gbanking.db.dao.enu;

public enum ParameterDataType {
	
	BPD("Bank-Parameter-Daten (BPD)"),
	UPD("User-Parameter-Daten (UPD)");

	public static ParameterDataType forString(String strValue) {
		for (ParameterDataType x : values()) {
			if (x.description.equals(strValue))
				return x;
		}
		return null;
	}

	private final String description;

	private ParameterDataType(String description) {
		this.description = description;
	}

	@Override
	public final String toString() {
		return description;
	}

}
