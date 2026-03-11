package de.gbanking.db.dao.enu;

public enum Source {

	ONLINE("Online Abruf Fin/TS", "O", false), 
	ONLINE_PRENO("Online - vorgemerkt", "U", false),
	AUTO_ADJUSTING("Ausgleichsbuchung", "A", false), 
	AUTO_PRENO("autom. vorgemerkt", "V", false), 
	MANUELL("manuelle Anlage", "M", false),
	MONEYTRANSFER("Zahlungsauftrag", "Z", false), 
	IMPORT("Datenimport", "I", false), 
	IMPORT_INITIAL("Datenimport (initial)", "B", false),

	ONLINE_NEW("Online Abruf Fin/TS", "O", true), 
	ONLINE_PRENO_NEW("Online - vorgemerkt", "U", true),
	AUTO_ADJUSTING_NEW("Ausgleichsbuchung", "A", true), 
	AUTO_PRENO_NEW("autom. vorgemerkt", "V", true),
	MANUELL_NEW("manuelle Anlage", "M", true), 
	MONEYTRANSFER_NEW("Zahlungsauftrag", "Z", true), 
	IMPORT_NEW("Datenimport", "I", true),
	IMPORT_INITIAL_NEW("Datenimport (initial)", "B", true);

	public static Source forString(String strValue) {
		for (Source x : values()) {
			if (x.translation.equals(strValue))
				return x;
		}
		return null;
	}

	private final String translation;
	private final String symbol;
	private final boolean isNew;

	private Source(String translation, String symbol, boolean isNew) {
		this.translation = translation;
		this.symbol = symbol;
		this.isNew = isNew;
	}

	public String getSymbol() {
		return symbol;
	}

	public boolean isNew() {
		return isNew;
	}

	@Override
	public final String toString() {
		return translation;
	}

	public final Source getCorresponding() {
		return valueOf(this.isNew ? this.name().substring(0, this.name().length() - 4) : this.name() + "_NEW");
	}

}
