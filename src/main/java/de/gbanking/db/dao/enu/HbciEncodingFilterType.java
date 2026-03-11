package de.gbanking.db.dao.enu;

public enum HbciEncodingFilterType {
	
	BASE64("Base64"), 
	NONE(null);

	public static HbciEncodingFilterType forString(String strValue) {
		for (HbciEncodingFilterType x : values()) {
			if (x.translation.equals(strValue))
				return x;
		}
		return null;
	}

	private final String translation;

	private HbciEncodingFilterType(String translation) {
		this.translation = translation;
	}

	public String getTranslation() {
		return translation;
	}

	@Override
	public final String toString() {
		return translation;
	}

}
