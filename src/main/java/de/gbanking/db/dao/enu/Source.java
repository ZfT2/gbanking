package de.gbanking.db.dao.enu;

import de.gbanking.db.enu.IdType;

public enum Source implements IdType {

	ONLINE("Online Abruf Fin/TS", "O", false, 1),
	ONLINE_PRENO("Online - vorgemerkt", "U", false, 2),
	AUTO_ADJUSTING("Ausgleichsbuchung", "A", false, 3),
	AUTO_PRENO("autom. vorgemerkt", "V", false, 4),
	MANUELL("manuelle Anlage", "M", false, 5),
	MONEYTRANSFER("Zahlungsauftrag", "Z", false, 6),
	IMPORT("Datenimport", "I", false, 7),
	IMPORT_INITIAL("Datenimport (initial)", "B", false, 8),

	ONLINE_NEW("Online Abruf Fin/TS", "O", true, 10),
	ONLINE_PRENO_NEW("Online - vorgemerkt", "U", true, 11),
	AUTO_ADJUSTING_NEW("Ausgleichsbuchung", "A", true, 12),
	AUTO_PRENO_NEW("autom. vorgemerkt", "V", true, 13),
	MANUELL_NEW("manuelle Anlage", "M", true, 14),
	MONEYTRANSFER_NEW("Zahlungsauftrag", "Z", true, 15),
	IMPORT_NEW("Datenimport", "I", true, 16),
	IMPORT_INITIAL_NEW("Datenimport (initial)", "B", true, 17);

	private final String description;
	private final String symbol;
	private final boolean isNew;
	private final int dbStateId;

	private Source(String translation, String symbol, boolean isNew, int dbStateId) {
		this.description = translation;
		this.symbol = symbol;
		this.isNew = isNew;
		this.dbStateId = dbStateId;
	}

	public static Source forString(String strValue) {
		for (Source x : values()) {
			if (x.description.equals(strValue))
				return x;
		}
		return null;
	}

	public static Source forInt(int intValue) {
		return IdType.forId(Source.class, intValue);
	}

	public String getSymbol() {
		return symbol;
	}

	public boolean isNew() {
		return isNew;
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	@Override
	public final String toString() {
		return description;
	}

	public final Source getCorresponding() {
		return valueOf(this.isNew ? this.name().substring(0, this.name().length() - 4) : this.name() + "_NEW");
	}

}
