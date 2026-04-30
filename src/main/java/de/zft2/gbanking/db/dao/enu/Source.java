package de.zft2.gbanking.db.dao.enu;

import static de.zft2.gbanking.db.dao.enu.SourceGroup.GROUP_AUTO;
import static de.zft2.gbanking.db.dao.enu.SourceGroup.GROUP_IMPORT;
import static de.zft2.gbanking.db.dao.enu.SourceGroup.GROUP_MANUELL;
import static de.zft2.gbanking.db.dao.enu.SourceGroup.GROUP_MONEYTRANSFER;
import static de.zft2.gbanking.db.dao.enu.SourceGroup.GROUP_ONLINE;

import de.zft2.gbanking.db.enu.IdType;

public enum Source implements IdType {

	ONLINE(SourceGroup.GROUP_ONLINE, "Online Abruf Fin/TS", "O", false, 1),
	ONLINE_PRENO(GROUP_ONLINE, "Online - vorgemerkt", "U", false, 2),
	AUTO_ADJUSTING(GROUP_AUTO, "Ausgleichsbuchung", "A", false, 3),
	AUTO_PRENO(GROUP_AUTO, "autom. vorgemerkt", "V", false, 4),
	MANUELL(GROUP_MANUELL, "manuelle Anlage", "M", false, 5),
	MONEYTRANSFER(GROUP_MONEYTRANSFER, "Zahlungsauftrag", "Z", false, 6),
	IMPORT(GROUP_IMPORT, "Datenimport", "I", false, 7),
	IMPORT_INITIAL(GROUP_IMPORT, "Datenimport (initial)", "B", false, 8),

	ONLINE_NEW(GROUP_ONLINE, "Online Abruf Fin/TS", "O", true, 10),
	ONLINE_PRENO_NEW(GROUP_ONLINE, "Online - vorgemerkt", "U", true, 11),
	AUTO_ADJUSTING_NEW(GROUP_AUTO, "Ausgleichsbuchung", "A", true, 12),
	AUTO_PRENO_NEW(GROUP_AUTO, "autom. vorgemerkt", "V", true, 13),
	MANUELL_NEW(GROUP_MANUELL, "manuelle Anlage", "M", true, 14),
	MONEYTRANSFER_NEW(GROUP_MANUELL, "Zahlungsauftrag", "Z", true, 15),
	IMPORT_NEW(GROUP_IMPORT, "Datenimport", "I", true, 16),
	IMPORT_INITIAL_NEW(GROUP_IMPORT, "Datenimport (initial)", "B", true, 17);

	private final SourceGroup group;
	private final String description;
	private final String symbol;
	private final boolean isNew;
	private final int dbStateId;

	private Source(SourceGroup group, String translation, String symbol, boolean isNew, int dbStateId) {
		this.group = group;
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

	public SourceGroup getGroup() {
		return group;
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
