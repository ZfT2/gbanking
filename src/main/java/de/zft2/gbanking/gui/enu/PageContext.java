package de.zft2.gbanking.gui.enu;

public enum PageContext {
	
	ACCOUNTS_TRANSACTIONS("Konten/Umsätze"),
	ALL_ACCOUNTS("Alle Konten"),
	ALL_TRANSACTIONS("Alle Umsätze"),
	ACCOUNTS_MONEYTRANSFERS("Aufträge"), 
	BANKACCESS("Bankzugänge"),
	RECIPIENTS("Adressbuch"),
	CATEGORIES("Kategorien");

	public static PageContext forString(String strValue) {
		for (PageContext x : values()) {
			if (x.description.equals(strValue))
				return x;
		}
		return null;
	}

	private final String description;

	private PageContext(String description) {
		this.description = description;
	}

	@Override
	public final String toString() {
		return description;
	}

}
