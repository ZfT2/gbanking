package de.gbanking.db.dao.enu;

public enum StandingorderMode {

	MONTHLY("monatlich"), 
	BIMONTHLY("zweimonatlich"), 
	QUARTERLY("quartalsweise"), 
	SEMI_ANNUALLY("halbjährlich"),
	ANNUALLY("jährlich");

	public static StandingorderMode forString(String strValue) {
		for (StandingorderMode x : values()) {
			if (x.translation.equals(strValue))
				return x;
		}
		return null;
	}

	private final String translation;

	private StandingorderMode(String translation) {
		this.translation = translation;
	}

	@Override
	public final String toString() {
		return translation;
	}

}
