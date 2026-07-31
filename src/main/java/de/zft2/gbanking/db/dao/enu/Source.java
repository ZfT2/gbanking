package de.zft2.gbanking.db.dao.enu;

import static de.zft2.gbanking.db.dao.enu.SourceGroup.GROUP_AUTO;
import static de.zft2.gbanking.db.dao.enu.SourceGroup.GROUP_IMPORT;
import static de.zft2.gbanking.db.dao.enu.SourceGroup.GROUP_MANUELL;
import static de.zft2.gbanking.db.dao.enu.SourceGroup.GROUP_MONEYTRANSFER;
import static de.zft2.gbanking.db.dao.enu.SourceGroup.GROUP_ONLINE;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum Source implements IdType, LocalizedEnumValue {

	ONLINE(SourceGroup.GROUP_ONLINE, "O", false, 1),
	ONLINE_PRENO(GROUP_ONLINE, "U", false, 2),
	AUTO_ADJUSTING(GROUP_AUTO, "A", false, 3),
	AUTO_PRENO(GROUP_AUTO, "V", false, 4),
	MANUELL(GROUP_MANUELL, "M", false, 5),
	MONEYTRANSFER(GROUP_MONEYTRANSFER, "Z", false, 6),
	IMPORT(GROUP_IMPORT, "I", false, 7),
	IMPORT_INITIAL(GROUP_IMPORT, "B", false, 8),

	ONLINE_NEW(GROUP_ONLINE, "O", true, 10),
	ONLINE_PRENO_NEW(GROUP_ONLINE, "U", true, 11),
	AUTO_ADJUSTING_NEW(GROUP_AUTO, "A", true, 12),
	AUTO_PRENO_NEW(GROUP_AUTO, "V", true, 13),
	MANUELL_NEW(GROUP_MANUELL, "M", true, 14),
	MONEYTRANSFER_NEW(GROUP_MONEYTRANSFER, "Z", true, 15),
	IMPORT_NEW(GROUP_IMPORT, "I", true, 16),
	IMPORT_INITIAL_NEW(GROUP_IMPORT, "B", true, 17);

	private final SourceGroup group;
	private final String symbol;
	private final boolean isNew;
	private final int dbStateId;

	private Source(SourceGroup group, String symbol, boolean isNew, int dbStateId) {
		this.group = group;
		this.symbol = symbol;
		this.isNew = isNew;
		this.dbStateId = dbStateId;
	}

	public static Source forString(String strValue) {
		return LocalizedEnumValue.forString(Source.class, strValue);
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

	public boolean isPrenotification() {
		return this == ONLINE_PRENO || this == ONLINE_PRENO_NEW || this == AUTO_PRENO || this == AUTO_PRENO_NEW;
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	@Override
	public final String toString() {
		return getDisplayName();
	}

	public final Source getCorresponding() {
		return valueOf(this.isNew ? this.name().substring(0, this.name().length() - 4) : this.name() + "_NEW");
	}

}
