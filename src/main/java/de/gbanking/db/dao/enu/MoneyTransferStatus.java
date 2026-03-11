package de.gbanking.db.dao.enu;

import de.gbanking.db.enu.StateType;

public enum MoneyTransferStatus implements StateType {
	
	NEW("neu"),
	ERROR("Fehler"), 
	SENT("gesendet");

	public static MoneyTransferStatus forString(String strValue) {
		for (MoneyTransferStatus x : values()) {
			if (x.translation.equals(strValue))
				return x;
		}
		return null;
	}

	private final String translation;

	private MoneyTransferStatus(String translation) {
		this.translation = translation;
	}

	@Override
	public final String toString() {
		return translation;
	}

}
